package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.util.math.Vec3d;

public class LongJump extends Module {
    public final NumberSetting boost = new NumberSetting("Boost", "Forward jump boost", 1.5, 0.5, 3.0, 0.1);
    public final NumberSetting height = new NumberSetting("Height", "Jump height", 0.5, 0.1, 1.5, 0.05);

    public LongJump() {
        super("LongJump", "Boosts forward when jumping on ground", Category.UZNY11);
        addSetting(boost);
        addSetting(height);
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;
        try {
            if (!mc.player.isOnGround() || !mc.options.jumpKey.isPressed()) return;
            if (mc.player.forwardSpeed == 0 && mc.player.sidewaysSpeed == 0) return;
            double rad = Math.toRadians(mc.player.getYaw());
            double dx = -Math.sin(rad);
            double dz = Math.cos(rad);
            Vec3d v = mc.player.getVelocity();
            double b = boost.getValue() * 0.35;
            mc.player.setVelocity(v.x + dx * b, height.getValue(), v.z + dz * b);
        } catch (Throwable ignored) {}
    }
}
