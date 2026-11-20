package com.anormal.client.module.impl.combat;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.module.impl.client.Friends;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * Reach — extended attack range towards entities/players.
 *
 * <p>The old implementation compared feet-to-feet centre distance and swung on every single tick
 * without waiting for the attack cooldown. Servers silently drop attack packets that arrive
 * faster than the weapon cooldown or that are clearly out of range, so the module looked dead.
 * This version:</p>
 * <ul>
 *   <li>extends the vanilla {@code entity_interaction_range} attribute, so the client's own
 *       crosshair and attack paths accept the longer distance (vanilla clicks benefit too),</li>
 *   <li>measures eye -> hitbox distance — the same metric the server uses for its check,</li>
 *   <li>turns the player onto the target (limited turn speed) before the attack packet, so the
 *       hit is genuinely aimed and does not look like a through-the-wall swing,</li>
 *   <li>waits for a full attack cooldown, which is what makes the hit register on real servers,</li>
 *   <li>leaves targets inside vanilla reach to the normal click, so no double swings happen.</li>
 * </ul>
 *
 * <p>Range values far above vanilla are still rejected by servers that enforce their own reach
 * limit; keeping the value legit (≈3.1-3.6) is what makes it work online.</p>
 */
public class Reach extends Module {
    private static final double VANILLA_RANGE = 3.0;

    public final NumberSetting range = new NumberSetting("Range", "Reach distance in blocks", 3.5, 3.0, 20.0, 0.1);
    public final BooleanSetting sprintOnly = new BooleanSetting("Sprint Only", "Only applies reach while sprinting", false);
    public final BooleanSetting weaponOnly = new BooleanSetting("Weapon Only", "Only applies reach when holding sword/axe", false);

    private double lastAppliedRange = -1.0;

    public Reach() {
        super("Reach", "Extends attack reach distance towards targets", Category.COMBAT);
        addSetting(range);
        addSetting(sprintOnly);
        addSetting(weaponOnly);
    }

    @Override
    public void onEnable() {
        lastAppliedRange = -1.0;
    }

    @Override
    public void onDisable() {
        applyAttribute(VANILLA_RANGE);
        lastAppliedRange = -1.0;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;

        boolean active = isActive();
        // Keep the attribute in sync with the slider; revert to vanilla when a condition blocks it.
        applyAttribute(active ? Math.max(VANILLA_RANGE, range.getValue()) : VANILLA_RANGE);

        if (!active || mc.options == null || !mc.options.attackKey.isPressed()) return;

        // Attack packets sent faster than the cooldown are ignored by the server (and by vanilla),
        // so this is what makes an extended-range hit actually land.
        if (mc.player.getAttackCooldownProgress(0.0f) < 0.96f) return;

        Entity target = findTarget();
        if (target == null) return;

        rotateTo(target);
        try {
            mc.interactionManager.attackEntity(mc.player, target);
            mc.player.swingHand(Hand.MAIN_HAND);
        } catch (Throwable ignored) {
        }
    }

    private boolean isActive() {
        if (sprintOnly.isEnabled() && !mc.player.isSprinting()) return false;
        return !weaponOnly.isEnabled() || isWeapon();
    }

    /** Best extended-range target: the crosshair entity first, then the nearest one we can see. */
    private Entity findTarget() {
        double reach = Math.max(VANILLA_RANGE, range.getValue());

        if (mc.crosshairTarget instanceof EntityHitResult hit
                && hit.getEntity() instanceof LivingEntity aimed
                && isValidTarget(aimed)) {
            double d = eyeDistance(aimed);
            if (d > VANILLA_RANGE && d <= reach) return aimed;
        }

        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;
        Vec3d eye = mc.player.getEyePos();
        Vec3d look = mc.player.getRotationVec(1.0f);

        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof LivingEntity living) || !isValidTarget(living)) continue;
            double d = eyeDistance(living);
            if (d <= VANILLA_RANGE || d > reach) continue;
            // Servers reject hits without line of sight, so only consider visible targets.
            if (!mc.player.canSee(living)) continue;

            Vec3d dir = living.getBoundingBox().getCenter().subtract(eye).normalize();
            double aimPenalty = (1.0 - look.dotProduct(dir)) * 4.0;
            double score = d + Math.max(0.0, aimPenalty);
            if (score < bestScore) {
                bestScore = score;
                best = living;
            }
        }
        return best;
    }

    private boolean isValidTarget(LivingEntity entity) {
        if (entity == mc.player || !entity.isAlive() || entity.isSpectator()) return false;
        if (entity instanceof PlayerEntity player && Friends.isFriend(player.getName().getString())) return false;
        return true;
    }

    /** Eye -> closest point of the target's hitbox, in blocks. */
    private double eyeDistance(Entity entity) {
        Vec3d eye = mc.player.getEyePos();
        Box box = entity.getBoundingBox();
        double dx = Math.max(Math.max(box.minX - eye.x, 0.0), eye.x - box.maxX);
        double dy = Math.max(Math.max(box.minY - eye.y, 0.0), eye.y - box.maxY);
        double dz = Math.max(Math.max(box.minZ - eye.z, 0.0), eye.z - box.maxZ);
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** Turns onto the target with a limited turn speed so the attack is aimed when it is sent. */
    private void rotateTo(Entity target) {
        Vec3d eye = mc.player.getEyePos();
        Vec3d aim = target.getBoundingBox().getCenter();
        double dx = aim.x - eye.x;
        double dy = aim.y - eye.y;
        double dz = aim.z - eye.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);

        float targetYaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float targetPitch = (float) (-Math.toDegrees(Math.atan2(dy, horizontal)));

        float maxStep = 45.0f;
        float yaw = mc.player.getYaw() + clamp(wrapDegrees(targetYaw - mc.player.getYaw()), maxStep);
        float pitch = mc.player.getPitch() + clamp(targetPitch - mc.player.getPitch(), maxStep);
        mc.player.setYaw(yaw);
        mc.player.setPitch(Math.max(-90.0f, Math.min(90.0f, pitch)));
    }

    private static float clamp(float value, float limit) {
        return Math.max(-limit, Math.min(limit, value));
    }

    private static float wrapDegrees(float degrees) {
        float d = degrees % 360.0f;
        if (d >= 180.0f) d -= 360.0f;
        if (d < -180.0f) d += 360.0f;
        return d;
    }

    private void applyAttribute(double value) {
        if (mc.player == null) return;
        if (Math.abs(value - lastAppliedRange) < 1.0e-4) return;
        try {
            EntityAttributeInstance inst = mc.player.getAttributeInstance(EntityAttributes.ENTITY_INTERACTION_RANGE);
            if (inst != null) {
                inst.setBaseValue(value);
                lastAppliedRange = value;
            }
        } catch (Throwable ignored) {
        }
    }

    private boolean isWeapon() {
        Identifier id = Registries.ITEM.getId(mc.player.getMainHandStack().getItem());
        if (id == null) return false;
        String path = id.getPath();
        return path.endsWith("_sword") || path.endsWith("_axe")
                || path.equals("mace") || path.equals("trident");
    }
}
