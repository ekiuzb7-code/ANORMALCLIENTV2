package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.util.math.Vec3d;

public class Strafe extends Module {
    public final NumberSetting factor = new NumberSetting("Factor", "Air strafe control factor", 1.2, 1.0, 2.0, 0.05);

    public Strafe() {
        super("Strafe", "Boosts air control while moving mid-air", Category.UZNY11);
        addSetting(factor);
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;
        try {
            if (mc.player.isOnGround()) return;
            if (mc.player.forwardSpeed == 0 && mc.player.sidewaysSpeed == 0) return;
            double boost = (factor.getValue() - 1.0) * 0.06;
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
            Vec3d v = mc.player.getVelocity();
            mc.player.setVelocity(v.x + (dx / len) * boost, v.y, v.z + (dz / len) * boost);
        } catch (Throwable ignored) {}
    }
}
