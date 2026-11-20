package com.anormal.client.module.impl.legit;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.theme.ThemeManager;
import com.anormal.client.util.ColorUtils;
import com.anormal.client.util.HudStyle;
import com.anormal.client.util.RenderUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;

/**
 * FPS / frame time / entity count.
 *
 * <p>Modernised: FPS is the big value with a frame-time meter that fills up as the frame budget is
 * used (16.7ms = 60fps), and the entity count sits underneath.</p>
 */
public class RenderStats extends Module {
    public final NumberSetting posX = new NumberSetting("Pos X", "X Position on screen", 10.0, 0.0, 1920.0, 1.0);
    public final NumberSetting posY = new NumberSetting("Pos Y", "Y Position on screen", 300.0, 0.0, 1080.0, 1.0);
    public final NumberSetting scale = new NumberSetting("Scale", "HUD scale (0.05 = tiny, 2.0 = huge)", 1.0, 0.05, 2.0, 0.01);
    public final ColorSetting textColor = new ColorSetting("Text Color", "Stats text color", ColorUtils.rgba(255, 255, 255, 255));

    /** Frame time that fills the meter completely (30 fps). */
    private static final double WORST_MS = 33.3;

    public RenderStats() {
        super("RenderStats", "Shows FPS frame time and entities", Category.LEGIT);
        addSetting(posX);
        addSetting(posY);
        addSetting(scale);
        addSetting(textColor);
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.textRenderer == null) return;
        try {
            int fps = mc.getCurrentFps();
            double ms = 1000.0 / Math.max(1, fps);
            int ents = 0;
            try {
                if (mc.world != null) {
                    for (Entity ignored : mc.world.getEntities()) {
                        if (++ents >= 1000) break;
                    }
                }
            } catch (Throwable ignored) {}

            int fpsColor = fps >= 120 ? 0xFF4ADE80 : (fps >= 60 ? 0xFFFBBF24 : 0xFFF87171);
            int accent = ThemeManager.getAccentColor();

            int w = 184, h = 46;
            int[] at = HudStyle.place(posX.getValue().intValue(), posY.getValue().intValue(), w, h);
            int x = at[0], y = at[1];

            HudStyle.panel(context, x, y, w, h, accent);
            HudStyle.title(context, mc.textRenderer, "Render", x + 10, y + 7);

            String fpsText = fps + " fps";
            int fw = mc.textRenderer.getWidth(fpsText);
            RenderUtils.drawText(context, mc.textRenderer, fpsText, x + w - 10 - fw, y + 5,
                    fpsColor, true);

            String frame = String.format(java.util.Locale.ROOT, "%.1f ms", ms) + "  ·  " + ents + " entities";
            RenderUtils.drawText(context, mc.textRenderer, frame, x + 10, y + 19,
                    textColor.getValue(), false);

            HudStyle.bar(context, x + 10, y + 32, w - 20, 4,
                    (float) Math.min(1.0, ms / WORST_MS), fpsColor);
            RenderUtils.drawText(context, mc.textRenderer,
                    fps >= 120 ? "smooth" : (fps >= 60 ? "playable" : "frame time high"),
                    x + 10, y + 38, 0xFF6E7482, false);
        } catch (Throwable ignored) {}
    }
}
