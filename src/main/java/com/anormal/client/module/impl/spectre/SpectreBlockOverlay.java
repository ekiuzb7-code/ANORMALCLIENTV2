package com.anormal.client.module.impl.spectre;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.ColorUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;

/**
 * Spectre port: "Block Overlay" — an animated outline and fill on the block you look at.
 *
 * <p>Spectre draws it with gizmo lines; this port keeps the style / colour / flow / smooth /
 * mining-progress settings and every geometric rule (face-only clamp, see-through lift,
 * pop-in scale, fade in and out) and draws the overlay with the shared box renderer.</p>
 */
public final class SpectreBlockOverlay extends Module {
    private static final float FADE_IN = 0.12F;
    private static final float FADE_OUT = 0.2F;
    private static final double SNAP_DISTANCE = 6.0;

    public final ModeSetting style = addSetting(new ModeSetting("Style", "Outline, fill or both", "Both", "Both", "Outline", "Fill"));
    public final ModeSetting colorMode = addSetting(new ModeSetting("Color Mode", "Gradient, solid or rainbow", "Gradient", "Gradient", "Solid", "Rainbow"));
    public final ColorSetting color = addSetting(new ColorSetting("Color", "Primary colour", ColorUtils.rgba(255, 190, 60, 255)));
    public final ColorSetting secondColor = addSetting(new ColorSetting("Second Color", "Gradient partner", ColorUtils.rgba(60, 220, 255, 255)));
    public final BooleanSetting flow = addSetting(new BooleanSetting("Flow", "Animate the gradient", true));
    public final NumberSetting fillOpacity = addSetting(new NumberSetting("Fill Opacity", "Fill strength (%)", 20.0, 2.0, 60.0, 1.0));
    public final NumberSetting width = addSetting(new NumberSetting("Width", "Outline width", 2.0, 1.0, 5.0, 0.5));
    public final BooleanSetting glow = addSetting(new BooleanSetting("Glow", "Draw a soft outer glow", true));
    public final BooleanSetting smooth = addSetting(new BooleanSetting("Smooth", "Slide between blocks", true));
    public final NumberSetting smoothSpeed = addSetting(new NumberSetting("Smooth Speed", "Sliding speed", 16.0, 4.0, 40.0, 1.0));
    public final BooleanSetting miningProgress = addSetting(new BooleanSetting("Mining Progress", "Show break progress", true));
    public final BooleanSetting faceOnly = addSetting(new BooleanSetting("Face Only", "Only outline the hit face", false));
    public final BooleanSetting seeThrough = addSetting(new BooleanSetting("See Through", "Draw through walls", false));

    private Box goal;
    private Box current;
    private float presence;
    private float progress;
    private long lastFrame;
    private long openedAt;

    public SpectreBlockOverlay() {
        super("SpectreBlockOverlay", "Spectre port: animated outline and fill on the block you look at.", Category.SPECTRE);
    }

    @Override
    public void onEnable() {
        goal = null;
        current = null;
        presence = 0.0F;
        progress = 0.0F;
        lastFrame = 0L;
        openedAt = System.nanoTime();
    }

    @Override
    public void onDisable() {
        goal = null;
        current = null;
        presence = 0.0F;
        progress = 0.0F;
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.player == null || mc.world == null || mc.options.hudHidden) return;
        long now = System.nanoTime();
        float dt = lastFrame == 0L ? 0.0F : Math.min((now - lastFrame) / 1.0E9F, 0.1F);
        lastFrame = now;

        Box target = targetBox();
        if (target != null) {
            goal = target;
            presence = Math.min(1.0F, presence + dt / FADE_IN);
            if (current == null || !smooth.isEnabled()
                    || current.getCenter().distanceTo(goal.getCenter()) > SNAP_DISTANCE) {
                current = goal;
            } else {
                current = lerp(current, goal, 1.0F - (float) Math.exp(-smoothSpeed.getAsDouble() * dt));
            }
        } else {
            presence = Math.max(0.0F, presence - dt / FADE_OUT);
            if (presence <= 0.0F) {
                goal = null;
                current = null;
                progress = 0.0F;
                return;
            }
        }
        if (current == null) return;

        float want = 0.0F;
        if (miningProgress.isEnabled() && mc.interactionManager != null && mc.interactionManager.isBreakingBlock()) {
            want = 1.0F;
        }
        progress += (want - progress) * (1.0F - (float) Math.exp(-18.0F * dt));

        float fade = presence * presence * (3.0F - 2.0F * presence);
        double pop = 0.94 + 0.06 * fade;
        Box box = shrink(current, pop);
        double time = (now - openedAt) / 1.0E9;
        // Flow animates the gradient; with it off the two colours stay at a fixed blend.
        double flowTime = flow.isEnabled() ? time : 0.0;
        if (!seeThrough.isEnabled() && target != null && occluded(box)) return;

        boolean lines = !style.is("Fill");
        boolean fill = !style.is("Outline");
        int primary = color.getColor();
        int secondary = secondColor.getColor();

        if (fill) {
            int alpha = (int) Math.round(fillOpacity.getAsDouble() / 100.0 * fade * 255.0);
            int rgb = colorMode.is("Rainbow") ? rainbow(time, 0.0) : (colorMode.is("Solid") ? primary : blend(primary, secondary, 0.5 + 0.5 * Math.cos(flowTime * 2.0)));
            SpectreHoleEsp.drawBox(context, tickDelta, box, 0, (rgb & 0x00FFFFFF) | (Math.max(0, Math.min(255, alpha)) << 24), false, true, 1);
        }
        if (progress > 0.01F) {
            double top = box.minY + (box.maxY - box.minY) * Math.min(1.0, progress);
            SpectreHoleEsp.drawBox(context, tickDelta,
                    new Box(box.minX, box.minY, box.minZ, box.maxX, top, box.maxZ),
                    0, (primary & 0x00FFFFFF) | 0x55000000, false, true, 1);
        }
        if (lines) {
            int lineWidth = Math.max(1, (int) Math.round(width.getAsDouble()));
            int outline = colorMode.is("Solid") ? primary : colorMode.is("Rainbow") ? rainbow(time, 0.0) : blend(primary, secondary, 0.5 + 0.5 * Math.cos(flowTime * 2.0));
            int withAlpha = (outline & 0x00FFFFFF) | ((int) (255 * fade) << 24);
            if (glow.isEnabled()) {
                SpectreHoleEsp.drawBox(context, tickDelta, box.expand(0.02), (outline & 0x00FFFFFF) | (int) (90 * fade) << 24, 0, true, false, 1);
                SpectreHoleEsp.drawBox(context, tickDelta, box.expand(0.05), (outline & 0x00FFFFFF) | (int) (40 * fade) << 24, 0, true, false, 1);
            }
            SpectreHoleEsp.drawBox(context, tickDelta, box, withAlpha, 0, true, false, lineWidth);
            if (faceOnly.isEnabled() && mc.crosshairTarget instanceof BlockHitResult hit) {
                SpectreHoleEsp.drawBox(context, tickDelta, faceBox(box, hit.getSide()), withAlpha, 0, true, false, lineWidth);
            }
        }
    }

    /** True when a block sits between the camera and the overlay box, i.e. it is hidden. */
    private boolean occluded(Box box) {
        if (mc.player == null || mc.world == null) return false;
        var eye = mc.player.getEyePos();
        var center = box.getCenter();
        var hit = mc.world.raycast(new net.minecraft.world.RaycastContext(eye, center,
                net.minecraft.world.RaycastContext.ShapeType.OUTLINE,
                net.minecraft.world.RaycastContext.FluidHandling.NONE, mc.player));
        if (hit.getType() != HitResult.Type.BLOCK) return false;
        BlockHitResult blockHit = (BlockHitResult) hit;
        return mc.crosshairTarget instanceof BlockHitResult looked
                && !blockHit.getBlockPos().equals(looked.getBlockPos());
    }

    private Box targetBox() {
        if (!(mc.crosshairTarget instanceof BlockHitResult hit) || mc.crosshairTarget.getType() != HitResult.Type.BLOCK) return null;
        if (mc.world.getBlockState(hit.getBlockPos()).isAir()) return null;
        return new Box(hit.getBlockPos());
    }

    private static Box faceBox(Box box, Direction side) {
        double e = 0.004;
        return switch (side) {
            case EAST -> new Box(box.maxX - e, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
            case WEST -> new Box(box.minX, box.minY, box.minZ, box.minX + e, box.maxY, box.maxZ);
            case UP -> new Box(box.minX, box.maxY - e, box.minZ, box.maxX, box.maxY, box.maxZ);
            case DOWN -> new Box(box.minX, box.minY, box.minZ, box.maxX, box.minY + e, box.maxZ);
            case SOUTH -> new Box(box.minX, box.minY, box.maxZ - e, box.maxX, box.maxY, box.maxZ);
            case NORTH -> new Box(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.minZ + e);
        };
    }

    private static Box shrink(Box box, double factor) {
        double cx = (box.minX + box.maxX) * 0.5;
        double cy = (box.minY + box.maxY) * 0.5;
        double cz = (box.minZ + box.maxZ) * 0.5;
        double hx = (box.maxX - box.minX) * 0.5 * factor;
        double hy = (box.maxY - box.minY) * 0.5 * factor;
        double hz = (box.maxZ - box.minZ) * 0.5 * factor;
        return new Box(cx - hx, cy - hy, cz - hz, cx + hx, cy + hy, cz + hz);
    }

    private static Box lerp(Box from, Box to, float k) {
        return new Box(
                from.minX + (to.minX - from.minX) * k, from.minY + (to.minY - from.minY) * k,
                from.minZ + (to.minZ - from.minZ) * k, from.maxX + (to.maxX - from.maxX) * k,
                from.maxY + (to.maxY - from.maxY) * k, from.maxZ + (to.maxZ - from.maxZ) * k);
    }

    private static int rainbow(double time, double offset) {
        float hue = (float) ((time * 0.35 + offset) % 1.0);
        return 0xFF000000 | java.awt.Color.HSBtoRGB(hue, 0.65F, 1.0F);
    }

    private static int blend(int a, int b, double k) {
        k = Math.max(0.0, Math.min(1.0, k));
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int r = (int) (ar + (br - ar) * k);
        int g = (int) (ag + (bg - ag) * k);
        int bl = (int) (ab + (bb - ab) * k);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }
}
