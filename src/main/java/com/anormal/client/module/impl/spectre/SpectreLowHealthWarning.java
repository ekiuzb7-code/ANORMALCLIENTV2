package com.anormal.client.module.impl.spectre;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.ColorUtils;
import com.anormal.client.util.RenderUtils;
import net.minecraft.client.gui.DrawContext;

/**
 * Spectre port: "Low Health Warning" — pulses a red vignette when your health is low.
 */
public final class SpectreLowHealthWarning extends Module {
    public final NumberSetting threshold = addSetting(new NumberSetting("Health Threshold", "Trigger health (hearts included)", 6.0, 1.0, 20.0, 1.0));
    public final BooleanSetting shake = addSetting(new BooleanSetting("Pulse", "Pulse the vignette", true));
    public final BooleanSetting showOnAbsorption = addSetting(new BooleanSetting("Count Absorption", "Include absorption hearts", false));
    public final ColorSetting color = addSetting(new ColorSetting("Color", "Vignette color", 0xFFB02020));

    public SpectreLowHealthWarning() {
        super("SpectreLowHealthWarning", "Spectre port: pulses a red vignette when your health is low.", Category.SPECTRE);
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (!isEnabled() || mc.player == null) return;
        if (mc.player.isDead()) return;

        float health = mc.player.getHealth() + (showOnAbsorption.isEnabled() ? mc.player.getAbsorptionAmount() : 0.0f);
        float trigger = (float) threshold.getAsDouble();
        if (health > trigger) return;

        // 0..1 intensity: full at 1 heart, fading towards the threshold
        float badness = 1.0f - Math.max(0.0f, Math.min(1.0f, (health - 1.0f) / Math.max(0.01f, trigger - 1.0f)));
        if (health <= 1.0f) badness = 1.0f;

        float pulse = shake.isEnabled() ? (0.65f + 0.35f * (float) Math.abs(Math.sin(
                (System.currentTimeMillis() % 6000L) / 1000.0 * Math.PI))) : 1.0f;

        int alpha = (int) ((100 + 90 * badness) * pulse);
        int cv = color.getValue();
        int col = ((cv >> 16) & 0xFF) << 16 | ((cv >> 8) & 0xFF) << 8 | (cv & 0xFF);
        int w = RenderUtils.screenWidth();
        int h = RenderUtils.screenHeight();

        // Frame vignette: strips on each edge with stepped alpha
        int steps = 14;
        for (int i = 0; i < steps; i++) {
            float f = 1.0f - (float) i / steps;
            int a = (int) (alpha * f * f) / (steps - i + 1);
            int argb = (a << 24) | col;
            int band = 24;
            int thick = band * (steps - i) / steps;
            context.fill(0, 0, w, thick, argb);
            context.fill(0, h - thick, w, h, argb);
            context.fill(0, 0, thick, h, argb);
            context.fill(w - thick, 0, w, h, argb);
        }
    }
}
