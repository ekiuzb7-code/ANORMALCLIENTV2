package com.anormal.client.util.kuro;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.FallingBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.AbstractDecorationEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

import java.util.function.Predicate;

/** Blok qo'yadigan / hotbar slotini tanlaydigan modullar uchun umumiy yordamchilar (kuro BlockPlacer port). */
public final class KuroBlockPlacer {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static final Direction[] ORDER = {
            Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.UP };
    public static final double REACH = 4.5;

    private KuroBlockPlacer() {}

    /** Ushlab turgan blokni target'ga qo'yadi, qo'shni mustahkam blokni kliklab. Imkonsiz bo'lsa false. */
    public static boolean place(BlockPos target) {
        World world = mc.world;
        if (world == null || mc.player == null || mc.interactionManager == null) return false;
        if (!world.getBlockState(target).isReplaceable()) return false;
        if (mc.player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(target)) > REACH * REACH) return false;
        for (Direction d : ORDER) {
            BlockPos n = target.offset(d);
            if (world.getBlockState(n).isReplaceable()) continue;
            Direction face = d.getOpposite();
            Vec3d hit = Vec3d.ofCenter(n).add(face.getOffsetX() * 0.5, face.getOffsetY() * 0.5, face.getOffsetZ() * 0.5);
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, new BlockHitResult(hit, face, n, false));
            mc.player.swingHand(Hand.MAIN_HAND);
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ vanilla uslubidagi qo'yish

    public enum PlaceResult {
        /** Blok qo'yildi (server "use"ni qabul qildi). */
        DONE,
        /** Qoida "bu tickda yo'q" deydi (swap allaqachon bor, rotatsiyani boshqa modul egallagan...): keyingi tickda urin. */
        LATER,
        /** Bu joy hozirgi joyingizdan qo'ymaydi (rasro yoki yo'l to'siqda, masofadan tashqarida, yo'l to'siqda, rad etilgan). */
        SKIP,
        /** (placeFromView faqat) to'g'ri klikqa qarayolmadingiz: kamerani burishtiring va keyingi tickda urin. */
        AIM
    }

    /**
     * Hotbar slotindaki 'slot'dagi blokni 'target'ga real klient uslubida qo'yadi va real klient
     * bemalol qolmaydigan dasturlarni rad etadi.
     *
     * Klient tick BOSHIDA chaqiring (Module.onTick'da), vanilla klient-o'yinchi shu yerda
     * right-click'ni ushlaydi: "use" paketi shu tickning harakat paketidan OLDIN chiqadi. Paket
     * tartibi: 1. hotbar slot paketi (faqat slot o'zgarganda), 2. aim burchaklari bilan look paketi,
     * 3. use-item-on paketi, 4. swing paketi (natijada talab qilinsa); keyingi harakat paketi real
     * ko'rish burchaklarini qayta olib chiqadi.
     *
     * Holat tekshiruvlari (biri ishlamasa hech narsa yuborilmaydi): qurish mumkin (spectator/adventure emas),
     * boshqa item ishlatilmaydi, target bo'sh va blok u yerda turishi mumkin, o'sha joyda biror entity borligi,
     * o'yin chegarasi ichida va balandlik chegarasida. O'z klik REAL: maqsadni o'z ichiga oladigan mavjud
     * blok biror yuzasida aim nuqta qidiriladi, burchaklar sichqoncha-qadamida kesiladi va ko'zlaringizdan
     * bu burchaklardagi ray o'yin cast qilgan uslubda cast qilinadi (outline shakllari, blok masofasi 4.5).
     * Aniq shu blok va yuzaga tegishi shart, va orasida siz klik qila oladigan entity turmasa (o'yin
     * entity bilan o'zini ishlov berardi). Yuborilgan BlockHitResult o'z ray natijasi, ixtiyoriy nuqta emas.
     * Siz allaqachon qarayotgan aim'ga eng yaqini o'sha ishlatiladi.
     */
    public static PlaceResult placeAt(Object owner, int slot, BlockPos target) {
        PlaceResult pre = check(slot, target);
        if (pre != null) return pre;
        PlayerEntity p = mc.player;
        Aim aim = findAim(p, target);
        if (aim == null) return PlaceResult.SKIP;
        if (!KuroRotations.free() && !KuroRotations.ownedBy(owner)) return PlaceResult.LATER;   // boshqa modul silent burchaklarni ushlayapti
        if (!KuroSwapLimit.select(slot)) return PlaceResult.LATER;                              // tickda bitta swap: hozir emas

        // 1. slot paketi ENDI — look va use paketlardan oldin simda bo'lishi uchun
        ((com.anormal.client.mixin.KuroInteractionManagerInvoker) mc.interactionManager).kuro$syncSelectedSlot();
        // 2. aim burchaklari bilan look paketi (pastdan use paketi server tomonidan shu burchaklar bilan baholanadi)
        if (!KuroRotations.set(owner, aim.yaw(), aim.pitch()) || !KuroRotations.sendLookPacket(true)) {
            KuroRotations.release(owner);
            return PlaceResult.LATER;
        }
        float oldYaw = p.getYaw();
        float oldPitch = p.getPitch();
        p.setYaw(aim.yaw());
        p.setPitch(aim.pitch());
        // 3. use-item-on paketi
        ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, aim.hit());
        // 4. swing paketi — faqat natija kliyentdan so'rash bo'lsa (MinecraftClient.doItemUse qilganidek)
        if (result instanceof ActionResult.Success s && s.swingSource() == ActionResult.SwingSource.CLIENT) {
            p.swingHand(Hand.MAIN_HAND);
        }
        // ekran aim burchaklarini hech qachon ko'rsatmaydi; keyingi harakat paketi real burchaklarni ko'taradi (Rotations.consumeRestore)
        p.setYaw(oldYaw);
        p.setPitch(oldPitch);
        KuroRotations.release(owner);
        return result.isAccepted() ? PlaceResult.DONE : PlaceResult.SKIP;
    }

    /** Har bir qo'yish uchun holat tekshiruvlari. Null = hammasi OK, aks holda shu natija (hech narsa yuborilmadi). */
    private static PlaceResult check(int slot, BlockPos target) {
        World world = mc.world;
        if (world == null || mc.player == null || mc.interactionManager == null) return PlaceResult.LATER;
        PlayerEntity p = mc.player;
        if (mc.currentScreen != null || p.isSpectator() || !p.isAlive() || p.isUsingItem()) return PlaceResult.LATER;
        if (!p.getAbilities().allowModifyWorld) return PlaceResult.SKIP;
        ItemStack stack = p.getInventory().getStack(slot);
        if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem bi)) return PlaceResult.SKIP;
        if (!world.getBlockState(target).isReplaceable()) return PlaceResult.SKIP;
        if (target.getY() < world.getBottomY()
                || target.getY() > world.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, target)
                || !world.getWorldBorder().contains(target)) return PlaceResult.SKIP;
        BlockState placed = bi.getBlock().getDefaultState();
        if (!placed.canPlaceAt(world, target)) return PlaceResult.SKIP;
        // "unobstructed" o'rniga: joyning kolizion qutisi bo'sh bo'lishi tekshiriladi (vanilla isUnobstructed'dan yengil farq,
        // lekin o'yin qo'za olmaydi: joy qutisi yo yengil yoki entity bilan to'ldirilgan)
        Box spotBox = new Box(target);
        if (!world.isSpaceEmpty(p, spotBox)) return PlaceResult.SKIP;
        return null;
    }

    /** placeFromView natijasi: AIM — "hozir to'g'ri klik emas, avval yaw/pitchga buril" (hech narsa yuborilmadi). */
    public record Attempt(PlaceResult result, float yaw, float pitch) {}

    /**
     * placeAt'ning kamera versiyasi: look paketi yo'q. Klik server allaqachon biladigan burchaklar
     * bilan qilinadi (oxirgi harakat paketi ularni olib chiqqan), shu sababli tick boshida chaqiring va
     * AIM javob berishda real kamerani o'zingiz burishtiring (turnToward); klik keyingi tickda chiqadi.
     * Paket tartibi oddiy vanilla: slot paketi, use-item-on, swing. Klik aynan target'ga qo'shni
     * yuzaga tegishi shart (placeAt bilan bir xil ray qoidalari), orasida entity bo'lmasligi kabi.
     */
    public static Attempt placeFromView(Object owner, int slot, BlockPos target) {
        PlaceResult pre = check(slot, target);
        if (pre != null) return new Attempt(pre, 0.0f, 0.0f);
        PlayerEntity p = mc.player;
        if (!KuroRotations.free()) return new Attempt(PlaceResult.LATER, 0.0f, 0.0f);   // boshqa modul silent burchaklarni egallagan
        Vec3d eye = p.getEyePos();
        Vec3d end = eye.add(Vec3d.fromPolar(p.getPitch(), p.getYaw()).multiply(REACH));
        BlockHitResult hr = mc.world.raycast(new RaycastContext(eye, end, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, p));
        boolean onTarget = hr.getType() == HitResult.Type.BLOCK
                && hr.getBlockPos().offset(hr.getSide()).equals(target)
                && !mc.world.getBlockState(hr.getBlockPos()).isReplaceable()
                && !entityInTheWay(p, eye, hr.getPos());
        if (!onTarget) {
            Aim aim = findAim(p, target);
            return aim == null ? new Attempt(PlaceResult.SKIP, 0.0f, 0.0f) : new Attempt(PlaceResult.AIM, aim.yaw(), aim.pitch());
        }
        if (!KuroSwapLimit.select(slot)) return new Attempt(PlaceResult.LATER, 0.0f, 0.0f);  // tickda bitta swap: hozir emas
        ((com.anormal.client.mixin.KuroInteractionManagerInvoker) mc.interactionManager).kuro$syncSelectedSlot();   // 1. slot paketi
        ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hr);   // 2. use-item-on
        if (result instanceof ActionResult.Success s && s.swingSource() == ActionResult.SwingSource.CLIENT) {
            p.swingHand(Hand.MAIN_HAND);                                   // 3. swing
        }
        return new Attempt(result.isAccepted() ? PlaceResult.DONE : PlaceResult.SKIP, 0.0f, 0.0f);
    }

    /**
     * Real kamera burchaklariga yaqinlashish (kuro kabi): qolgan xatoning bir qismi (speed 1..10
     * = taxminan 19%..95% har tickda) va tick chegarasi bilan, sichqoncha qadamiga berkast qilib
     * burilish. Silent Aura bilan bir xil usul. Keyingi harakat paketi yangi burchaklarni ko'taradi,
     * server ularni shu yerda o'rganadi.
     */
    public static void turnToward(float wantYaw, float wantPitch, double speed) {
        PlayerEntity p = mc.player;
        float yaw = p.getYaw();
        float pitch = p.getPitch();
        float yawError = MathHelper.wrapDegrees(wantYaw - yaw);
        float pitchError = wantPitch - pitch;
        float frac = MathHelper.clamp((float) (0.1 + speed * 0.085), 0.1f, 0.95f);
        float capYaw = (float)(8.0 + speed * 4.0);
        float capPitch = (float)(5.0 + speed * 2.5);
        float yawStep = MathHelper.clamp(yawError * frac, -capYaw, capYaw);
        float pitchStep = MathHelper.clamp(pitchError * frac, -capPitch, capPitch);
        double gcd = mouseStep();
        float newYaw = yaw + yawStep;
        float newPitch = pitch + pitchStep;
        if (gcd > 1.0E-4) {
            newYaw = yaw + (float) (Math.round(yawStep / gcd) * gcd);
            newPitch = pitch + (float) (Math.round(pitchStep / gcd) * gcd);
        }
        newPitch = MathHelper.clamp(newPitch, -90.0f, 90.0f);
        if (newYaw == yaw && newPitch == pitch) return;
        p.setYaw(newYaw);
        p.setPitch(newPitch);
        p.headYaw = newYaw;
        p.lastYaw += newYaw - yaw;         // kamera interpolatsiyasi silliq qoladi (real sichqoncha silji paytidagidek)
        p.lastPitch += newPitch - pitch;
    }

    /**
     * 'target'dagi blok sizning hozirgi joyingizdan rostanay qo'yilishi mumkin: joy bo'sh va
     * qo'shni blokning reach ichida, orasida entity bo'lmasa, yuzasi real klik bilan tegilishi
     * mumkin bo'lsa true. Targetning orqa yuzlari (tanasi tomon yashirilgan) false beradi.
     * Hech narsa yubormaydi.
     */
    public static boolean reachable(BlockPos target) {
        World world = mc.world;
        if (world == null || mc.player == null) return false;
        if (!world.getBlockState(target).isReplaceable()) return false;
        return findAim(mc.player, target) != null;
    }

    private record Aim(float yaw, float pitch, BlockHitResult hit) {}

    /** target atrofidagi bloklar yuzalari orasidan u yerda rostanay blok qo'ya oladigan klikni qidiradi. Null = yo'q. */
    private static Aim findAim(PlayerEntity p, BlockPos target) {
        World world = mc.world;
        if (world == null) return null;
        Vec3d eye = p.getEyePos();
        float curYaw = p.getYaw();
        float curPitch = p.getPitch();
        double step = mouseStep();
        double[] offsets = {0.0, -0.3, 0.3};
        Aim best = null;
        double bestCost = Double.MAX_VALUE;
        for (Direction d : ORDER) {
            BlockPos n = target.offset(d);
            if (world.getBlockState(n).isReplaceable()) continue;   // klik qilaladigan narsa yo'q
            Direction face = d.getOpposite();                       // n'ning target tomoniga qaragan yuzi
            Direction.Axis a = face.getAxis();
            for (double u : offsets) {
                for (double v : offsets) {
                    double px = n.getX() + 0.5 + face.getOffsetX() * 0.5;
                    double py = n.getY() + 0.5 + face.getOffsetY() * 0.5;
                    double pz = n.getZ() + 0.5 + face.getOffsetZ() * 0.5;
                    if (a == Direction.Axis.X) { py += u; pz += v; }
                    else if (a == Direction.Axis.Y) { px += u; pz += v; }
                    else { px += u; py += v; }
                    double dx = px - eye.x, dy = py - eye.y, dz = pz - eye.z;
                    double flat = Math.sqrt(dx * dx + dz * dz);
                    float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
                    float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.max(flat, 1.0E-4)));
                    // burilishni sichqoncha qadamiga beruann (real sichqoncha kirenida kiriladi)
                    float dYaw = MathHelper.wrapDegrees(yaw - curYaw);
                    float dPitch = pitch - curPitch;
                    if (step > 1.0E-4) {
                        dYaw = (float) (Math.round(dYaw / step) * step);
                        dPitch = (float) (Math.round(dPitch / step) * step);
                    }
                    float qYaw = curYaw + dYaw;
                    float qPitch = MathHelper.clamp(curPitch + dPitch, -90.0f, 90.0f);
                    // ray'sni o'yin cast qilgan uslubda cast qilib bu aniq blok-yuzaqa talab qilib
                    Vec3d end = eye.add(Vec3d.fromPolar(qPitch, qYaw).multiply(REACH));
                    BlockHitResult hr = world.raycast(new RaycastContext(eye, end, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, p));
                    if (hr.getType() != HitResult.Type.BLOCK || !hr.getBlockPos().equals(n) || hr.getSide() != face) continue;
                    if (entityInTheWay(p, eye, hr.getPos())) continue;
                    double cost = Math.abs(dYaw) + Math.abs(dPitch);
                    if (cost < bestCost) { bestCost = cost; best = new Aim(qYaw, qPitch, hr); }
                }
            }
        }
        return best;
    }

    /** Ko'zlar bilan hit point orasida o'yin klikka oladigan biror entity to'qig qarshida turadigan bo'lsa true. */
    private static boolean entityInTheWay(PlayerEntity p, Vec3d from, Vec3d to) {
        World world = mc.world;
        if (world == null) return false;
        Box seg = new Box(from, to).expand(1.0);
        for (Entity e : world.getOtherEntities(p, seg, x -> !x.isSpectator() && x.isAlive() && !(x instanceof AbstractDecorationEntity))) {
            Box box = e.getBoundingBox().expand(e.getTargetingMargin());
            if (box.contains(from) || box.raycast(from, to).isPresent()) return true;
        }
        return false;
    }

    /** Joriy sensitivity bo'yicha sichqonchaning bir pikselli siljishining burilish darajasi (vanilla: (s*0.6+0.2)^3 * 8 * 0.15). */
    private static double mouseStep() {
        try {
            double f = ((Double) mc.options.getMouseSensitivity().getValue()) * 0.6 + 0.2;
            return f * f * f * 1.2;
        } catch (Exception e) {
            return 0.0;
        }
    }

    public static int findSlot(Predicate<ItemStack> wanted) {
        if (mc.player == null) return -1;
        for (int i = 0; i < 9; i++) {
            if (wanted.test(mc.player.getInventory().getStack(i))) return i;
        }
        return -1;
    }

    public static int selected() { return mc.player == null ? -1 : mc.player.getInventory().getSelectedSlot(); }

    /** Slotni tanlaydi, barcha modullar uchun tickda bitta swap (SwapLimit'ga qarang). False = rad etilgan, keyingi tickda urin. */
    public static boolean select(int slot) { return KuroSwapLimit.select(slot); }

    /** Modul o'chirilganda tozalash: tickda swap qilingan bo'lsa ham slotni tanlaydi. */
    public static void selectForce(int slot) { KuroSwapLimit.selectNow(slot); }

    /** Faqat oddiy to'q bloklar: sandig'i/pech, qum/qir, plita/zinapoya/osimlik emas. */
    public static boolean isSolidBlock(ItemStack s) {
        World world = mc.world;
        if (s.isEmpty() || !(s.getItem() instanceof BlockItem bi) || world == null) return false;
        Block b = bi.getBlock();
        return !(b instanceof FallingBlock) && b.getDefaultState().isFullCube(world, BlockPos.ORIGIN);
    }

    /** Masofa ichida eng yaqin boshqa tirik, spectator bo'lmagan o'yinchi, yo'q bo'lsa null. */
    public static PlayerEntity nearestPlayer(double range) {
        World world = mc.world;
        if (world == null || mc.player == null) return null;
        PlayerEntity best = null;
        double bestD = range;
        for (var p : world.getPlayers()) {
            if (p == mc.player || p.isSpectator() || !p.isAlive()) continue;
            double d = mc.player.distanceTo(p);
            if (d <= bestD) { bestD = d; best = p; }
        }
        return best;
    }
}
