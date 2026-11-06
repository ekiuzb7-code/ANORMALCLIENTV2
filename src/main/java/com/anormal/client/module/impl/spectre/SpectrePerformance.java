package com.anormal.client.module.impl.spectre;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.module.ModuleManager;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.ColorUtils;
import net.minecraft.client.gui.DrawContext;

import java.util.Locale;

/**
 * Spectre port: "Performance" — render performance settings and a small HUD readout.
 *
 * <p>Spectre shows its 3D-listener frame time and chunk-scan queue through ImGui. This port
 * measures the same frame time (smoothed the same way, an eighth per frame) in the Anormal
 * HUD pass, and keeps the frustum-culling accessor that the render hooks read.</p>
 */
public final class SpectrePerformance extends Module {
    public final BooleanSetting frustumCulling = addSetting(new BooleanSetting("Frustum Culling", "Skip chunks outside the view frustum", true));
    public final NumberSetting scanBudget = addSetting(new NumberSetting("Scan Budget ms", "Time budget for chunk scanning", 2.0, 0.5, 8.0, 0.5));
    public final BooleanSetting hud = addSetting(new BooleanSetting("HUD", "Show the performance readout", true));
    public final BooleanSetting shadow = addSetting(new BooleanSetting("Shadow", "Draw the readout with a shadow", true));
    public final ColorSetting textColor = addSetting(new ColorSetting("Text Color", "Readout colour", ColorUtils.rgba(255, 176, 72, 255)));

    private long smoothedNanos;
    private long lastFrameNanos;

    public SpectrePerformance() {
        super("SpectrePerformance", "Spectre port: render performance settings and a small HUD readout.", Category.SPECTRE);
    }

    @Override
    public void onEnable() {
        smoothedNanos = 0L;
        lastFrameNanos = 0L;
    }

    public static boolean frustumCulling() {
        SpectrePerformance module = ModuleManager.getModule(SpectrePerformance.class);
        return module == null || !module.isEnabled() || module.frustumCulling.isEnabled();
    }

    public static long scanBudgetNanos() {
        SpectrePerformance module = ModuleManager.getModule(SpectrePerformance.class);
        return module == null ? 2_000_000L : (long) (module.scanBudget.getAsDouble() * 1_000_000.0);
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        long now = System.nanoTime();
        if (lastFrameNanos != 0L) {
            long frame = now - lastFrameNanos;
            smoothedNanos = smoothedNanos + (frame - smoothedNanos) / 8L;
        }
        lastFrameNanos = now;
        if (!hud.isEnabled() || mc.player == null || mc.options.hudHidden) return;

        String text = String.format(Locale.ROOT, "frame %.2f ms  ·  %d modules",
                smoothedNanos / 1_000_000.0, ModuleManager.getModules().size());
        int width = mc.textRenderer.getWidth(text);
        int x = mc.getWindow().getScaledWidth() - width - 8;
        int y = mc.getWindow().getScaledHeight() - 12;
        context.drawText(mc.textRenderer, text, x, y, textColor.getColor(), shadow.isEnabled());
    }
}
