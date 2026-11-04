package com.anormal.client.module.impl.kuro;

import com.anormal.client.mixin.KuroInteractionManagerInvoker;
import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.kuro.KuroBlockPlacer;
import com.anormal.client.util.kuro.KuroRotations;
import com.anormal.client.util.kuro.KuroServerSync;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * kuro {@code PearlCatch} port.
 *
 * <p>Ender pearl otiladi, so'ng shamol zaryasi (wind charge) havoda pearl bilan
 * uchrashadigan qilib otildi. Hotbar'da Ender Pearl va Wind Charge bo'lishi
 * shart. Yoqilsa (yoki bind tugmasi bosilsa): otish burchaklari va vaqtingiz
 * joyingiz va harakatingizdan hisoblanadi, ikkala otish bajariladi, modul
 * o'zini o'chiradi.</p>
 *
 * <p>Planning har ikkala parvozni tick tick bashoratlaydi: pearl 1.5
 * block/tick + sizning harakatingiz bilan boshlanadi, drag (0.99) yo'qotadi
 * va gravity bilan tushadi (0.03/tick); wind charge to'g'ri 1.5 block/tick.
 * Plan faqat ikki yo'l yaru bir-biriga 0.6 blockdan yaqin o'tsa
 * ishlatiladi, xotin joylashuvda oddiy rad etadi. Plan otish sodir
 * bo'ladigan tickda (tugma bosilgan tickda emas) sizning haqiqiy tick'lik
 * harakatingizdan yasaladi, chunki server otgan itemga oxirgi tickdaki
 * harakatingizni beradi.</p>
 *
 * <p>Vanilla qoidalari va paket tartibi. Har bir otish oddiy "use item" amali
 * bo'lib, klient tick boshida (onPreTick) bajariladi — real klient input
 * ushlab turadigan o'rin, ya'ni shu tickning harakat paketidan OLDIN:
 *   1. hotbar slot paketi (faqat slot o'zgarganda)   2. look paketi (otish
 *   burchaklari bilan)   3. use-item paketi (ayni shu burchaklar bilan)
 *   4. swing paketi (faqat natija so'rasa)
 * so'ng real ko'rish burchaklari tiklanadi va keyingi harakat paketi yana
 * shularni ko'taradi.</p>
 */
public class KuroPearlCatch extends Module {
    private static final double SPEED = 1.5;
    private static final double PEARL_DRAG = 0.99;
    private static final double PEARL_GRAVITY = 0.03;
    private static final double MAX_MISS_SQ = 0.25;     // 0.5 blocks
    private static final int FLIGHT_TICKS = 30;
    /** Both throws get a random spread of ~0.011 blocks/tick per axis: expected squared miss after t ticks is about 0.00076 * t^2. */
    private static final double SPREAD = 0.00076;
    private static final int GIVE_UP_TICKS = 80;

    private final BooleanSetting useCurrentAim = new BooleanSetting("Use current aim", false);
    private final NumberSetting chargeDelay = new NumberSetting("Charge delay (ticks)", "Ticks between the pearl and the charge", 0.0, 0.0, 10.0, 1.0);
    private final BooleanSetting showChat = new BooleanSetting("Chat messages", true);

    private enum State { IDLE, THROW_PEARL, WAIT_CHARGE, RESTORE }
    private State state = State.IDLE;
    private Plan plan;
    private Vec3d mv = Vec3d.ZERO;     // how far the player moved during the last tick (what the server hands to thrown items)
    private int meetTick;              // tick of the closest approach found by the last evaluate()
    private int pearlSlot = -1, chargeSlot = -1, savedSlot = -1, elapsed;

    public KuroPearlCatch() {
        super("Pearl Catch", "Throws a pearl, then a wind charge that catches it mid-air", Category.KURO);
        addSetting(useCurrentAim);
        addSetting(chargeDelay);
        addSetting(showChat);
    }

    @Override
    public void onEnable() {
        state = State.IDLE;
        PlayerEntity p = mc.player;
        if (p == null || mc.world == null || mc.interactionManager == null) { setEnabled(false); return; }
        pearlSlot = KuroBlockPlacer.findSlot(s -> s.isOf(Items.ENDER_PEARL));
        chargeSlot = KuroBlockPlacer.findSlot(s -> s.isOf(Items.WIND_CHARGE));
        if (pearlSlot < 0 || chargeSlot < 0) { fail("needs an Ender Pearl and a Wind Charge in the hotbar"); return; }
        if (p.getItemCooldownManager().isCoolingDown(p.getInventory().getStack(pearlSlot))) { fail("the Ender Pearl is on cooldown"); return; }
        if (p.getItemCooldownManager().isCoolingDown(p.getInventory().getStack(chargeSlot))) { fail("the Wind Charge is on cooldown"); return; }
        savedSlot = KuroBlockPlacer.selected();
        plan = null;
        elapsed = 0;
        state = State.THROW_PEARL;      // the plan is made at the moment of the throw (see onPreTick)
    }

    @Override
    public void onDisable() {
        if (mc.player != null && savedSlot >= 0) {
            KuroBlockPlacer.selectForce(savedSlot);
            if (mc.interactionManager != null) ((KuroInteractionManagerInvoker) mc.interactionManager).kuro$syncSelectedSlot();
        }
        KuroRotations.release(this);
        state = State.IDLE;
        plan = null;
        pearlSlot = chargeSlot = savedSlot = -1;
    }

    /**
     * Runs at the start of the client tick, before input handling, physics and the movement packet: the same place in the
     * packet order where a real client's right-click goes out. Position and motion read here are exactly what the server
     * knows (last tick's movement packet), which is also what it uses for the thrown item.
     */
    @Override
    public void onPreTick() {
        PlayerEntity p = mc.player;
        if (state == State.IDLE) return;
        if (p == null || mc.world == null || mc.interactionManager == null || mc.currentScreen != null || p.isSpectator() || !p.isAlive()) {
            setEnabled(false);
            return;
        }
        if (state == State.RESTORE) {       // one swap per tick, and a full tick after the last throw
            if (!KuroServerSync.confirmed(5)) return;
            if (savedSlot < 0 || KuroBlockPlacer.select(savedSlot)) {
                KuroServerSync.beginStep();
                ((KuroInteractionManagerInvoker) mc.interactionManager).kuro$syncSelectedSlot();
                setEnabled(false);
            }
            return;
        }
        if (state == State.THROW_PEARL) {
            if (!KuroServerSync.ready(5)) return;   // one step per tick; not on a tick where the server moved us (plan would be stale)
            if (p.isUsingItem()) return;        // eating / blocking / drawing a bow: vanilla would not throw now either
            // plan from the state the server has when it handles the throw: the position of the last tick's movement
            // packet and the movement it implies (position now minus position one tick ago, both before this tick's physics)
            Vec3d now = p.getLerpedPos(1.0f), before = p.getLerpedPos(0.0f);
            mv = new Vec3d(now.x - before.x, now.y - before.y, now.z - before.z);
            int delay = Math.max(1, chargeDelay.getValue().intValue());   // one swap per tick: the charge goes out at least a tick after the pearl
            plan = useCurrentAim.isEnabled() ? currentAimPlan(p, delay) : upwardPlan(p, delay);
            if (plan == null) { fail("no good way to catch the pearl from here (stand still for a moment, or change the charge delay)"); return; }
            Throw r = fire(pearlSlot, plan.pearlYaw(), plan.pearlPitch());
            if (r == Throw.LATER) return;       // a rule said "not this tick": plan again next tick
            if (r == Throw.REFUSED) { fail("the Ender Pearl throw was refused, so no Wind Charge was thrown"); return; }
            KuroServerSync.beginStep();
            elapsed = 0;
            state = State.WAIT_CHARGE;
            return;
        }
        // WAIT_CHARGE: the charge goes out at the start of the tick that is 'delay' ticks after the pearl's tick
        elapsed++;
        if (elapsed > GIVE_UP_TICKS) { state = State.RESTORE; return; }
        if (elapsed < plan.delay()) return;
        if (p.isUsingItem()) return;
        Throw r = fire(chargeSlot, plan.chargeYaw(), plan.chargePitch());
        if (r == Throw.LATER) return;           // on cooldown / swap not allowed / rotation busy: try again next tick (timing slips, plan may miss)
        KuroServerSync.beginStep();
        state = State.RESTORE;
    }

    private enum Throw { DONE, LATER, REFUSED }

    /**
     * One vanilla use-item action, in vanilla packet order: slot packet, look packet, use-item packet, swing packet.
     * LATER = a rule says not now (nothing was sent that would need undoing), REFUSED = the use was not accepted.
     */
    private Throw fire(int slot, float yaw, float pitch) {
        PlayerEntity p = mc.player;
        ItemStack stack = p.getInventory().getStack(slot);
        if (stack.isEmpty() || p.getItemCooldownManager().isCoolingDown(stack)) return Throw.LATER;   // cooldown: pearl 20 ticks, wind charge 10
        if (!KuroRotations.free() && !KuroRotations.ownedBy(this)) return Throw.LATER;               // another module is using the silent angles
        if (!KuroBlockPlacer.select(slot)) return Throw.LATER;                                       // one swap per tick: not now

        // 1. slot packet, now, so it is on the wire before the look and use packets
        ((KuroInteractionManagerInvoker) mc.interactionManager).kuro$syncSelectedSlot();

        // 2. look packet with the throw angles; the use packet below carries the same angles
        if (!KuroRotations.set(this, yaw, pitch) || !KuroRotations.sendLookPacket(true)) {
            KuroRotations.release(this);
            return Throw.LATER;
        }
        float oldYaw = p.getYaw(), oldPitch = p.getPitch();
        p.setYaw(yaw);
        p.setPitch(MathHelper.clamp(pitch, -90f, 90f));

        // 3. use-item packet
        ActionResult result = mc.interactionManager.interactItem(p, Hand.MAIN_HAND);

        // 4. swing packet, only when the result asks the client for it (what startUseItem does)
        if (result instanceof ActionResult.Success s && s.swingSource() == ActionResult.SwingSource.CLIENT) {
            p.swingHand(Hand.MAIN_HAND);
        }

        // the screen never shows the throw angles; the next movement packet carries the real ones (consumeRestore)
        p.setYaw(oldYaw);
        p.setPitch(oldPitch);
        KuroRotations.release(this);
        return result.isAccepted() ? Throw.DONE : Throw.REFUSED;
    }

    private void fail(String why) {
        if (showChat.isEnabled() && mc.player != null) {
            mc.player.sendMessage(Text.literal("[Pearl Catch] ").formatted(Formatting.GOLD)
                    .append(Text.literal(why).formatted(Formatting.WHITE)), false);
        }
        setEnabled(false);
    }

    // ---------------------------------------------------------------- planning

    private record Plan(float pearlYaw, float pearlPitch, float chargeYaw, float chargePitch, int delay, double miss) {}

    /** Pearl goes up (a little forward/back), the charge goes up too and meets it. */
    private Plan upwardPlan(PlayerEntity p, int delay) {
        float ref = p.getYaw();
        Vec3d v = mv;
        float[] pearlBases;
        if (v.x * v.x + v.z * v.z < 0.003) {
            pearlBases = new float[]{ref};
        } else {
            float moveYaw = (float) Math.toDegrees(Math.atan2(-v.x, v.z));
            pearlBases = Math.abs(MathHelper.wrapDegrees(moveYaw - ref)) < 20f ? new float[]{ref} : new float[]{ref, moveYaw};
        }
        Plan best = null;
        double bestScore = Double.MAX_VALUE;
        for (float pPitch = -90f; pPitch <= -76f; pPitch += 0.5f) {
            for (float base : pearlBases) {
                for (float pOff = -20f; pOff <= 20f; pOff += 2.5f) {
                    float pYaw = base + pOff;
                    for (float cPitch = -90f; cPitch <= -86f; cPitch += 1f) {
                        for (float cOff = -20f; cOff <= 20f; cOff += 5f) {
                            float cYaw = ref + cOff;
                            double miss = evaluate(p, pYaw, pPitch, cYaw, cPitch, delay);
                            if (miss > MAX_MISS_SQ) continue;
                            // prefer a close meeting, an EARLY one (both throws get a small random spread that grows
                            // with flight time), and angles that stay near where you already face
                            double score = miss + SPREAD * meetTick * meetTick + Math.abs(MathHelper.wrapDegrees(cYaw - ref)) * 0.01
                                    + Math.abs(MathHelper.wrapDegrees(pYaw - ref)) * 0.003 + Math.abs(cPitch + 90f) * 0.03;
                            if (score < bestScore) { bestScore = score; best = new Plan(pYaw, pPitch, cYaw, cPitch, delay, miss); }
                        }
                    }
                }
            }
        }
        return best;
    }

    /** The charge goes where you are looking now; the pearl angle and the delay are searched. */
    private Plan currentAimPlan(PlayerEntity p, int minDelay) {
        float cYaw = p.getYaw(), cPitch = p.getPitch();
        int first = Math.max(3, minDelay);
        Plan best = null;
        double bestScore = Double.MAX_VALUE;
        for (int delay = first; delay <= first + 12; delay++) {
            for (float pPitch = -90f; pPitch <= 60f; pPitch += 2.5f) {
                for (float off = -180f; off < 180f; off += 5f) {
                    float pYaw = cYaw + off;
                    double miss = evaluate(p, pYaw, pPitch, cYaw, cPitch, delay);
                    if (miss > MAX_MISS_SQ) continue;
                    double score = miss + SPREAD * meetTick * meetTick + Math.abs(off) * 0.0005 + delay * 0.0001;
                    if (score < bestScore) { bestScore = score; best = new Plan(pYaw, pPitch, cYaw, cPitch, delay, miss); }
                }
            }
        }
        return best;
    }

    /**
     * Smallest squared distance between the pearl and the charge once the charge is in flight, or +infinity when
     * the plan is not usable. Pearl is thrown now; the charge is thrown 'delay' ticks later from where you will be.
     */
    private double evaluate(PlayerEntity p, float pYaw, float pPitch, float cYaw, float cPitch, int delay) {
        Vec3d pd = dir(pYaw, pPitch), cd = dir(cYaw, cPitch);
        Vec3d v = mv;
        boolean air = !p.isOnGround();

        // pearl: leaves 0.1 below the eyes, inherits your motion (vertical only while airborne)
        double px = p.getX(), py = p.getEyeY() - 0.1, pz = p.getZ();
        double pvx = pd.x * SPEED + v.x, pvy = pd.y * SPEED + (air ? v.y : 0.0), pvz = pd.z * SPEED + v.z;

        // where you (and so the charge) will be when the charge is thrown
        double[] o = originAfter(p, delay);
        double cx = o[0], cy = o[1], cz = o[2];
        double cvx = cd.x * SPEED + o[3], cvy = cd.y * SPEED + (air ? o[4] : 0.0), cvz = cd.z * SPEED + o[5];

        double closest = Double.POSITIVE_INFINITY;
        for (int t = 1; t <= delay + FLIGHT_TICKS; t++) {
            pvy -= PEARL_GRAVITY;
            pvx *= PEARL_DRAG; pvy *= PEARL_DRAG; pvz *= PEARL_DRAG;
            px += pvx; py += pvy; pz += pvz;
            if (t <= delay) continue;               // the charge is not in the air yet
            cx += cvx; cy += cvy; cz += cvz;
            double dx = px - cx, dy = py - cy, dz = pz - cz;
            double d = dx * dx + dy * dy + dz * dz;
            if (d < closest) { closest = d; meetTick = t; }
        }
        return closest;
    }

    /**
     * {x, eyeY, z, vx, vy, vz} of the player after 'ticks' ticks, assuming no input change. vx/vy/vz is the movement
     * during the last of those ticks (at 0 ticks: the last real tick), which is what a thrown item inherits.
     */
    private double[] originAfter(PlayerEntity p, int ticks) {
        Vec3d v = mv;
        double x = p.getX(), y = p.getEyeY(), z = p.getZ(), vx = v.x, vy = v.y, vz = v.z;
        boolean air = !p.isOnGround();
        for (int i = 0; i < ticks; i++) {
            if (air) { vy = (vy - 0.08) * 0.98; vx *= 0.91; vz *= 0.91; y += vy; }
            else { vx *= 0.546; vz *= 0.546; }
            x += vx; z += vz;
        }
        return new double[]{x, y, z, vx, air ? vy : 0.0, vz};
    }

    private static Vec3d dir(float yaw, float pitch) {
        double yr = Math.toRadians(yaw), pr = Math.toRadians(pitch);
        return new Vec3d(-Math.sin(yr) * Math.cos(pr), -Math.sin(pr), Math.cos(yr) * Math.cos(pr));
    }
}
