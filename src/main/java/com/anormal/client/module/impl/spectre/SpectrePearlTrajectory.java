package com.anormal.client.module.impl.spectre;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.ColorUtils;
import com.anormal.client.util.ProjectionUtil;
import com.anormal.client.util.RenderUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.item.Items;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Spectre port: "Pearl Trajectory" — predicts where a held pearl lands and previews the
 * flight of pearls already in the air.
 *
 * <p>The simulation mirrors vanilla pearl physics (spawn at eye height minus 0.1, launch
 * speed 1.5 along the look vector, 0.99 drag and 0.03 gravity per tick) and walks the
 * path with a block raycast per step. Spectre draws the path with gizmos; this port
 * projects the same points to the screen and draws them with the shared 2D line helper.</p>
 */
public final class SpectrePearlTrajectory extends Module {
    private static final int MAX_TRAIL_POINTS = 200;
    private static final int MAX_SIM_STEPS = 160;
    private static final double PIECE_LENGTH = 0.45;
    private static final double FADE_IN_DISTANCE = 1.8;
    private static final int RING_SEGMENTS = 48;

    public final BooleanSetting predict = addSetting(new BooleanSetting("Predict", "Predict the flight of the pearl you are holding", true));
    public final BooleanSetting trail = addSetting(new BooleanSetting("Trail", "Draw the trail of pearls already thrown", true));
    public final BooleanSetting others = addSetting(new BooleanSetting("Others' Pearls", "Also track pearls thrown by other players", false));
    public final ModeSetting landing = addSetting(new ModeSetting("Landing", "Landing marker", "Ring", "Ring", "Box", "None"));
    public final BooleanSetting info = addSetting(new BooleanSetting("Landing Info", "Show distance and flight time at the landing spot", true));
    public final BooleanSetting glow = addSetting(new BooleanSetting("Glow", "Draw a soft glow behind the path", true));
    public final BooleanSetting gradient = addSetting(new BooleanSetting("Gradient", "Blend from Colour to End Colour along the path", true));
    public final ColorSetting color = addSetting(new ColorSetting("Color", "Path colour", ColorUtils.rgba(108, 214, 255, 255)));
    public final ColorSetting endColor = addSetting(new ColorSetting("End Color", "Colour at the end of the path", ColorUtils.rgba(199, 168, 26, 255)));
    public final ColorSetting hitColor = addSetting(new ColorSetting("Hit Color", "Colour used when the pearl hits an entity", ColorUtils.rgba(255, 90, 90, 255)));
    public final NumberSetting lineWidth = addSetting(new NumberSetting("Line Width", "Path line width", 2.0, 0.5, 5.0, 0.5));

    private final Map<Integer, List<Vec3d>> trails = new HashMap<>();
    private Vec3d landingPoint;
    private float landingSeconds;
    private double landingDistance;

    public SpectrePearlTrajectory() {
        super("SpectrePearlTrajectory", "Spectre port: predicts where a held pearl lands and previews thrown pearls.", Category.SPECTRE);
    }

    @Override
    public void onDisable() {
        trails.clear();
        landingPoint = null;
    }

    @Override
    public void onTick() {
        if (mc.world == null || mc.player == null) {
            trails.clear();
            return;
        }
        Set<Integer> active = new HashSet<>();
        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof EnderPearlEntity pearl)) continue;
            if (!tracked(pearl)) continue;
            active.add(pearl.getId());
            List<Vec3d> points = trails.computeIfAbsent(pearl.getId(), id -> new ArrayList<>());
            Vec3d position = new Vec3d(pearl.getX(), pearl.getY(), pearl.getZ());
            if (points.isEmpty() || points.get(points.size() - 1).squaredDistanceTo(position) >= 1.0E-4) {
                points.add(position);
                if (points.size() > MAX_TRAIL_POINTS) points.remove(0);
            }
        }
        trails.keySet().retainAll(active);
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.world == null || mc.player == null) return;
        float width = lineWidth.getValue().floatValue();
        landingPoint = null;

        if (predict.isEnabled() && (mc.player.getMainHandStack().isOf(Items.ENDER_PEARL)
                || mc.player.getOffHandStack().isOf(Items.ENDER_PEARL))) {
            Simulation sim = simulate(tickDelta, true);
            drawPath(context, tickDelta, sim.points, width, true, 1.0F);
            drawLanding(context, tickDelta, sim, width, 1.0F);
        }

        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof EnderPearlEntity pearl)) continue;
            if (!tracked(pearl)) continue;
            Vec3d head = new Vec3d(pearl.getX(), pearl.getY(), pearl.getZ());
            if (trail.isEnabled()) drawTrail(context, tickDelta, trails.get(pearl.getId()), head, width);
            Simulation sim = simulatePearl(pearl);
            drawPath(context, tickDelta, sim.points, width * 0.8F, false, 0.7F);
            drawLanding(context, tickDelta, sim, width, 0.75F);
        }

        if (info.isEnabled() && landingPoint != null) {
            int[] screen = ProjectionUtil.project(landingPoint.add(0.0, 0.35, 0.0), tickDelta);
            if (screen != null) {
                String primary = String.format(Locale.ROOT, "%.1fm", landingDistance);
                String secondary = String.format(Locale.ROOT, "%.1fs", landingSeconds);
                context.drawText(mc.textRenderer, primary, screen[0] + 3, screen[1] - 9, 0xFFFFFFFF, true);
                context.drawText(mc.textRenderer, secondary, screen[0] + 3, screen[1] + 1, 0xFFAAAAAA, true);
            }
        }
    }

    /** One simulated flight: the point list plus where and when it ends. */
    private static final class Simulation {
        final List<Vec3d> points = new ArrayList<>();
        Vec3d landing;
        BlockHitResult blockHit;
        float ticks;
        boolean landed() {
            return landing != null;
        }
    }

    private Simulation simulatePearl(EnderPearlEntity pearl) {
        Simulation sim = new Simulation();
        Vec3d pos = new Vec3d(pearl.getX(), pearl.getY(), pearl.getZ());
        Vec3d vel = new Vec3d(pearl.getVelocity().x, pearl.getVelocity().y, pearl.getVelocity().z);
        sim.points.add(pos);
        step(sim, pos, vel);
        return sim;
    }

    private Simulation simulate(float tickDelta, boolean held) {
        Simulation sim = new Simulation();
        var player = mc.player;
        Vec3d look = player.getRotationVec(tickDelta);
        Vec3d pos = new Vec3d(player.getX(), player.getEyeY() - 0.1, player.getZ());
        Vec3d vel = look.multiply(1.5).add(player.getVelocity().x, player.isOnGround() ? 0.0 : player.getVelocity().y, player.getVelocity().z);
        sim.points.add(pos);
        step(sim, pos, vel);
        return sim;
    }

    private void step(Simulation sim, Vec3d start, Vec3d startVel) {
        Vec3d pos = start;
        Vec3d vel = startVel;
        for (int i = 0; i < MAX_SIM_STEPS; i++) {
            Vec3d next = pos.add(vel);
            BlockHitResult hit = mc.world.raycast(new RaycastContext(pos, next,
                    RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player));
            if (hit.getType() == HitResult.Type.BLOCK) {
                sim.points.add(hit.getPos());
                sim.landing = hit.getPos();
                sim.blockHit = hit;
                sim.ticks = i + 1;
                break;
            }
            sim.points.add(next);
            pos = next;
            vel = new Vec3d(vel.x * 0.99, (vel.y - 0.03) * 0.99, vel.z * 0.99);
            if (pos.y < mc.world.getBottomY() - 8) {
                sim.landing = pos;
                sim.ticks = i + 1;
                break;
            }
        }
        if (sim.landing == null && !sim.points.isEmpty()) {
            sim.landing = sim.points.get(sim.points.size() - 1);
            sim.ticks = sim.points.size();
        }
    }

    private void drawPath(DrawContext context, float tickDelta, List<Vec3d> points, float width,
                          boolean fadeIn, float alphaMul) {
        if (points.size() < 2) return;
        double total = 0.0;
        for (int i = 1; i < points.size(); i++) total += points.get(i - 1).distanceTo(points.get(i));
        if (total <= 1.0E-6) return;

        int start = color.getColor();
        int end = gradient.isEnabled() ? endColor.getColor() : start;
        double travelled = 0.0;
        int[] prev = null;
        for (int i = 1; i < points.size(); i++) {
            Vec3d a = points.get(i - 1);
            Vec3d b = points.get(i);
            double length = a.distanceTo(b);
            int pieces = Math.max(1, (int) Math.ceil(length / PIECE_LENGTH));
            for (int p = 0; p < pieces; p++) {
                double t0 = (double) p / pieces;
                double t1 = (double) (p + 1) / pieces;
                double mid = travelled + length * (t0 + t1) * 0.5;
                float alpha = fadeIn ? alphaMul * smoothstep((float) (mid / FADE_IN_DISTANCE)) : alphaMul;
                if (alpha >= 0.02F) {
                    int lineColor = scaleAlpha(lerpColor(start, end, (float) (mid / total)), alpha);
                    int[] pa = ProjectionUtil.project(a.lerp(b, t0), tickDelta);
                    int[] pb = ProjectionUtil.project(a.lerp(b, t1), tickDelta);
                    if (pa != null && pb != null) {
                        if (glow.isEnabled()) RenderUtils.drawLine(context, pa[0], pa[1], pb[0], pb[1], scaleAlpha(lineColor, 0.2F), width * 3.2F);
                        RenderUtils.drawLine(context, pa[0], pa[1], pb[0], pb[1], lineColor, width);
                    }
                }
            }
            travelled += length;
            prev = null;
        }
    }

    private void drawTrail(DrawContext context, float tickDelta, List<Vec3d> points, Vec3d head, float width) {
        if (points == null || points.isEmpty()) return;
        int lineColor = color.getColor();
        int count = points.size() + 1;
        int[] prev = ProjectionUtil.project(points.get(0), tickDelta);
        for (int i = 1; i < count; i++) {
            Vec3d to = i < points.size() ? points.get(i) : head;
            float alpha = 0.15F + 0.85F * i / (count - 1.0F);
            int[] cur = ProjectionUtil.project(to, tickDelta);
            if (prev != null && cur != null) {
                RenderUtils.drawLine(context, prev[0], prev[1], cur[0], cur[1], scaleAlpha(lineColor, alpha), width);
            }
            prev = cur;
        }
    }

    private void drawLanding(DrawContext context, float tickDelta, Simulation sim, float width, float alphaMul) {
        if (sim.landing == null) return;
        double distance = ProjectionUtil.eye(tickDelta).distanceTo(sim.landing);
        if (landingPoint == null || distance < landingDistance) {
            landingPoint = sim.landing;
            landingDistance = distance;
            landingSeconds = sim.ticks / 20.0F;
        }

        BlockHitResult hit = sim.blockHit;
        String mode = landing.getValue();
        if (hit == null || mode.equals("None")) return;

        int base = gradient.isEnabled() ? endColor.getColor() : color.getColor();
        int markerColor = scaleAlpha(base, alphaMul);
        Direction face = hit.getSide();
        Vec3d normal = new Vec3d(face.getOffsetX(), face.getOffsetY(), face.getOffsetZ());

        if (mode.equals("Box")) {
            BlockPos pos = hit.getBlockPos();
            Box box = new Box(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1).expand(0.002);
            SpectreHoleEsp.drawBox(context, tickDelta, box, markerColor, scaleAlpha(markerColor, 0.22F), true, true, (int) Math.max(1, width));
        } else {
            Vec3d centre = sim.landing.add(normal.multiply(0.02));
            drawRing(context, tickDelta, centre, normal, 0.38F, markerColor, width);
            drawCross(context, tickDelta, centre, normal, 0.09F, markerColor, width);
        }
    }

    private void drawRing(DrawContext context, float tickDelta, Vec3d centre, Vec3d normal,
                          float radius, int lineColor, float width) {
        Vec3d[] basis = planeBasis(normal);
        int[] prev = null;
        for (int i = 0; i <= RING_SEGMENTS; i++) {
            double angle = (Math.PI * 2.0) * i / RING_SEGMENTS;
            Vec3d point = centre.add(basis[0].multiply(Math.cos(angle) * radius)).add(basis[1].multiply(Math.sin(angle) * radius));
            int[] cur = ProjectionUtil.project(point, tickDelta);
            if (prev != null && cur != null) RenderUtils.drawLine(context, prev[0], prev[1], cur[0], cur[1], lineColor, width);
            prev = cur;
        }
    }

    private void drawCross(DrawContext context, float tickDelta, Vec3d centre, Vec3d normal,
                           float half, int lineColor, float width) {
        Vec3d[] basis = planeBasis(normal);
        int[] a = ProjectionUtil.project(centre.add(basis[0].multiply(-half)), tickDelta);
        int[] b = ProjectionUtil.project(centre.add(basis[0].multiply(half)), tickDelta);
        if (a != null && b != null) RenderUtils.drawLine(context, a[0], a[1], b[0], b[1], lineColor, width);
        int[] c = ProjectionUtil.project(centre.add(basis[1].multiply(-half)), tickDelta);
        int[] d = ProjectionUtil.project(centre.add(basis[1].multiply(half)), tickDelta);
        if (c != null && d != null) RenderUtils.drawLine(context, c[0], c[1], d[0], d[1], lineColor, width);
    }

    private static Vec3d[] planeBasis(Vec3d normal) {
        Vec3d u = Math.abs(normal.y) > 0.9
                ? new Vec3d(1.0, 0.0, 0.0)
                : normal.crossProduct(new Vec3d(0.0, 1.0, 0.0)).normalize();
        return new Vec3d[]{u, normal.crossProduct(u).normalize()};
    }

    private boolean tracked(EnderPearlEntity pearl) {
        if (pearl.isRemoved()) return false;
        Entity owner = pearl.getOwner();
        return owner == null || owner == mc.player || others.isEnabled();
    }

    private static int lerpColor(int a, int b, float t) {
        t = Math.max(0.0F, Math.min(1.0F, t));
        int aa = (a >>> 24) & 0xFF, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int ba = (b >>> 24) & 0xFF, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int na = Math.round(aa + (ba - aa) * t);
        int nr = Math.round(ar + (br - ar) * t);
        int ng = Math.round(ag + (bg - ag) * t);
        int nb = Math.round(ab + (bb - ab) * t);
        return (na << 24) | (nr << 16) | (ng << 8) | nb;
    }

    private static int scaleAlpha(int argb, float mul) {
        int alpha = Math.round(((argb >>> 24) & 0xFF) * Math.max(0.0F, Math.min(1.0F, mul)));
        return (alpha << 24) | (argb & 0xFFFFFF);
    }

    private static float smoothstep(float t) {
        t = Math.max(0.0F, Math.min(1.0F, t));
        return t * t * (3.0F - 2.0F * t);
    }
}
