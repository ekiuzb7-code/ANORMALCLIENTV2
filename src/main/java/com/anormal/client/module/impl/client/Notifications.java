package com.anormal.client.module.impl.client;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.ColorUtils;
import com.anormal.client.util.HudStyle;
import com.anormal.client.util.NotificationManager;
import com.anormal.client.util.RenderUtils;
import net.minecraft.client.gui.DrawContext;

/**
 * On-screen toast notifications for module toggles.
 *
 * <p>Each toast animates in with an ease-out slide, holds, then slides away.
 * The animation is time-based (see {@link NotificationManager}), so it stays
 * smooth no matter the frame rate.</p>
 */
public class Notifications extends Module {

    public final ModeSetting position = new ModeSetting("Position", "Where notifications appear", "Top Right",
            "Top Right", "Top Left", "Bottom Right", "Bottom Left");
    public final NumberSetting duration = new NumberSetting("Duration", "How long each notification stays (ms)", 1800.0, 500.0, 10000.0, 100.0);
    public final NumberSetting maxVisible = new NumberSetting("Max Visible", "Maximum notifications on screen", 5.0, 1.0, 15.0, 1.0);
    public final NumberSetting width = new NumberSetting("Width", "Panel width", 150.0, 80.0, 320.0, 5.0);
    public final NumberSetting scale = new NumberSetting("Scale", "Overall size multiplier", 1.0, 0.5, 2.0, 0.05);
    public final NumberSetting spacing = new NumberSetting("Spacing", "Gap between notifications", 4.0, 0.0, 20.0, 1.0);
    public final ModeSetting animation = new ModeSetting("Animation", "Slide direction", "Side",
            "Side", "Vertical", "Fade");

    public final ColorSetting enabledColor = new ColorSetting("Enabled Color", "Accent for enabling a module", ColorUtils.rgba(85, 255, 85, 255));
    public final ColorSetting disabledColor = new ColorSetting("Disabled Color", "Accent for disabling a module", ColorUtils.rgba(255, 85, 85, 255));
    public final ColorSetting background = new ColorSetting("Background", "Panel background", ColorUtils.rgba(14, 16, 22, 225));
    public final ColorSetting textColor = new ColorSetting("Text Color", "Title text colour", ColorUtils.rgba(255, 255, 255, 255));
    public final ColorSetting subTextColor = new ColorSetting("Sub Text Color", "Subtitle text colour", ColorUtils.rgba(170, 170, 175, 255));

    public final BooleanSetting accentBar = new BooleanSetting("Accent Bar", "Coloured bar on the left edge", true);
    public final BooleanSetting progressBar = new BooleanSetting("Progress Bar", "Shrinking bar showing remaining time", true);
    public final BooleanSetting shadow = new BooleanSetting("Shadow", "Drop shadow behind the panel", true);
    public final BooleanSetting rounded = new BooleanSetting("Rounded Corners", "Clip the panel corners", true);
    public final BooleanSetting showDisable = new BooleanSetting("Show Disable", "Also notify when a module is switched off", true);
    public final BooleanSetting sound = new BooleanSetting("Sound", "Play a click when a notification appears", false);

    public Notifications() {
        super("Notifications", "Smooth animated toasts for module toggles", Category.LEGIT);
        addSetting(position);
        addSetting(duration);
        addSetting(maxVisible);
        addSetting(width);
        addSetting(scale);
        addSetting(spacing);
        addSetting(animation);
        addSetting(enabledColor);
        addSetting(disabledColor);
        addSetting(background);
        addSetting(textColor);
        addSetting(subTextColor);
        addSetting(accentBar);
        addSetting(progressBar);
        addSetting(shadow);
        addSetting(rounded);
        addSetting(showDisable);
        addSetting(sound);
    }

    @Override
    public void onDisable() {
        NotificationManager.clear();
    }

    @Override
    public void onTick() {
        NotificationManager.tick();
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (!isEnabled() || mc.textRenderer == null) return;

        var list = NotificationManager.entries();
        if (list.isEmpty()) return;

        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();

        double sc = scale.getValue();
        int w = (int) (width.getValue() * sc);
        int h = (int) (26 * sc);
        int gap = (int) (spacing.getValue() * sc);
        int margin = 6;

        boolean right = position.is("Top Right") || position.is("Bottom Right");
        boolean bottom = position.is("Bottom Left") || position.is("Bottom Right");

        int count = Math.min(list.size(), maxVisible.getValue().intValue());

        for (int i = 0; i < count; i++) {
            NotificationManager.Entry e = list.get(i);

            // Ease-out cubic on the linear progress -> smooth slide + fade.
            float t = Math.max(0f, Math.min(1f, e.progress()));
            float eased = 1f - (float) Math.pow(1f - t, 3);

            int baseY = bottom
                    ? sh - margin - (i + 1) * (h + gap)
                    : margin + i * (h + gap);
            int baseX = right ? sw - margin - w : margin;

            int slide = (int) ((1f - eased) * (w + 12));
            int x = baseX;
            int y = baseY;
            if (animation.is("Side")) {
                x += right ? slide : -slide;
            } else if (animation.is("Vertical")) {
                y += bottom ? slide : -slide;
            }

            int alpha = (int) (eased * 255);
            if (alpha <= 2) continue;

            drawToast(context, e, x, y, w, h, alpha, sc);
        }
    }

    private void drawToast(DrawContext context, NotificationManager.Entry e,
                           int x, int y, int w, int h, int alpha, double sc) {
        int bg = withAlpha(background.getValue(), alpha);
        int accent = withAlpha(e.accent, alpha);
        int titleCol = withAlpha(textColor.getValue(), alpha);
        int subCol = withAlpha(subTextColor.getValue(), alpha);
        int radius = rounded.isEnabled() ? 4 : 0;

        // Soft shadow sits one pixel lower so the toast lifts off the world.
        if (shadow.isEnabled()) {
            RenderUtils.fillRoundedRaw(context, x - 1, y + 1, w + 2, h + 2, radius + 1,
                    withAlpha(0xFF000000, (int) (alpha * 0.30)));
        }

        RenderUtils.fillRoundedRaw(context, x, y, w, h, radius, bg);
        RenderUtils.fillRoundedRaw(context, x, y + 1, w, 1, 1, withAlpha(0xFFFFFFFF, alpha / 12));
        RenderUtils.drawBorder(context, x, y, x + w, y + h, 1,
                withAlpha(0xFF313644, (int) (alpha * 0.9)));

        // Accent spine + status dot, both in the toast's own colour.
        int textX = x + 9;
        if (accentBar.isEnabled()) {
            RenderUtils.fillRoundedRaw(context, x + 1, y + 4, 3, h - 8, 1, accent);
            textX = x + 10;
        }
        RenderUtils.fillRoundedRaw(context, x + w - 13, y + 7, 5, 5, 2, accent);

        RenderUtils.drawText(context, mc.textRenderer, HudStyle.trim(mc.textRenderer, e.title, w - 32),
                textX, y + 5, titleCol, true);
        if (e.subtitle != null && !e.subtitle.isEmpty()) {
            RenderUtils.drawText(context, mc.textRenderer,
                    HudStyle.trim(mc.textRenderer, e.subtitle, w - 26),
                    textX, y + 15, subCol, false);
        }

        if (progressBar.isEnabled()) {
            long total = NotificationManager.IN_MS + e.hold + NotificationManager.OUT_MS;
            float remain = 1f - Math.min(1f, e.elapsed() / (float) total);
            int pw = (int) ((w - 4) * remain);
            if (pw > 0) {
                RenderUtils.fillRoundedRaw(context, x + 2, y + h - 3, pw, 2, 1, accent);
            }
        }
    }

    private static int withAlpha(int color, int alpha) {
        int a = (int) ((color >>> 24) * (alpha / 255f));
        return (color & 0x00FFFFFF) | ((a & 0xFF) << 24);
    }
}
