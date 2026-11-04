package com.anormal.client.module.impl.kuro;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import net.minecraft.util.math.MathHelper;

/**
 * kuro'dagi {@code RadialMenuScreen} porti (Anormal DrawContext): ekran
 * o‘rtasida uch slice — slapik berilgan aylanma segment sifatida chiziladi
 * (1-pikselli qatorlar bilan; Anormal 1.21.11 DrawContext'ida uchburchak
 * pipeline yo‘q). O‘rtada yopish zonasi. Bosish/modda menyu moduli orqali
 * yuboriladi; ESC yoki o‘rta bosing yopadi.
 */
public class KuroRadialMenuScreen extends Screen {
    private static final float R = 78f;
    private static final float HOLE = 26f;
    private static final double SECTOR = Math.toRadians(55);

    private final KuroRadialMenu module;
    private int cx, cy;

    public KuroRadialMenuScreen(KuroRadialMenu module) {
        super(Text.literal("Radial Menu"));
        this.module = module;
    }

    private static double sliceCenter(int i) { return Math.toRadians(-90 + 120 * i); }

    /** Nuqta (dx,dy=ekran o'qiga nisbatan) qaysi slice'da: -1 bo'sh (o'rta/tashqarida). */
    private static int sliceAt(int cx, int cy, int mx, int my) {
        double dx = mx - cx, dy = my - cy;
        double dist = Math.sqrt(dx * dx + dy * dy);
        if (dist < HOLE || dist > R) return -1;
        double ang = Math.atan2(dy, dx);
        for (int i = 0; i < KuroRadialMenu.SLOTS; i++) {
            double c = sliceCenter(i);
            double d = norm(ang - c);
            if (Math.abs(d) <= SECTOR) return i;
        }
        return -1;
    }

    private static double norm(double a) {
        a = a % (Math.PI * 2);
        if (a > Math.PI) a -= Math.PI * 2;
        if (a < -Math.PI) a += Math.PI * 2;
        return a;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        cx = width / 2;
        cy = height / 2;
        int hovered = sliceAt(cx, cy, mouseX, mouseY);
        context.fill(0, 0, width, height, 0x60101018);   // mayda qaralish qatlam

        for (int i = 0; i < KuroRadialMenu.SLOTS; i++) {
            double c = sliceCenter(i);
            int ar = (i == hovered) ? 0x88E05AB8 : 0x40E05AB8;
            drawSector(context, c, ar);
            int lr = (i == hovered) ? 0xFFFFFFFF : 0xFFE7C2F0;
            int tx = cx + (int) (R * 0.66 * Math.cos(c));
            int ty = cy + (int) (R * 0.66 * Math.sin(c));
            String label = module.slot(i);
            String shown = label.isEmpty() ? "(bo'sh) " + (i + 1) : label;
            if (shown.length() > 18) shown = shown.substring(0, 18) + "…";
            context.drawCenteredTextWithShadow(this.textRenderer, shown, tx, ty - 4, lr);
        }

        // o'rta zonasi '\u274c'
        context.drawCenteredTextWithShadow(this.textRenderer, "✕", cx, cy - 5, hovered == -1 ? 0xFFE05AB8 : 0xFF777777);
        context.drawCenteredTextWithShadow(this.textRenderer, "yopish", cx, cy + 8, 0xFF555555);
        super.render(context, mouseX, mouseY, delta);
    }

    /** Sector'i bir-necha qator fill bilan ko'rinishdek bo'yadi (1-pikselli qatorlar bilan). */
    private void drawSector(DrawContext context, double center, int argb) {
        int outer = (int) R;
        for (int d = -outer; d <= outer; d++) {
            int y = cy + d;
            int runStart = Integer.MIN_VALUE;
            for (int dxx = -outer; dxx <= outer; dxx++) {
                double dx = dxx, dist = Math.sqrt(dx * dx + d * d);
                boolean in = false;
                if (dist >= HOLE && dist <= R) {
                    double ang = Math.atan2(d, dx);
                    double diff = norm(ang - center);
                    in = Math.abs(diff) <= SECTOR;
                }
                if (in) { if (runStart == Integer.MIN_VALUE) runStart = dxx; }
                else if (runStart != Integer.MIN_VALUE) {
                    context.fill(cx + runStart, y, cx + dxx, y + 1, argb);
                    runStart = Integer.MIN_VALUE;
                }
            }
            if (runStart != Integer.MIN_VALUE) context.fill(cx + runStart, y, cx + outer + 1, y + 1, argb);
        }
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.gui.Click click, boolean doubled) {
        double mouseX = click.x(); double mouseY = click.y(); int button = click.button();
        int i = sliceAt(cx, cy, (int) mouseX, (int) mouseY);
        if (i >= 0) {
            if (button == 1) module.openEditor(i);           // o'ng: tahrirlash
            else {
                module.send(i);                               // chap: yuborish
                if (module.closeAfterSending()) close();
            }
            return true;
        }
        double dx = mouseX - cx, dy = mouseY - cy;
        if (dx * dx + dy * dy < HOLE * HOLE) { close(); return true; }   // o'rta: faqat yopadi
        return super.mouseClicked(click, doubled);
    }

    @Override
    public void removed() {
        module.screenRemoved();
        super.removed();
    }

    @Override
    public boolean shouldPause() { return false; }

    /** Yarn MathHelper uslubidagi norm sifatida suyak (MathHelper signatalar salbiy burchaklar) bilan bog'lanadi. */
    private static float clamp360(float v) { return MathHelper.wrapDegrees(v); }
}
