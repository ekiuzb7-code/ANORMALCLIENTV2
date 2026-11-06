package com.anormal.client.module.impl.spectre;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.ColorUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.util.math.MathHelper;

/**
 * Spectre port: "Custom Crosshair" — draws a configurable crosshair in place of
 * the vanilla one.
 *
 * <p>Shapes, geometry, outline, center dot, target tint and dynamic gap all follow
 * the Spectre implementation; drawing uses the vanilla {@link DrawContext} fill API
 * instead of Spectre's ImGui draw list.</p>
 */
public final class SpectreCustomCrosshair extends Module {
    private static final int OUTLINE_ABGR = -1946157056;
    private static final float DIAGONAL = 0.70710677F;

    public final ModeSetting style = addSetting(new ModeSetting("Style", "Crosshair shape", "Cross", "Cross", "T", "X", "Dot", "Circle"));
    public final NumberSetting size = addSetting(new NumberSetting("Size", "Arm / dot / circle length", 6.0, 1.0, 20.0, 1.0));
    public final NumberSetting gap = addSetting(new NumberSetting("Gap", "Distance from the center", 2.0, 0.0, 16.0, 1.0));
    public final NumberSetting thickness = addSetting(new NumberSetting("Thickness", "Arm thickness", 2.0, 1.0, 5.0, 1.0));
    public final BooleanSetting centerDot = addSetting(new BooleanSetting("Center Dot", "Draw a dot in the center", false));
    public final BooleanSetting outline = addSetting(new BooleanSetting("Outline", "Draw a subtle dark outline", true));
    public final ColorSetting color = addSetting(new ColorSetting("Color", "Crosshair color", ColorUtils.rgba(147, 220, 84, 255)));
    public final BooleanSetting targetTint = addSetting(new BooleanSetting("Target Tint", "Tint while aiming at a living target", false));
    public final ColorSetting targetColor = addSetting(new ColorSetting("Target Color", "Color while on target", ColorUtils.rgba(255, 226, 126, 255)));
    public final BooleanSetting dynamicGap = addSetting(new BooleanSetting("Dynamic Gap", "Open the gap while moving / recovering", false));
    public final NumberSetting spread = addSetting(new NumberSetting("Spread", "Dynamic gap amount", 4.0, 1.0, 12.0, 1.0));

    private float tint;
    private float extraGap;

    public SpectreCustomCrosshair() {
        super("SpectreCustomCrosshair", "Spectre port: draws a configurable crosshair in place of the vanilla one.", Category.SPECTRE);
    }

    /** InGameHudMixin hides the vanilla crosshair while this module is on. */
    public boolean shouldHideVanilla() {
        return isEnabled();
    }

    @Override
    public void onEnable() {
        tint = 0.0F;
        extraGap = 0.0F;
    }

    @Override
    public void onRender2D(DrawContext g, float tickDelta) {
        var player = mc.player;
        if (player == null || mc.world == null || !isVisible()) return;

        float dt = Math.min(Math.max(tickDelta / 20.0F + 0.05F, 0.0F), 0.1F);
        updateMotion(player, dt);

        var window = mc.getWindow();
        float cx = window.getScaledWidth() * 0.5F;
        float cy = window.getScaledHeight() * 0.5F;

        int fill = currentColor();
        float fillAlpha = ((fill >>> 24) & 0xFF) / 255.0F;
        float edge = outline.isEnabled() ? 1.0F : 0.0F;
        var geometry = geometry(cx, cy);

        if (edge > 0.0F) {
            drawShape(g, geometry, withAlpha(OUTLINE_ABGR, (int) (fillAlpha * 255)), edge);
        }
        drawShape(g, geometry, fill, 0.0F);
    }

    private boolean isVisible() {
        if (mc.world == null || mc.options.hudHidden) return false;
        if (mc.currentScreen != null && !(mc.currentScreen instanceof net.minecraft.client.gui.screen.ChatScreen)) return false;
        return mc.options.getPerspective().isFirstPerson();
    }

    private void updateMotion(net.minecraft.entity.player.PlayerEntity player, float dt) {
        boolean onTarget = targetTint.isEnabled()
                && mc.targetedEntity instanceof net.minecraft.entity.LivingEntity living
                && living.isAlive();
        tint = approach(tint, onTarget ? 1.0F : 0.0F, 14.0F, dt);
        float wanted = 0.0F;
        if (dynamicGap.isEnabled()) {
            double moved = Math.hypot(player.getX() - player.lastX, player.getZ() - player.lastZ);
            float moving = (float) Math.min(1.0, moved / 0.28);
            float swing = 1.0F - player.getAttackCooldownProgress(0.0F);
            wanted = (float) spread.getAsDouble() * Math.max(moving, swing);
        }
        extraGap = approach(extraGap, wanted, 12.0F, dt);
    }

    private static float approach(float current, float target, float rate, float dt) {
        float next = current + (target - current) * (1.0F - (float) Math.exp(-rate * dt));
        return Math.abs(target - next) < 0.001F ? target : next;
    }

    private int currentColor() {
        int base = color.getColor();
        return tint <= 0.0F ? base : lerpColor(base, targetColor.getColor(), tint);
    }

    private static int lerpColor(int a, int b, float t) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF, aa = (a >> 24) & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF, ba = (b >> 24) & 0xFF;
        int r = (int) MathHelper.lerp(t, ar, br);
        int gr = (int) MathHelper.lerp(t, ag, bg);
        int bl = (int) MathHelper.lerp(t, ab, bb);
        int al = (int) MathHelper.lerp(t, aa, ba);
        return (al << 24) | (r << 16) | (gr << 8) | bl;
    }

    private static int withAlpha(int abgrColor, int alpha) {
        return (alpha << 24) | (abgrColor & 0x00FFFFFF);
    }

    private boolean hasArms() {
        return style.is("Cross") || style.is("T") || style.is("X");
    }

    private record Geometry(float cx, float cy, float size, float open, float thick, float below) { }

    private Geometry geometry(float cx, float cy) {
        float thick = Math.max(1.0F, Math.round((float) thickness.getAsDouble()));
        float length = Math.max(1.0F, Math.round((float) size.getAsDouble()));
        float baseGap = hasArms() ? (float) gap.getAsDouble() : 0.0F;
        float open = Math.round(baseGap + extraGap);
        float maxExtra = dynamicGap.isEnabled() && !style.is("Dot") ? (float) spread.getAsDouble() : 0.0F;
        float below = reachBelow(length, open, thick);
        return new Geometry(cx, cy, length, open, thick, below);
    }

    private float reachBelow(float length, float open, float thick) {
        String shape = style.getValue();
        float below = switch (shape) {
            case "Dot" -> length * 0.5F;
            case "Circle" -> length + open + thick * 0.5F;
            case "X" -> (open + length) * DIAGONAL + thick * 0.5F;
            default -> open + length + thick * 0.5F;
        };
        return centerDot.isEnabled() ? Math.max(below, thick * 0.5F) : below;
    }

    private boolean armsJoined(Geometry g) {
        return (style.is("Cross") || style.is("T")) && g.open() < 1.0F;
    }

    private void drawShape(DrawContext g, Geometry geo, int color, float grow) {
        String shape = style.getValue();
        switch (shape) {
            case "Dot" -> {
                float r = Math.max(1.0F, geo.size() * 0.5F) + grow;
                rect(g, geo.cx() - r, geo.cy() - r, r * 2, r * 2, color);
            }
            case "Circle" -> {
                // filled octagon approximation via 16 rects (cheap ring)
                circle(g, geo.cx(), geo.cy(), geo.size() + geo.open(), geo.thick() + grow * 2.0F, color);
            }
            case "X" -> drawDiagonals(g, geo, color, grow);
            default -> drawArms(g, geo, color, grow, !style.is("T"));
        }
        if (centerDot.isEnabled() && !style.is("Dot") && !armsJoined(geo)) {
            float x0 = (float) Math.floor(geo.cx() - geo.thick() * 0.5F);
            float y0 = (float) Math.floor(geo.cy() - geo.thick() * 0.5F);
            rect(g, x0, y0, geo.thick(), geo.thick(), color);
        }
    }

    private void drawArms(DrawContext g, Geometry geo, int color, float grow, boolean top) {
        float x0 = (float) Math.floor(geo.cx() - geo.thick() * 0.5F);
        float x1 = x0 + geo.thick();
        float y0 = (float) Math.floor(geo.cy() - geo.thick() * 0.5F);
        float y1 = y0 + geo.thick();
        // left / right arms
        rect(g, x0 - (geo.open() + geo.size()) - grow, y0, geo.open() + geo.size(), geo.thick(), color);
        rect(g, x1 + geo.open() - grow, y0, geo.open() + geo.size() + grow, geo.thick(), color);
        if (top) {
            rect(g, x0, y0 - (geo.open() + geo.size() + geo.below()) - grow, geo.thick(),
                    geo.open() + geo.size(), color);
            rect(g, x0, y1 + geo.open(), geo.thick(), geo.below() + grow, color);
        } else {
            rect(g, x0, y1 + geo.open(), geo.thick(), geo.below() + grow, color);
        }
    }

    private void drawDiagonals(DrawContext g, Geometry geo, int color, float grow) {
        float dx = (geo.open() + geo.size()) * DIAGONAL + grow;
        // four diagonal stubs (stepped, ImGui-free)
        for (int i = 0; i < (int) dx; i++) {
            rect(g, geo.cx() + 1 + i, geo.cy() + 1 + i, 1, 1, color);      // ↘
            rect(g, geo.cx() - 1 - i, geo.cy() + 1 + i, 1, 1, color);      // ↙
            rect(g, geo.cx() + 1 + i, geo.cy() - 1 - i, 1, 1, color);      // ↗
            rect(g, geo.cx() - 1 - i, geo.cy() - 1 - i, 1, 1, color);      // ↖
        }
    }

    private void circle(DrawContext g, float cx, float cy, float radius, float thick, int color) {
        int steps = 64;
        for (int i = 0; i < steps; i++) {
            double a0 = (Math.PI * 2 * i) / steps;
            double a1 = (Math.PI * 2 * (i + 1)) / steps;
            for (int t = 0; t < (int) Math.max(1, thick); t++) {
                double r = radius + t;
                float x = cx + (float) (Math.cos(a0) * r * 0.5 + Math.cos(a1) * r * 0.5);
                float y = cy + (float) (Math.sin(a0) * r * 0.5 + Math.sin(a1) * r * 0.5);
                rect(g, x, y, 1.0F, 1.0F, color);
            }
        }
    }

    private void rect(DrawContext g, float x, float y, float w, float h, int color) {
        g.fill((int) x, (int) y, (int) Math.ceil(x + w), (int) Math.ceil(y + h), color);
    }
}
