package com.anormal.client.module.impl.kuro;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.module.impl.client.AntiBot;
import com.anormal.client.module.impl.client.Friends;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.NotificationManager;
import com.anormal.client.util.kuro.KuroBlockPlacer;
import com.anormal.client.util.kuro.KuroExplosionUtils;
import com.anormal.client.util.kuro.KuroRotations;
import com.anormal.client.util.kuro.KuroServerSync;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.RespawnAnchorBlock;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

/**
 * Kuro SafeAnchor port (dev.kuro.client.module.impl.SafeAnchor'dan Anormal'da).
 *
 * <p>Bitta-tugma makro. Anchor qo'ymoqchi bo'lgan blokqa qarang va modulni yoqing
 * (yoki keybind bos):</p>
 * <pre>anchor qo'yish → glowstone bilan zaryadlash → glowstone preload blok → portlash</pre>
 *
 * <ul>
 *   <li>"Place near player" (default on): range'dagi eng yaqin dushman o'yinchi nishon
 *       bo'ladi va anchor uni tanasi eng yaqin erkin, tegimga mumkin bo'lgan joyga qo'yiladi
 *       (sizning va o'zi tanasiga emas qo'masummanga). O'chirilgan bo'lsa crosshair'an
 *       normal right-click yuboradigan joyga qo'yadi; agar blokqa qaramayotgan bo'lsangiz
 *       (yoki dushman yaqinida joy yo'q bo'lsa) — dushmanga eng yaqin, siz teg oladigan blokka qo'yadi.</li>
 *   <li>"Silent aim" (default on): aim faqat server tomonda (KuroRotations, har bir кликdan oldin
 *       look paketi), yurishni silliq buradi (aim assist uslubi: qolgan burchakning qismi per tick,
 *       "Aim speed" bilan chegaralanadi). Klik o'zi nuqtaga bo'lguncha kutadi. Kamerangiz hech
 *       qachon qimizmaydi. Off = клики oldingi real ko'rinish bilan chiqadi.</li>
 *   <li>"Cover" blok avtomatik tanlanadi: anchor atrofidagi erkin joydan tanangizni eng ko'p
 *       saqlaydiganini (portlash zarari portlashga ko'rinishga qarab "kerak") tanlaydi.
 *       Anchor o'zini boshqa zaryad kelib qo'ymasligiga qarshi kemlik blokqa qo'yiladi.</li>
 *   <li>Cover uchun joy bo'lmasa (yoki qo'yib bo'lmasa) baribir portlaydi. "Require cover"
 *       (default off) portlashdan oldin to'htytadigan edi va anchor zaryadlangan holda qoladi.</li>
 *   <li>Hotbar'dagi: Respawn Anchor va kamida 2 ta Glowstone (1 zaryad + 1 cover). Nether'da emas.</li>
 *   <li>Anchor Bomb bilan bir xil pagination: har bir right-click o'yinning o'z interactBlock'i,
 *       bitta qadam per tick, har qadam server javobini kutadi (KuroServerSync). Tugallaganda
 *       o'zini o'chiradi va eski slotni qaytaradi.</li>
 * </ul>
 */
public class KuroSafeAnchor extends Module {
    private static final int MAX_AGE = 160;
    private static final int SERVER_TIMEOUT = 5;
    private static final int MAX_RETRIES = 3;
    private static final double REACH = 4.5;

    private static KuroSafeAnchor instance;

    private final BooleanSetting nearPlayer = new BooleanSetting("Place near player", "Anchors the spot closest to the nearest enemy", true);
    private final NumberSetting targetRange = new NumberSetting("Target range", "Enemy search radius", 6.0, 2.0, 8.0, 0.5);
    private final BooleanSetting silentAim = new BooleanSetting("Silent aim", "Aim server-side only", true);
    private final NumberSetting aimSpeed = new NumberSetting("Aim speed (deg/tick)", "Silent aim turn speed", 45.0, 5.0, 180.0, 5.0);
    private final BooleanSetting requireCover = new BooleanSetting("Require cover", "Do not detonate without the cover block", false);
    private final NumberSetting delay = new NumberSetting("Extra delay (ticks)", "Extra ticks between steps", 0.0, 0.0, 10.0, 1.0);

    private enum Phase { IDLE, INIT, PLACE, PLACE_WAIT, CHARGE, CHARGE_WAIT, COVER, COVER_WAIT, EXPLODE, RESTORE }
    private Phase phase = Phase.IDLE;
    private BlockHitResult placeHit;
    private BlockPos pos, coverPos;
    private int origSlot, age, wait, retries;
    private boolean swapped;
    private double exposureAfter = 1.0;
    private PlayerEntity target;
    private float curYaw = Float.NaN, curPitch = Float.NaN;   // server-side aim hozir qayerda

    public KuroSafeAnchor() {
        super("Safe Anchor", "Place, charge, cover, detonate — one key (kuro port)", Category.COMBAT);
        instance = this;
        addSetting(nearPlayer);
        addSetting(targetRange);
        addSetting(silentAim);
        addSetting(aimSpeed);
        addSetting(requireCover);
        addSetting(delay);
    }

    /** Makro ishlayotgan paytda true (Anchor Bomb uning yo'ldan chetda turadi). */
    public static boolean running() { return instance != null && instance.phase != Phase.IDLE; }

    @Override
    public void onEnable() {
        phase = Phase.INIT;
        age = 0; wait = 0; retries = 0;
        swapped = false;
        pos = null; coverPos = null;
        target = null;
        curYaw = Float.NaN; curPitch = Float.NaN;
    }

    @Override
    public void onDisable() {
        if (swapped && mc.player != null) KuroBlockPlacer.selectForce(origSlot);
        KuroRotations.release(this);
        phase = Phase.IDLE;
        pos = null; coverPos = null; target = null;
        swapped = false;
    }

    // ---------------- ketma-ketlik ----------------

    @Override
    public void onTick() {
        if (phase == Phase.IDLE) return;
        if (mc.player == null || mc.world == null || mc.interactionManager == null) { finish(); return; }
        if (phase == Phase.INIT) { init(); return; }
        if (mc.currentScreen != null) return;
        if (++age > MAX_AGE) { abort("Gave up (took too long)"); return; }
        if (wait > 0) { wait--; return; }
        if (phase != Phase.PLACE && phase != Phase.PLACE_WAIT && phase != Phase.RESTORE && !isAnchor()) { abort("The anchor is gone"); return; }

        switch (phase) {
            case PLACE -> place();
            case PLACE_WAIT -> placeWait();
            case CHARGE -> charge();
            case CHARGE_WAIT -> chargeWait();
            case COVER -> cover();
            case COVER_WAIT -> coverWait();
            case EXPLODE -> explode();
            case RESTORE -> restore();
            default -> { }
        }
    }

    private void init() {
        if (mc.currentScreen != null) { fail("Press its key bind in game (a menu is open)"); return; }
        if (mc.world.getRegistryKey() == World.NETHER) { fail("Anchors do not explode in the Nether"); return; }
        if (KuroBlockPlacer.findSlot(s -> s.isOf(Items.RESPAWN_ANCHOR)) < 0) { fail("No Respawn Anchor in your hotbar"); return; }
        int need = requireCover.isEnabled() ? 2 : 1;
        if (glowstoneCount() < need) {
            fail(need == 2 ? "Needs 2 glowstone blocks in your hotbar (charge + cover)" : "No glowstone in your hotbar");
            return;
        }

        BlockHitResult hit = null;
        BlockPos spot = null;
        target = bestTarget();
        if (nearPlayer.isEnabled() && target != null) {
            hit = spotNear(target);
            if (hit != null) spot = hit.getBlockPos().offset(hit.getSide());
        }
        if (spot == null && mc.crosshairTarget instanceof BlockHitResult ch && ch.getType() == HitResult.Type.BLOCK) {
            BlockPos clicked = ch.getBlockPos();
            BlockPos s2 = mc.world.getBlockState(clicked).isReplaceable() ? clicked : clicked.offset(ch.getSide());
            if (!mc.world.getBlockState(s2).isReplaceable()) { fail("That spot is taken"); return; }
            if (new Box(s2).intersects(mc.player.getBoundingBox())) { fail("The anchor would be inside you"); return; }
            if (mc.player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(s2)) > REACH * REACH) { fail("Too far away"); return; }
            hit = ch;
            spot = s2;
        }
        if (spot == null && target != null) {                // blokqa qaramayapti: dushmanga eng yaqin tegiladigan blok
            hit = spotNear(target);
            if (hit != null) spot = hit.getBlockPos().offset(hit.getSide());
        }
        if (spot == null) {
            fail(target == null ? "Look at a block (or get a player in range)" : "No block near the enemy is in reach");
            return;
        }

        placeHit = hit;
        pos = spot;
        origSlot = KuroBlockPlacer.selected();
        wait = (int) delay.getAsDouble();
        phase = Phase.PLACE;
    }

    private void place() {
        if (!hold(Items.RESPAWN_ANCHOR)) return;
        if (!mc.world.getBlockState(pos).isReplaceable()) { abort("The spot got taken"); return; }
        if (!aim(placeHit.getPos())) return;
        if (!canClick()) return;
        if (click(placeHit)) { wait = (int) delay.getAsDouble(); phase = Phase.PLACE_WAIT; }
    }

    private void placeWait() {
        if (!KuroServerSync.confirmed(SERVER_TIMEOUT)) return;
        if (isAnchor()) { phase = Phase.CHARGE; return; }
        if (++retries > MAX_RETRIES) { abort("The server did not accept the anchor"); return; }
        phase = Phase.PLACE;
    }

    private void charge() {
        if (!hold(Items.GLOWSTONE)) return;
        BlockHitResult hit = anchorHit();
        if (!aim(hit.getPos())) return;
        if (!canClick()) return;
        if (click(hit)) { wait = (int) delay.getAsDouble(); phase = Phase.CHARGE_WAIT; }
    }

    private void chargeWait() {
        if (!KuroServerSync.confirmed(SERVER_TIMEOUT)) return;
        if (chargeLevel() > 0) { retries = 0; planCover(); return; }
        if (++retries > MAX_RETRIES) { abort("The server did not charge the anchor"); return; }
        phase = Phase.CHARGE;
    }

    /** Cover joyini topadi. Kerak emas bo'lsa COVER bosqichini o'tkazib yuboradi, foyda keltirmasa to'htatti. */
    private void planCover() {
        Vec3d blast = Vec3d.ofCenter(pos);
        double base = exposure(blast, null);
        if (base <= 0.0) { exposureAfter = 0.0; phase = Phase.EXPLODE; return; }   // teren allaqachon to'liq soya:

        BlockPos best = null;
        double bestExp = base, bestDist = Double.MAX_VALUE;
        Vec3d eye = mc.player.getEyePos();
        Box me = mc.player.getBoundingBox();
        boolean haveBlock = glowstoneCount() > 0;                    // zaryad bittasini oldi: cover uchun bittasi bormi?
        for (int x = -1; x <= 1 && haveBlock; x++)
            for (int y = -1; y <= 1; y++)
                for (int z = -1; z <= 1; z++) {
                    if (x == 0 && y == 0 && z == 0) continue;
                    BlockPos c = pos.add(x, y, z);
                    if (!mc.world.getBlockState(c).isReplaceable()) continue;
                    Box box = new Box(c);
                    if (box.intersects(me)) continue;                // o'zing ichingizga blok qo'ymaydi
                    if (coverHit(c) == null) continue;               // klikka yo'qlik qo/yilgan emas / reach tashqarida
                    double e = exposure(blast, box);
                    double d = eye.squaredDistanceTo(Vec3d.ofCenter(c));
                    if (e < bestExp - 1e-9 || (Math.abs(e - bestExp) <= 1e-9 && best != null && d < bestDist)) {
                        best = c; bestExp = e; bestDist = d;
                    }
                }
        if (best == null) {
            if (requireCover.isEnabled()) { abort("No spot to cover you: anchor left charged"); return; }   // aks holda baribir portlash
            exposureAfter = base;
            phase = Phase.EXPLODE;
            return;
        }
        coverPos = best;
        exposureAfter = bestExp;
        retries = 0;
        phase = Phase.COVER;
    }

    private void cover() {
        if (!hold(Items.GLOWSTONE)) return;
        if (!mc.world.getBlockState(coverPos).isReplaceable()) { phase = Phase.COVER_WAIT; return; }
        BlockHitResult hit = coverHit(coverPos);
        if (hit == null) {
            if (requireCover.isEnabled()) { abort("Cannot reach the cover spot: anchor left charged"); return; }
            phase = Phase.EXPLODE;                                   // quyb qo'yi boshlang: kelasi baribir portlash
            return;
        }
        if (!aim(hit.getPos())) return;
        if (!canClick()) return;
        if (click(hit)) { wait = (int) delay.getAsDouble(); phase = Phase.COVER_WAIT; }
    }

    private void coverWait() {
        if (!KuroServerSync.confirmed(SERVER_TIMEOUT)) return;
        if (mc.world.getBlockState(coverPos).isOf(Blocks.GLOWSTONE)) { retries = 0; phase = Phase.EXPLODE; return; }
        if (++retries > MAX_RETRIES) {
            if (requireCover.isEnabled()) { abort("The cover block was not placed: anchor left charged"); return; }
            phase = Phase.EXPLODE;
            return;
        }
        phase = Phase.COVER;
    }

    private void explode() {
        // glowstone bilan qo'l zaryadaansiga yeriga qil-qish emask detoneyt
        if (mc.player.getMainHandStack().isOf(Items.GLOWSTONE)) {
            int slot = !mc.player.getInventory().getStack(origSlot).isOf(Items.GLOWSTONE)
                    ? origSlot
                    : KuroBlockPlacer.findSlot(s -> !s.isOf(Items.GLOWSTONE));
            if (slot < 0) { abort("No slot to hold instead of glowstone"); return; }
            if (!KuroServerSync.ready(SERVER_TIMEOUT) || !KuroBlockPlacer.select(slot)) return;
            KuroServerSync.beginStep();
            swapped = slot != origSlot;
            wait = (int) delay.getAsDouble();
            return;
        }
        BlockHitResult hit = anchorHit();
        if (!aim(hit.getPos())) return;
        if (!canClick()) return;
        if (!click(hit)) return;
        double self = KuroExplosionUtils.getAnchorDamageTo(mc.player, pos) * exposureAfter;
        String msg = String.format("Detonated: ~%.0f damage to you (%.0f%% exposed)", self, exposureAfter * 100);
        if (target != null && target.isAlive()) {
            msg += String.format(", ~%.0f to %s",
                    KuroExplosionUtils.getAnchorDamageTo(target, pos), target.getName().getString());
        }
        NotificationManager.info("Safe Anchor", msg);
        phase = Phase.RESTORE;
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

    /** Main hand'da item turishini ta'minlaydi: false = hali emas (bu tick swap qilindi/refuz/missing). */
    private boolean hold(Item item) {
        if (mc.player.getMainHandStack().isOf(item)) return true;
        int slot = KuroBlockPlacer.findSlot(s -> s.isOf(item));
        if (slot < 0) { abort("Ran out of " + (item == Items.GLOWSTONE ? "glowstone" : "anchors")); return false; }
        if (!KuroServerSync.ready(SERVER_TIMEOUT) || !KuroBlockPlacer.select(slot)) return false;
        KuroServerSync.beginStep();
        swapped = true;
        wait = (int) delay.getAsDouble();
        return false;
    }

    private int glowstoneCount() {
        int n = 0;
        for (int i = 0; i < 9; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (s.isOf(Items.GLOWSTONE)) n += s.getCount();
        }
        return n;
    }

    /** Anchor zaryadlash / portlash klik: sizga qaragan va KO'Relınadigan yuza (nimà  bo'lguni mas, cover blok
     * oldin mumkinligi emas). Silent aim off bo'lsa crosshair'ni anchor ustida u hit ishlatiladi. */
    private BlockHitResult anchorHit() {
        if (!silentAim.isEnabled() && mc.crosshairTarget instanceof BlockHitResult b
                && b.getType() == HitResult.Type.BLOCK && b.getBlockPos().equals(pos)) return b;
        Vec3d eye = mc.player.getEyePos();
        Vec3d c = Vec3d.ofCenter(pos);
        BlockHitResult best = null;
        double bestD = Double.MAX_VALUE;
        for (Direction d : Direction.values()) {
            Vec3d pt = c.add(d.getOffsetX() * 0.5, d.getOffsetY() * 0.5, d.getOffsetZ() * 0.5);
            Vec3d out = eye.subtract(c);
            if (out.x * d.getOffsetX() + out.y * d.getOffsetY() + out.z * d.getOffsetZ() <= 0) continue;   // yuz sizga qaramoqda
            if (!visible(pt, pos)) continue;
            double dist = eye.squaredDistanceTo(pt);
            if (dist < bestD) { bestD = dist; best = new BlockHitResult(pt, d, pos, false); }
        }
        if (best != null) return best;
        // Yuzlar sundasall (cover to'serida): sizga qaraan yuza fallback
        Vec3d dd = eye.subtract(c);
        Direction face = approxNearest(dd);
        Vec3d hitP = c.add(face.getOffsetX() * 0.5, face.getOffsetY() * 0.5, face.getOffsetZ() * 0.5);
        return new BlockHitResult(hitP, face, pos, false);
    }

    /** c da blok qo'yladif: anchor emas, konteynar emas mustahkam qo shni bryiga bosish. Ko'rinadigan cyjozyust trisu. */
    private BlockHitResult coverHit(BlockPos c) {
        BlockHitResult h = placementHit(c, pos, true);
        return h != null ? h : placementHit(c, pos, false);
    }

    /**
     * c da blok qo'yladif mustahkam qושniрga bos Bloqui) "avoid" — mustahkirda qуotla-iqinse blok (anchor).
     * Reach tashiqdisa yo pruvıdıl (visibleOnly bo'siyti) нulși.
     */
    private BlockHitResult placementHit(BlockPos c, BlockPos avoid, boolean visibleOnly) {
        Vec3d eye = mc.player.getEyePos();
        BlockHitResult best = null;
        double bestD = Double.MAX_VALUE;
        for (Direction d : Direction.values()) {
            BlockPos s = c.offset(d);
            if (s.equals(avoid)) continue;
            BlockState st = mc.world.getBlockState(s);
            if (st.isReplaceable() || st.isOf(Blocks.RESPAWN_ANCHOR) || st.hasBlockEntity()) continue;
            Direction face = d.getOpposite();
            Vec3d hit = Vec3d.ofCenter(s).add(face.getOffsetX() * 0.5, face.getOffsetY() * 0.5, face.getOffsetZ() * 0.5);
            double dist = eye.squaredDistanceTo(hit);
            if (dist > REACH * REACH || dist >= bestD) continue;
            if (visibleOnly && !visible(hit, s)) continue;
            best = new BlockHitResult(hit, face, s, false);
            bestD = dist;
        }
        return best;
    }

    /** Ko'nlovlaringdan nouqtaga tуо to'g'ri chizig q berilga u u er bunaю blo kchera to' langele manganlig矩 ko rute true. */
    private boolean visible(Vec3d point, BlockPos block) {
        BlockHitResult r = mc.world.raycast(new RaycastContext(
                mc.player.getEyePos(), point, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player));
        return r.getType() == HitResult.Type.MISS || r.getBlockPos().equals(block);
    }

    /** Anchor joyini (reach ichidagi istalgan joy) target'ning tanasiga eng yaqинкуза ahınıf va kl clickable, yo q ko'rinad,yu facesda uya anchor. Nullase. */
    private BlockHitResult spotNear(PlayerEntity t) {
        Vec3d eye = mc.player.getEyePos();
        Vec3d body = t.getEntityPos().add(0, t.getStandingEyeHeight() / 2.0, 0);
        Box me = mc.player.getBoundingBox(), them = t.getBoundingBox();
        BlockPos base = mc.player.getBlockPos();
        BlockHitResult best = null;
        double bestScore = Double.MAX_VALUE;
        for (int pass = 0; pass < 2 && best == null; pass++) {
            for (int x = -5; x <= 5; x++)
                for (int y = -5; y <= 5; y++)
                    for (int z = -5; z <= 5; z++) {
                        BlockPos c = base.add(x, y, z);
                        if (!mc.world.getBlockState(c).isReplaceable()) continue;
                        Box box = new Box(c);
                        if (box.intersects(me) || box.intersects(them)) continue;        // refuz bo lib / kim noger ichida
                        Vec3d cc = Vec3d.ofCenter(c);
                        if (eye.squaredDistanceTo(cc) > REACH * REACH) continue;
                        BlockHitResult h = placementHit(c, null, pass == 0);
                        if (h == null) continue;
                        double score = cc.distanceTo(body) - 0.01 * cc.distanceTo(eye);  // eng yaqin ular, beril: undon undan uzoq
                        if (score < bestScore) { bestScore = score; best = h; }
                    }
        }
        return best;
    }

    /** Range ichidagi eng yaqin dushman o'yinchi: do'stlar va botlar o'tkazib yuboriladi. */
    private PlayerEntity bestTarget() {
        PlayerEntity best = null;
        double bestD = targetRange.getValue();
        if (mc.world == null) return null;
        for (PlayerEntity p : mc.world.getPlayers()) {
            double d = mc.player.distanceTo(p);
            if (d >= bestD) continue;
            if (p == mc.player || p.isSpectator() || !p.isAlive()) continue;
            if (Friends.isFriend(p.getGameProfile().name()) || AntiBot.isBot(p)) continue;
            bestD = d;
            best = p;
        }
        return best;
    }

    // ---------------- silent aim ----------------

    /**
     * Server-side aimni nuqtaga buradi (aim assist uslubi): har bir tickda qolgan burchakning qismi
     * (kamida 1 daraja, ko'pi bilan "Aim speed"), keyin look paket serverga aytadi. Aim nuqtadan 2 daraja
     * ichida bo'lsa true — клика kettyando chiqishi mumkin. Silent aim off yoki boshqa modul silent
     * rotatsiyani egallagan bo'lsa doim true.
     */
    private boolean aim(Vec3d point) {
        if (!silentAim.isEnabled()) return true;
        if (!KuroRotations.free() && !KuroRotations.ownedBy(this)) return true;
        Vec3d eye = mc.player.getEyePos();
        double dx = point.x - eye.x, dy = point.y - eye.y, dz = point.z - eye.z;
        double h = Math.sqrt(dx * dx + dz * dz);
        float wantYaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float wantPitch = (float) MathHelper.clamp(-Math.toDegrees(Math.atan2(dy, Math.max(h, 1.0E-4))), -90.0, 90.0);
        if (Float.isNaN(curYaw)) { curYaw = mc.player.getYaw(); curPitch = mc.player.getPitch(); }
        curYaw += ease(MathHelper.wrapDegrees(wantYaw - curYaw), aimSpeed.getValue());
        curPitch = MathHelper.clamp(curPitch + ease(wantPitch - curPitch, aimSpeed.getValue()), -90f, 90f);
        if (!KuroRotations.set(this, curYaw, curPitch)) return true;
        KuroRotations.sendLookPacket();
        return Math.abs(MathHelper.wrapDegrees(wantYaw - curYaw)) < 2f && Math.abs(wantPitch - curPitch) < 2f;
    }

    private static float ease(float diff, double maxStep) {
        float a = Math.abs(diff);
        if (a < 0.05f) return diff;
        float step = Math.min(a, (float) Math.min(maxStep, Math.max(a * 0.35f, 1.0f)));
        return Math.copySign(step, diff);
    }

    /**
     * Portlash "from" nuqtasidan tanangiz (0..1) namuna nuqtalari ulushi to'g'ri chiziqda yetib
     * boradegan (o'yin portlash zararini shunday o'lchaydi). "extra" — gıpotechakskiy blok (cover),
     * u ham yo'lda. Anchor o'zi inançhimas, chunki portlashdan oldin olib tashlanadi.
     */
    private double exposure(Vec3d from, Box extra) {
        Box b = mc.player.getBoundingBox();
        int seen = 0, total = 0;
        for (int x = 0; x <= 2; x++)
            for (int y = 0; y <= 3; y++)
                for (int z = 0; z <= 2; z++) {
                    Vec3d pt = new Vec3d(
                            b.minX + b.getLengthX() * (x / 2.0),
                            b.minY + b.getLengthY() * (y / 3.0),
                            b.minZ + b.getLengthZ() * (z / 2.0));
                    total++;
                    if (clear(from, pt, extra)) seen++;
                }
        return seen / (double) total;
    }

    private boolean clear(Vec3d a, Vec3d b, Box extra) {
        if (extra != null && extra.raycast(a, b).isPresent()) return false;
        BlockHitResult r = mc.world.raycast(new RaycastContext(
                a, b, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player));
        if (r.getType() != HitResult.Type.BLOCK) return true;
        return r.getBlockPos().equals(pos);      // anchor o'zi portlashdan oldin olib tashlanadi
    }

    /** Vanilla right-click shidqa shartlar: item ishlatish paytida emas, sneaking emas (sneaking skip). */
    private boolean canClick() {
        return !mc.player.isUsingItem() && !mc.player.isSneaking() && KuroServerSync.ready(SERVER_TIMEOUT);
    }

    /** Vanilla usul: ClientPlayerInteractionManager.interactBlock (use paket tartibi bilan), consume bo'lsa swing. */
    private boolean click(BlockHitResult hit) {
        if (!mc.world.getWorldBorder().contains(hit.getBlockPos())) return false;
        ActionResult r = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
        if (!r.isAccepted()) return false;
        mc.player.swingHand(Hand.MAIN_HAND);
        KuroServerSync.beginStep();
        return true;
    }

    private boolean isAnchor() {
        return pos != null && mc.world.getBlockState(pos).isOf(Blocks.RESPAWN_ANCHOR);
    }

    private int chargeLevel() {
        BlockState st = mc.world.getBlockState(pos);
        return st.isOf(Blocks.RESPAWN_ANCHOR) ? st.getOrEmpty(RespawnAnchorBlock.CHARGES).orElse(0) : -1;
    }

    /** Hali hech narsa qilinmagan: xabar berib jo'natadi. */
    private void fail(String why) {
        NotificationManager.info("Safe Anchor", why);
        finish();
    }

    /** Yarim-yo'lda to'xtash: slоtni qaytaradi, keyin o'chiradi. */
    private void abort(String why) {
        NotificationManager.info("Safe Anchor", why);
        if (swapped) phase = Phase.RESTORE; else finish();
    }

    private void finish() {
        KuroRotations.release(this);
        curYaw = Float.NaN; curPitch = Float.NaN;
        phase = Phase.IDLE;
        if (isEnabled()) setEnabled(false);
    }

    /** Ko'z-kirish yo'nalishiga eng yaqin Direction. */
    private static Direction approxNearest(Vec3d v) {
        double ax = Math.abs(v.x), ay = Math.abs(v.y), az = Math.abs(v.z);
        if (ax >= ay && ax >= az) return v.x > 0 ? Direction.EAST : Direction.WEST;
        if (ay >= az) return v.y > 0 ? Direction.UP : Direction.DOWN;
        return v.z > 0 ? Direction.SOUTH : Direction.NORTH;
    }
}
