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

/**
 * Frames-per-second readout with a live graph.
 *
 * <p>Modernised from a plain card into a small panel: the current FPS as the big value plus the
 * last 32 frames as a bar graph, so stutters are visible instead of just the average number.</p>
 */
public class FPS extends Module {
    public final NumberSetting posX = new NumberSetting("Pos X", "X Position on screen", 10.0, 0.0, 1920.0, 1.0);
    public final NumberSetting posY = new NumberSetting("Pos Y", "Y Position on screen", 30.0, 0.0, 1080.0, 1.0);
    public final NumberSetting scale = new NumberSetting("Scale", "HUD scale (0.05 = tiny, 2.0 = huge)", 1.0, 0.05, 2.0, 0.01);
    public final ColorSetting fpsColor = new ColorSetting("Color", "FPS text color", ColorUtils.rgba(0, 230, 255, 255));

    private static final int HISTORY = 32;
    private final int[] frames = new int[HISTORY];
    private int cursor = 0;
    private boolean primed = false;

    public FPS() {
        super("FPS", "Displays current client frames per second on HUD", Category.LEGIT);
        addSetting(posX);
        addSetting(posY);
        addSetting(scale);
        addSetting(fpsColor);
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.textRenderer == null) return;
        try {
            int fps = mc.getCurrentFps();
            if (!primed) {
                java.util.Arrays.fill(frames, fps);
                primed = true;
            }
            frames[cursor] = fps;
            cursor = (cursor + 1) % HISTORY;

            int fpsColor = this.fpsColor.getValue();
            int accent = ThemeManager.getAccentColor();

            int w = 156, h = 46;
            int[] at = HudStyle.place(posX.getValue().intValue(), posY.getValue().intValue(), w, h);
            int x = at[0], y = at[1];

            HudStyle.panel(context, x, y, w, h, accent);
            HudStyle.title(context, mc.textRenderer, "FPS", x + 10, y + 7);

            String value = String.valueOf(fps);
            int vw = mc.textRenderer.getWidth(value);
            RenderUtils.drawText(context, mc.textRenderer, value, x + w - 10 - vw, y + 5,
                    fpsColor, true);

            // Bar graph: each column is one past frame, scaled to a 240 fps ceiling.
            int gw = w - 20;
            int gh = 18;
            int gy = y + 22;
            int barW = Math.max(1, gw / HISTORY);
            for (int i = 0; i < HISTORY; i++) {
                int sample = frames[(cursor + i) % HISTORY];
                int barH = (int) Math.max(1, Math.min(gh, gh * (sample / 240.0f)));
                int col = sample >= 120 ? 0xFF4ADE80 : (sample >= 60 ? 0xFFFBBF24 : 0xFFF87171);
                int bx = x + 10 + i * barW;
                RenderUtils.fill(context, bx, gy + gh - barH, bx + barW - 1, gy + gh, col);
            }
            RenderUtils.fill(context, x + 10, gy + gh, x + 10 + gw, gy + gh + 1, 0x22FFFFFF);
        } catch (Throwable ignored) {}
    }
}
