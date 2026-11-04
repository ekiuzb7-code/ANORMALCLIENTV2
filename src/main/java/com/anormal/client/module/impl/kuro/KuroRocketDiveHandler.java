package com.anormal.client.module.impl.kuro;

import com.anormal.client.util.kuro.KuroBlockPlacer;
import com.anormal.client.util.InputForcer;

// ochiq namespace: KeyBindingMixin har harakat-tick paytida forced booleans QAYTA QO'YADI
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.EquipmentSlot;


import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;

import java.util.Locale;
import java.util.function.Predicate;

/**
 * Kuro RocketDiveHandler port (dev.kuro.client.module.impl.automace.RocketDiveHandler'dan Anormal'da).
 *
 * <p>Rocket / elytra dive — faqat o'yinchi o'yinda qila oladigan narsalar bilan:</p>
 * <ul>
 *   <li>elytra va chestplate hotbar'da right-click orqali kiyiladi (inventory click paketlari yo'q);</li>
 *   <li>parvoz real jump tugmasi bosilishi bilan boshlanadi (tugma bir daqiqa ushlab turiladi,
 *       o'yin parvoz boshlanishini o'zi yuboradi);</li>
 *   <li>fireworklar oddiy item kabi ishlatiladi, faqat parvoz paytida;</li>
 *   <li>aim real player rotationga buriladi (KuroAutoMace.aimAt), server buni normal
 *       harakat paketlarida oladi;</li>
 *   <li>slot o'zgartirishlar KuroBlockPlacer.select (tick bitta swap), hujumlar
 *       KuroAutoMace.executeAttack (tickdagi bitta) orqali.</li>
 * </ul>
 * Tick'da bajarilolmaydigan qadam keyingisini kutadi. Dive faqat barcha kerakli
 * itemlar hotbar'da bo'lganda boshlanadi — yarim yo'lda qolmaydi.
 */
public final class KuroRocketDiveHandler {
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    private enum State { IDLE, START, ASCEND, DROP, ATTACK, RECOVER }

    private static final long MAX_RUN_MS = 25_000L;
    private static final long MAX_STEP_MS = 4_000L;

    private final KuroAutoMace parent;
    private State state = State.IDLE;
    private Entity target;
    private int originalSlot = -1;
    private boolean startedWithElytra;
    private long nextAt, runStart, stepStart, lastFirework, lastAttack, dropAt;
    private double fallStartY = Double.NaN;

    private boolean jumpForced;
    private long jumpReleaseAt, lastJumpReleased;

    public KuroRocketDiveHandler(KuroAutoMace parent) { this.parent = parent; }

    public boolean idle() { return state == State.IDLE; }

    // -------------------------------------------------------- lifecycle

    /** Dive tugaydi. Reequip bilan qiyofa o'yinchida qo'yilgan qadm bot bo'ladi. */
    public void reset(boolean restore) {
        boolean wasRunning = state != State.IDLE;
        releaseJump();
        if (wasRunning && restore && mc.player != null && mc.interactionManager != null) restoreGear();
        state = State.IDLE;
        target = null;
        originalSlot = -1;
        fallStartY = Double.NaN;
        parent.releaseAim();
    }

    private void restoreGear() {
        boolean wantElytra = startedWithElytra;
        if (hasElytraEquipped() != wantElytra) {
            int slot = KuroBlockPlacer.findSlot(wantElytra ? s -> s.isOf(Items.ELYTRA) : KuroRocketDiveHandler::isChestplate);
            if (slot >= 0) {
                KuroBlockPlacer.selectForce(slot);
                mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            }
        }
        if (originalSlot >= 0) KuroBlockPlacer.selectForce(originalSlot);
    }

    public void tick() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) { reset(false); return; }
        long now = System.currentTimeMillis();

        if (jumpForced && now >= jumpReleaseAt) releaseJump();
        if (state == State.IDLE) { if (now >= nextAt) beginIfReady(now); return; }

        if (mc.player.hasVehicle() || mc.player.isTouchingWater() || mc.player.isSpectator()
                || now - runStart > MAX_RUN_MS || (state != State.ASCEND && now - stepStart > MAX_STEP_MS)) {
            reset(true);
            nextAt = now + 1000;
            return;
        }
        if (now < nextAt) return;

        switch (state) {
            case START -> doStart(now);
            case ASCEND -> doAscend(now);
            case DROP -> doDrop(now);
            case ATTACK -> doAttack(now);
            case RECOVER -> doRecover(now);
            default -> {}
        }
    }

    private void enter(State s, long now, long delayMs) {
        state = s;
        stepStart = now;
        nextAt = now + delayMs;
    }

    // -------------------------------------------------------- steps

    private void beginIfReady(long now) {
        if (mc.currentScreen != null || mc.player.hasVehicle() || mc.player.isTouchingWater()) return;
        if (!mc.player.isOnGround() && !mc.player.isGliding()) return;

        target = KuroAutoMaceTargeting.find(
                parent.rocketTargetRange(), parent.targetPlayers(), parent.targetMobs(),
                KuroAutoMaceTargeting.Priority.DISTANCE, parent.rocketSnapFov());
        if (target == null) return;

        boolean elytraOn = hasElytraEquipped();
        boolean elytraInBar = KuroBlockPlacer.findSlot(s -> s.isOf(Items.ELYTRA)) >= 0;
        boolean plateInBar = KuroBlockPlacer.findSlot(KuroRocketDiveHandler::isChestplate) >= 0;
        boolean plateWorn = isChestplate(mc.player.getEquippedStack(EquipmentSlot.CHEST));
        boolean canHaveElytra = elytraOn || elytraInBar;
        boolean canDrop = elytraOn ? plateInBar : plateWorn || plateInBar;
        if (!canHaveElytra || !canDrop) { target = null; return; }
        if (KuroBlockPlacer.findSlot(s -> s.isOf(Items.FIREWORK_ROCKET)) < 0) { target = null; return; }
        if (parent.findMaceSlot(0) < 0) { target = null; return; }

        startedWithElytra = elytraOn;
        originalSlot = KuroBlockPlacer.selected();
        runStart = now;
        fallStartY = Double.NaN;
        enter(State.START, now, 0);
    }

    private void doStart(long now) {
        if (invalidTarget()) { reset(true); return; }
        if (mc.player.isGliding()) { enter(State.ASCEND, now, 0); return; }

        if (!hasElytraEquipped()) { equipChest(true, now); return; }

        if (mc.player.isOnGround()) { tapJump(now); return; }
        if (!jumpForced && now - lastJumpReleased > 80 && mc.player.getVelocity().y < 0) tapJump(now);
    }

    private void doAscend(long now) {
        if (invalidTarget()) { reset(true); return; }
        if (!mc.player.isGliding()) {
            if (mc.player.isOnGround() || hasElytraEquipped()) { enter(State.START, now, 0); return; }
        }
        Vec3d above = new Vec3d(target.getX(), target.getY() + parent.rocketHeightAbove(), target.getZ());
        Vec3d lead = above.add(target.getRotationVec(1.0f).multiply(parent.rocketAimOffset()));
        parent.aimAt(KuroAutoMaceTargeting.angles(lead), mc.player.distanceTo(target));

        double wantY = target.getY() + parent.rocketHeightAbove();
        double horiz = Math.hypot(target.getX() - mc.player.getX(), target.getZ() - mc.player.getZ());
        if (mc.player.getY() >= wantY - 1.5 && horiz <= 8.0) {
            enter(State.DROP, now, parent.rocketDropDelayMs());
            return;
        }
        if (now - lastFirework >= parent.rocketFireworkDelayMs() && mc.player.isGliding()) {
            int fw = KuroBlockPlacer.findSlot(s -> s.isOf(Items.FIREWORK_ROCKET));
            if (fw < 0) { reset(true); return; }
            if (!KuroBlockPlacer.select(fw)) return;
            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            lastFirework = now;
        }
    }

    private void doDrop(long now) {
        if (invalidTarget()) { reset(true); return; }
        if (hasElytraEquipped()) { equipChest(false, now); return; }
        fallStartY = mc.player.getY();
        dropAt = now;
        enter(State.ATTACK, now, 0);
    }

    private void doAttack(long now) {
        if (invalidTarget()) { enter(State.RECOVER, now, 0); return; }
        if (Double.isNaN(fallStartY)) fallStartY = mc.player.getY();
        double fall = Math.max(0, fallStartY - mc.player.getY());

        parent.aimAt(KuroAutoMaceTargeting.angles(KuroAutoMaceTargeting.aimPoint(target, KuroAutoMaceTargeting.AimPoint.CENTER)), mc.player.distanceTo(target));

        if (mc.player.isOnGround()) { enter(State.RECOVER, now, 0); return; }
        if (now - dropAt < parent.rocketAttackDelayMs() || now - lastAttack < parent.rocketAttackDelayMs()) return;
        if (fall < parent.minFall()) return;
        if (mc.player.getVelocity().y > -0.1) return;

        int mace = parent.findMaceSlot(fall);
        if (mace < 0) { enter(State.RECOVER, now, 0); return; }
        if (!KuroBlockPlacer.select(mace)) return;

        if (mc.player.distanceTo(target) > parent.rocketAttackRange() + 1.5) return;
        if (parent.executeAttack(target)) {
            lastAttack = now;
            enter(State.RECOVER, now, parent.rocketReequipDelayMs());
        }
    }

    private void doRecover(long now) {
        boolean wantElytra = startedWithElytra || parent.rocketRecover();
        if (hasElytraEquipped() != wantElytra) { equipChest(wantElytra, now); return; }
        if (originalSlot >= 0 && !KuroBlockPlacer.select(originalSlot)) return;

        Entity next = parent.rocketLoop() && parent.rocketRecover() && target != null && target.isAlive()
                ? KuroAutoMaceTargeting.find(parent.rocketTargetRange(), parent.targetPlayers(), parent.targetMobs(),
                        KuroAutoMaceTargeting.Priority.DISTANCE, parent.rocketSnapFov())
                : null;
        long glideDelay = parent.rocketGlideDelayMs();
        releaseJump();
        parent.releaseAim();
        if (next != null) {
            target = next;
            fallStartY = Double.NaN;
            runStart = now;
            enter(State.START, now, glideDelay);
        } else {
            state = State.IDLE;
            target = null;
            originalSlot = -1;
            fallStartY = Double.NaN;
            nextAt = now + 500;
        }
    }

    // -------------------------------------------------------- helpers

    private boolean invalidTarget() {
        return target == null || !target.isAlive() || target.isRemoved()
                || mc.player.distanceTo(target) > parent.rocketTargetRange() * 1.5;
    }

    private void equipChest(boolean elytra, long now) {
        int slot = KuroBlockPlacer.findSlot(elytra ? (Predicate<ItemStack>) s -> s.isOf(Items.ELYTRA) : KuroRocketDiveHandler::isChestplate);
        if (slot < 0) { reset(false); return; }
        if (!KuroBlockPlacer.select(slot)) return;
        mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
        nextAt = now + 100;
    }

    private void tapJump(long now) {
        if (jumpForced) return;
        InputForcer.jump = true;
        jumpForced = true;
        jumpReleaseAt = now + 60;
    }

    private void releaseJump() {
        if (!jumpForced) return;
        InputForcer.jump = false;
        jumpForced = false;
        lastJumpReleased = System.currentTimeMillis();
    }

    private boolean hasElytraEquipped() {
        return mc.player != null && mc.player.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA);
    }

    private static boolean isChestplate(ItemStack s) {
        return !s.isEmpty() && !s.isOf(Items.ELYTRA) && s.getItem().toString().toLowerCase(Locale.ROOT).contains("chestplate");
    }
}
