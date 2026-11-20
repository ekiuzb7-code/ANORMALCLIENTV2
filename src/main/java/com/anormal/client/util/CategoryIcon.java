package com.anormal.client.util;

import com.anormal.client.module.Category;
import net.minecraft.client.gui.DrawContext;

/**
 * Vector icon set used by the ClickGUI.
 *
 * <p>Section icons used to be Unicode / emoji glyphs ({@code "⚔"}, {@code "👁"}, {@code "🎒"}).
 * Those glyphs live in whichever font is loaded, so a server that sends its own resource pack
 * (or any pack that overrides {@code minecraft:font/default.json}) replaced them with
 * missing-glyph boxes as soon as you joined. Everything below is drawn from rectangles, lines and
 * filled polygons, so the icons look identical on every server and in every resource pack.</p>
 *
 * <p>All shapes live on a 12x12 unit grid that is scaled to the requested pixel size. The sidebar
 * draws them at 7-10 px, where a 1 px outline disappears, so the silhouettes are built from solid
 * fills and strokes of at least two pixels.</p>
 */
public final class CategoryIcon {

    private CategoryIcon() {
    }

    /** Draws the icon for a category inside a {@code size x size} box at {@code (x, y)}. */
    public static void draw(DrawContext context, Category category, int x, int y, int size, int color) {
        if (context == null || category == null || size <= 0) return;
        switch (category) {
            case COMBAT -> combat(context, x, y, size, color);
            case MOVEMENT -> bolt(context, x, y, size, color);
            case RENDER -> eye(context, x, y, size, color);
            case PLAYER -> player(context, x, y, size, color);
            case WORLD -> globe(context, x, y, size, color);
            case INVENTORY -> bag(context, x, y, size, color);
            case OPTIMIZATION -> gauge(context, x, y, size, color);
            case LEGIT -> shield(context, x, y, size, color);
            case CLIENT -> gearShape(context, x, y, size, color, 8);
            case SPECTRE -> ghost(context, x, y, size, color);
            case KURO -> planet(context, x, y, size, color);
            case KEYBINDS -> keyboard(context, x, y, size, color);
            case DEVELOPER -> code(context, x, y, size, color);
            default -> sparkle(context, x, y, size, color); // MISC
        }
    }

    /** Instagram-style camera badge (developer credits). */
    public static void camera(DrawContext context, int x, int y, int size, int color) {
        if (context == null || size <= 0) return;
        int w = stroke(size);
        RenderUtils.drawBorder(context, p(x, size, 0.9f), p(y, size, 1.8f), p(x, size, 11.1f), p(y, size, 11.1f), w, color);
        disc(context, x, y, size, 6f, 6.4f, 2.5f, color);
        box(context, x, y, size, 8.2f, 3.2f, 9.8f, 4.6f, color);
    }

    /** Telegram-style paper plane (developer credits). */
    public static void plane(DrawContext context, int x, int y, int size, int color) {
        if (context == null || size <= 0) return;
        poly(context, x, y, size, color,
                0.8f, 5.4f, 11.2f, 1.2f, 6.6f, 11.0f, 5.2f, 6.6f);
    }

    /** Rounded gear used by the "Edit HUD" button. */
    public static void gear(DrawContext context, int x, int y, int size, int color) {
        if (context == null || size <= 0) return;
        gearShape(context, x, y, size, color, 4);
    }

    /** Map pin used by the "Waypoints" button. */
    public static void waypoint(DrawContext context, int x, int y, int size, int color) {
        if (context == null || size <= 0) return;
        poly(context, x, y, size, color,
                6f, 0.8f, 9.4f, 4.6f, 9.4f, 7.0f, 6f, 11.2f, 2.6f, 7.0f, 2.6f, 4.6f);
        disc(context, x, y, size, 6f, 5.2f, 1.4f, 0xFF000000);
    }

    /** Right-pointing chevron (replaces the "›" glyph). */
    public static void chevronRight(DrawContext context, int x, int y, int size, int color) {
        if (context == null || size <= 0) return;
        int w = Math.max(1, Math.round(size / 3.5f));
        int mid = Math.max(1, size / 2);
        RenderUtils.drawLine(context, x, y, x + mid, y + mid, color, w);
        RenderUtils.drawLine(context, x + mid, y + mid, x, y + size, color, w);
    }

    /** Up-pointing filled triangle (replaces the "▲" glyph). */
    public static void triangleUp(DrawContext context, int x, int y, int w, int h, int color) {
        if (context == null || w <= 0 || h <= 0) return;
        for (int i = 0; i < h; i++) {
            int inset = Math.round((h - 1 - i) * (w / 2f) / Math.max(1, h - 1));
            context.fill(x + inset, y + i, x + w - inset, y + i + 1, color);
        }
    }

    /** Down-pointing filled triangle (replaces the "▼" glyph). */
    public static void triangleDown(DrawContext context, int x, int y, int w, int h, int color) {
        if (context == null || w <= 0 || h <= 0) return;
        for (int i = 0; i < h; i++) {
            int inset = Math.round(i * (w / 2f) / Math.max(1, h - 1));
            context.fill(x + inset, y + i, x + w - inset, y + i + 1, color);
        }
    }

    /** Radio marker for mode rows (replaces the "●" / "○" glyphs). */
    public static void radio(DrawContext context, int x, int y, int size, boolean selected, int color) {
        if (context == null || size <= 0) return;
        if (selected) {
            disc(context, x, y, size, 6f, 6f, 4.6f, color);
        } else {
            ring(context, x, y, size, 6f, 6f, 4.4f, color);
        }
    }

    /** Checkbox marker for multi-select rows (replaces the "☑" / "☐" glyphs). */
    public static void checkbox(DrawContext context, int x, int y, int size, boolean checked, int color) {
        if (context == null || size <= 0) return;
        int w = Math.max(1, Math.round(size / 9f));
        RenderUtils.drawBorder(context, p(x, size, 1f), p(y, size, 1f), p(x, size, 11f), p(y, size, 11f), w, color);
        if (checked) {
            RenderUtils.drawLine(context, p(x, size, 2.6f), p(y, size, 6f), p(x, size, 5f), p(y, size, 9f), color, Math.max(1, w + 1));
            RenderUtils.drawLine(context, p(x, size, 5f), p(y, size, 9f), p(x, size, 9.6f), p(y, size, 3f), color, Math.max(1, w + 1));
        }
    }

    /** Magnifier used by the "Select Blocks..." button. */
    public static void search(DrawContext context, int x, int y, int size, int color) {
        if (context == null || size <= 0) return;
        ring(context, x, y, size, 5f, 5f, 3.6f, color);
        RenderUtils.drawLine(context, p(x, size, 8.2f), p(y, size, 8.4f), p(x, size, 11.4f), p(y, size, 11.4f),
                color, Math.max(2, stroke(size)));
    }

    // ---------------------------------------------------------------- shapes

    /** Single sword with a cross guard and pommel - distinct from a plain X at 9 px. */
    private static void combat(DrawContext context, int x, int y, int size, int color) {
        int blade = Math.max(3, Math.round(size / 3.2f));
        RenderUtils.drawLine(context, p(x, size, 3.0f), p(y, size, 9.0f), p(x, size, 9.8f), p(y, size, 2.2f), color, blade);
        RenderUtils.drawLine(context, p(x, size, 5.6f), p(y, size, 3.4f), p(x, size, 8.6f), p(y, size, 6.4f), color, blade);
        RenderUtils.drawLine(context, p(x, size, 3.0f), p(y, size, 9.0f), p(x, size, 1.7f), p(y, size, 10.3f), color, Math.max(2, blade - 1));
        disc(context, x, y, size, 1.4f, 10.6f, 1.2f, color);
    }

    /** Solid lightning bolt. */
    private static void bolt(DrawContext context, int x, int y, int size, int color) {
        poly(context, x, y, size, color,
                7.9f, 0.4f, 2.2f, 6.7f, 5.5f, 6.7f, 4.1f, 11.6f, 9.8f, 4.9f, 6.5f, 4.9f);
    }

    /** Eye with a filled pupil. */
    private static void eye(DrawContext context, int x, int y, int size, int color) {
        int w = stroke(size);
        int[][] lens = {
                {1, 6}, {3, 3}, {6, 2}, {9, 3}, {11, 6}, {9, 9}, {6, 10}, {3, 9}
        };
        for (int i = 0; i < lens.length; i++) {
            int[] a = lens[i];
            int[] b = lens[(i + 1) % lens.length];
            RenderUtils.drawLine(context, p(x, size, a[0]), p(y, size, a[1]),
                    p(x, size, b[0]), p(y, size, b[1]), color, w);
        }
        disc(context, x, y, size, 6f, 6f, 2.2f, color);
    }

    /** Head and shoulders silhouette. */
    private static void player(DrawContext context, int x, int y, int size, int color) {
        disc(context, x, y, size, 6f, 3.4f, 2.7f, color);
        rounded(context, x, y, size, 1.6f, 7.4f, 10.4f, 11.6f, 3, color);
    }

    /** Globe: ring with an equator and one meridian line. */
    private static void globe(DrawContext context, int x, int y, int size, int color) {
        int w = stroke(size);
        ring(context, x, y, size, 6f, 6f, 4.6f, color);
        RenderUtils.drawLine(context, p(x, size, 1.4f), p(y, size, 6f), p(x, size, 10.6f), p(y, size, 6f), color, w);
        RenderUtils.drawLine(context, p(x, size, 6f), p(y, size, 1.4f), p(x, size, 6f), p(y, size, 10.6f), color, Math.max(2, w - 1));
    }

    /** Chest with a lid seam and a latch - a container, not a padlock. */
    private static void bag(DrawContext context, int x, int y, int size, int color) {
        int w = stroke(size);
        RenderUtils.drawBorder(context, p(x, size, 1.8f), p(y, size, 4.4f),
                p(x, size, 10.2f), p(y, size, 10.8f), w, color);
        RenderUtils.drawLine(context, p(x, size, 1.8f), p(y, size, 7.0f), p(x, size, 10.2f), p(y, size, 7.0f), color, w);
        box(context, x, y, size, 5.3f, 7.0f, 6.7f, 8.6f, color);
        box(context, x, y, size, 2.4f, 10.8f, 3.6f, 11.8f, color);
        box(context, x, y, size, 8.4f, 10.8f, 9.6f, 11.8f, color);
    }

    /** Speedometer with a bold needle for the optimization tab. */
    private static void gauge(DrawContext context, int x, int y, int size, int color) {
        int w = stroke(size);
        arc(context, x, y, size, 6f, 8.2f, 4.2f, 4.2f, (float) Math.PI, (float) (Math.PI * 2), w, color);
        RenderUtils.drawLine(context, p(x, size, 6f), p(y, size, 8.2f), p(x, size, 7.9f), p(y, size, 3.8f),
                color, Math.max(3, w + 1));
        disc(context, x, y, size, 6f, 8.2f, 1.4f, color);
    }

    /** Shield with a check mark. */
    private static void shield(DrawContext context, int x, int y, int size, int color) {
        int w = stroke(size);
        RenderUtils.drawLine(context, p(x, size, 2.2f), p(y, size, 1.6f), p(x, size, 9.8f), p(y, size, 1.6f), color, w);
        RenderUtils.drawLine(context, p(x, size, 2.2f), p(y, size, 1.6f), p(x, size, 2.2f), p(y, size, 6.4f), color, w);
        RenderUtils.drawLine(context, p(x, size, 9.8f), p(y, size, 1.6f), p(x, size, 9.8f), p(y, size, 6.4f), color, w);
        RenderUtils.drawLine(context, p(x, size, 2.2f), p(y, size, 6.4f), p(x, size, 6f), p(y, size, 11.2f), color, w);
        RenderUtils.drawLine(context, p(x, size, 9.8f), p(y, size, 6.4f), p(x, size, 6f), p(y, size, 11.2f), color, w);
        RenderUtils.drawLine(context, p(x, size, 4.1f), p(y, size, 6.1f), p(x, size, 5.5f), p(y, size, 7.6f), color, w);
        RenderUtils.drawLine(context, p(x, size, 5.5f), p(y, size, 7.6f), p(x, size, 8.2f), p(y, size, 4.2f), color, w);
    }

    /** Gear: a ring with radial teeth and a solid hub. */
    private static void gearShape(DrawContext context, int x, int y, int size, int color, int teeth) {
        int w = stroke(size);
        ring(context, x, y, size, 6f, 6f, 3.5f, color);
        int count = Math.max(4, teeth);
        for (int i = 0; i < count; i++) {
            double a = Math.PI * 2 * i / count;
            RenderUtils.drawLine(context,
                    p(x, size, (float) (6f + Math.cos(a) * 3.4f)),
                    p(y, size, (float) (6f + Math.sin(a) * 3.4f)),
                    p(x, size, (float) (6f + Math.cos(a) * 5.3f)),
                    p(y, size, (float) (6f + Math.sin(a) * 5.3f)), color, w);
        }
        disc(context, x, y, size, 6f, 6f, 1.4f, color);
    }

    /** Filled four-point sparkle. */
    private static void sparkle(DrawContext context, int x, int y, int size, int color) {
        poly(context, x, y, size, color,
                6f, 0.2f, 7.4f, 4.6f, 11.8f, 6f, 7.4f, 7.4f, 6f, 11.8f, 4.6f, 7.4f, 0.2f, 6f, 4.6f, 4.6f);
    }

    /** Ghost with a wavy hem. */
    private static void ghost(DrawContext context, int x, int y, int size, int color) {
        int w = stroke(size);
        arc(context, x, y, size, 6f, 5.4f, 4.0f, 4.0f, (float) Math.PI, (float) (Math.PI * 2), w, color);
        RenderUtils.drawLine(context, p(x, size, 2f), p(y, size, 5.4f), p(x, size, 2f), p(y, size, 9.2f), color, w);
        RenderUtils.drawLine(context, p(x, size, 10f), p(y, size, 5.4f), p(x, size, 10f), p(y, size, 9.2f), color, w);
        RenderUtils.drawLine(context, p(x, size, 2f), p(y, size, 9.2f), p(x, size, 3.3f), p(y, size, 11f), color, w);
        RenderUtils.drawLine(context, p(x, size, 3.3f), p(y, size, 11f), p(x, size, 4.6f), p(y, size, 9.2f), color, w);
        RenderUtils.drawLine(context, p(x, size, 4.6f), p(y, size, 9.2f), p(x, size, 6f), p(y, size, 11f), color, w);
        RenderUtils.drawLine(context, p(x, size, 6f), p(y, size, 11f), p(x, size, 7.4f), p(y, size, 9.2f), color, w);
        RenderUtils.drawLine(context, p(x, size, 7.4f), p(y, size, 9.2f), p(x, size, 8.7f), p(y, size, 11f), color, w);
        RenderUtils.drawLine(context, p(x, size, 8.7f), p(y, size, 11f), p(x, size, 10f), p(y, size, 9.2f), color, w);
        box(context, x, y, size, 4.1f, 5.1f, 5.3f, 6.7f, color);
        box(context, x, y, size, 6.7f, 5.1f, 7.9f, 6.7f, color);
    }

    /** Planet with a ring. */
    private static void planet(DrawContext context, int x, int y, int size, int color) {
        disc(context, x, y, size, 6f, 6f, 3.0f, color);
        RenderUtils.drawLine(context, p(x, size, 0.9f), p(y, size, 8.6f), p(x, size, 11.1f), p(y, size, 3.4f),
                color, Math.max(2, stroke(size) - 1));
    }

    /** Keyboard with keys and a space bar. */
    private static void keyboard(DrawContext context, int x, int y, int size, int color) {
        int w = stroke(size);
        RenderUtils.drawBorder(context, p(x, size, 0.9f), p(y, size, 3.2f),
                p(x, size, 11.1f), p(y, size, 9.6f), w, color);
        box(context, x, y, size, 2.2f, 4.8f, 4.0f, 6.4f, color);
        box(context, x, y, size, 4.8f, 4.8f, 6.6f, 6.4f, color);
        box(context, x, y, size, 7.4f, 4.8f, 9.2f, 6.4f, color);
        RenderUtils.drawLine(context, p(x, size, 3.0f), p(y, size, 8.1f), p(x, size, 9.0f), p(y, size, 8.1f),
                color, Math.max(2, w));
    }

    /** Code brackets for the developer tab. */
    private static void code(DrawContext context, int x, int y, int size, int color) {
        int w = stroke(size);
        RenderUtils.drawLine(context, p(x, size, 4.6f), p(y, size, 2.6f), p(x, size, 1.4f), p(y, size, 6f), color, w);
        RenderUtils.drawLine(context, p(x, size, 1.4f), p(y, size, 6f), p(x, size, 4.6f), p(y, size, 9.4f), color, w);
        RenderUtils.drawLine(context, p(x, size, 7.4f), p(y, size, 2.6f), p(x, size, 10.6f), p(y, size, 6f), color, w);
        RenderUtils.drawLine(context, p(x, size, 10.6f), p(y, size, 6f), p(x, size, 7.4f), p(y, size, 9.4f), color, w);
    }

    // ---------------------------------------------------------------- helpers

    /** Unit -> pixel conversion on the 12x12 grid. */
    private static int p(int origin, int size, float units) {
        return origin + Math.round(units * size / 12f);
    }

    /** Minimum two pixels: at sidebar size a one pixel stroke is invisible. */
    private static int stroke(int size) {
        return Math.max(2, Math.round(size / 6f));
    }

    private static void box(DrawContext context, int x, int y, int size, float x1, float y1, float x2, float y2, int color) {
        int sx = p(x, size, x1);
        int sy = p(y, size, y1);
        int ex = Math.max(sx + 1, p(x, size, x2));
        int ey = Math.max(sy + 1, p(y, size, y2));
        context.fill(sx, sy, ex, ey, color);
    }

    /** Scanline-filled polygon; {@code units} is a flat x,y list on the 12 unit grid. */
    private static void poly(DrawContext context, int x, int y, int size, int color, float... units) {
        int n = units.length / 2;
        if (n < 3 || size <= 0) return;
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (int i = 0; i < n; i++) {
            int py = p(y, size, units[i * 2 + 1]);
            minY = Math.min(minY, py);
            maxY = Math.max(maxY, py);
        }
        float[] xs = new float[n];
        for (int py = Math.max(y, minY); py <= Math.min(y + size - 1, maxY); py++) {
            float uy = (py + 0.5f - y) * 12f / size;
            int count = 0;
            for (int i = 0; i < n; i++) {
                int j = (i + 1) % n;
                float y1 = units[i * 2 + 1];
                float y2 = units[j * 2 + 1];
                if ((y1 <= uy && y2 > uy) || (y2 <= uy && y1 > uy)) {
                    float x1 = units[i * 2];
                    float x2 = units[j * 2];
                    xs[count++] = x1 + (uy - y1) * (x2 - x1) / (y2 - y1);
                }
            }
            if (count < 2) continue;
            for (int a = 1; a < count; a++) {
                float value = xs[a];
                int b = a - 1;
                while (b >= 0 && xs[b] > value) {
                    xs[b + 1] = xs[b];
                    b--;
                }
                xs[b + 1] = value;
            }
            for (int k = 0; k + 1 < count; k += 2) {
                int sx = p(x, size, xs[k]);
                int ex = Math.max(sx + 1, p(x, size, xs[k + 1]));
                context.fill(sx, py, ex, py + 1, color);
            }
        }
    }

    /** Ellipse arc made of short segments; angles are radians in screen space. */
    private static void arc(DrawContext context, int x, int y, int size, float cx, float cy,
                            float rx, float ry, float a0, float a1, int width, int color) {
        int steps = 16;
        int px0 = p(x, size, cx + (float) (Math.cos(a0) * rx));
        int py0 = p(y, size, cy + (float) (Math.sin(a0) * ry));
        for (int i = 1; i <= steps; i++) {
            float a = a0 + (a1 - a0) * i / steps;
            int nx = p(x, size, cx + (float) (Math.cos(a) * rx));
            int ny = p(y, size, cy + (float) (Math.sin(a) * ry));
            RenderUtils.drawLine(context, px0, py0, nx, ny, color, width);
            px0 = nx;
            py0 = ny;
        }
    }

    /** Circle outline built from short segments (no erase pass needed). */
    private static void ring(DrawContext context, int x, int y, int size, float cx, float cy, float r, int color) {
        int w = stroke(size);
        int steps = 14;
        int prevX = p(x, size, cx + r);
        int prevY = p(y, size, cy);
        for (int i = 1; i <= steps; i++) {
            double a = Math.PI * 2 * i / steps;
            int nx = p(x, size, (float) (cx + Math.cos(a) * r));
            int ny = p(y, size, (float) (cy + Math.sin(a) * r));
            RenderUtils.drawLine(context, prevX, prevY, nx, ny, color, w);
            prevX = nx;
            prevY = ny;
        }
    }

    private static void disc(DrawContext context, int x, int y, int size, float cx, float cy, float r, int color) {
        int sx = p(x, size, cx - r);
        int sy = p(y, size, cy - r);
        int w = Math.max(1, p(x, size, cx + r) - sx);
        int h = Math.max(1, p(y, size, cy + r) - sy);
        RenderUtils.fillRoundedRaw(context, sx, sy, w, h, Math.min(w, h) / 2, color);
    }

    private static void rounded(DrawContext context, int x, int y, int size,
                                float x1, float y1, float x2, float y2, int radius, int color) {
        int sx = p(x, size, x1);
        int sy = p(y, size, y1);
        int w = Math.max(1, p(x, size, x2) - sx);
        int h = Math.max(1, p(y, size, y2) - sy);
        RenderUtils.fillRoundedRaw(context, sx, sy, w, h, radius, color);
    }
}
