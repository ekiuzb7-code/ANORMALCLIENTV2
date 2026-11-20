package com.anormal.client.theme;

import com.anormal.client.util.ColorUtils;
import com.anormal.client.util.RenderUtils;
import net.minecraft.client.gui.DrawContext;

/**
 * The Frozen theme's living layer.
 *
 * <p>The glass panels alone were static: a blue recolour of the Glassmorphism chrome. This class
 * adds the motion that makes Frozen feel like ice — a breathing rim that shifts between pale blue
 * and white, crystalline facets in the corners, a slow sheen travelling across the glass, a few
 * snow crystals drifting on the surface, ambient snowfall over the whole screen and a short
 * shockwave when the GUI opens.</p>
 *
 * <p>Everything is procedural and integer cheap: fixed particle counts, no per-frame allocations
 * and no textures, so an animated theme never costs frames.</p>
 */
public final class FrozenFx {
    /** Snowflakes in the ambient screen-space snowfall. */
    private static final int SNOWFLAKES = 44;
    /** Crystals resting on each glass panel. */
    private static final int SURFACE_CRYSTALS = 6;
    /** Duration of the opening shockwave. */
    private static final long OPEN_BURST_MS = 520L;

    /** When the GUI was opened, 0 when no shockwave is running. */
    private static long openedAt = 0L;

    private FrozenFx() {
    }

    public static boolean active() {
        return ThemeManager.getActiveTheme() == Theme.FROZEN;
    }

    /**
     * Marks the GUI as freshly opened.
     *
     * @return true when this is a real open rather than a resize (screens are re-initialised on
     *         every window resize, and the entrance sound must not replay for those).
     */
    public static boolean markOpened() {
        long now = System.currentTimeMillis();
        boolean fresh = now - openedAt > 500L;
        openedAt = now;
        return fresh;
    }

    /** Draws the icy rim, corner facets, sheen and surface crystals of one glass panel. */
    public static void decoratePanel(DrawContext context, int x, int y, int w, int h) {
        if (!active() || context == null || w < 16 || h < 16) return;
        long now = System.currentTimeMillis();
        float pulse = pulse(now, 3400L, 0f);

        int rimBase = ColorUtils.interpolateColor(ColorUtils.rgba(140, 214, 255, 255), 0xFFFFFFFF, pulse);
        int rimTop = alpha(rimBase, 54 + Math.round(56f * pulse));
        int rimSide = alpha(rimBase, 40 + Math.round(44f * pulse));
        int rimBottom = alpha(rimBase, 30 + Math.round(34f * pulse));
        int radius = 6;
        // The rim is inset one pixel and shortened at the corners so it follows the rounded body
        // instead of poking out of it.
        RenderUtils.fill(context, x + radius, y + 1, x + w - radius, y + 2, rimTop);
        RenderUtils.fill(context, x + radius, y + h - 2, x + w - radius, y + h - 1, rimBottom);
        RenderUtils.fill(context, x + 1, y + radius, x + 2, y + h - radius, rimSide);
        RenderUtils.fill(context, x + w - 2, y + radius, x + w - 1, y + h - radius, rimSide);

        cornerFacets(context, x, y, w, h, alpha(0xFFFFFF, 42 + Math.round(46f * pulse)));

        sheen(context, x, y, w, h, now);
        surfaceCrystals(context, x, y, w, h, now);
    }

    /** Ambient snowfall across the whole screen, drawn over the GUI as a winter overlay. */
    public static void snowfield(DrawContext context, int screenW, int screenH) {
        if (!active() || context == null || screenW <= 0 || screenH <= 0) return;
        long now = System.currentTimeMillis();
        for (int i = 0; i < SNOWFLAKES; i++) {
            float fall = 0.020f + 0.045f * rnd(i * 7 + 3);
            float driftAmp = 6f + 14f * rnd(i * 11 + 5);
            double drift = (now / 1000.0) * (0.35 + 0.40 * rnd(i * 13 + 9)) + i;
            int px = Math.round(rnd(i * 17 + 1) * screenW + (float) Math.sin(drift) * driftAmp);
            float fallT = (rnd(i * 23 + 7) + now * fall / 1000f) % 1f;
            int py = Math.round(fallT * screenH);
            px = Math.floorMod(px, screenW);
            if (py < 0 || py >= screenH) continue;

            int a = 26 + Math.round(48f * rnd(i * 29 + 11));
            boolean big = rnd(i * 37 + 13) > 0.82f;
            int size = big ? 2 : 1;
            RenderUtils.fill(context, px, py, px + size, py + size, alpha(0xFFFFFF, a));
            if (big) {
                int arm = alpha(0xCBE8FF, Math.max(14, a / 2));
                RenderUtils.fill(context, px - 1, py, px + 3, py + 2, arm);
                RenderUtils.fill(context, px, py - 1, px + 2, py + 3, arm);
            }
        }
    }

    /** Expanding frost ring around the module panel right after the GUI opens. */
    public static void openBurst(DrawContext context, int x, int y, int w, int h) {
        if (!active() || openedAt <= 0L || context == null) return;
        float t = (System.currentTimeMillis() - openedAt) / (float) OPEN_BURST_MS;
        if (t >= 1f) {
            openedAt = 0L;
            return;
        }
        float eased = 1f - (1f - t) * (1f - t);
        int grow = Math.round(26f * eased);
        int a = Math.round(95f * (1f - t));
        int col = ColorUtils.rgba(196, 238, 255, a);
        int ox = x - grow;
        int oy = y - grow;
        int ow = w + grow * 2;
        int oh = h + grow * 2;
        RenderUtils.fill(context, ox + 8, oy, ox + ow - 8, oy + 1, col);
        RenderUtils.fill(context, ox + 8, oy + oh - 1, ox + ow - 8, oy + oh, col);
        RenderUtils.fill(context, ox, oy + 8, ox + 1, oy + oh - 8, col);
        RenderUtils.fill(context, ox + ow - 1, oy + 8, ox + ow, oy + oh - 8, col);
    }

    /** Soft cyan bloom drawn behind a hovered row. */
    public static void rowBloom(DrawContext context, int x, int y, int w, int h) {
        if (!active() || context == null) return;
        float pulse = pulse(System.currentTimeMillis(), 1800L, 0f);
        RenderUtils.fillRounded(context, x - 1, y - 1, w + 2, h + 2, 4,
                ColorUtils.rgba(150, 215, 255, 22 + Math.round(18f * pulse)));
    }

    // ------------------------------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------------------------------

    /** 0..1 sine pulse with a configurable period and phase. */
    private static float pulse(long now, long period, float phase) {
        double t = ((now % period) / (double) period) + phase;
        return (float) ((Math.sin(t * Math.PI * 2.0) + 1.0) * 0.5);
    }

    private static int alpha(int color, int a) {
        return (color & 0x00FFFFFF) | (Math.max(0, Math.min(255, a)) << 24);
    }

    /** Deterministic 0..1 noise, so every flake keeps its own spot and speed. */
    private static float rnd(int seed) {
        int h = seed * 374761393 + 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        h ^= h >>> 16;
        return (h & 0xFFFF) / 65535f;
    }

    /** Two short diagonals per corner: crystalline facets just inside the rounded body. */
    private static void cornerFacets(DrawContext context, int x, int y, int w, int h, int color) {
        int tick = alpha(0xFFFFFF, (color >>> 24) / 2);
        // top-left
        RenderUtils.drawLine(context, x + 5, y + 5, x + 9, y + 9, color, 1f);
        RenderUtils.drawLine(context, x + 7, y + 4, x + 4, y + 7, tick, 1f);
        // top-right
        RenderUtils.drawLine(context, x + w - 6, y + 5, x + w - 10, y + 9, color, 1f);
        RenderUtils.drawLine(context, x + w - 8, y + 4, x + w - 5, y + 7, tick, 1f);
        // bottom-left
        RenderUtils.drawLine(context, x + 5, y + h - 6, x + 9, y + h - 10, color, 1f);
        RenderUtils.drawLine(context, x + 7, y + h - 5, x + 4, y + h - 8, tick, 1f);
        // bottom-right
        RenderUtils.drawLine(context, x + w - 6, y + h - 6, x + w - 10, y + h - 10, color, 1f);
        RenderUtils.drawLine(context, x + w - 8, y + h - 5, x + w - 5, y + h - 8, tick, 1f);
    }

    /** A diagonal light band that slides across the panel every few seconds. */
    private static void sheen(DrawContext context, int x, int y, int w, int h, long now) {
        int innerX = x + 7;
        int innerW = w - 14;
        int y1 = y + 4;
        int y2 = y + h - 4;
        if (innerW < 8 || y2 - y1 < 4) return;

        long period = 5200L;
        float t = ((now + (x & 0xFF) * 13L) % period) / (float) period;
        int bandW = Math.max(16, innerW / 3);
        int bandX = innerX - bandW + Math.round((innerW + bandW) * t);
        int half = Math.max(1, bandW / 2);
        for (int i = 0; i <= bandW; i += 2) {
            int px = bandX + i;
            if (px < innerX || px + 2 > innerX + innerW) continue;
            float f = 1f - Math.abs(i - half) / (float) half;
            if (f <= 0f) continue;
            int a = Math.round(22f * f * f);
            if (a <= 0) continue;
            RenderUtils.fill(context, px, y1, px + 2, y2, alpha(0xE6F6FF, a));
        }
    }

    /** A handful of snow crystals resting on the glass, bobbing gently. */
    private static void surfaceCrystals(DrawContext context, int x, int y, int w, int h, long now) {
        int areaW = w - 16;
        int areaH = h - 14;
        if (areaW < 4 || areaH < 4) return;
        double phase = (now % 7000L) / 7000.0 * Math.PI * 2.0;
        for (int i = 0; i < SURFACE_CRYSTALS; i++) {
            float fx = rnd(x * 31 + y * 17 + i * 53);
            float fy = rnd(x * 71 + y * 29 + i * 97);
            float bob = (float) Math.sin(phase + i * 1.7);
            int px = x + 8 + Math.round(fx * areaW);
            int py = y + 7 + Math.round(fy * areaH + bob * 1.5f);
            if (py < y + 5 || py > y + h - 6) continue;
            int a = 34 + Math.round(26f * (0.5f + 0.5f * bob));
            RenderUtils.fill(context, px, py, px + 1, py + 1, alpha(0xFFFFFF, a));
            if (i % 3 == 1) {
                int arm = alpha(0xCFEBFF, Math.max(18, a / 2));
                RenderUtils.fill(context, px - 1, py, px + 2, py + 1, arm);
                RenderUtils.fill(context, px, py - 1, px + 1, py + 2, arm);
            }
        }
    }
}
