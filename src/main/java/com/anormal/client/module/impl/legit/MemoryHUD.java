package com.anormal.client.module.impl.legit;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.theme.ThemeManager;
import com.anormal.client.util.ColorUtils;
import com.anormal.client.util.HudStyle;
import com.anormal.client.util.RenderUtils;
import net.minecraft.client.gui.DrawContext;

/**
 * Heap usage readout.
 *
 * <p>Modernised: one panel with the used percentage as the big value, a colour-coded heap meter and
 * the used / max numbers underneath instead of three stacked text lines.</p>
 */
public class MemoryHUD extends Module {
    public final NumberSetting posX = new NumberSetting("Pos X", "X Position on screen", 10.0, 0.0, 1920.0, 1.0);
    public final NumberSetting posY = new NumberSetting("Pos Y", "Y Position on screen", 270.0, 0.0, 1080.0, 1.0);
    public final NumberSetting scale = new NumberSetting("Scale", "HUD scale (0.05 = tiny, 2.0 = huge)", 1.0, 0.05, 2.0, 0.01);
    public final BooleanSetting showMax = new BooleanSetting("Show Max", "Show max heap line", true);
    public final ColorSetting textColor = new ColorSetting("Text Color", "Memory text color", ColorUtils.rgba(255, 255, 255, 255));

    public MemoryHUD() {
        super("MemoryHUD", "Shows RAM used total and GC hint", Category.LEGIT);
        addSetting(posX);
        addSetting(posY);
        addSetting(scale);
        addSetting(showMax);
        addSetting(textColor);
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.textRenderer == null) return;
        try {
            Runtime rt = Runtime.getRuntime();
            long used = (rt.totalMemory() - rt.freeMemory()) / 1048576L;
            long total = rt.totalMemory() / 1048576L;
            long max = rt.maxMemory() / 1048576L;
            long free = rt.freeMemory() / 1048576L;

            float frac = max > 0 ? Math.min(1.0f, (float) used / max) : 0.0f;
            int pct = Math.round(frac * 100);
            int barColor = pct >= 85 ? 0xFFF87171 : (pct >= 60 ? 0xFFFBBF24 : 0xFF4ADE80);
            int accent = ThemeManager.getAccentColor();

            int w = 184, h = 48;
            int[] at = HudStyle.place(posX.getValue().intValue(), posY.getValue().intValue(), w, h);
            int x = at[0], y = at[1];

            HudStyle.panel(context, x, y, w, h, accent);
            HudStyle.title(context, mc.textRenderer, "Memory", x + 10, y + 7);

            String pctText = pct + "%";
            int pw = mc.textRenderer.getWidth(pctText);
            RenderUtils.drawText(context, mc.textRenderer, pctText, x + w - 10 - pw, y + 6,
                    barColor, true);

            String usedText = used + " / " + (showMax.isEnabled() ? max : total) + " MB";
            RenderUtils.drawText(context, mc.textRenderer, usedText, x + 10, y + 19,
                    textColor.getValue(), false);

            HudStyle.bar(context, x + 10, y + 32, w - 20, 4, frac, barColor);

            String hint = "free " + free + " MB" + (pct >= 85 ? "  ·  restart soon" : "");
            RenderUtils.drawText(context, mc.textRenderer, hint, x + 10, y + 39,
                    pct >= 85 ? 0xFFF87171 : 0xFF6E7482, false);
        } catch (Throwable ignored) {}
    }
}
