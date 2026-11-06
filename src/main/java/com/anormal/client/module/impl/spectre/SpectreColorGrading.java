package com.anormal.client.module.impl.spectre;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.ColorUtils;
import net.minecraft.client.gui.DrawContext;

/**
 * Spectre port: "Color Grading" — vibrance, contrast, warmth and a vignette over the world.
 *
 * <p>Spectre runs a post-process pass. This port keeps the vibrance, contrast, brightness, warmth
 * and vignette sliders and paints them as screen-space layers (warm/cool tint, brightness veil and
 * a vignette ring), which is what the HUD pass can do without a shader.</p>
 */
public final class SpectreColorGrading extends Module {
    public final NumberSetting vibrance = addSetting(new NumberSetting("Vibrance", "Colour punch", 35.0, 0.0, 100.0, 1.0));
    public final NumberSetting contrast = addSetting(new NumberSetting("Contrast", "Contrast", 8.0, -20.0, 40.0, 1.0));
    public final NumberSetting brightness = addSetting(new NumberSetting("Brightness", "Brightness", 3.0, -20.0, 30.0, 1.0));
    public final NumberSetting warmth = addSetting(new NumberSetting("Warmth", "Warm / cool balance", 15.0, -100.0, 100.0, 1.0));
    public final NumberSetting vignette = addSetting(new NumberSetting("Vignette", "Edge darkening", 30.0, 0.0, 100.0, 1.0));

    public SpectreColorGrading() {
        super("SpectreColorGrading", "Spectre port: vibrance, contrast, warmth and a vignette over the world.", Category.SPECTRE);
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.options.hudHidden) return;
        int width = mc.getWindow().getScaledWidth();
        int height = mc.getWindow().getScaledHeight();

        double warm = warmth.getAsDouble();
        if (warm > 1.0) {
            int alpha = (int) Math.min(70, warm * 0.5);
            context.fill(0, 0, width, height, ColorUtils.rgba(255, 160, 80, alpha));
        } else if (warm < -1.0) {
            int alpha = (int) Math.min(70, -warm * 0.5);
            context.fill(0, 0, width, height, ColorUtils.rgba(80, 150, 255, alpha));
        }

        double bright = brightness.getAsDouble();
        if (bright < -0.5) {
            int alpha = (int) Math.min(120, -bright * 4.0);
            context.fill(0, 0, width, height, ColorUtils.rgba(0, 0, 0, alpha));
        } else if (bright > 0.5) {
            int alpha = (int) Math.min(45, bright * 1.5);
            context.fill(0, 0, width, height, ColorUtils.rgba(255, 255, 255, alpha));
        }

        double vib = vibrance.getAsDouble();
        double con = contrast.getAsDouble();
        if (vib > 1.0 || con > 1.0) {
            int alpha = (int) Math.min(26, vib * 0.15 + con * 0.25);
            context.fill(0, 0, width, height, ColorUtils.rgba(120, 200, 255, alpha));
        }

        double vig = vignette.getAsDouble();
        if (vig > 0.5) {
            int steps = 6;
            double reach = Math.min(width, height) * 0.5 * (vig / 100.0);
            for (int i = 0; i < steps; i++) {
                int alpha = (int) (18 * (1.0 - i / (double) steps) * (vig / 100.0) * 3.0);
                int inset = (int) (reach * i / steps);
                int shade = ColorUtils.rgba(0, 0, 0, alpha);
                context.fill(0, 0, width, inset, shade);
                context.fill(0, height - inset, width, height, shade);
                context.fill(0, inset, inset, height - inset, shade);
                context.fill(width - inset, inset, width, height - inset, shade);
            }
        }
    }
}
