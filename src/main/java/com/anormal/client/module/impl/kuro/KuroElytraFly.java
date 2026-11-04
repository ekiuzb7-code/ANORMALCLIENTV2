package com.anormal.client.module.impl.kuro;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;

import net.minecraft.util.math.Vec3d;

/**
 * Ported from kuro (dev.kuro.client.module.impl.ElytraFly).
 *
 * <p>Takes over your elytra while you are gliding.</p>
 * <ul>
 *   <li><b>Control mode</b>: W = fly where you look, S = back, A/D = sideways,
 *       Space = up, Shift = down, no input = hover.</li>
 *   <li><b>Boost mode</b>: hold W to accelerate along your view up to the speed limit
 *       (like fireworks that never run out).</li>
 * </ul>
 * <p>Start flying the normal way (jump, then jump again in the air).</p>
 */
public class KuroElytraFly extends Module {
    private final NumberSetting speed = new NumberSetting("Speed",
            "Glide speed in blocks per tick", 1.5, 0.5, 4.0, 0.1);
    private final NumberSetting vertical = new NumberSetting("Vertical speed",
            "Up / down speed", 0.8, 0.2, 2.0, 0.1);
    private final BooleanSetting boost = new BooleanSetting("Boost mode",
            "Hold W to accelerate along your view", false);

    public KuroElytraFly() {
        super("Elytra Fly", "Takes over the elytra while gliding (kuro port)", Category.KURO);
        addSetting(speed);
        addSetting(vertical);
        addSetting(boost);
    }

    @Override
    public void onPreTick() {
        if (mc.player == null || mc.currentScreen != null || !mc.player.isGliding()) return;

        boolean fwd = mc.options.forwardKey.isPressed();
        boolean back = mc.options.backKey.isPressed();
        boolean left = mc.options.leftKey.isPressed();
        boolean right = mc.options.rightKey.isPressed();
        boolean up = mc.options.jumpKey.isPressed();
        boolean down = mc.options.sneakKey.isPressed();
        Vec3d look = Vec3d.fromPolar(mc.player.getPitch(), mc.player.getYaw());
        double sp = speed.getValue();

        if (boost.isEnabled()) {
            if (!fwd) return;
            Vec3d v = mc.player.getVelocity().add(look.multiply(0.1));
            double len = v.length();
            if (len > sp) v = v.multiply(sp / len);
            mc.player.setVelocity(v);
            return;
        }

        double yaw = Math.toRadians(mc.player.getYaw());
        double rx = -Math.cos(yaw), rz = -Math.sin(yaw);
        double f = (fwd ? 1 : 0) - (back ? 1 : 0);
        double s = (right ? 1 : 0) - (left ? 1 : 0);

        double vx = look.x * f * sp + rx * s * sp;
        double vy = look.y * f * sp;
        double vz = look.z * f * sp + rz * s * sp;
        if (up) vy += vertical.getValue();
        if (down) vy -= vertical.getValue();
        if (f == 0 && s == 0 && !up && !down) vy = 0.02;      // hover (cancels the small sink each tick)
        mc.player.setVelocity(vx, vy, vz);
    }
}
