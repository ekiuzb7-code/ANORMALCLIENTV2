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
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Spectre port: "Jump Circles" — draws a fading ring where a player jumps.
 *
 * <p>Spectre renders the ring as real world geometry with its own render pipeline and a
 * custom logo texture. This port keeps the ring's timing, expansion curve, colours and
 * fade exactly, but projects the circle's points to the screen and draws the ring with
 * the shared 2D line helper, so it works with the Anormal HUD without a custom pipeline.</p>
 */
public final class SpectreJumpCircles extends Module {
    private static final int MAX_RINGS = 24;
    private static final int SEGMENTS = 64;
    private static final double LIFT = 0.015;
    private static final double MAX_DISTANCE_SQR = 16384.0;
    private static final double JUMP_RISE = 0.2;
    private static final int RISE_TICKS = 6;
    private static final int SWEEP_TICKS = 100;
    private static final float FADE_IN_NANOS = 8.0E7F;
    private static final float CORE_WHITE = 0.45F;
    private static final float CORE_ALPHA = 0.95F;
    private static final float HALO_ALPHA = 0.55F;

    private static final float[] COS = new float[SEGMENTS + 1];
    private static final float[] SIN = new float[SEGMENTS + 1];

    static {
        for (int i = 0; i <= SEGMENTS; i++) {
            double angle = (Math.PI * 2.0 * i) / SEGMENTS;
            COS[i] = (float) Math.cos(angle);
            SIN[i] = (float) Math.sin(angle);
        }
    }

    public final ModeSetting colorMode = addSetting(new ModeSetting("Color", "Ring colour source", "Accent", "Accent", "Custom"));
    public final ColorSetting color = addSetting(new ColorSetting("Custom Color", "Ring colour", ColorUtils.rgba(227, 190, 252, 255)));
    public final NumberSetting size = addSetting(new NumberSetting("Size", "Maximum ring radius", 1.2, 0.5, 3.0, 0.1));
    public final NumberSetting duration = addSetting(new NumberSetting("Duration", "Ring lifetime (seconds)", 1.0, 0.3, 3.0, 0.1));
    public final BooleanSetting others = addSetting(new BooleanSetting("Others", "Also draw rings for other players", false));

    private final List<Ring> rings = new ArrayList<>();
    private final Map<Integer, Lift> lifts = new HashMap<>();
    private int tick;

    private double lastX, lastY, lastZ;
    private float lastYaw;
    private boolean lastOnGround = true;
    private int airTicks;
    private boolean judged;

    /** One jump ring in the world. */
    private record Ring(double x, double y, double z, long born) { }

    /** Airborne tracking state for another player (Spectre's Lift). */
    private static final class Lift {
        double x, y, z;
        float yaw;
        int airTicks = RISE_TICKS;
        boolean judged = true;
        int seen;
    }

    public SpectreJumpCircles() {
        super("SpectreJumpCircles", "Spectre port: draws a fading Spectre ring where you jump.", Category.SPECTRE);
    }

    @Override
    public void onEnable() {
        clear();
    }

    @Override
    public void onDisable() {
        clear();
    }

    private void clear() {
        rings.clear();
        lifts.clear();
        judged = true;
        airTicks = 0;
        lastOnGround = true;
    }

    @Override
    public void onTick() {
        var player = mc.player;
        if (player == null || mc.world == null) {
            clear();
            return;
        }
        expireRings();
        trackSelf(player);
        if (others.isEnabled()) trackOthers(player);
    }

    private void trackSelf(net.minecraft.entity.player.PlayerEntity player) {
        boolean onGround = player.isOnGround();
        if (onGround) {
            lastX = player.getX();
            lastY = player.getY();
            lastZ = player.getZ();
            lastYaw = player.getYaw();
            airTicks = -1;
            judged = false;
        } else if (!judged) {
            airTicks++;
            if (player.getY() - lastY > JUMP_RISE) {
                judged = true;
                if (canLeaveRing(player)) spawn(lastX, lastY, lastZ, lastYaw);
            } else if (airTicks >= RISE_TICKS) {
                judged = true;
            }
        }
        lastOnGround = onGround;
    }

    private void trackOthers(net.minecraft.entity.player.PlayerEntity self) {
        tick++;
        for (Entity entity : mc.world.getPlayers()) {
            if (!(entity instanceof net.minecraft.client.network.AbstractClientPlayerEntity player)) continue;
            if (player == self) continue;
            Lift lift = lifts.computeIfAbsent(player.getId(), id -> new Lift());
            lift.seen = tick;
            if (player.isOnGround()) {
                lift.x = player.getX();
                lift.y = player.getY();
                lift.z = player.getZ();
                lift.yaw = player.getYaw();
                lift.airTicks = -1;
                lift.judged = false;
            } else if (!lift.judged) {
                lift.airTicks++;
                if (player.getY() - lift.y > JUMP_RISE) {
                    lift.judged = true;
                    if (canLeaveRing(player)) spawn(lift.x, lift.y, lift.z, lift.yaw);
                } else if (lift.airTicks >= RISE_TICKS) {
                    lift.judged = true;
                }
            }
        }
        if (tick % SWEEP_TICKS == 0) {
            int now = tick;
            lifts.values().removeIf(l -> l.seen != now);
        }
    }

    private static boolean canLeaveRing(net.minecraft.entity.player.PlayerEntity player) {
        return !player.hasVehicle()
                && !player.isGliding()
                && !player.isTouchingWater()
                && !player.isClimbing()
                && player.hurtTime == 0
                && !player.isSpectator()
                && !player.isInvisible();
    }

    private void spawn(double x, double y, double z, float yaw) {
        if (rings.size() >= MAX_RINGS) rings.remove(0);
        rings.add(new Ring(x, y, z, System.nanoTime()));
    }

    private long durationNanos() {
        return (long) (duration.getAsDouble() * 1.0E9);
    }

    private void expireRings() {
        long now = System.nanoTime();
        long life = durationNanos();
        for (Iterator<Ring> it = rings.iterator(); it.hasNext(); ) {
            if (now - it.next().born() >= life) it.remove();
            else break;
        }
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (rings.isEmpty() || mc.player == null || mc.world == null) return;
        long now = System.nanoTime();
        long life = durationNanos();
        int argb = colorMode.is("Custom") ? color.getColor() : ColorUtils.rgba(227, 190, 252, 255);
        int rgb = argb & 0xFFFFFF;
        float radiusMax = (float) size.getAsDouble();
        float core = 0.025F + 0.02F * radiusMax;
        int coreRgb = towardsWhite(rgb, CORE_WHITE);
        Vec3d eye = ProjectionUtil.eye(tickDelta);

        for (Ring ring : rings) {
            long age = now - ring.born();
            if (age >= life) continue;
            float t = Math.min(1.0F, (float) age / life);
            float open = 1.0F - (1.0F - t) * (1.0F - t) * (1.0F - t);
            float fade = (1.0F - t) * (1.0F - t) * Math.min(1.0F, (float) age / FADE_IN_NANOS);
            if (fade <= 0.004F) continue;
            float radius = radiusMax * (0.18F + 0.82F * open);

            int halo = withAlpha(rgb, HALO_ALPHA * fade);
            int line = withAlpha(coreRgb, CORE_ALPHA * fade);
            double cy = ring.y() + LIFT;
            double dx = ring.x() - eye.x;
            double dy = cy - eye.y;
            double dz = ring.z() - eye.z;
            if (dx * dx + dy * dy + dz * dz > MAX_DISTANCE_SQR) continue;

            // four bands: outer halo, core, inner core, inner halo (Spectre's band() ladder)
            ringBand(context, tickDelta, ring, cy, radius + core * 0.35F, halo, line);
            ringBand(context, tickDelta, ring, cy, radius, line, halo);
        }
    }

    /** Draws one circle band by projecting its points and joining them with lines. */
    private void ringBand(DrawContext context, float tickDelta, Ring ring, double y,
                          float radius, int colorA, int colorB) {
        int[] prev = null;
        for (int i = 0; i <= SEGMENTS; i++) {
            double wx = ring.x() + COS[i] * radius;
            double wz = ring.z() + SIN[i] * radius;
            int[] p = ProjectionUtil.project(new Vec3d(wx, y, wz), tickDelta);
            if (prev != null && p != null) {
                RenderUtils.drawLine(context, prev[0], prev[1], p[0], p[1], colorA, 1);
            }
            prev = p;
        }
        // second, slightly tighter pass gives the two-tone core look
        prev = null;
        float inner = radius * 0.88F;
        for (int i = 0; i <= SEGMENTS; i++) {
            double wx = ring.x() + COS[i] * inner;
            double wz = ring.z() + SIN[i] * inner;
            int[] p = ProjectionUtil.project(new Vec3d(wx, y, wz), tickDelta);
            if (prev != null && p != null) {
                RenderUtils.drawLine(context, prev[0], prev[1], p[0], p[1], colorB, 1);
            }
            prev = p;
        }
    }

    private static int withAlpha(int rgb, float alpha) {
        int a = (int) (Math.max(0.0F, Math.min(1.0F, alpha)) * 255.0F + 0.5F);
        return (a << 24) | rgb;
    }

    private static int towardsWhite(int rgb, float amount) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        r += (int) ((255 - r) * amount);
        g += (int) ((255 - g) * amount);
        b += (int) ((255 - b) * amount);
        return (r << 16) | (g << 8) | b;
    }
}
