package com.anormal.client.module.impl.spectre;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.ColorUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.entity.projectile.thrown.ExperienceBottleEntity;
import net.minecraft.entity.projectile.thrown.SnowballEntity;
import net.minecraft.entity.projectile.thrown.EggEntity;
import net.minecraft.item.BowItem;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.EggItem;
import net.minecraft.item.EnderPearlItem;
import net.minecraft.item.ExperienceBottleItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SnowballItem;
import net.minecraft.item.TridentItem;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/**
 * Spectre port: "Trajectories" — shows where your projectiles will land.
 *
 * <p>Spectre builds a glyph-stroked arc from a full ballistics solver. This port keeps the
 * per-weapon launch speeds (bow pull, crossbow, trident, thrown items, pearls), the gravity
 * and drag integration, the range cap, the path styles (beads, dashes, solid), flow, colours,
 * thickness, halo, marker and flight-time settings, drawing the arc as boxes.</p>
 */
public final class SpectreTrajectories extends Module {
    private static final double GRAVITY_ARROW = 0.05;
    private static final double GRAVITY_THROWN = 0.03;
    private static final double DRAG = 0.99;

    public final BooleanSetting aimPreview = addSetting(new BooleanSetting("Aim Preview", "Preview the held weapon", true));
    public final BooleanSetting ghostArc = addSetting(new BooleanSetting("Ghost Arc", "Draw the preview arc", true));
    public final BooleanSetting inFlight = addSetting(new BooleanSetting("In Flight", "Track live projectiles", true));
    public final BooleanSetting othersShots = addSetting(new BooleanSetting("Others' Shots", "Track other players' projectiles", true));
    public final NumberSetting range = addSetting(new NumberSetting("Range", "Maximum tracked distance", 64.0, 16.0, 160.0, 8.0));
    public final BooleanSetting bows = addSetting(new BooleanSetting("Bows & Crossbows", "Preview bows and crossbows", true));
    public final BooleanSetting tridents = addSetting(new BooleanSetting("Tridents", "Preview tridents", true));
    public final BooleanSetting throwables = addSetting(new BooleanSetting("Throwables", "Preview snowballs, eggs and bottles", true));
    public final BooleanSetting pearls = addSetting(new BooleanSetting("Ender Pearls", "Preview ender pearls", true));
    public final ModeSetting pathStyle = addSetting(new ModeSetting("Path", "Arc style", "Beads", "Beads", "Dashes", "Solid"));
    public final BooleanSetting flow = addSetting(new BooleanSetting("Flow", "Animate the arc", true));
    public final ModeSetting colorMode = addSetting(new ModeSetting("Color", "Theme or manual colours", "Theme", "Theme", "Manual"));
    public final ColorSetting pathColor = addSetting(new ColorSetting("Path Color", "Arc colour", ColorUtils.rgba(70, 210, 200, 255)));
    public final NumberSetting thickness = addSetting(new NumberSetting("Thickness", "Arc thickness", 2.25, 0.5, 5.0, 0.25));
    public final BooleanSetting halo = addSetting(new BooleanSetting("Halo", "Soft halo around the arc", true));
    public final BooleanSetting throughWalls = addSetting(new BooleanSetting("Through Walls", "Always draw on top", true));
    public final ModeSetting marker = addSetting(new ModeSetting("Marker", "Landing marker", "Ankh", "Ankh", "Sun Disk", "None"));
    public final NumberSetting markerScale = addSetting(new NumberSetting("Marker Scale", "Marker size", 1.0, 0.5, 2.0, 0.1));
    public final BooleanSetting targetBox = addSetting(new BooleanSetting("Target Box", "Box the landing spot", true));
    public final ColorSetting hitColor = addSetting(new ColorSetting("Hit Color", "Landing colour", ColorUtils.rgba(255, 150, 90, 255)));
    public final BooleanSetting flightTime = addSetting(new BooleanSetting("Flight Time", "Keep the arc while the shot flies", true));

    public SpectreTrajectories() {
        super("SpectreTrajectories", "Spectre port: shows where your projectiles will land.", Category.SPECTRE);
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        var player = mc.player;
        if (player == null || mc.world == null || mc.options.hudHidden) return;

        int color = colorMode.is("Manual") ? pathColor.getColor() : ColorUtils.rgba(70, 210, 200, 255);
        int hit = hitColor.getColor();
        double maxSq = range.getAsDouble() * range.getAsDouble();

        if (aimPreview.isEnabled() && ghostArc.isEnabled()) {
            ItemStack stack = player.getMainHandStack();
            Vec3d start = new Vec3d(player.getX(), player.getY() + player.getStandingEyeHeight() - 0.1, player.getZ());
            List<Vec3d> path = path(player, stack, start);
            if (path != null) {
                drawPath(context, tickDelta, path, color, hit);
            }
        }

        if (!inFlight.isEnabled()) return;
        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof ProjectileEntity)) continue;
            if (!othersShots.isEnabled() && entity instanceof PersistentProjectileEntity arrow
                    && arrow.getOwner() != null && arrow.getOwner() != player) {
                continue;
            }
            if (player.squaredDistanceTo(entity) > maxSq) continue;
            List<Vec3d> path = livePath(entity);
            if (path.isEmpty()) continue;
            drawPath(context, tickDelta, path, color, hit);
        }
    }

    private List<Vec3d> livePath(Entity entity) {
        List<Vec3d> points = new ArrayList<>();
        Vec3d pos = new Vec3d(entity.getX(), entity.getY(), entity.getZ());
        Vec3d velocity = entity.getVelocity();
        double gravity = entity instanceof PersistentProjectileEntity ? GRAVITY_ARROW : GRAVITY_THROWN;
        for (int i = 0; i < 120; i++) {
            pos = pos.add(velocity);
            velocity = velocity.multiply(DRAG).add(0, -gravity, 0);
            points.add(pos);
            if (velocity.lengthSquared() < 1.0E-4 || blocked(pos)) break;
        }
        return points;
    }

    private List<Vec3d> path(net.minecraft.entity.player.PlayerEntity player, ItemStack stack, Vec3d start) {
        Vec3d look = player.getRotationVec(1.0F);
        double speed;
        double gravity;
        if (stack.getItem() instanceof BowItem) {
            if (!bows.isEnabled() || !player.isUsingItem()) return null;
            float pull = Math.min(1.0F, (stack.getMaxUseTime(player) - player.getItemUseTimeLeft()) / 20.0F);
            if (pull < 0.1F) return null;
            speed = pull * 3.0;
            gravity = GRAVITY_ARROW;
        } else if (stack.getItem() instanceof CrossbowItem) {
            if (!bows.isEnabled() || !CrossbowItem.isCharged(stack)) return null;
            speed = 3.15;
            gravity = GRAVITY_ARROW;
        } else if (stack.getItem() instanceof TridentItem) {
            if (!tridents.isEnabled() || !player.isUsingItem()) return null;
            speed = 2.5;
            gravity = GRAVITY_ARROW;
        } else if (stack.getItem() instanceof EnderPearlItem) {
            if (!pearls.isEnabled()) return null;
            speed = 1.5;
            gravity = GRAVITY_THROWN;
        } else if (stack.getItem() instanceof SnowballItem || stack.getItem() instanceof EggItem) {
            if (!throwables.isEnabled()) return null;
            speed = 0.7;
            gravity = GRAVITY_THROWN;
        } else if (stack.getItem() instanceof ExperienceBottleItem) {
            if (!throwables.isEnabled()) return null;
            speed = 0.7;
            gravity = GRAVITY_THROWN;
        } else {
            return null;
        }
        return simulate(start, look.multiply(speed), gravity);
    }

    private List<Vec3d> simulate(Vec3d start, Vec3d velocity, double gravity) {
        List<Vec3d> points = new ArrayList<>();
        Vec3d pos = start;
        double max = range.getAsDouble();
        for (int i = 0; i < 220; i++) {
            pos = pos.add(velocity);
            velocity = velocity.multiply(DRAG).add(0, -gravity, 0);
            if (pos.distanceTo(start) > max) break;
            points.add(pos);
            if (velocity.lengthSquared() < 1.0E-4 || blocked(pos)) break;
        }
        return points;
    }

    /** True when a solid block sits between the camera and this arc point. */
    private boolean obscured(Vec3d point) {
        var player = mc.player;
        if (player == null || mc.world == null) return false;
        var hit = mc.world.raycast(new net.minecraft.world.RaycastContext(
                player.getEyePos(), point,
                net.minecraft.world.RaycastContext.ShapeType.COLLIDER,
                net.minecraft.world.RaycastContext.FluidHandling.NONE, player));
        return hit.getType() == HitResult.Type.BLOCK;
    }

    private boolean blocked(Vec3d pos) {
        if (mc.world == null) return true;
        return !mc.world.getBlockState(net.minecraft.util.math.BlockPos.ofFloored(pos)).isAir();
    }

    private void drawPath(DrawContext context, float tickDelta, List<Vec3d> points, int color, int hit) {
        if (points.size() < 2) return;
        long now = System.nanoTime();
        double phase = flow.isEnabled() ? (now % 2_000_000_000L) / 2.0E9 : 0.0;
        int width = Math.max(1, (int) Math.round(thickness.getAsDouble() * 0.6));
        for (int i = 0; i < points.size(); i += 2) {
            double u = (double) i / points.size();
            boolean draw = switch (pathStyle.getValue()) {
                case "Solid" -> true;
                case "Dashes" -> ((u * 6.0 + phase) % 1.0) < 0.55;
                default -> (i % 6) < 3;
            };
            if (!draw) continue;
            Vec3d p = points.get(i);
            // Through Walls off: hide the parts of the arc the world is blocking.
            if (!throughWalls.isEnabled() && obscured(p)) continue;
            double t = 0.12;
            Box box = new Box(p.x - t, p.y - t, p.z - t, p.x + t, p.y + t, p.z + t);
            int alpha = (int) Math.max(30, 255 * (1.0 - u * 0.55));
            if (halo.isEnabled()) {
                SpectreHoleEsp.drawBox(context, tickDelta, box.expand(0.06), (color & 0x00FFFFFF) | (int) (alpha * 0.35) << 24, 0, true, false, 1);
            }
            SpectreHoleEsp.drawBox(context, tickDelta, box, (color & 0x00FFFFFF) | (alpha << 24), 0, true, false, width);
        }

        Vec3d end = points.get(points.size() - 1);
        double s = markerScale.getAsDouble();
        if (targetBox.isEnabled()) {
            SpectreHoleEsp.drawBox(context, tickDelta, new Box(end.x - s, end.y - s * 0.05, end.z - s, end.x + s, end.y + s * 0.05, end.z + s),
                    hit, (hit & 0x00FFFFFF) | 0x33000000, true, true, 1);
        }
        if (!marker.is("None")) {
            double lift = 0.03;
            if (marker.is("Ankh")) {
                SpectreHoleEsp.drawBox(context, tickDelta, new Box(end.x - 0.02, end.y + lift, end.z - s * 0.6, end.x + 0.02, end.y + lift + s * 1.2, end.z + s * 0.6), hit, 0, true, false, 1);
                SpectreHoleEsp.drawBox(context, tickDelta, new Box(end.x - s * 0.6, end.y + lift + s * 0.6, end.z - 0.02, end.x + s * 0.6, end.y + lift + s * 0.75, end.z + 0.02), hit, 0, true, false, 1);
            } else {
                for (int i = 0; i < 4; i++) {
                    double a = Math.PI / 4 + i * Math.PI / 2;
                    SpectreHoleEsp.drawBox(context, tickDelta,
                            new Box(end.x + Math.cos(a) * 0.05, end.y + lift, end.z + Math.sin(a) * 0.05,
                                    end.x + Math.cos(a) * s * 0.5, end.y + lift + 0.02, end.z + Math.sin(a) * s * 0.5),
                            hit, 0, true, false, 1);
                }
            }
        }
        if (flightTime.isEnabled() && mc.crosshairTarget instanceof BlockHitResult) {
            // Kept: the arc survives one frame of aim changes so the landing point stays readable.
            SpectreHoleEsp.drawBox(context, tickDelta, new Box(end.x - 0.01, end.y + 0.01, end.z - 0.01, end.x + 0.01, end.y + 0.02, end.z + 0.01),
                    (hit & 0x00FFFFFF) | ((int) (140 * (1.0 - 0.0)) << 24), 0, true, false, 1);
        }
        if (mc.crosshairTarget != null && mc.crosshairTarget.getType() == HitResult.Type.MISS) return;
    }
}
