package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.util.math.Vec3d;

public class Timer extends Module {
    public final NumberSetting speed = new NumberSetting("Timer Speed", "Movement boost factor (approximation, no timer access)", 1.2, 0.5, 2.0, 0.05);

    public Timer() {
        super("Timer", "Approximates timer via ground movement boost (no timer access)", Category.UZNY11);
        addSetting(speed);
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;
        if (!mc.player.isOnGround()) return;
        if (mc.player.forwardSpeed == 0 && mc.player.sidewaysSpeed == 0) return;
        try {
            double boost = (speed.getValue() - 1.0) * 0.15;
            if (boost <= 0.0) return;
            double rad = Math.toRadians(mc.player.getYaw());
            double dx = 0.0, dz = 0.0;
            if (mc.player.forwardSpeed != 0) {
                double s = Math.signum(mc.player.forwardSpeed);
                dx += -Math.sin(rad) * s;
                dz += Math.cos(rad) * s;
            }
            if (mc.player.sidewaysSpeed != 0) {
                double s = Math.signum(mc.player.sidewaysSpeed);
                dx += Math.cos(rad) * s;
                dz += -Math.sin(rad) * s;
            }
            double len = Math.hypot(dx, dz);
            if (len < 1e-4) return;
            dx /= len;
            dz /= len;
            Vec3d v = mc.player.getVelocity();
            mc.player.setVelocity(v.x + dx * boost, v.y, v.z + dz * boost);
        } catch (Throwable ignored) {}
    }
}
