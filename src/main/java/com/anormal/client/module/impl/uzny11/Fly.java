package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.util.math.Vec3d;

public class Fly extends Module {
    public final NumberSetting speed = new NumberSetting("Speed", "Fly speed (servers may kick)", 1.0, 0.1, 3.0, 0.1);

    public Fly() {
        super("Fly", "Velocity fly, vertical via jump/sneak (servers may kick)", Category.UZNY11);
        addSetting(speed);
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;
        try {
            double s = speed.getValue();
            Vec3d v = mc.player.getVelocity();
            double my;
            if (mc.options.jumpKey.isPressed()) my = s * 0.35;
            else if (mc.options.sneakKey.isPressed()) my = -s * 0.35;
            else my = 0.0;
            double rad = Math.toRadians(mc.player.getYaw());
            double mx = 0.0, mz = 0.0;
            if (mc.options.forwardKey.isPressed()) {
                mx += -Math.sin(rad) * s * 0.35;
                mz += Math.cos(rad) * s * 0.35;
            }
            if (mc.options.backKey.isPressed()) {
                mx -= -Math.sin(rad) * s * 0.35;
                mz -= Math.cos(rad) * s * 0.35;
            }
            if (mx == 0.0 && mz == 0.0) {
                mx = v.x * 0.9;
                mz = v.z * 0.9;
            }
            mc.player.setVelocity(mx, my, mz);
        } catch (Throwable ignored) {}
    }
}
