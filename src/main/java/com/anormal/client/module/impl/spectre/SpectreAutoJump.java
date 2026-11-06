package com.anormal.client.module.impl.spectre;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.util.math.Vec3d;

/**
 * Spectre port: "Auto Jump" — jumps for you.
 *
 * <p>Jump mode uses the vanilla jump velocity; Low Hop uses the configured height so the
 * hop stays small. {@code Jump If} decides whether sprinting, walking or always counts.</p>
 */
public final class SpectreAutoJump extends Module {
    private static final double VANILLA_JUMP = 0.42;

    public final ModeSetting mode = addSetting(new ModeSetting("Mode", "Jump style", "Jump", "Jump", "LowHop"));
    public final ModeSetting jumpIf = addSetting(new ModeSetting("Jump If", "When to jump", "Always", "Sprinting", "Walking", "Always"));
    public final NumberSetting velocityHeight = addSetting(new NumberSetting("Velocity Height", "Low Hop height", 0.25, 0.0, 2.0, 0.05));

    public SpectreAutoJump() {
        super("SpectreAutoJump", "Spectre port: automatically jumps.", Category.SPECTRE);
    }

    @Override
    public void onPreTick() {
        var player = mc.player;
        if (player == null || mc.world == null) return;
        if (!player.isOnGround() || player.isSneaking()) return;
        if (!shouldJump()) return;

        Vec3d velocity = player.getVelocity();
        double height = mode.is("Jump") ? VANILLA_JUMP : velocityHeight.getAsDouble();
        player.setVelocity(velocity.x, height, velocity.z);
    }

    private boolean shouldJump() {
        boolean moving = mc.options.forwardKey.isPressed() || mc.options.backKey.isPressed()
                || mc.options.leftKey.isPressed() || mc.options.rightKey.isPressed();
        return switch (jumpIf.getValue()) {
            case "Sprinting" -> mc.player.isSprinting() && moving;
            case "Walking" -> moving;
            default -> true;
        };
    }
}
