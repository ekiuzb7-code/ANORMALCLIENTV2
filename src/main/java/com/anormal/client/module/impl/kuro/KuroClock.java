package com.anormal.client.module.impl.kuro;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.util.RenderUtils;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * kuro {@code Clock} port: haqiqiy (IRL) vaqt — HUD plitkasida. Joylashuv,
 * 12/24 soatlik format, sekund va sana — hammasi sozlama. kuro'daki
 * {@code HudElement} interfeysi / HUD muharriri Anormalda yo'q; bu portda
 * KuroStopwash uslubidagi X/Y sozlamalar bilan joylashadi.
 */
public class KuroClock extends Module {
    private static final DateTimeFormatter T24 = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter T24S = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter T12 = DateTimeFormatter.ofPattern("h:mm a");
    private static final DateTimeFormatter T12S = DateTimeFormatter.ofPattern("h:mm:ss a");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("EEE d MMM");

    private static final int BG = 0xB0101214, BORDER = 0xFF3A3A44, TEXT = 0xFFE8E8F0, MUTED = 0xFF8D8D98, ACCENT = 0xFFFFD27F;

    private final NumberSetting x = new NumberSetting("X", "HUD position", 6.0, 0.0, 4000.0, 1.0);
    private final NumberSetting y = new NumberSetting("Y", "HUD position", 20.0, 0.0, 4000.0, 1.0);
    private final BooleanSetting clock24 = new BooleanSetting("24h Clock", true);
    private final BooleanSetting seconds = new BooleanSetting("Seconds", true);
    private final BooleanSetting date = new BooleanSetting("Date", false);

    private int lastW = 60, lastH = 15;

    public KuroClock() {
        super("Clock", "Real-world time as a HUD plate", Category.KURO);
        addSetting(x);
        addSetting(y);
        addSetting(clock24);
        addSetting(seconds);
        addSetting(date);
    }

    @Override
    public void onRender2D(DrawContext g, float tickDelta) {
        TextRenderer font = mc.textRenderer;
        if (font == null) return;
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter f = clock24.isEnabled() ? (seconds.isEnabled() ? T24S : T24) : (seconds.isEnabled() ? T12S : T12);
        String time = now.format(f);
        String day = date.isEnabled() ? now.format(DATE) : "";

        // the plate is sized from a string of 8s (the widest digit) so it does not jitter as the digits change
        int tw = Math.max(font.getWidth(time), font.getWidth(time.replaceAll("[0-9]", "8")));
        int w = Math.max(tw, font.getWidth(day)) + 18;
        int h = day.isEmpty() ? 15 : 26;
        lastW = w; lastH = h;
        int x1 = clampX(x.getValue().intValue(), w), y1 = clampY(y.getValue().intValue(), h);

        // kuro Look.panel: background + 1px border + left accent stripe
        g.fill(x1, y1, x1 + w, y1 + h, BG);
        g.fill(x1, y1, x1 + 3, y1 + h, ACCENT);
        g.fill(x1 + 3, y1, x1 + w, y1 + 1, BORDER);
        g.fill(x1 + 3, y1 + h - 1, x1 + w, y1 + h, BORDER);
        RenderUtils.drawText(g, font, time, x1 + 9, y1 + 4, TEXT, false);
        if (!day.isEmpty()) RenderUtils.drawText(g, font, day, x1 + 9, y1 + 15, MUTED, false);
    }

    private int clampX(int px, int w) { return Math.max(0, Math.min(px, mc.getWindow().getScaledWidth() - w)); }
    private int clampY(int py, int h) { return Math.max(0, Math.min(py, mc.getWindow().getScaledHeight() - h)); }
}
