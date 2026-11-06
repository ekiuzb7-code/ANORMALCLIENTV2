package com.anormal.client.module.impl.spectre;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.network.ClientPlayerEntity;

/**
 * Spectre port: "Speed" — modifies your movement speed when moving on the ground.
 *
 * <p>Vanilla mode drives the velocity straight from the movement input; Strafe mode is
 * Space's classic stage-machine strafe (hop + decay Decel) ported value-for-value.
 * The server rubberband reset needs a client-position packet hook and false-negative
 * Surface detection is halved honestly here: any forced position re-sync resets the
 * stage machine via a position-count heuristic.</p>
 */
public final class SpectreSpeed extends Module implements SpectreMovementHooks.MoveHook {
    private static final double BASE_SPEED = 0.2873;
    private static final double DIAGONAL = 1.0 / Math.sqrt(2.0);

    public final ModeSetting mode = addSetting(new ModeSetting("Mode", "Speed mode", "Vanilla", "Vanilla", "Strafe"));
    public final NumberSetting vanillaSpeed = addSetting(new NumberSetting("Vanilla Speed", "Vanilla speed in blocks-per-tick", 5.6, 0.0, 20.0, 0.1));
    public final NumberSetting strafeSpeed = addSetting(new NumberSetting("Strafe Speed", "Strafe hop multiplier", 1.6, 0.0, 3.0, 0.1));
    public final BooleanSetting speedLimit = addSetting(new BooleanSetting("Speed Limit", "Cap the strafe speed (anti-anticheat burst)", false));
    public final BooleanSetting inLiquids = addSetting(new BooleanSetting("In Liquids", "Apply while in water/lava", false));
    public final BooleanSetting whenSneaking = addSetting(new BooleanSetting("When Sneaking", "Apply while sneaking", false));
    public final BooleanSetting onlyOnGround = addSetting(new BooleanSetting("Only On Ground", "Vanilla mode only while grounded", false));

    private int stage;
    private double distance;
    private double speed;
    private double lastY = Double.NaN;
    private int forcedMoveCounter = 0;

    public SpectreSpeed() {
        super("SpectreSpeed", "Spectre port: modifies your movement speed when moving on the ground.", Category.SPECTRE);
    }

    @Override
    public void onEnable() {
        resetStage();
        SpectreMovementHooks.rebind();
    }

    @Override
    public void onDisable() {
        resetStage();
        SpectreMovementHooks.rebind();
    }

    private void resetStage() {
        stage = 0;
        distance = 0;
        speed = BASE_SPEED;
    }

    /** Registration point the mixin dispatch layer polls for hooks. */
    public Vec3d onMove(Entity entity, MovementType type, Vec3d movement) {
        if (!isEnabled() || !(entity instanceof ClientPlayerEntity player)) return movement;
        if (stopSpeed(player)) return movement;

        if (Double.isNaN(lastY)) lastY = player.getY();
        // Rubberband-ish reset: teleport-sized positional jumps reset the state machine
        if (Math.abs(player.getY() - lastY) > 8.0) resetStage();
        lastY = player.getY();

        phase(player);
        return mode.is("Strafe") ? strafeMove(player, movement) : vanillaMove(player, movement);
    }

    private boolean stopSpeed(PlayerEntity player) {
        if (player.isGliding() || player.isClimbing() || player.getVehicle() != null) return true;
        if (!whenSneaking.isEnabled() && player.isSneaking()) return true;
        if (onlyOnGround.isEnabled() && !player.isOnGround() && mode.is("Vanilla")) return true;
        if (!inLiquids.isEnabled() && (player.isTouchingWater() || player.isInLava())) return true;
        return false;
    }

    private void phase(PlayerEntity player) {
        double dx = player.getX() - player.lastX;
        double dz = player.getZ() - player.lastZ;
        distance = Math.sqrt(dx * dx + dz * dz);
    }

    private double getDefaultSpeed(PlayerEntity player) {
        double defaultSpeed = BASE_SPEED;
        StatusEffectInstance speedEffect = player.getStatusEffect(StatusEffects.SPEED);
        if (speedEffect != null) {
            defaultSpeed *= 1.0 + 0.2 * (speedEffect.getAmplifier() + 1);
        }
        StatusEffectInstance slowness = player.getStatusEffect(StatusEffects.SLOWNESS);
        if (slowness != null) {
            defaultSpeed /= 1.0 + 0.2 * (slowness.getAmplifier() + 1);
        }
        return defaultSpeed;
    }

    private double getHop(PlayerEntity player, double height) {
        StatusEffectInstance jumpBoost = player.getStatusEffect(StatusEffects.JUMP_BOOST);
        if (jumpBoost != null) height += (jumpBoost.getAmplifier() + 1) * 0.1;
        return height;
    }

    private Vec3d vanillaMove(ClientPlayerEntity player, Vec3d movement) {
        double bps = vanillaSpeed.getAsDouble() / 20.0;
        float yaw = player.getYaw();
        Vec3d forward = Vec3d.fromPolar(0.0f, yaw);
        Vec3d right = Vec3d.fromPolar(0.0f, yaw + 90.0f);
        double velX = 0.0, velZ = 0.0;
        boolean fwd = mc.options.forwardKey.isPressed();
        boolean back = mc.options.backKey.isPressed();
        boolean lft = mc.options.leftKey.isPressed();
        boolean rgt = mc.options.rightKey.isPressed();
        if (fwd) {
            velX += forward.x * bps;
            velZ += forward.z * bps;
        }
        if (back) {
            velX -= forward.x * bps;
            velZ -= forward.z * bps;
        }
        if (rgt) {
            velX += right.x * bps;
            velZ += right.z * bps;
        }
        if (lft) {
            velX -= right.x * bps;
            velZ -= right.z * bps;
        }
        if ((fwd || back) && (lft || rgt)) {
            velX *= DIAGONAL;
            velZ *= DIAGONAL;
        }
        StatusEffectInstance speedEffect = player.getStatusEffect(StatusEffects.SPEED);
        if (speedEffect != null) {
            double value = (speedEffect.getAmplifier() + 1) * 0.205;
            velX += velX * value;
            velZ += velZ * value;
        }
        return new Vec3d(velX, movement.y, velZ);
    }

    private Vec3d strafeMove(ClientPlayerEntity player, Vec3d movement) {
        var mv = player.input.getMovementInput();
        boolean moving = (mv.x != 0.0f || mv.y != 0.0f);
        double y = movement.y;
        switch (stage) {
            case 0 -> {
                if (moving) {
                    stage++;
                    speed = 1.18 * getDefaultSpeed(player) - 0.01;
                }
            }
            case 1 -> {
                if (moving && player.isOnGround()) {
                    y = getHop(player, 0.40123128);
                    speed = speed * strafeSpeed.getAsDouble();
                    stage++;
                }
            }
            case 2 -> {
                speed = distance - 0.76 * (distance - getDefaultSpeed(player));
                stage++;
            }
            case 3 -> {
                var box = player.getBoundingBox().offset(0.0, player.getVelocity().y, 0.0);
                boolean nextBlocked = mc.world != null && !mc.world.isSpaceEmpty(box);
                if (nextBlocked || (player.verticalCollision && stage > 0)) stage = 0;
                speed = distance - distance / 159.0;
            }
        }
        speed = Math.max(speed, getDefaultSpeed(player));
        if (speedLimit.isEnabled()) {
            speed = Math.min(speed, 0.44);
        }
        double[] change = transformStrafe(player, speed);
        return new Vec3d(change[0], y, change[1]);
    }

    private static double[] transformStrafe(ClientPlayerEntity player, double speed) {
        var mv = player.input.getMovementInput();
        float inpForward = mv.y;
        float inpSide = -mv.x;
        float yaw = player.getYaw();
        float signumForward = Math.signum(inpForward);
        float signumSide = Math.signum(inpSide);
        if (signumForward == 0.0f && signumSide == 0.0f) return new double[2];
        float strafe = 90.0f * signumSide;
        if (signumForward != 0.0f) strafe *= signumForward * 0.5f;
        yaw -= strafe;
        if (signumForward < 0.0f) yaw -= 180.0f;
        double yawRadians = Math.toRadians(yaw);
        return new double[]{-Math.sin(yawRadians) * speed, Math.cos(yawRadians) * speed};
    }
}
