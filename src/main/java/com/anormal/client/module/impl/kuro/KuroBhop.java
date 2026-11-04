package com.anormal.client.module.impl.kuro;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.InputForcer;
import com.anormal.client.util.kuro.KuroFreecam;

import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.util.math.Vec3d;

/**
 * Ported from kuro (dev.kuro.client.module.impl.Bhop).
 *
 * <p>Bunny hop: jumps automatically every time you land while moving. Optional air speed
 * (0 = vanilla).</p>
 *
 * <p>Same phase split as kuro: the jump key is driven in {@code onPreTick} (kuro's
 * {@code onPreTick}) so it is already down when vanilla reads the input, the air speed is applied in
 * {@code onTick} (kuro's {@code onTick}). Releasing calls {@link KeyBinding#updatePressedStates()},
 * which is kuro's {@code KeyMapping.setAll()} - it re-syncs every key with the real keyboard.
 * {@link InputForcer} keeps the fake press alive through that same refresh.</p>
 */
public class KuroBhop extends Module {
    private final NumberSetting airSpeed = new NumberSetting("Air speed",
            "Horizontal air speed while hopping (0 = vanilla)", 0.0, 0.0, 0.6, 0.02);

    private boolean pressedByUs;

    public KuroBhop() {
        super("Bhop", "Jumps automatically every time you land while moving (kuro port)", Category.KURO);
        addSetting(airSpeed);
    }

    @Override
    public void onDisable() {
        release();
    }

    private void release() {
        if (!pressedByUs) return;
        pressedByUs = false;
        InputForcer.jump = false;
        try {
            KeyBinding.updatePressedStates();          // kuro: KeyMapping.setAll()
        } catch (Throwable ignored) {
        }
    }

    /** Normalised WASD direction for the given yaw, or null when no movement key is held. */
    private static double[] moveDir(GameOptions o, float yawDeg) {
        if (o == null) return null;
        double f = (o.forwardKey.isPressed() ? 1 : 0) - (o.backKey.isPressed() ? 1 : 0);
        double s = (o.leftKey.isPressed() ? 1 : 0) - (o.rightKey.isPressed() ? 1 : 0);
        double yaw = Math.toRadians(yawDeg);
        double dx = -Math.sin(yaw) * f + Math.cos(yaw) * s;
        double dz = Math.cos(yaw) * f + Math.sin(yaw) * s;
        double len = Math.hypot(dx, dz);
        if (len < 1e-6) return null;
        return new double[]{dx / len, dz / len};
    }

    @Override
    public void onPreTick() {
        if (mc.player == null || mc.currentScreen != null || KuroFreecam.active()) {
            release();
            return;
        }
        boolean moving = moveDir(mc.options, mc.player.getYaw()) != null;
        boolean ok = moving && mc.player.isOnGround() && !mc.player.isSneaking()
                && !mc.player.isTouchingWater() && !mc.player.isInLava() && !mc.player.isClimbing();
        if (ok) {
            mc.options.jumpKey.setPressed(true);
            InputForcer.jump = true;
            pressedByUs = true;
        } else {
            release();
        }
    }

    /** kuro's {@code onTick}: optional air speed while airborne. */
    @Override
    public void onTick() {
        double speed = airSpeed.getValue();
        if (speed <= 0 || mc.player == null || mc.currentScreen != null || KuroFreecam.active()) return;
        if (mc.player.isOnGround() || mc.player.isTouchingWater() || mc.player.isInLava()
                || mc.player.isClimbing()) return;
        double[] dir = moveDir(mc.options, mc.player.getYaw());
        if (dir == null) return;
        Vec3d v = mc.player.getVelocity();
        mc.player.setVelocity(dir[0] * speed, v.y, dir[1] * speed);
    }
}
