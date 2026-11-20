package com.anormal.client.module.impl.render;

import com.anormal.client.lucid.WorldRender3D;
import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.ColorUtils;
import com.anormal.client.util.ProjectionUtil;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * China Hat - a conical hat worn on the head of every player (client side only).
 *
 * <p>The hat is real 3D geometry: a triangle fan from the rim to the apex plus an optional darker
 * rim band, emitted on a POSITION_COLOR layer with per-vertex colours so the GPU interpolates the
 * gradient. It is drawn in world space from {@code WorldRenderEvents.END_MAIN}, so it follows the
 * head while walking, sneaking and flying instead of sliding around like a screen-space overlay.</p>
 *
 * <p>Because the layer is depth tested, the head hides the far half of the cone (a hat is not an
 * ESP box). "Through Walls" switches to the no-depth layer when a full x-ray look is wanted.</p>
 */
public class ChinaHat extends Module {
    public final ModeSetting colorMode = new ModeSetting("Color Mode", "Solid colour, two-colour gradient or rainbow",
            "Gradient", "Solid", "Gradient", "Rainbow");
    public final ColorSetting color = new ColorSetting("Color", "Hat colour (gradient start)", ColorUtils.rgba(192, 132, 252, 255));
    public final ColorSetting color2 = new ColorSetting("Color 2", "Gradient end colour", ColorUtils.rgba(253, 230, 138, 255));
    public final NumberSetting rainbowSpeed = new NumberSetting("Rainbow Speed", "Hue cycles per second", 0.35, 0.05, 2.0, 0.05);

    public final NumberSetting scale = new NumberSetting("Scale", "Overall hat size", 1.2, 0.4, 3.0, 0.05);
    public final NumberSetting rimRadius = new NumberSetting("Rim Radius", "Hat width in blocks", 0.5, 0.15, 2.0, 0.01);
    public final NumberSetting coneHeight = new NumberSetting("Cone Height", "Hat height in blocks", 0.4, 0.1, 2.0, 0.01);
    public final NumberSetting yOffset = new NumberSetting("Y Offset", "Move the hat up or down", 0.0, -0.6, 1.0, 0.01);
    public final NumberSetting segments = new NumberSetting("Segments", "Cone smoothness (higher = rounder)", 128.0, 12.0, 128.0, 4.0);
    public final BooleanSetting shade = new BooleanSetting("Shade", "Shade the cone so it reads as 3D instead of flat colour", true);
    public final NumberSetting opacity = new NumberSetting("Opacity", "Hat opacity", 225.0, 30.0, 255.0, 5.0);
    public final NumberSetting spin = new NumberSetting("Spin Speed", "Rotation of the hat (visible on gradient / rainbow)", 0.6, -3.0, 3.0, 0.1);

    public final BooleanSetting band = new BooleanSetting("Rim Band", "Darker band around the rim", true);
    public final BooleanSetting firstPerson = new BooleanSetting("First Person", "Show your own hat while in first person view", true);
    public final BooleanSetting throughWalls = new BooleanSetting("Through Walls", "Draw the hat through blocks", false);
    public final BooleanSetting others = new BooleanSetting("Other Players", "Show the hat on other players too", true);
    public final NumberSetting range = new NumberSetting("Range", "Render distance for other players", 64.0, 8.0, 256.0, 8.0);

    public ChinaHat() {
        super("ChinaHat", "Wear a conical China hat with colour, rainbow, scale and more", Category.RENDER);
        addSetting(colorMode);
        addSetting(color);
        addSetting(color2);
        addSetting(rainbowSpeed);
        addSetting(scale);
        addSetting(rimRadius);
        addSetting(coneHeight);
        addSetting(yOffset);
        addSetting(segments);
        addSetting(shade);
        addSetting(opacity);
        addSetting(spin);
        addSetting(band);
        addSetting(firstPerson);
        addSetting(throughWalls);
        addSetting(others);
        addSetting(range);

        color2.visibleIf(() -> colorMode.is("Gradient"));
        rainbowSpeed.visibleIf(() -> colorMode.is("Rainbow"));
        color.visibleIf(() -> !colorMode.is("Rainbow"));

        WorldRenderEvents.END_MAIN.register(this::dispatchWorld);
    }

    private void dispatchWorld(WorldRenderContext wrc) {
        if (!isEnabled() || mc.world == null || mc.player == null || mc.gameRenderer == null) return;
        Camera camera = wrc.gameRenderer().getCamera();
        if (camera == null || !camera.isReady()) return;
        MatrixStack matrices = wrc.matrices();
        VertexConsumerProvider consumers = wrc.consumers();
        if (matrices == null || consumers == null) return;

        float tickDelta = mc.getRenderTickCounter().getTickProgress(true);
        RenderLayer layer = throughWalls.isEnabled() ? WorldRender3D.QUADS_NO_DEPTH : WorldRender3D.QUADS_DEPTH;
        Vec3d cam = camera.getCameraPos();
        double range2 = range.getValue() * range.getValue();

        WorldRender3D.begin(matrices, camera);
        try {
            boolean firstPersonView = mc.options.getPerspective().isFirstPerson();
            for (AbstractClientPlayerEntity player : mc.world.getPlayers()) {
                if (player == null || player.isInvisible() || player.isSpectator()) continue;
                if (player != mc.player) {
                    if (!others.isEnabled()) continue;
                    if (player.squaredDistanceTo(mc.player) > range2) continue;
                } else if (firstPersonView && !firstPerson.isEnabled()) {
                    // Own hat only: hidden in first person, still drawn in third person / F5.
                    continue;
                }
                drawHat(consumers, layer, player, tickDelta);
            }
        } catch (Throwable ignored) {
        } finally {
            WorldRender3D.end(matrices);
        }
    }

    private void drawHat(VertexConsumerProvider consumers, RenderLayer layer,
                         AbstractClientPlayerEntity player, float tickDelta) {
        Vec3d pos = ProjectionUtil.lerpPos(player, tickDelta);
        double s = scale.getValue();
        double rim = rimRadius.getValue() * s;
        double height = coneHeight.getValue() * s;
        // The brim sits ON TOP of the head (getHeight() is the top of the hitbox). It used to be
        // 0.16 below the head top, which put the brim across the player's face.
        double baseY = pos.y + player.getHeight() + yOffset.getValue();

        Vec3d base = new Vec3d(pos.x, baseY, pos.z);
        Vec3d apex = new Vec3d(pos.x, baseY + height, pos.z);

        int segs = Math.max(12, segments.getValue().intValue());
        int alpha = (int) MathHelper.clamp(opacity.getValue().intValue(), 0, 255);
        if (alpha <= 0) return;

        double phase = (System.currentTimeMillis() / 1000.0) * spin.getValue();
        double rainbow = (System.currentTimeMillis() / 1000.0) * rainbowSpeed.getValue();

        Vec3d[] rimPts = new Vec3d[segs + 1];
        int[] rimCols = new int[segs + 1];
        for (int i = 0; i <= segs; i++) {
            double ang = Math.PI * 2.0 * i / segs + phase;
            rimPts[i] = new Vec3d(base.x + Math.cos(ang) * rim, baseY, base.z + Math.sin(ang) * rim);
            rimCols[i] = rimColour(i / (double) segs, ang, rainbow, alpha);
        }

        // One shared apex colour: deriving it per triangle made every segment shade differently,
        // which is what produced the visible facets around the tip.
        int apexRgb = colorMode.is("Rainbow")
                ? MathHelper.hsvToRgb((float) ((rainbow + 0.5) % 1.0), 0.55f, 1.0f)
                : blend(colorMode.is("Gradient") ? blend(color.getColor(), color2.getColor(), 0.5f) : color.getColor(),
                        0xFFFFFF, 0.35f);
        int apexCol = withAlpha(apexRgb, alpha);

        // Side surface: one triangle per segment; the GPU interpolates rim -> apex smoothly.
        for (int i = 0; i < segs; i++) {
            WorldRender3D.triangle(consumers, layer, rimPts[i], rimPts[i + 1], apex,
                    rimCols[i], rimCols[i + 1], apexCol);
        }

        // Rim band: a short, darker wall around the bottom edge.
        if (band.isEnabled()) {
            double bandH = Math.max(0.02, height * 0.06);
            for (int i = 0; i < segs; i++) {
                Vec3d a = rimPts[i];
                Vec3d b = rimPts[i + 1];
                Vec3d c = b.add(0, bandH, 0);
                Vec3d d = a.add(0, bandH, 0);
                int ca = darken(rimCols[i], 0.45f, alpha);
                int cb = darken(rimCols[i + 1], 0.45f, alpha);
                WorldRender3D.quad(consumers, layer, a, b, c, d, ca, cb, cb, ca);
            }
        }
    }

    /** Per-rim-vertex colour for the selected colour mode, with soft directional shading. */
    private int rimColour(double fraction, double angle, double rainbowPhase, int alpha) {
        int rgb;
        if (colorMode.is("Rainbow")) {
            rgb = MathHelper.hsvToRgb((float) ((rainbowPhase + fraction) % 1.0), 0.75f, 1.0f);
        } else if (colorMode.is("Gradient")) {
            float t = (float) (0.5 + 0.5 * Math.cos(angle - Math.PI));
            rgb = blend(color.getColor(), color2.getColor(), t);
        } else {
            rgb = color.getColor() & 0xFFFFFF;
        }
        if (shade.isEnabled()) {
            // Fixed light from the front-left: smooth per-vertex falloff, no flat facets.
            float light = (float) (0.72 + 0.28 * (0.5 + 0.5 * Math.cos(angle + Math.PI * 0.75)));
            rgb = scaleRgb(rgb, light);
        }
        return withAlpha(rgb, alpha);
    }

    private static int scaleRgb(int rgb, float factor) {
        int r = (int) Math.min(255, ((rgb >> 16) & 0xFF) * factor);
        int g = (int) Math.min(255, ((rgb >> 8) & 0xFF) * factor);
        int b = (int) Math.min(255, (rgb & 0xFF) * factor);
        return (r << 16) | (g << 8) | b;
    }

    private static int withAlpha(int color, int alpha) {
        int a = Math.max(0, Math.min(255, alpha));
        return (a << 24) | (color & 0x00FFFFFF);
    }

    private static int lighten(int color, float amount, int alpha) {
        return withAlpha(blend(color, 0xFFFFFF, amount), alpha);
    }

    private static int darken(int color, float amount, int alpha) {
        return withAlpha(blend(color, 0x000000, amount), alpha);
    }

    private static int blend(int a, int b, float f) {
        f = Math.max(0f, Math.min(1f, f));
        int r = (int) (((a >> 16) & 0xFF) * (1 - f) + ((b >> 16) & 0xFF) * f);
        int g = (int) (((a >> 8) & 0xFF) * (1 - f) + ((b >> 8) & 0xFF) * f);
        int bl = (int) ((a & 0xFF) * (1 - f) + (b & 0xFF) * f);
        return (r << 16) | (g << 8) | bl;
    }
}
