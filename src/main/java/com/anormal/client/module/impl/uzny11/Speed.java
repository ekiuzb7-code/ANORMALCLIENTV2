package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.util.math.Vec3d;

public class Speed extends Module {
    public final NumberSetting factor = new NumberSetting("Factor", "Sprint ground boost factor", 1.3, 1.0, 2.0, 0.05);

    public Speed() {
        super("Speed", "Boosts horizontal speed while sprinting on ground", Category.UZNY11);
        addSetting(factor);
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;
        try {
            if (!mc.player.isSprinting() || !mc.player.isOnGround()) return;
            if (mc.player.forwardSpeed == 0 && mc.player.sidewaysSpeed == 0) return;
            double boost = (factor.getValue() - 1.0) * 0.18;
            if (boost <= 0.0) return;
            double rad = Math.toRadians(mc.player.getYaw());
            double dx = -Math.sin(rad) * Math.signum(mc.player.forwardSpeed == 0 ? 1.0 : mc.player.forwardSpeed);
            double dz = Math.cos(rad) * Math.signum(mc.player.forwardSpeed == 0 ? 1.0 : mc.player.forwardSpeed);
            if (mc.player.forwardSpeed == 0) {
                double s = Math.signum(mc.player.sidewaysSpeed);
                dx = Math.cos(rad) * s;
                dz = -Math.sin(rad) * s;
            }
            Vec3d v = mc.player.getVelocity();
            mc.player.setVelocity(v.x + dx * boost, v.y, v.z + dz * boost);
        } catch (Throwable ignored) {}
    }
}
