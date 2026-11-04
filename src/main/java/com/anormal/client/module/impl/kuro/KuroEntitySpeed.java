package com.anormal.client.module.impl.kuro;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.kuro.KuroFreecam;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;

/**
 * Ported from kuro (dev.kuro.client.module.impl.EntitySpeed).
 *
 * <p>Sets the horizontal speed of the entity you are riding (boat, horse, pig, minecart...)
 * while you hold a movement key.</p>
 */
public class KuroEntitySpeed extends Module {
    private final NumberSetting speed = new NumberSetting("Speed",
            "Ride speed in blocks per tick", 1.0, 0.1, 5.0, 0.1);

    public KuroEntitySpeed() {
        super("Entity Speed", "Sets the speed of the entity you are riding (kuro port)", Category.KURO);
        addSetting(speed);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.currentScreen != null || KuroFreecam.active()) return;
        Entity vehicle = mc.player.getVehicle();
        if (vehicle == null) return;

        double f = (mc.options.forwardKey.isPressed() ? 1 : 0) - (mc.options.backKey.isPressed() ? 1 : 0);
        double s = (mc.options.leftKey.isPressed() ? 1 : 0) - (mc.options.rightKey.isPressed() ? 1 : 0);
        if (f == 0 && s == 0) return;

        double yaw = Math.toRadians(mc.player.getYaw());
        double dx = -Math.sin(yaw) * f + Math.cos(yaw) * s;
        double dz = Math.cos(yaw) * f + Math.sin(yaw) * s;
        double len = Math.hypot(dx, dz);
        dx /= len;
        dz /= len;

        Vec3d v = vehicle.getVelocity();
        vehicle.setVelocity(dx * speed.getValue(), v.y, dz * speed.getValue());
    }
}
