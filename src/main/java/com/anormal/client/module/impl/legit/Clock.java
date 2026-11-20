package com.anormal.client.module.impl.legit;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.theme.ThemeManager;
import com.anormal.client.util.HudStyle;
import com.anormal.client.util.RenderUtils;
import net.minecraft.client.gui.DrawContext;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Local real-world clock.
 *
 * <p>Modernised: the time is the big value with the date on the right and a seconds arc along the
 * bottom of the panel, so it also reads as a ticking clock instead of static text.</p>
 */
public class Clock extends Module {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("EEE d MMM");

    public final NumberSetting posX = new NumberSetting("Pos X", "X Position on screen", 4.0, 0.0, 1920.0, 1.0);
    public final NumberSetting posY = new NumberSetting("Pos Y", "Y Position on screen", 38.0, 0.0, 1080.0, 1.0);
    public final NumberSetting scale = new NumberSetting("Scale", "HUD scale (0.05 = tiny, 2.0 = huge)", 1.0, 0.05, 2.0, 0.01);

    public Clock() {
        super("Clock", "Displays real-world local time on HUD", Category.LEGIT);
        addSetting(posX);
        addSetting(posY);
        addSetting(scale);
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.textRenderer == null) return;
        try {
            LocalTime now = LocalTime.now();
            String time = now.format(TIME);
            String date = LocalDate.now().format(DATE);

            int w = 176, h = 44;
            int[] at = HudStyle.place(posX.getValue().intValue(), posY.getValue().intValue(), w, h);
            int x = at[0], y = at[1];

            int accent = ThemeManager.getAccentColor();
            HudStyle.panel(context, x, y, w, h, accent);
            HudStyle.title(context, mc.textRenderer, "Time", x + 10, y + 7);

            int dw = mc.textRenderer.getWidth(date);
            RenderUtils.drawText(context, mc.textRenderer, date, x + w - 10 - dw, y + 7,
                    0xFF6E7482, false);

            RenderUtils.drawText(context, mc.textRenderer, time, x + 10, y + 18, accent, true);

            // Seconds arc along the bottom: a live tick without any extra text.
            float sec = (now.getSecond() + now.getNano() / 1.0e9f) / 60.0f;
            HudStyle.bar(context, x + 10, y + h - 9, w - 20, 3, sec, accent);
        } catch (Throwable ignored) {}
    }
}
