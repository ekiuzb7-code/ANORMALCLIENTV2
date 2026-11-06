package com.anormal.client.module.impl.spectre;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.ColorUtils;
import net.minecraft.client.gui.DrawContext;

/**
 * Spectre port: "Motion Blur" — smears the view when you turn.
 *
 * <p>Spectre blends the previous frame with the current one in a post pass. This port keeps the
 * strength slider and the turn-driven response (the smear only appears while yaw or pitch is
 * changing and fades out once you stop), painting the trail as screen-edge streaks.</p>
 */
public final class SpectreMotionBlur extends Module {
    public final NumberSetting strength = addSetting(new NumberSetting("Strength", "Blur strength", 50.0, 10.0, 90.0, 5.0));

    private float lastYaw;
    private float lastPitch;
    private float speed;

    public SpectreMotionBlur() {
        super("SpectreMotionBlur", "Spectre port: smears the view when you turn.", Category.SPECTRE);
    }

    @Override
    public void onEnable() {
        speed = 0.0F;
        lastYaw = 0.0F;
        lastPitch = 0.0F;
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        var player = mc.player;
        if (player == null || mc.options.hudHidden) return;

        float yaw = player.getYaw();
        float pitch = player.getPitch();
        float delta = Math.abs(net.minecraft.util.math.MathHelper.wrapDegrees(yaw - lastYaw)) + Math.abs(pitch - lastPitch) * 0.5F;
        lastYaw = yaw;
        lastPitch = pitch;
        speed += (Math.min(delta, 20.0F) - speed) * 0.35F;
        if (speed < 0.6F) return;

        double strengthValue = strength.getAsDouble() / 100.0;
        float amount = (float) Math.min(1.0, speed / 12.0) * (float) strengthValue;
        int width = mc.getWindow().getScaledWidth();
        int height = mc.getWindow().getScaledHeight();
        int steps = 8;
        for (int i = 0; i < steps; i++) {
            double u = i / (double) steps;
            int alpha = (int) (26 * amount * (1.0 - u));
            if (alpha <= 0) continue;
            int span = (int) (width * 0.35 * (1.0 - u) * amount);
            int color = ColorUtils.rgba(235, 235, 245, alpha);
            context.fill(0, 0, span, height, color);
            context.fill(width - span, 0, width, height, color);
        }
    }
}
