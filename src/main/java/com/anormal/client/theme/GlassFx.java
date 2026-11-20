package com.anormal.client.theme;

import com.anormal.client.util.ColorUtils;
import com.anormal.client.util.RenderUtils;
import net.minecraft.client.gui.DrawContext;

/**
 * Glassmorphism's living layer.
 *
 * <p>Glassmorphism was a static frosted panel with one shimmer on the sidebar pill. This class
 * turns it into actual light through liquid: the rim slides between an ice blue and a violet and
 * a light pool follows the cursor across the glass. Everything is procedural, fixed-count and
 * allocation free so the theme animates without costing frames.</p>
 */
public final class GlassFx {
    /** Duration of the opening bloom. */
    private static final long OPEN_BURST_MS = 600L;

    private static long openedAt = 0L;

    private GlassFx() {
    }

    public static boolean active() {
        return ThemeManager.getActiveTheme() == Theme.GLASSMORPHISM;
    }

    /** @return true for a real open rather than a resize (screens re-init on resize). */
    public static boolean markOpened() {
        long now = System.currentTimeMillis();
        boolean fresh = now - openedAt > 500L;
        openedAt = now;
        return fresh;
    }

    /** Rim colour: a slow slide between ice blue and violet, so the edge always catches light. */
    public static int rimColor() {
        float p = pulse(System.currentTimeMillis(), 4200L, 0f);
        int c = ColorUtils.interpolateColor(ColorUtils.rgba(120, 190, 255, 255),
                ColorUtils.rgba(172, 142, 255, 255), p);
        return (c & 0x00FFFFFF) | (86 << 24);
    }

    /** Liquid blobs, cursor light pool and a bright top rim for one glass panel. */
    public static void decoratePanel(DrawContext context, int x, int y, int w, int h,
                                     int mouseX, int mouseY) {
        if (!active() || context == null || w < 16 || h < 16) return;
        long now = System.currentTimeMillis();
        float p = pulse(now, 4200L, 0f);

        // Top rim brightens and cools with the rim colour, like light at the lip of the glass.
        int rim = ColorUtils.interpolateColor(ColorUtils.rgba(190, 225, 255, 255),
                ColorUtils.rgba(214, 190, 255, 255), p);
        RenderUtils.fill(context, x + 6, y + 1, x + w - 6, y + 2, alpha(rim, 58 + Math.round(40f * p)));
        RenderUtils.fill(context, x + 6, y + h - 2, x + w - 6, y + h - 1, alpha(rim, 34));

        spotlight(context, x, y, w, h, mouseX, mouseY);
    }

    /** Short bloom ring around the module panel when the GUI opens. */
    public static void openBurst(DrawContext context, int x, int y, int w, int h) {
        if (!active() || openedAt <= 0L || context == null) return;
        long elapsed = System.currentTimeMillis() - openedAt;
        float t = elapsed / (float) OPEN_BURST_MS;
        if (t >= 1f) {
            openedAt = 0L;
            return;
        }
        float eased = 1f - (1f - t) * (1f - t);
        int grow = Math.round(22f * eased);
        int a = Math.round(80f * (1f - t));
        int col = (rimColor() & 0x00FFFFFF) | (a << 24);
        int ox = x - grow;
        int oy = y - grow;
        int ow = w + grow * 2;
        int oh = h + grow * 2;
        RenderUtils.fillRounded(context, ox + 10, oy, ow - 20, 1, 1, col);
        RenderUtils.fillRounded(context, ox + 10, oy + oh - 1, ow - 20, 1, 1, col);
        RenderUtils.fillRounded(context, ox, oy + 10, 1, oh - 20, 1, col);
        RenderUtils.fillRounded(context, ox + ow - 1, oy + 10, 1, oh - 20, 1, col);
    }

    /** Violet bloom behind a hovered row. */
    public static void rowGlow(DrawContext context, int x, int y, int w, int h) {
        if (!active() || context == null) return;
        float p = pulse(System.currentTimeMillis(), 2200L, 0f);
        RenderUtils.fillRounded(context, x - 1, y - 1, w + 2, h + 2, 4,
                ColorUtils.rgba(166, 154, 255, 16 + Math.round(16f * p)));
    }

    // ------------------------------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------------------------------

    private static float pulse(long now, long period, float phase) {
        double t = ((now % period) / (double) period) + phase;
        return (float) ((Math.sin(t * Math.PI * 2.0) + 1.0) * 0.5);
    }

    private static int alpha(int color, int a) {
        return (color & 0x00FFFFFF) | (Math.max(0, Math.min(255, a)) << 24);
    }

    /** A pool of light that trails the cursor across the glass (fake radial gradient). */
    private static void spotlight(DrawContext context, int x, int y, int w, int h,
                                  int mouseX, int mouseY) {
        if (mouseX < x + 6 || mouseX > x + w - 6 || mouseY < y + 6 || mouseY > y + h - 6) return;
        int accent = ThemeManager.getAccentColor();
        for (int layer = 3; layer >= 1; layer--) {
            int rad = 15 * layer;
            int a = 5 + (3 - layer) * 3;
            RenderUtils.fillRounded(context, mouseX - rad, mouseY - rad, rad * 2, rad * 2, rad,
                    alpha(accent, a));
        }
    }
}
