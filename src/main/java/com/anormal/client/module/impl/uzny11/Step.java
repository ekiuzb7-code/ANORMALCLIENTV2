package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.util.math.Vec3d;

public class Step extends Module {
    public final NumberSetting height = new NumberSetting("Height", "Step jump height factor", 1.0, 0.5, 2.5, 0.5);

    public Step() {
        super("Step", "Steps up blocks when colliding on ground", Category.UZNY11);
        addSetting(height);
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;
        try {
            if (mc.player.horizontalCollision && mc.player.isOnGround()) {
                Vec3d v = mc.player.getVelocity();
                mc.player.setVelocity(v.x, 0.42 * height.getValue(), v.z);
            }
        } catch (Throwable ignored) {}
    }
}
