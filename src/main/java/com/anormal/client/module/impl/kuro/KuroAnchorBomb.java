package com.anormal.client.module.impl.kuro;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.module.ModuleManager;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.NotificationManager;
import com.anormal.client.util.kuro.KuroBlockPlacer;
import com.anormal.client.util.kuro.KuroExplosionUtils;
import com.anormal.client.util.kuro.KuroServerSync;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Kuro AnchorBomb port (dev.kuro.client.module.impl.AnchorBomb'dan Anormal'da).
 *
 * <p>O'zingiz qo'yganningizdan keyin respawn anchor'ni glowstone bilan zaryadlaydi
 * va portlatadi — aynan o'yinchi qilganidet, simda ortiqcha hech narsa yo'q:</p>
 * <pre>qo'yish (siz) → glowstone tanlash → right-click anchor → eski slot → right-click anchor</pre>
 *
 * <ul>
 *   <li>Faqat SIZ qo'ygan anchor'larga reaksiya qiladi (ClientPlayerInteractionManagerMixin'dagi
 *       beforeUse / afterUse hooklari orqali).</li>
 *   <li>Har bir right-click — o'yinning o'z {@code interactBlock} i, sichqonchingiz rostanay
 *       qaragan hit natijasi bilan. Rotatsiya/look paketi yuborilmaydi: sichqoncha anchor ustiga
 *       kelguncha kutadi.</li>
 *   <li>Har bir tickda bitta interaksiya qadami, har keyingi qadam server javobini kutadi
 *       (KuroServerSync). Slot almashtirishlar oddiy tanlangan-slot yo'li bilan.</li>
 *   <li>Nether'da hech narsa qilmaydi (anchor portlamaydi) va Auto Anchor yoqilgan paytda
 *       ham (u o'z ketma-ketligini o'zi yurgizadi).</li>
 *   <li>Glowstone hotbar'da bo'lishi kerak. Bitta glowstone yetarli: portlash zaryad
 *       darajasiga bog'liq emas.</li>
 * </ul>
 */
public class KuroAnchorBomb extends Module {
    private static final int MAX_AGE = 100;          // tugallanmagan ketma-ketlik shu qadar tickda tashlanadi
    private static final int SERVER_TIMEOUT = 5;     // server tranzaksiyasini kutish chegarasi

    private static KuroAnchorBomb instance;
    private static BlockPos pendingTarget;
    private static boolean preAnchor;

    private final BooleanSetting explode = new BooleanSetting("Detonate", "Detonate after charging", true);
    private final BooleanSetting limitSelf = new BooleanSetting("Limit self damage", "Do not detonate when too much self damage", false);
    private final NumberSetting maxSelf = new NumberSetting("Max self damage", "Self damage limit (with the setting above)", 10.0, 0.0, 36.0, 0.5);
    private final NumberSetting delay = new NumberSetting("Extra delay (ticks)", "Extra ticks between steps", 0.0, 0.0, 10.0, 1.0);

    private enum Phase { IDLE, GLOW_SELECT, CHARGE_CLICK, CHARGED_WAIT, EXPLODE_SELECT, EXPLODE_CLICK, RESTORE }
    private Phase phase = Phase.IDLE;
    private BlockPos pos;
    private int origSlot, age, wait;
    private boolean swapped, ownClick;

    public KuroAnchorBomb() {
        super("Anchor Bomb", "Charges and detonates the anchor you place (kuro port)", Category.COMBAT);
        instance = this;
        addSetting(explode);
        addSetting(limitSelf);
        addSetting(maxSelf);
        addSetting(delay);
    }

    @Override
    public void onDisable() {
        if (swapped && mc.player != null) KuroBlockPlacer.selectForce(origSlot);
        finish();
    }

    // ---------------- hook'lar (ClientPlayerInteractionManagerMixin'dan chaqiriladi) ----------------

    /** Vanilla o'yinchi har bir blok-qo'llashini atrofida ishlaydi. */
    public static void beforeUse(BlockHitResult hit) {
        pendingTarget = null;
        KuroAnchorBomb m = instance;
        if (m == null || !m.isEnabled() || m.ownClick || mc.world == null) return;
        BlockPos clicked = hit.getBlockPos();
        // BlockPlaceContext kabi: almashtiriladigan blok (o't...) o'zi almashtiriladi, qolganiga quriladi
        pendingTarget = mc.world.getBlockState(clicked).isReplaceable()
                ? clicked
                : clicked.offset(hit.getSide());
        preAnchor = mc.world.getBlockState(pendingTarget).isOf(net.minecraft.block.Blocks.RESPAWN_ANCHOR);
    }

    public static void afterUse(BlockHitResult hit, ActionResult result) {
        KuroAnchorBomb m = instance;
        BlockPos target = pendingTarget;
        pendingTarget = null;
        if (m == null || target == null || !m.isEnabled() || m.ownClick || m.phase != Phase.IDLE || mc.world == null) return;
        if (KuroSafeAnchor.running()) return;                       // Safe Anchor o'z ketma-ketligini yurgizadi
        if (preAnchor || !result.isAccepted() || mc.world.getRegistryKey() == World.NETHER) return;
        if (ModuleManager.getModule(KuroAutoAnchor.class) instanceof KuroAutoAnchor a && a.isEnabled()) return;
        var st = mc.world.getBlockState(target);
        if (st.isOf(net.minecraft.block.Blocks.RESPAWN_ANCHOR) && KuroAutoAnchor.anchorCharge(target) == 0) m.start(target);
    }

    private void start(BlockPos target) {
        pos = target;
        origSlot = KuroBlockPlacer.selected();
        age = 0;
        wait = (int) delay.getAsDouble();
        swapped = false;
        phase = Phase.GLOW_SELECT;
    }

    // ---------------- ketma-ketlik (tick boshi, o'yinchining tugma bosishlari bu yerda ishlanadi) ----------------

    @Override
    public void onTick() {
        if (phase == Phase.IDLE) return;
        if (mc.player == null || mc.world == null || mc.interactionManager == null) { finish(); return; }
        if (mc.currentScreen != null) return;                        // menyu ochiq: o'yinchi kabi kutyapti
        if (++age > MAX_AGE) { abort("Gave up (took too long)"); return; }
        if (wait > 0) { wait--; return; }
        if (phase != Phase.RESTORE && phase != Phase.EXPLODE_CLICK && !isAnchor()) { abort("The anchor is gone"); return; }

        switch (phase) {
            case GLOW_SELECT -> glowSelect();
            case CHARGE_CLICK -> chargeClick();
            case CHARGED_WAIT -> chargedWait();
            case EXPLODE_SELECT -> explodeSelect();
            case EXPLODE_CLICK -> explodeClick();
            case RESTORE -> restore();
            default -> { }
        }
    }

    private void glowSelect() {
        if (mc.player.getMainHandStack().isOf(Items.GLOWSTONE)) { phase = Phase.CHARGE_CLICK; chargeClick(); return; }
        int slot = KuroBlockPlacer.findSlot(s -> s.isOf(Items.GLOWSTONE));
        if (slot < 0) { abort("No glowstone in your hotbar"); return; }
        if (!KuroServerSync.ready(SERVER_TIMEOUT) || !KuroBlockPlacer.select(slot)) return;   // bu tickda rad etildi: keyingi tick
        KuroServerSync.beginStep();
        swapped = true;
        wait = (int) delay.getAsDouble();
        phase = Phase.CHARGE_CLICK;
    }

    private void chargeClick() {
        if (!mc.player.getMainHandStack().isOf(Items.GLOWSTONE)) { phase = Phase.GLOW_SELECT; return; }
        BlockHitResult hit = lookingAtAnchor();
        if (hit == null || !canClick()) return;                      // nishon boshqa joyda / hali sneaking: kut
        if (click(hit)) { wait = (int) delay.getAsDouble(); phase = Phase.CHARGED_WAIT; }
    }

    private void chargedWait() {
        if (!KuroServerSync.confirmed(SERVER_TIMEOUT)) return;       // charge klikning server javobini kutiw turnulates
        int charge = KuroAutoAnchor.anchorCharge(pos);
        if (charge > 0) phase = explode.isEnabled() ? Phase.EXPLODE_SELECT : Phase.RESTORE;
        else phase = Phase.CHARGE_CLICK;                             // server olmadi: yana kliklidi (click again)
    }

    private void explodeSelect() {
        if (!mc.player.getMainHandStack().isOf(Items.GLOWSTONE)) { phase = Phase.EXPLODE_CLICK; explodeClick(); return; }
        // glowstone bilan qo'l zaryadlashimsini preventing (anchor detonate o'mi ning yo'q), shuning uchun oldingi slotga qaytamiz
        int slot = !mc.player.getInventory().getStack(origSlot).isOf(Items.GLOWSTONE)
                ? origSlot
                : KuroBlockPlacer.findSlot(s -> !s.isOf(Items.GLOWSTONE));
        if (slot < 0) { abort("No slot to hold instead of glowstone"); return; }
        if (!KuroServerSync.ready(SERVER_TIMEOUT) || !KuroBlockPlacer.select(slot)) return;
        KuroServerSync.beginStep();
        swapped = slot != origSlot;
        wait = (int) delay.getAsDouble();
        phase = Phase.EXPLODE_CLICK;
    }

    private void explodeClick() {
        if (mc.player.getMainHandStack().isOf(Items.GLOWSTONE)) { phase = Phase.EXPLODE_SELECT; return; }
        if (!KuroAutoAnchor.isAnchor(pos)) { phase = Phase.RESTORE; return; }
        BlockHitResult hit = lookingAtAnchor();
        if (hit == null || !canClick()) return;
        double self = KuroExplosionUtils.getAnchorDamageTo(mc.player, pos);
        if (limitSelf.isEnabled() && self > maxSelf.getValue()) {
            NotificationManager.info("Anchor Bomb", String.format("Left charged: ~%.0f damage to you", self));
            phase = Phase.RESTORE;
            return;
        }
        if (click(hit)) phase = Phase.RESTORE;
    }

    private void restore() {
        if (swapped && KuroBlockPlacer.selected() != origSlot) {
            if (!KuroServerSync.ready(SERVER_TIMEOUT) || !KuroBlockPlacer.select(origSlot)) return;
            KuroServerSync.beginStep();
        }
        swapped = false;
        finish();
    }

    // ---------------- yordamchilar ----------------

    /** Sichqonchaning o'ziqqa blok hit'i — bizning anchor ustiga. Bu rostanay right-click yuboradigan narsan nima. */
    private BlockHitResult lookingAtAnchor() {
        return mc.crosshairTarget instanceof BlockHitResult b
                && b.getType() == HitResult.Type.BLOCK
                && b.getBlockPos().equals(pos) ? b : null;
    }

    /** Vanilla right-click sharti xuddi shula: item ishlatsa emas, sneakingda emas (sneaking blok interaction skip). */
    private boolean canClick() {
        return !mc.player.isUsingItem() && !mc.player.isSneaking() && KuroServerSync.ready(SERVER_TIMEOUT);
    }

    /** Vanilla usul: ClientPlayerInteractionManager.interactBlock (use paketi tartib raqami bilan), consume bo'lsa swing. */
    private boolean click(BlockHitResult hit) {
        if (!mc.world.getWorldBorder().contains(hit.getBlockPos())) return false;
        ownClick = true;
        try {
            ActionResult r = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
            if (!r.isAccepted()) return false;
            mc.player.swingHand(Hand.MAIN_HAND);
            KuroServerSync.beginStep();
            return true;
        } finally {
            ownClick = false;
        }
    }

    private boolean isAnchor() { return pos != null && mc.world != null && mc.world.getBlockState(pos).isOf(net.minecraft.block.Blocks.RESPAWN_ANCHOR); }

    private void abort(String why) {
        NotificationManager.info("Anchor Bomb", why);
        phase = swapped ? Phase.RESTORE : Phase.IDLE;
        if (phase == Phase.IDLE) finish();
    }

    private void finish() {
        phase = Phase.IDLE;
        pos = null;
        swapped = false;
    }
}
