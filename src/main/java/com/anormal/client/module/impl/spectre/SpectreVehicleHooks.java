package com.anormal.client.module.impl.spectre;

import com.anormal.client.module.ModuleManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.vehicle.AbstractBoatEntity;
import net.minecraft.util.math.Vec3d;

/**
 * Shared vehicle override used by the Spectre movement ports (Entity Speed, Boat Fly,
 * Boat Noclip).
 *
 * <p>Spectre rewrites the movement vector of a ridden entity inside its own
 * {@code Entity#move} hook. This port does the same from a single additive mixin
 * ({@code SpectreVehicleMixin}) that asks this class for a replacement vector. All three
 * modules feed the same helper, so they can never fight each other: noclip wins over fly,
 * fly wins over speed.</p>
 */
public final class SpectreVehicleHooks {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static final double DIAGONAL = 1.0 / Math.sqrt(2.0);

    private SpectreVehicleHooks() {
    }

    /** @return the replacement movement vector, or {@code null} to leave vanilla alone. */
    public static Vec3d modify(Entity entity, Vec3d movement) {
        var player = mc.player;
        if (player == null || entity != player.getVehicle()) return null;
        if (entity.getControllingPassenger() != player) return null;

        SpectreBoatNoclip noclip = ModuleManager.getModule(SpectreBoatNoclip.class);
        if (noclip != null && noclip.isEnabled() && entity instanceof AbstractBoatEntity boat) {
            entity.setYaw(player.getYaw());
            return noclip.boatMovement(boat, player);
        }

        SpectreBoatFly fly = ModuleManager.getModule(SpectreBoatFly.class);
        SpectreEntitySpeed speed = ModuleManager.getModule(SpectreEntitySpeed.class);

        Vec3d horizontal = null;
        Double vertical = null;

        if (fly != null && fly.isEnabled()) {
            entity.setYaw(player.getYaw());
            horizontal = horizontalVelocity(fly.horizontalSpeed());
            vertical = fly.verticalVelocity();
        } else if (speed != null && speed.isEnabled() && speed.appliesTo(entity)) {
            horizontal = horizontalVelocity(speed.horizontalSpeed());
        }

        if (horizontal == null && vertical == null) return null;
        Vec3d base = movement;
        return new Vec3d(
                horizontal != null ? horizontal.x : base.x,
                vertical != null ? vertical : base.y,
                horizontal != null ? horizontal.z : base.z);
    }

    /** Boats with a passenger keep their own turning; the hook only takes over the speed. */
    public static Vec3d horizontalVelocity(double blocksPerSecond) {
        var player = mc.player;
        if (player == null) return Vec3d.ZERO;
        float yaw = player.getYaw();
        Vec3d forward = Vec3d.fromPolar(0.0F, yaw);
        Vec3d right = Vec3d.fromPolar(0.0F, yaw + 90.0F);

        double x = 0.0;
        double z = 0.0;
        boolean longitudinal = false;
        boolean lateral = false;

        if (mc.options.forwardKey.isPressed()) {
            x += forward.x / 20.0 * blocksPerSecond;
            z += forward.z / 20.0 * blocksPerSecond;
            longitudinal = true;
        }
        if (mc.options.backKey.isPressed()) {
            x -= forward.x / 20.0 * blocksPerSecond;
            z -= forward.z / 20.0 * blocksPerSecond;
            longitudinal = true;
        }
        if (mc.options.rightKey.isPressed()) {
            x += right.x / 20.0 * blocksPerSecond;
            z += right.z / 20.0 * blocksPerSecond;
            lateral = true;
        }
        if (mc.options.leftKey.isPressed()) {
            x -= right.x / 20.0 * blocksPerSecond;
            z -= right.z / 20.0 * blocksPerSecond;
            lateral = true;
        }

        if (longitudinal && lateral) {
            x *= DIAGONAL;
            z *= DIAGONAL;
        }
        return new Vec3d(x, 0.0, z);
    }

    /** Down means "space is not held"; boats only lift while the jump key is down. */
    public static double verticalVelocity(double verticalSpeed, double fallSpeed) {
        double y = 0.0;
        if (mc.options.jumpKey.isPressed()) y += verticalSpeed / 20.0;
        if (mc.options.sprintKey.isPressed()) y -= verticalSpeed / 20.0;
        else y -= fallSpeed / 20.0;
        return y;
    }
}
