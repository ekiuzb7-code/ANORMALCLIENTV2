package com.anormal.client.module.impl.render;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.ColorUtils;
import com.anormal.client.util.ProjectionUtil;
import com.anormal.client.util.RenderUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/**
 * Marks flying projectiles with their real flight path.
 *
 * <p><b>What was wrong:</b> the old marker drew a full 3D box through
 * {@code RenderUtils.drawBox3D}, which fills the whole projected screen rectangle and connects
 * corners across the screen. For an arrow a couple of blocks away that produced huge diamonds and
 * long lines lying across the HUD, all stacked on top of each other. Every projectile also had a
 * tracer from the crosshair, so a field of arrows turned into a web of crossing lines.</p>
 *
 * <p><b>Now:</b> each projectile gets a compact screen-space diamond whose size is clamped by
 * distance, the tracer is only an off-screen indicator, the flight path is skipped for projectiles
 * that have already landed (velocity near zero), stops at the first solid block, and labels are
 * nudged apart instead of overlapping.</p>
 */
public class ProjectileESP extends Module {
    public final BooleanSetting arrows = new BooleanSetting("Arrows", "Mark arrows", true);
    public final BooleanSetting spectralArrows = new BooleanSetting("Spectral Arrows", "Mark spectral arrows", true);
    public final BooleanSetting tridents = new BooleanSetting("Tridents", "Mark tridents", true);
    public final BooleanSetting snowballs = new BooleanSetting("Snowballs", "Mark snowballs", false);
    public final BooleanSetting eggs = new BooleanSetting("Eggs", "Mark eggs", false);
    public final BooleanSetting pearls = new BooleanSetting("Ender Pearls", "Mark ender pearls", true);
    public final BooleanSetting fireballs = new BooleanSetting("Fireballs", "Mark fireballs", true);
    public final BooleanSetting skulls = new BooleanSetting("Wither Skulls", "Mark wither skulls", true);
    public final BooleanSetting bobber = new BooleanSetting("Fishing Bobber", "Mark fishing bobbers", false);
    public final BooleanSetting showName = new BooleanSetting("Show Name", "Show projectile name", true);
    public final BooleanSetting showDistance = new BooleanSetting("Show Distance", "Show distance", true);
    public final BooleanSetting box = new BooleanSetting("Marker", "Draw the projectile marker", true);
    public final BooleanSetting tracer = new BooleanSetting("Tracer",
            "Off-screen indicator line (only shown when the projectile is outside the view)", true);
    public final BooleanSetting trajectory = new BooleanSetting("Trajectory", "Draw flight path", true);
    public final NumberSetting range = new NumberSetting("Range", "Max distance", 48.0, 8.0, 128.0, 8.0);
    public final NumberSetting maxDistance = new NumberSetting("Max Distance", "Skip beyond this", 200.0, 50.0, 500.0, 25.0);
    public final ColorSetting color = new ColorSetting("Color", "Marker color", ColorUtils.rgba(255, 85, 85, 255));

    private static final int TRACE_STEPS = 24;
    /** Below this speed a projectile has landed, so a predicted path would be a vertical droop. */
    private static final double MOVING_EPSILON = 0.02;

    public ProjectileESP() {
        super("ProjectileESP", "Marks flying projectiles with paths", Category.RENDER);
        addSetting(arrows);
        addSetting(spectralArrows);
        addSetting(tridents);
        addSetting(snowballs);
        addSetting(eggs);
        addSetting(pearls);
        addSetting(fireballs);
        addSetting(skulls);
        addSetting(bobber);
        addSetting(showName);
        addSetting(showDistance);
        addSetting(box);
        addSetting(tracer);
        addSetting(trajectory);
        addSetting(range);
        addSetting(maxDistance);
        addSetting(color);
    }

    private String kind(Entity e) {
        String path;
        try {
            path = Registries.ENTITY_TYPE.getId(e.getType()).getPath();
        } catch (Throwable ignored) {
            return null;
        }
        if (path.equals("arrow") && arrows.isEnabled()) return "Arrow";
        if (path.equals("spectral_arrow") && spectralArrows.isEnabled()) return "Spectral Arrow";
        if (path.contains("trident") && tridents.isEnabled()) return "Trident";
        if (path.equals("snowball") && snowballs.isEnabled()) return "Snowball";
        if (path.equals("egg") && eggs.isEnabled()) return "Egg";
        if (path.equals("ender_pearl") && pearls.isEnabled()) return "Ender Pearl";
        if (path.contains("fireball") && fireballs.isEnabled()) return "Fireball";
        if (path.contains("wither_skull") && skulls.isEnabled()) return "Wither Skull";
        if (path.contains("fishing_bobber") && bobber.isEnabled()) return "Bobber";
        return null;
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.player == null || mc.world == null) return;
        double maxR = Math.min(range.getValue(), maxDistance.getValue());
        int col = color.getValue();
        int traceCol = (col & 0x00FFFFFF) | 0x70000000;
        int sw = RenderUtils.screenWidth();
        int sh = RenderUtils.screenHeight();
        int cx = sw / 2, cy = sh / 2;

        // Label boxes already drawn this frame; a new one is nudged down instead of overlapping.
        List<int[]> labels = new ArrayList<>();

        for (Entity entity : mc.world.getEntities()) {
            try {
                if (entity == mc.player || !entity.isAlive()) continue;
                String kind = kind(entity);
                if (kind == null) continue;
                double dist = mc.player.distanceTo(entity);
                if (dist > maxR) continue;

                Vec3d pos = ProjectionUtil.lerpPos(entity, tickDelta);
                int[] sc = ProjectionUtil.project(pos.add(0.0, 0.3, 0.0), tickDelta);
                if (sc == null) continue;

                boolean onScreen = sc[0] >= -4 && sc[0] <= sw + 4 && sc[1] >= -4 && sc[1] <= sh + 4;

                if (trajectory.isEnabled()) {
                    Vec3d velocity = entity.getVelocity();
                    if (velocity.lengthSquared() > MOVING_EPSILON) {
                        drawTrajectory(context, pos, velocity, traceCol, tickDelta);
                    }
                }

                if (box.isEnabled()) {
                    drawMarker(context, sc[0], sc[1], markerRadius(dist), col);
                }

                // A tracer is only useful when the projectile is out of view - drawing one for
                // every on-screen projectile was what turned the HUD into a web of lines.
                if (tracer.isEnabled() && !onScreen) {
                    double dx = sc[0] - cx, dy = sc[1] - cy;
                    double len = Math.hypot(dx, dy);
                    if (len > 0.001) {
                        double reach = Math.min(sw, sh) / 2.0 - 26.0;
                        int ex = (int) (cx + dx / len * reach);
                        int ey = (int) (cy + dy / len * reach);
                        RenderUtils.drawLine(context, cx, cy, ex, ey, traceCol, 1.0f);
                    }
                }

                if (!onScreen || mc.textRenderer == null) continue;

                StringBuilder label = new StringBuilder();
                if (showName.isEnabled()) label.append(kind);
                if (showDistance.isEnabled()) {
                    if (label.length() > 0) label.append("  ");
                    label.append((int) dist).append('m');
                }
                if (label.length() == 0) continue;

                int textW = mc.textRenderer.getWidth(label.toString());
                int lx = sc[0] - textW / 2 - 6;
                int top = sc[1] + 6;
                for (int[] prev : labels) {
                    if (lx < prev[0] + prev[2] && lx + textW + 12 > prev[0]
                            && top < prev[1] + prev[3] && top + 11 > prev[1]) {
                        top = prev[1] + prev[3] + 2;
                    }
                }
                RenderUtils.drawChip(context, mc.textRenderer, label.toString(), sc[0], top, col);
                labels.add(new int[]{lx, top, textW + 12, 11});
            } catch (Throwable ignored) {
            }
        }
    }

    /**
     * Marker radius: big enough to be readable far away, clamped so a projectile standing next to
     * the camera cannot cover the screen.
     */
    private int markerRadius(double dist) {
        return (int) Math.max(4, Math.min(15, 96.0 / Math.max(1.0, dist)));
    }

    /** Compact diamond marker + centre dot. No screen-space fill, no perspective sprawl. */
    private void drawMarker(DrawContext context, int x, int y, int r, int col) {
        RenderUtils.drawLine(context, x, y - r, x + r, y, col, 1.0f);
        RenderUtils.drawLine(context, x + r, y, x, y + r, col, 1.0f);
        RenderUtils.drawLine(context, x, y + r, x - r, y, col, 1.0f);
        RenderUtils.drawLine(context, x - r, y, x, y - r, col, 1.0f);
        RenderUtils.fill(context, x - 1, y - 1, x + 2, y + 2, col);
    }

    /**
     * Simulates the real projectile physics a few ticks ahead (gravity + 0.99 drag), stopping at
     * the first non-air block so the path does not shoot through terrain.
     */
    private void drawTrajectory(DrawContext context, Vec3d start, Vec3d initial,
                                int color, float tickDelta) {
        Vec3d velocity = initial;
        Vec3d prev = start;
        for (int i = 0; i < TRACE_STEPS; i++) {
            velocity = new Vec3d(velocity.x * 0.99, (velocity.y - 0.05) * 0.99, velocity.z * 0.99);
            Vec3d next = prev.add(velocity);

            boolean blocked = false;
            try {
                blocked = !mc.world.getBlockState(BlockPos.ofFloored(next)).isAir();
            } catch (Throwable ignored) {
            }
            if (blocked) break;

            int[] a = ProjectionUtil.project(prev, tickDelta);
            int[] b = ProjectionUtil.project(next, tickDelta);
            if (a != null && b != null) {
                // Fade the tail so the near path reads brighter than the predicted end.
                int alpha = 0xC0 - i * 5;
                int shade = ((Math.max(0x30, alpha)) << 24) | (color & 0x00FFFFFF);
                RenderUtils.drawLine(context, a[0], a[1], b[0], b[1], shade, 1.0f);
            }
            prev = next;
        }
    }
}
