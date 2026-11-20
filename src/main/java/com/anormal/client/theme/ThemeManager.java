package com.anormal.client.theme;

import com.anormal.client.util.ColorUtils;
import com.anormal.client.util.RenderUtils;
import net.minecraft.client.gui.DrawContext;

/**
 * Palette + window chrome for the three GUI themes.
 *
 * <p>{@link #VAPE_V4} is the client's own "Anormal" look (charcoal with the user's accent).
 * {@link #VAPE} is the Vape V4 inspired look: near-black rounded panels, a thin white outline and a
 * fixed purple accent, so it stays recognisable no matter what the accent colour is set to.</p>
 */
public class ThemeManager {
    /** Vape's teal. Fixed on purpose: the theme should look the same for everyone. */
    public static final int VAPE_ACCENT = 0xFF00D6A1;
    /** Text drawn on top of an enabled (teal) card. */
    public static final int VAPE_ON_ACCENT = 0xFF0E0E0E;

    private static Theme activeTheme = Theme.VAPE_V4;
    /** Layout switch from ClientSettings > Type: false = Anormal classic frame, true = Vape frames. */
    private static boolean vapeLayout = false;
    private static int customAccentColor = ColorUtils.rgba(255, 85, 0, 255); // vivid brutalist orange

    public static Theme getActiveTheme() {
        return activeTheme;
    }

    public static void setActiveTheme(Theme theme) {
        activeTheme = theme;
    }

    public static boolean isVapeLayout() {
        return vapeLayout;
    }

    public static void setVapeLayout(boolean enabled) {
        vapeLayout = enabled;
    }

    public static int getAccentColor() {
        if (activeTheme == Theme.VAPE) {
            return VAPE_ACCENT;
        }
        if (activeTheme == Theme.FROZEN) {
            return ColorUtils.rgba(120, 214, 255, 255); // Frozen ice cyan
        }
        if (activeTheme == Theme.VAPE_V4 || activeTheme == Theme.BRUTALISM) {
            return customAccentColor;
        } else {
            return ColorUtils.rgba(70, 160, 245, 255); // Frost glass neon blue
        }
    }

    public static void setCustomAccentColor(int color) {
        customAccentColor = color;
    }

    public static int getBackgroundColor() {
        if (activeTheme == Theme.BRUTALISM) {
            return ColorUtils.rgba(17, 19, 26, 255); // Deep neo-brutalist graphite
        } else if (activeTheme == Theme.VAPE) {
            return ColorUtils.rgba(24, 24, 24, 246);
        } else if (activeTheme == Theme.FROZEN) {
            return ColorUtils.rgba(16, 34, 48, 200); // Frozen arctic glass
        } else if (activeTheme == Theme.VAPE_V4) {
            return ColorUtils.rgba(20, 20, 20, 245);
        } else {
            return ColorUtils.rgba(18, 22, 34, 165); // Frosted Glass
        }
    }

    public static int getHeaderColor() {
        if (activeTheme == Theme.BRUTALISM) {
            return ColorUtils.rgba(0, 0, 0, 255); // Stark black
        } else if (activeTheme == Theme.VAPE) {
            return ColorUtils.rgba(17, 17, 17, 250);
        } else if (activeTheme == Theme.FROZEN) {
            return ColorUtils.rgba(24, 48, 66, 215);
        } else if (activeTheme == Theme.VAPE_V4) {
            return ColorUtils.rgba(15, 15, 15, 250);
        } else {
            return ColorUtils.rgba(25, 32, 50, 190);
        }
    }

    public static int getCardColor(boolean enabled, boolean hovered) {
        if (activeTheme == Theme.BRUTALISM) {
            if (enabled) {
                return hovered ? ColorUtils.rgba(36, 42, 58, 255) : ColorUtils.rgba(24, 28, 38, 255);
            } else {
                return hovered ? ColorUtils.rgba(25, 29, 39, 255) : ColorUtils.rgba(18, 20, 28, 255);
            }
        } else if (activeTheme == Theme.VAPE) {
            if (enabled) {
                // Vape paints the whole enabled row teal and writes dark text on it.
                return hovered ? ColorUtils.rgba(0, 230, 172, 255) : VAPE_ACCENT;
            } else {
                return hovered ? ColorUtils.rgba(41, 41, 41, 255) : ColorUtils.rgba(31, 31, 31, 255);
            }
        } else if (activeTheme == Theme.FROZEN) {
            if (enabled) {
                return hovered ? ColorUtils.rgba(88, 150, 185, 200) : ColorUtils.rgba(62, 118, 150, 175);
            } else {
                return hovered ? ColorUtils.rgba(34, 62, 84, 150) : ColorUtils.rgba(24, 46, 62, 120);
            }
        } else if (activeTheme == Theme.VAPE_V4) {
            if (enabled) {
                return hovered ? ColorUtils.rgba(38, 38, 38, 255) : ColorUtils.rgba(30, 30, 30, 255);
            } else {
                return hovered ? ColorUtils.rgba(24, 24, 24, 240) : ColorUtils.rgba(18, 18, 18, 240);
            }
        } else {
            // Glassmorphism
            if (enabled) {
                return hovered ? ColorUtils.rgba(45, 60, 95, 180) : ColorUtils.rgba(35, 48, 80, 150);
            } else {
                return hovered ? ColorUtils.rgba(30, 38, 55, 130) : ColorUtils.rgba(22, 28, 42, 100);
            }
        }
    }

    public static int getBorderColor() {
        if (activeTheme == Theme.BRUTALISM) {
            return 0xFFFFFFFF; // Stark solid white border
        } else if (activeTheme == Theme.VAPE) {
            return ColorUtils.rgba(255, 255, 255, 26);
        } else if (activeTheme == Theme.FROZEN) {
            return ColorUtils.rgba(205, 240, 255, 70);
        } else if (activeTheme == Theme.VAPE_V4) {
            return ColorUtils.rgba(10, 10, 10, 255);
        } else {
            return ColorUtils.rgba(255, 255, 255, 45);
        }
    }

    public static int getGlowColor() {
        if (activeTheme == Theme.BRUTALISM) {
            return customAccentColor;
        } else if (activeTheme == Theme.VAPE) {
            return ColorUtils.rgba(0, 214, 161, 55);
        } else if (activeTheme == Theme.FROZEN) {
            return ColorUtils.rgba(130, 215, 255, 85);
        } else if (activeTheme == Theme.VAPE_V4) {
            return ColorUtils.rgba(0, 0, 0, 100);
        } else {
            return ColorUtils.rgba(80, 140, 230, 70);
        }
    }

    /**
     * Neutral surface colour for sidebars, setting rows and buttons.
     *
     * <p>The generic panels in the GUI were hard-coded to a slightly blue grey, which looked wrong
     * in the Vape theme next to its warm near-black cards, so the tint is theme aware now.</p>
     */
    public static int getSurfaceColor(int alpha) {
        if (activeTheme == Theme.VAPE) return ColorUtils.rgba(31, 31, 31, alpha);
        if (activeTheme == Theme.BRUTALISM) return ColorUtils.rgba(20, 22, 30, alpha);
        if (activeTheme == Theme.FROZEN) return ColorUtils.rgba(18, 38, 52, alpha);
        return ColorUtils.rgba(14, 16, 20, alpha);
    }

    public static int getTextColor(boolean active) {
        if (active) {
            return 0xFFFFFFFF;
        }
        if (activeTheme == Theme.BRUTALISM) return 0xFF8B95AF;
        if (activeTheme == Theme.FROZEN) return 0xFFBCD6E4;
        if (activeTheme == Theme.VAPE) return 0xFF9A9A9A;
        return activeTheme == Theme.VAPE_V4 ? 0xFFAAAAAA : 0xFFB0BCCC;
    }

    public static void renderWindow(DrawContext context, int x, int y, int width, int height, String title) {
        if (activeTheme == Theme.BRUTALISM) {
            // Hard offset brutalist drop shadow (box-shadow: 6px 6px 0px accent)
            RenderUtils.fill(context, x + 6, y + 6, x + width + 6, y + height + 6, getAccentColor());
            // Main container
            RenderUtils.fill(context, x, y, x + width, y + height, getBackgroundColor());
            RenderUtils.drawBorder(context, x, y, x + width, y + height, 2, 0xFFFFFFFF);
            // Black header bar
            RenderUtils.fill(context, x + 2, y + 2, x + width - 2, y + 26, 0xFF000000);
            // White divider line
            RenderUtils.fill(context, x, y + 25, x + width, y + 27, 0xFFFFFFFF);
            // Brutalism keeps its hazard tape, corner blocks and ticker on the classic frame too.
            BrutalismFx.decoratePanel(context, x, y, width, height, 27);
        } else if (activeTheme == Theme.VAPE) {
            renderVapeWindow(context, x, y, width, height);
        } else if (activeTheme == Theme.VAPE_V4) {
            RenderUtils.drawDarkPanel(context, x, y, x + width, y + height, 22, getHeaderColor(), getBackgroundColor(), getAccentColor());
        } else {
            RenderUtils.drawGlassPanel(context, x, y, x + width, y + height, getBackgroundColor(), getBorderColor(), getGlowColor());
            // Header divider line with glow
            context.fill(x + 2, y + 22, x + width - 2, y + 23, ColorUtils.rgba(100, 180, 255, 80));
            // Frozen and Glassmorphism keep their living effect layer on the classic frame too.
            if (activeTheme == Theme.FROZEN) {
                FrozenFx.decoratePanel(context, x, y, width, height);
            } else if (activeTheme == Theme.GLASSMORPHISM) {
                GlassFx.decoratePanel(context, x, y, width, height, -1, -1);
            }
        }
    }

    /**
     * Vape V4 chrome: one rounded near-black card, a slightly darker header and a hairline divider
     * with the purple accent showing through it.
     */
    private static void renderVapeWindow(DrawContext context, int x, int y, int width, int height) {
        int radius = 5;
        // Border ring drawn as a 1px larger rounded rect, then the body on top of it.
        RenderUtils.fillRounded(context, x, y, width, height, radius, ColorUtils.rgba(255, 255, 255, 26));
        RenderUtils.fillRounded(context, x + 1, y + 1, width - 2, height - 2, radius, getBackgroundColor());
        // Header block, inset so the rounded corners stay clean.
        RenderUtils.fill(context, x + 2, y + 1, x + width - 2, y + 22, getHeaderColor());
        RenderUtils.fill(context, x + 2, y + 22, x + width - 2, y + 23, ColorUtils.rgba(0, 214, 161, 90));
    }
}
