package com.anormal.client.module.impl.kuro;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.module.impl.client.AntiBot;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import org.lwjgl.glfw.GLFW;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Ported from kuro (dev.kuro.client.module.impl.TriggerBot).
 *
 * <p>Same control surface and decision order as Vape: canAttack gates → crosshair entity →
 * select-first-hit → mouse-over delay → attack readiness with an extra-delay offset →
 * optional miss chance → attack.</p>
 */
public class KuroTriggerBot extends Module {

    // ---- Vape setting parity ----
    private final NumberSetting extraDelayMin = new NumberSetting("Extra delay min (ticks)",
            "Cooldown offset in ticks (negative = attack before full charge)", 0.0, -20.0, 20.0, 1.0);
    private final NumberSetting extraDelayMax = new NumberSetting("Extra delay max (ticks)",
            "Cooldown offset in ticks (negative = attack before full charge)", 0.0, -20.0, 20.0, 1.0);
    private final NumberSetting mouseOverMin = new NumberSetting("Mouse over delay min (ms)",
            "Minimum time the target must stay under the crosshair", 0.0, 0.0, 200.0, 10.0);
    private final NumberSetting mouseOverMax = new NumberSetting("Mouse over delay max (ms)",
            "Maximum time the target must stay under the crosshair", 0.0, 0.0, 200.0, 10.0);
    // ---- Late fire: wait N game ticks after the weapon is fully charged and the target is under the crosshair ----
    private final BooleanSetting lateFire = new BooleanSetting("Late fire",
            "Wait after full charge before attacking", false);
    private final NumberSetting lateMin = new NumberSetting("Late fire delay min (ticks)",
            "Minimum extra full-charge wait", 1.0, 0.0, 20.0, 1.0);
    private final NumberSetting lateMax = new NumberSetting("Late fire delay max (ticks)",
            "Maximum extra full-charge wait", 2.0, 0.0, 20.0, 1.0);
    private final BooleanSetting requireMouseDown = new BooleanSetting("Require mouse down",
            "Only attack while the left mouse button is held", false);
    private final BooleanSetting ignoreActivationClick = new BooleanSetting("Ignore activation click",
            "Do not attack from the click that activated the module (unused without a rotation engine)", false);
    private final BooleanSetting airCrits = new BooleanSetting("Air crits",
            "Only attack while falling so every hit crits", false);
    private final BooleanSetting shieldCheck = new BooleanSetting("Shield check",
            "Skip players that are actively blocking", false);
    private final BooleanSetting selectFirstHit = new BooleanSetting("Select first hit",
            "Wait until the local client registers the target as hurt", false);
    private final NumberSetting targetMissChance = new NumberSetting("Target miss chance %",
            "Chance to skip the attack entirely", 0.0, 0.0, 100.0, 1.0);
    private final NumberSetting earlyHitChance = new NumberSetting("Early hit chance %",
            "Chance to attack before the weapon is fully charged", 0.0, 0.0, 100.0, 1.0);
    private final BooleanSetting weaponsOnly = new BooleanSetting("Weapons only",
            "Only while holding a sword, axe, mace or trident", false);
    private final BooleanSetting players = new BooleanSetting("Players",
            "Target other players", true);
    private final BooleanSetting ignoreBots = new BooleanSetting("Ignore bots (Anti Bot)",
            "Skip entities flagged by the AntiBot module", true);
    private final BooleanSetting mobs = new BooleanSetting("Other mobs",
            "Target non-player living entities", false);

    // ---- state (mirrors Vape fields) ----
    private long attackTimerMs;
    private long mouseOverTimerMs;
    private long mouseOverDelayMs;
    private int mouseOverTargetId = -1;
    private boolean mouseDownLatch;
    private boolean waitingForFirstHit;
    private int selectTargetId = -1;
    private int lastHurtTime;
    private float rolledExtraDelay;   // ticks, may be negative
    private int earlyHitTicks;
    private int lateTicks;            // full-charge ticks waited so far on the current target
    private int rolledLate;           // how many to wait before this attack
    private int lateTargetId = -1;
    private boolean wasSwinging;      // swing-edge detection stands in for kuro's AttackPackets listener

    public KuroTriggerBot() {
        super("TriggerBot", "Attacks the entity under your crosshair (kuro port)", Category.KURO);
        addSetting(extraDelayMin);
        addSetting(extraDelayMax);
        addSetting(mouseOverMin);
        addSetting(mouseOverMax);
        addSetting(lateFire);
        addSetting(lateMin);
        addSetting(lateMax);
        addSetting(requireMouseDown);
        addSetting(ignoreActivationClick);
        addSetting(airCrits);
        addSetting(shieldCheck);
        addSetting(selectFirstHit);
        addSetting(targetMissChance);
        addSetting(earlyHitChance);
        addSetting(weaponsOnly);
        addSetting(players);
        addSetting(ignoreBots);
        addSetting(mobs);
    }

    @Override
    public void onEnable() {
        attackTimerMs = 0;
        mouseOverTargetId = -1;
        mouseOverDelayMs = 0;
        mouseOverTimerMs = 0;
        mouseDownLatch = false;
        resetSelectState();
        rolledExtraDelay = rollExtraDelay();
        earlyHitTicks = 0;
        lastHurtTime = 0;
        resetLate();
        wasSwinging = mc.player != null && mc.player.handSwinging;
    }

    @Override
    public void onDisable() {
        mouseDownLatch = false;
        resetSelectState();
        mouseOverTargetId = -1;
    }

    private float rollExtraDelay() {
        double lo = Math.min(extraDelayMin.getValue(), extraDelayMax.getValue());
        double hi = Math.max(extraDelayMin.getValue(), extraDelayMax.getValue());
        if (lo == hi) return (float) lo;
        return (float) (lo + ThreadLocalRandom.current().nextDouble() * (hi - lo));
    }

    /** New wait for the next attack: random whole ticks between min and max. */
    private void resetLate() {
        int lo = (int) Math.min(lateMin.getAsDouble(), lateMax.getAsDouble());
        int hi = (int) Math.max(lateMin.getAsDouble(), lateMax.getAsDouble());
        rolledLate = lo == hi ? lo : lo + ThreadLocalRandom.current().nextInt(hi - lo + 1);
        lateTicks = 0;
        lateTargetId = -1;
    }

    private long rollMouseOverMs() {
        int lo = (int) Math.min(mouseOverMin.getAsDouble(), mouseOverMax.getAsDouble());
        int hi = (int) Math.max(mouseOverMin.getAsDouble(), mouseOverMax.getAsDouble());
        if (lo == hi) return lo;
        return lo + ThreadLocalRandom.current().nextInt(hi - lo + 1);
    }

    private boolean attackKeyDown() {
        return mc.getWindow() != null
                && GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
    }

    private void resetSelectState() {
        selectTargetId = -1;
        waitingForFirstHit = false;
    }

    /** Vape canAttack(requireActivation) */
    private boolean canAttack(boolean requireActivation) {
        if (mc.player == null) return false;
        if (mc.currentScreen != null) return false;
        if (mc.player.isSpectator()) return false;
        if (mc.player.isUsingItem()) return false;

        if (requireActivation && requireMouseDown.isEnabled()) {
            if (!attackKeyDown()) return false;
        }
        if (weaponsOnly.isEnabled() && !isWeapon(mc.player.getMainHandStack())) return false;

        if (airCrits.isEnabled() && !mc.player.isOnGround()) {
            // only allow while falling (will crit)
            if (mc.player.getVelocity().y >= 0.0 || mc.player.isClimbing() || mc.player.isTouchingWater()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Vape isAttackReady(cooldownOffset): attack progress with offset in ticks.
     * Positive offset = wait longer after full cooldown; negative = allow before 1.0.
     */
    private boolean isAttackReady(float cooldownOffsetTicks) {
        if (mc.player == null) return false;
        float progress = mc.player.getAttackCooldownProgress(0.5f);
        float threshold = 1.0f;
        if (cooldownOffsetTicks < 0) {
            // allow at (1 + offset/20) roughly for a 1-second weapon
            threshold = Math.max(0.0f, 1.0f + cooldownOffsetTicks / 20.0f);
        }
        return progress >= threshold;
    }

    private LivingEntity crosshairLiving() {
        HitResult hr = mc.crosshairTarget;
        if (hr instanceof EntityHitResult er && er.getEntity() instanceof LivingEntity le) return le;
        return null;
    }

    private boolean isValidTarget(Entity entity) {
        if (!(entity instanceof LivingEntity living) || !living.isAlive()) return false;
        if (entity == mc.player) return false;
        if (isFriendShield(entity) || (ignoreBots.isEnabled() && AntiBot.isBot(entity))) return false;
        if (entity instanceof net.minecraft.entity.player.PlayerEntity p) {
            if (!players.isEnabled() || p.isSpectator()) return false;
            if (shieldCheck.isEnabled() && p.isBlocking()) return false;
            return true;
        }
        return mobs.isEnabled();
    }

    /** kuro Friends.shield: friends are skipped by combat targeting. */
    private boolean isFriendShield(Entity entity) {
        try {
            return com.anormal.client.module.impl.client.Friends.isFriend(entity.getName().getString());
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean hasMouseOverDelayElapsed(Entity entity) {
        int id = entity.getId();
        if (mouseOverTargetId != id) {
            mouseOverTargetId = id;
            mouseOverDelayMs = rollMouseOverMs();
            mouseOverTimerMs = System.currentTimeMillis();
        }
        return System.currentTimeMillis() - mouseOverTimerMs >= mouseOverDelayMs;
    }

    private boolean updateSelectFirstHit(Entity entity, boolean wasJustHurt) {
        if (!selectFirstHit.isEnabled()) {
            resetSelectState();
            return true;
        }
        int id = entity.getId();
        if (selectTargetId != id) {
            selectTargetId = id;
            waitingForFirstHit = true;
        }
        if (waitingForFirstHit && wasJustHurt) {
            waitingForFirstHit = false;
        }
        return !waitingForFirstHit;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;

        int hurtTime = mc.player.hurtTime;
        boolean wasJustHurt = lastHurtTime <= 0 && hurtTime > 0;
        lastHurtTime = hurtTime;

        // Require mouse-down release tracking (Vape resets select state when mouse lifts)
        if (requireMouseDown.isEnabled()) {
            boolean down = attackKeyDown();
            if (mouseDownLatch && !down) {
                mouseDownLatch = false;
                resetSelectState();
            }
            if (down) mouseDownLatch = true;
        }

        // Global 50ms throttle like Vape attackTimer
        long now = System.currentTimeMillis();
        if (now - attackTimerMs < 50L) return;

        if (!canAttack(true)) return;

        // cooldownOffset = -extraDelay + earlyHitTicks  (Vape formula)
        float cooldownOffset = -rolledExtraDelay + earlyHitTicks;

        LivingEntity entity = crosshairLiving();
        if (entity != null && isValidTarget(entity)) {
            if (!updateSelectFirstHit(entity, wasJustHurt)) return;
            if (!hasMouseOverDelayElapsed(entity)) return;
            if (!isAttackReady(cooldownOffset)) {
                // early hit chance: sometimes treat as ready before full charge
                if (earlyHitChance.getValue() > 0
                        && mc.player.getAttackCooldownProgress(0.5f) >= 0.7f
                        && ThreadLocalRandom.current().nextDouble() * 100.0 < earlyHitChance.getValue()) {
                    // allow through
                } else {
                    return;
                }
            }

            // Positive extra delay: wait N ticks at full charge
            if (rolledExtraDelay > 0 && mc.player.getAttackCooldownProgress(0.5f) >= 1.0f) {
                earlyHitTicks++;
                if (earlyHitTicks < rolledExtraDelay) return;
            }

            // Late fire: count full-charge ticks on this target (game ticks, not wall clock), fire only after the wait.
            if (lateFire.isEnabled()) {
                if (entity.getId() != lateTargetId) { lateTargetId = entity.getId(); lateTicks = 0; }
                if (mc.player.getAttackCooldownProgress(0.5f) < 1.0f) { lateTicks = 0; return; }
                if (lateTicks < rolledLate) { lateTicks++; return; }
            }

            if (targetMissChance.getValue() > 0
                    && ThreadLocalRandom.current().nextDouble() * 100.0 < targetMissChance.getValue()) {
                attackTimerMs = now;
                rolledExtraDelay = rollExtraDelay();
                earlyHitTicks = 0;
                resetLate();
                return;
            }

            if (inReach(entity)) {
                try {
                    mc.interactionManager.attackEntity(mc.player, entity);
                    mc.player.swingHand(net.minecraft.util.Hand.MAIN_HAND);
                } catch (Throwable ignored) {
                }
            }
            attackTimerMs = now;
            rolledExtraDelay = rollExtraDelay();
            earlyHitTicks = 0;
            resetLate();
            return;
        }

        // No entity under crosshair — reset mouse-over target id (Vape does this)
        mouseOverTargetId = -1;
        mouseOverDelayMs = 0;
        lateTicks = 0;
        lateTargetId = -1;
    }

    /** kuro CombatReach (vanilla 3.0 from the eye to the nearest hitbox point). */
    private static boolean inReach(Entity e) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || e == null) return false;
        Vec3d eye = mc.player.getEyePos();
        Box b = e.getBoundingBox();
        double dx = Math.max(Math.max(b.minX - eye.x, 0), eye.x - b.maxX);
        double dy = Math.max(Math.max(b.minY - eye.y, 0), eye.y - b.maxY);
        double dz = Math.max(Math.max(b.minZ - eye.z, 0), eye.z - b.maxZ);
        return Math.sqrt(dx * dx + dy * dy + dz * dz) <= 3.0;
    }

    private static boolean isWeapon(ItemStack s) {
        if (s.isEmpty()) return false;
        return s.isIn(ItemTags.SWORDS) || s.isIn(ItemTags.AXES)
                || s.getItem() == Items.MACE || s.getItem() == Items.TRIDENT;
    }
}
