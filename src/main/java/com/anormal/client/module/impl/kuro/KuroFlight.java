package com.anormal.client.module.impl.kuro;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.kuro.KuroFreecam;

/**
 * Ported from kuro (dev.kuro.client.module.impl.Flight).
 *
 * <p>Free flight: move with the normal movement keys, Jump = up, Sneak = down.</p>
 *
 * <p>The motion is set every client tick, exactly like kuro. This is a raw velocity flight: it does
 * not touch the player abilities, so a server with movement checks may reject it.</p>
 */
public class KuroFlight extends Module {
    private final NumberSetting speed = new NumberSetting("Speed",
            "Flight speed in blocks per tick", 0.5, 0.1, 3.0, 0.1);

    public KuroFlight() {
        super("Flight", "Free flight with the movement keys (kuro port)", Category.KURO);
        addSetting(speed);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.currentScreen != null || KuroFreecam.active()) return;

        double f = (mc.options.forwardKey.isPressed() ? 1 : 0) - (mc.options.backKey.isPressed() ? 1 : 0);
        double s = (mc.options.leftKey.isPressed() ? 1 : 0) - (mc.options.rightKey.isPressed() ? 1 : 0);
        double u = (mc.options.jumpKey.isPressed() ? 1 : 0) - (mc.options.sneakKey.isPressed() ? 1 : 0);

        double yaw = Math.toRadians(mc.player.getYaw());
        double dx = -Math.sin(yaw) * f + Math.cos(yaw) * s;
        double dz = Math.cos(yaw) * f + Math.sin(yaw) * s;
        double len = Math.hypot(dx, dz);
        if (len > 1e-6) {
            dx /= len;
            dz /= len;
        }

        mc.player.setVelocity(dx * speed.getValue(), u * speed.getValue(), dz * speed.getValue());
        mc.player.fallDistance = 0.0f;
    }
}
