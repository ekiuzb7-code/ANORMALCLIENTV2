package com.anormal.client.util.kuro;

import net.minecraft.client.gui.DrawContext;

/**
 * kuro'daki {@code Gfx.fillPolygon} uslubidagi kichik yordamchi: 2D uchburchak
 * to'ldirish. Anormal'da vertex-bufersiz 2D polygon API yo'q, shuning uchun
 * skan-qator algoritmi bilan {@code DrawContext.fill} chaqiruvlaridan
 * to'ldiriladi (RenderUtils.drawLine bilan bir uslub: klip + fill).
 */
public final class KuroTriangles {
    private KuroTriangles() {}

    public static void fillTriangle(DrawContext g, double ax, double ay, double bx, double by, double cx, double cy, int argb) {
        // balandlik bo'yicha tartiblash: (a) eng tepada, (b) o'rtada, (c) eng pastda
        if (ay > by) { double t = ax; ax = bx; bx = t; t = ay; ay = by; by = t; }
        if (by > cy) { double t = bx; bx = cx; cx = t; t = by; by = cy; cy = t; }
        if (ay > by) { double t = ax; ax = bx; bx = t; t = ay; ay = by; by = t; }

        int y0 = (int) Math.ceil(ay - 0.5), y1 = (int) Math.floor(cy - 0.5);
        for (int y = Math.max(y0, 0); y <= y1; y++) {
            // chiziqlar: a-c (to'liq) va a-b yoki b-c (yarim)
            double t01 = (y - ay) / Math.max(1e-9, by - ay);
            boolean lowerHalf = y > by - 0.5;
            double t02 = (y - ay) / Math.max(1e-9, cy - ay);
            double xa = ax + (cx - ax) * t02;
            double xb = lowerHalf ? bx + (cx - bx) * (y - by) / Math.max(1e-9, cy - by) : ax + (bx - ax) * t01;
            int xL = (int) Math.ceil(Math.min(xa, xb) - 0.5);
            int xR = (int) Math.floor(Math.max(xa, xb) - 0.5);
            if (xR < xL) continue;
            g.fill(xL, y, xR + 1, y + 1, argb);
        }
    }

    public static void fillQuad(DrawContext g, double ax, double ay, double bx, double by, double cx, double cy, double dx, double dy, int argb) {
        fillTriangle(g, ax, ay, bx, by, cx, cy, argb);
        fillTriangle(g, ax, ay, cx, cy, dx, dy, argb);
    }
}
