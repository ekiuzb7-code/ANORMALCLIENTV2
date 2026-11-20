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
 * Distance walked this session and in total.
 *
 * <p>Modernised into the shared panel: the session distance is the big value, the total sits next to
 * it and the live speed is shown underneath with a subtle gauge.</p>
 */
public class TravelDistance extends Module {
    public final NumberSetting posX = new NumberSetting("Pos X", "X Position on screen", 10.0, 0.0, 1920.0, 1.0);
    public final NumberSetting posY = new NumberSetting("Pos Y", "Y Position on screen", 330.0, 0.0, 1080.0, 1.0);
    public final NumberSetting scale = new NumberSetting("Scale", "HUD scale (0.05 = tiny, 2.0 = huge)", 1.0, 0.05, 2.0, 0.01);
    public final BooleanSetting reset = new BooleanSetting("Reset", "Reset session distance", false);
    public final ColorSetting textColor = new ColorSetting("Text Color", "Distance text color", ColorUtils.rgba(255, 255, 255, 255));

    private double total = 0.0;
    private double session = 0.0;
    private double speed = 0.0;
    private double lastX = 0.0;
    private double lastY = 0.0;
    private double lastZ = 0.0;
    private boolean init = false;

    public TravelDistance() {
        super("TravelDistance", "Tracks distance walked total and session", Category.LEGIT);
        addSetting(posX);
        addSetting(posY);
        addSetting(scale);
        addSetting(reset);
        addSetting(textColor);
    }

    @Override
    public void onEnable() {
        init = false;
        speed = 0.0;
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;
        try {
            if (reset.isEnabled()) {
                reset.setValue(false);
                session = 0.0;
            }
            double x = mc.player.getX();
            double y = mc.player.getY();
            double z = mc.player.getZ();
            if (!init) {
                lastX = x;
                lastY = y;
                lastZ = z;
                init = true;
                return;
            }
            double dx = x - lastX;
            double dy = y - lastY;
            double dz = z - lastZ;
            double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
            // Ignore teleports / dimension jumps so the counter does not explode.
            if (d < 50.0) {
                total += d;
                session += d;
                speed = speed * 0.8 + d * 20.0 * 0.2;
            } else {
                speed = 0.0;
            }
            lastX = x;
            lastY = y;
            lastZ = z;
        } catch (Throwable ignored) {}
    }

    private String metres(double value) {
        if (value >= 1000.0) return String.format(java.util.Locale.ROOT, "%.1fk", value / 1000.0);
        return String.valueOf((int) value);
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.textRenderer == null) return;
        try {
            int accent = ThemeManager.getAccentColor();
            int w = 184, h = 46;
            int[] at = HudStyle.place(posX.getValue().intValue(), posY.getValue().intValue(), w, h);
            int x = at[0], y = at[1];

            HudStyle.panel(context, x, y, w, h, accent);
            HudStyle.title(context, mc.textRenderer, "Distance", x + 10, y + 7);

            String sess = metres(session) + " m";
            int sw = mc.textRenderer.getWidth(sess);
            RenderUtils.drawText(context, mc.textRenderer, sess, x + w - 10 - sw, y + 5,
                    textColor.getValue(), true);

            RenderUtils.drawText(context, mc.textRenderer, "total " + metres(total) + " m", x + 10,
                    y + 19, 0xFF868A99, false);

            String spd = String.format(java.util.Locale.ROOT, "%.1f m/s", speed);
            int pw = mc.textRenderer.getWidth(spd);
            RenderUtils.drawText(context, mc.textRenderer, spd, x + w - 10 - pw, y + 19,
                    0xFFC7CBD6, false);

            float frac = (float) Math.min(1.0, speed / 10.0);
            HudStyle.bar(context, x + 10, y + 32, w - 20, 4, frac, accent);
            RenderUtils.drawText(context, mc.textRenderer, speed < 0.1 ? "standing" : "moving",
                    x + 10, y + 38, 0xFF6E7482, false);
        } catch (Throwable ignored) {}
    }
}
