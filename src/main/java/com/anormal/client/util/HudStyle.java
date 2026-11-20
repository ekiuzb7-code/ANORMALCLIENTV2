package com.anormal.client.util;

import com.anormal.client.module.Module;
import com.anormal.client.module.impl.client.HudTheme;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import java.util.Map;

/**
 * Draws the shared HUD frame (outline + glow) and the optional text glow.
 *
 * <p>Everything is derived from the frame each module actually painted, which
 * {@code DrawContextMixin} records during the measurement pass. That keeps the styling in one
 * place: a module does not need to know it is being framed, and adding a new overlay costs
 * nothing.</p>
 */
public final class HudStyle {

    private static boolean inPass;
    private static boolean drawingGlow;

    private HudStyle() {
    }

    public static void beginPass() {
        inPass = true;
    }

    public static void endPass() {
        inPass = false;
    }

    // ------------------------------------------------------------------
    //  Modern HUD panels (legit section)
    // ------------------------------------------------------------------

    /** Body colour of the modern HUD panel. */
    public static final int PANEL_BG = 0xE80B0D14;

    public static int clamp(int value, int min, int max) {
        if (max < min) return min;
        return value < min ? min : Math.min(value, max);
    }

    /**
     * Keeps a whole panel on screen.
     *
     * <p>The legit overlays used to draw at raw {@code Pos X / Pos Y}, so a stale config (other
     * GUI scale or a smaller window) could put them at negative coordinates where they were
     * invisible and unreachable. Every panel now passes its top-left through this.</p>
     *
     * @return {@code {x, y}} clamped so the panel is fully visible
     */
    public static int[] place(int x, int y, int w, int h) {
        int sw = RenderUtils.screenWidth();
        int sh = RenderUtils.screenHeight();
        int px = clamp(x, 2, Math.max(2, sw - w - 2));
        int py = clamp(y, 2, Math.max(2, sh - h - 2));
        return new int[]{px, py};
    }

    /**
     * The shared panel: soft drop shadow, rounded dark body, top hairline and a 2px accent spine.
     * Drawn through {@link RenderUtils#fillRounded} so the HUD editor measures it correctly.
     */
    public static void panel(DrawContext ctx, int x, int y, int w, int h, int accent) {
        RenderUtils.fillRoundedRaw(ctx, x - 1, y + 1, w + 2, h + 2, 5, 0x33000000);
        RenderUtils.fillRounded(ctx, x, y, w, h, 5, PANEL_BG);
        RenderUtils.fill(ctx, x + 5, y, x + w - 5, y + 1, 0x22FFFFFF);
        RenderUtils.fill(ctx, x, y + 5, x + 2, y + h - 5, accent);
    }

    /** Header row: dim caps title on the left, the value stays with the caller. */
    public static void title(DrawContext ctx, TextRenderer tr, String text, int x, int y) {
        RenderUtils.drawText(ctx, tr, text == null ? "" : text.toUpperCase(java.util.Locale.ROOT),
                x, y, 0xFF868A99, false);
    }

    /** Thin rounded progress bar. */
    public static void bar(DrawContext ctx, int x, int y, int w, int h, float frac, int color) {
        if (w <= 0 || h <= 0) return;
        float f = Math.max(0.0f, Math.min(1.0f, frac));
        int r = Math.max(1, h / 2);
        RenderUtils.fillRounded(ctx, x, y, w, h, r, 0x38FFFFFF);
        int filled = (int) (w * f);
        if (filled > 0) {
            RenderUtils.fillRounded(ctx, x, y, Math.max(2, Math.min(w, filled)), h, r, color);
        }
    }

    /** Green / amber / red durability colour. */
    public static int durabilityColor(int pct) {
        if (pct > 50) return 0xFF4ADE80;
        if (pct > 25) return 0xFFFBBF24;
        return 0xFFF87171;
    }

    /** Truncates a label to fit {@code maxWidth}, keeping it readable. */
    public static String trim(TextRenderer tr, String text, int maxWidth) {
        if (text == null) return "";
        if (maxWidth <= 0 || tr.getWidth(text) <= maxWidth) return text;
        String dots = "..";
        int i = text.length();
        while (i > 0 && tr.getWidth(text.substring(0, i) + dots) > maxWidth) i--;
        return text.substring(0, Math.max(1, i)) + dots;
    }


    // ------------------------------------------------------------------
    //  Shared card look
    // ------------------------------------------------------------------

    public static final int CARD_H = 16;

    /** Width the card needs for a label + value pair. */
    public static int cardWidth(TextRenderer tr, String label, String value) {
        return 7 + tr.getWidth(label) + 10 + tr.getWidth(value) + 7;
    }

    /**
     * One HUD row: translucent body, a 2px accent bar on the left, a dim label and a bold value.
     *
     * <p>Every converted overlay uses this so the HUD reads as one design instead of a pile of
     * differently styled boxes.</p>
     *
     * @param centerOnX when true {@code x} is the centre of the card, not its left edge
     * @return the card width, so callers can stack rows
     */
    public static int card(DrawContext ctx, TextRenderer tr, int x, int y,
                           String label, String value, int accent, boolean centerOnX) {
        int w = cardWidth(tr, label, value);
        int left = centerOnX ? x - w / 2 : x;

        // Body + hairline top so it lifts off the world.
        ctx.fill(left, y, left + w, y + CARD_H, 0x8C10141C);
        ctx.fill(left, y, left + w, y + 1, 0x2AFFFFFF);
        // Accent bar.
        ctx.fill(left, y, left + 2, y + CARD_H, accent);

        ctx.drawText(tr, Text.literal(label).styled(st -> st.withColor(0xFF8A8A96)),
                left + 7, y + 4, 0xFF8A8A96, false);

        int valueWidth = tr.getWidth(value);
        ctx.drawText(tr, Text.literal(value).styled(st -> st.withBold(true)),
                left + w - 7 - valueWidth, y + 4, 0xFFE8E8EC, true);

        return w;
    }

    /** Draws the configured frame around every measured overlay. */
    public static void drawFrames(DrawContext ctx, Map<Module, int[]> frames) {
        HudTheme theme = HudTheme.getInstance();
        if (theme == null || !theme.isEnabled() || frames.isEmpty()) return;

        boolean wantGlow = theme.glow.isEnabled();
        boolean wantOutline = theme.outline.isEnabled();
        if (!wantGlow && !wantOutline) return;

        int glowColor = theme.glowColor.getColor();
        int glowStrength = theme.glowStrength.getValue().intValue();
        int outlineColor = theme.outlineColor.getColor();
        int outlineWidth = theme.outlineWidth.getValue().intValue();

        for (int[] f : frames.values()) {
            if (f == null || f[2] <= 0 || f[3] <= 0) continue;
            int x = f[0], y = f[1], w = f[2], h = f[3];

            if (wantGlow) {
                // Expanding rings with falling alpha read as a soft halo without a blur shader.
                for (int i = 1; i <= glowStrength; i++) {
                    int alpha = Math.max(0, 46 - i * 12);
                    if (alpha <= 0) break;
                    int color = (alpha << 24) | (glowColor & 0xFFFFFF);
                    RenderUtils.drawBorder(ctx, x - i, y - i, x + w + i, y + h + i, 1, color);
                }
            }

            if (wantOutline) {
                RenderUtils.drawBorder(ctx, x - 1, y - 1, x + w + 1, y + h + 1,
                        outlineWidth, outlineColor);
            }
        }
    }

    /**
     * Draws the text halo, if enabled. Called from the {@code DrawContext.drawText} mixin.
     *
     * <p>Guarded by {@code drawingGlow} so the copies it draws do not recurse into themselves.</p>
     */
    public static void beforeText(DrawContext ctx, TextRenderer tr, Text text, int x, int y) {
        HudTheme theme = HudTheme.getInstance();
        if (theme == null || !theme.isEnabled() || !theme.textGlow.isEnabled()) return;
        if (!inPass || drawingGlow || text == null) return;

        int color = (86 << 24) | (theme.textGlowColor.getColor() & 0xFFFFFF);
        drawingGlow = true;
        try {
            ctx.drawText(tr, text, x - 1, y, color, false);
            ctx.drawText(tr, text, x + 1, y, color, false);
            ctx.drawText(tr, text, x, y - 1, color, false);
            ctx.drawText(tr, text, x, y + 1, color, false);
        } catch (Throwable ignored) {
        } finally {
            drawingGlow = false;
        }
    }
}
