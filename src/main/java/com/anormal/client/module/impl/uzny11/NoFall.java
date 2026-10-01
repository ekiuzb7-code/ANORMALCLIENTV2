package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.util.math.Vec3d;

public class NoFall extends Module {
    public final NumberSetting distance = new NumberSetting("Distance", "Fall distance before negate", 5.0, 1.0, 20.0, 1.0);

    public NoFall() {
        super("NoFall", "Clamps descent and resets fall distance", Category.UZNY11);
        addSetting(distance);
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;
        try {
            if (mc.player.isOnGround()) return;
            Vec3d v = mc.player.getVelocity();
            if (mc.player.fallDistance > 2.0f && v.y < -0.3) {
                mc.player.setVelocity(v.x * 0.98, -0.25, v.z * 0.98);
            }
            if (mc.player.fallDistance >= distance.getValue().floatValue()) {
                mc.player.fallDistance = 0.0f;
            }
        } catch (Throwable ignored) {}
    }
}
