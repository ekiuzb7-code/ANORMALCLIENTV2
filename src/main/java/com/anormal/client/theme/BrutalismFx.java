package com.anormal.client.theme;

import com.anormal.client.util.ColorUtils;
import com.anormal.client.util.RenderUtils;
import net.minecraft.client.gui.DrawContext;

/**
 * Brutalism's motion layer.
 *
 * <p>Neo-brutalism is not smooth: it is hard edges, hard offset shadows, hazard tape and things
 * that click into place. So this layer animates in steps instead of eases — scrolling hazard tape
 * under every header, corner blocks that switch on and off, an accent ticker running along the
 * bottom edge and a two-frame "stamp" when the GUI opens. Nothing is blended, nothing is soft.</p>
 */
public final class BrutalismFx {
    /** Height of the header band the hazard tape sits under (Vape frame default). */
    public static final int HEADER_H = 22;
    /** Duration of the opening stamp. */
    private static final long STAMP_MS = 260L;

    private static long openedAt = 0L;

    private BrutalismFx() {
    }

    public static boolean active() {
        return ThemeManager.getActiveTheme() == Theme.BRUTALISM;
    }

    /** @return true for a real open rather than a resize (screens re-init on resize). */
    public static boolean markOpened() {
        long now = System.currentTimeMillis();
        boolean fresh = now - openedAt > 500L;
        openedAt = now;
        return fresh;
    }

    /** Scrolling hazard tape, blinking corner blocks and the running bottom ticker. */
    public static void decoratePanel(DrawContext context, int x, int y, int w, int h, int headerH) {
        if (!active() || context == null || w < 24 || h < headerH + 12) return;
        long now = System.currentTimeMillis();
        int accent = ThemeManager.getAccentColor();
        // 2px white frame drawn last, over header and rows: the hard edge nothing softens.
        RenderUtils.drawBorder(context, x, y, x + w, y + h, 2, 0xFFFFFFFF);
        hazardTape(context, x, y, w, headerH, now, accent);
        cornerBlocks(context, x, y, w, h, now, accent);
        ticker(context, x, y, w, h, now, accent);
    }

    /** Two-frame stamp: accent frame, then white frame, then gone. No fading. */
    public static void openStamp(DrawContext context, int x, int y, int w, int h) {
        if (!active() || openedAt <= 0L || context == null) return;
        long dt = System.currentTimeMillis() - openedAt;
        if (dt > STAMP_MS) {
            openedAt = 0L;
            return;
        }
        int col = dt < 130L ? ThemeManager.getAccentColor() : 0xFFFFFFFF;
        RenderUtils.drawBorder(context, x - 2, y - 2, x + w + 2, y + h + 2, 3, col);
    }

    /** Hovered row: a hard un-blurred white frame plus a chunky accent spine. */
    public static void rowDecor(DrawContext context, int x, int y, int w, int h, boolean enabled) {
        if (!active() || context == null) return;
        int accent = ThemeManager.getAccentColor();
        RenderUtils.drawBorder(context, x, y, x + w, y + h, 1, 0xFFFFFFFF);
        RenderUtils.fill(context, x + 1, y + 1, x + 4, y + h - 1, enabled ? 0xFF000000 : accent);
    }

    // ------------------------------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------------------------------

    /** Diagonal accent tape sliding under the header, like construction tape. */
    private static void hazardTape(DrawContext context, int x, int y, int w, int headerH,
                                   long now, int accent) {
        int top = y + headerH;
        int bottom = top + 3;
        RenderUtils.fill(context, x + 2, top, x + w - 2, bottom, 0xFF0E0E10);
        int shift = (int) ((now / 45L) % 8L);
        int col = alpha(accent, 190);
        for (int base = x + 2 - 8; base < x + w - 2; base += 8) {
            int sx = base + shift;
            for (int row = 0; row < 3; row++) {
                int cx = sx + row; // 45 degrees
                if (cx < x + 2 || cx + 2 > x + w - 2) continue;
                RenderUtils.fill(context, cx, top + row, cx + 2, top + row + 1, col);
            }
        }
        RenderUtils.fill(context, x + 2, bottom, x + w - 2, bottom + 1, 0xFF000000);
    }

    /** Corner registration blocks that alternate between white and the accent colour. */
    private static void cornerBlocks(DrawContext context, int x, int y, int w, int h,
                                     long now, int accent) {
        boolean on = ((now / 600L) % 2L) == 0L;
        int a = on ? 0xFFFFFFFF : accent;
        int b = on ? accent : 0xFFFFFFFF;
        RenderUtils.fill(context, x + 3, y + 3, x + 7, y + 7, a);
        RenderUtils.fill(context, x + w - 7, y + 3, x + w - 3, y + 7, b);
        RenderUtils.fill(context, x + 3, y + h - 7, x + 7, y + h - 3, b);
        RenderUtils.fill(context, x + w - 7, y + h - 7, x + w - 3, y + h - 3, a);
    }

    /** Segmented accent bar running along the bottom edge. */
    private static void ticker(DrawContext context, int x, int y, int w, int h,
                               long now, int accent) {
        int by1 = y + h - 4;
        int by2 = y + h - 2;
        RenderUtils.fill(context, x + 2, by1, x + w - 2, by2, 0xFF000000);
        int shift = (int) ((now / 60L) % 10L);
        int col = alpha(accent, 210);
        for (int base = x + 2 - 10; base < x + w - 2; base += 10) {
            int sx = base + shift;
            if (sx < x + 2) continue;
            int end = Math.min(sx + 6, x + w - 2);
            if (end <= sx) continue;
            RenderUtils.fill(context, sx, by1, end, by2, col);
        }
    }

    private static int alpha(int color, int a) {
        return (color & 0x00FFFFFF) | (Math.max(0, Math.min(255, a)) << 24);
    }

    /** Exposed for callers that want the theme's own dark surface tone. */
    public static int surface() {
        return ColorUtils.rgba(20, 20, 24, 255);
    }
}
