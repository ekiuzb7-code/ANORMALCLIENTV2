package com.anormal.client.module.impl.kuro;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.util.kuro.KuroFreecam;

/**
 * Ported from kuro (dev.kuro.client.module.impl.NoSlow).
 *
 * <p>Removes the walking slowdown while eating / drinking / blocking. Done without touching game
 * code: while you use an item the game scales your movement input by 0.2, so just before each tick
 * this adds back the missing speed so you end up moving at normal walking speed.</p>
 *
 * <p>Runs in {@code onPreTick} like kuro's {@code onPreTick}: the velocity has to be in place before
 * vanilla moves the player, which is why Anormal now has a tick-start phase (see
 * {@code Module#onPreTick}).</p>
 */
public class KuroNoSlow extends Module {

    public KuroNoSlow() {
        super("No Slow", "Removes the slowdown while eating, drinking or blocking (kuro port)", Category.KURO);
    }

    @Override
    public void onPreTick() {
        if (mc.player == null || mc.currentScreen != null || KuroFreecam.active()) return;
        if (!mc.player.isUsingItem() || mc.player.hasVehicle() || !mc.player.isOnGround()
                || mc.player.isSneaking() || mc.player.isTouchingWater() || mc.player.isClimbing()) return;

        double f = (mc.options.forwardKey.isPressed() ? 1 : 0) - (mc.options.backKey.isPressed() ? 1 : 0);
        double s = (mc.options.leftKey.isPressed() ? 1 : 0) - (mc.options.rightKey.isPressed() ? 1 : 0);
        double yaw = Math.toRadians(mc.player.getYaw());
        double dx = -Math.sin(yaw) * f + Math.cos(yaw) * s;
        double dz = Math.cos(yaw) * f + Math.sin(yaw) * s;
        double len = Math.hypot(dx, dz);
        if (len < 1e-6) return;
        dx /= len;
        dz /= len;

        // Normal ground walking covers ~2.158 x movement speed per tick; the slowed input still
        // supplies 0.2 x of it (kuro's numbers, unchanged).
        double ms = mc.player.getMovementSpeed();
        double v = ms * (2.158 - 0.2);
        mc.player.setVelocity(dx * v, mc.player.getVelocity().y, dz * v);
    }
}
