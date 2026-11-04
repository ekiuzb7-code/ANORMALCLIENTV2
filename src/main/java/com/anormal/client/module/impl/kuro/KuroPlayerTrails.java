package com.anormal.client.module.impl.kuro;

import com.anormal.client.lucid.WorldRender3D;
import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.module.impl.client.Friends;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * kuro {@code PlayerTrails} port: o'yinchilar ortasida kosmetik iz
 * (faqat klient tomonda). Uslublar: Ribbon (yumshoq lenta), Comet (orqa
 * qisqaruvchi, boshida yorqin nuqta), Helix (yo'l bo'ylab buriladigan ikki
 * poyal), Sparkles, Embers, Hearts, Stars, Snow (yo'ldan to'kilayotgan
 * zarrachalar) va Ring (yurib utirganda yerda to'lqinlar).
 *
 * <p>Anormaldagi farqlar: {Theme} rang rejimi yo'q (UI temasi port qilinmagan)
 * — Solid, Gradient va Rainbow qoldirilgan; ranglar doimiy konstantlar
 * (Setting.Hue yo'q). Kuro'daki WorldGfx'ning billboard-poligonal zarrachalari
 * (yurak, yulduz, yulduzcha shakllari) Anormalda yo'q — zarrachalar oddiy
 * kichik qutilar bilan chiziladi; Ring esa segment-chiziqlar bilan
 * (WorldRender3D.drawLine) yotadi. "Hide behind walls" yo'q —
 * WorldRender3D chiziqlarining chuqurlik testi aniq qilingan.</p>
 */
public class KuroPlayerTrails extends Module {
    private static final int SOLID_RGB = 0x2AA5E0;            // kuro hue 0.58 (ko'k); g'ildirak yo'q
    private static final int GRADIENT2_RGB = 0xE05AB8;        // kuro hue 0.85 (pushti); gradient oxiri
    private static final int FRIEND_RGB = 0x00FF00;           // Anormal Friends rang sozlamasi yo'q — doimiy yashil

    private final ModeSetting style = new ModeSetting("Style", "Trail shape",
            "Ribbon", "Ribbon", "Comet", "Helix", "Sparkles", "Embers", "Hearts", "Stars", "Snow", "Ring");
    private final ModeSetting colorMode = new ModeSetting("Color mode", "Theme removed in this port",
            "Solid", "Solid", "Gradient", "Rainbow");
    private final NumberSetting colorSpeed = new NumberSetting("Rainbow speed", "Rainbow cycling speed", 1.0, 0.2, 4.0, 0.1);
    private final NumberSetting length = new NumberSetting("Length (ticks)", "Trail lifetime", 26.0, 6.0, 100.0, 2.0);
    private final NumberSetting width = new NumberSetting("Width (blocks)", "Trail thickness", 0.32, 0.05, 1.2, 0.01);
    private final NumberSetting opacity = new NumberSetting("Opacity %", "Trail alpha", 85.0, 10.0, 100.0, 5.0);
    private final NumberSetting density = new NumberSetting("Particles per tick", "Particle rate", 3.0, 1.0, 8.0, 1.0);
    private final NumberSetting anchor = new NumberSetting("Height", "0=feet 1=waist 2=head", 0.0, 0.0, 2.0, 1.0);
    private final BooleanSetting glow = new BooleanSetting("Glow", true);
    private final BooleanSetting movingOnly = new BooleanSetting("Only while moving", true);
    private final BooleanSetting self = new BooleanSetting("Yourself", true);
    private final BooleanSetting firstPerson = new BooleanSetting("Show in first person", true);
    private final BooleanSetting others = new BooleanSetting("Other players", false);
    private final BooleanSetting friendColor = new BooleanSetting("Friends use friend colour", true);
    private final NumberSetting range = new NumberSetting("Range", "Distance for other players", 48.0, 8.0, 128.0, 4.0);

    private static final int MAX_PARTICLES = 1500;

    private static final int RIBBON = 0, COMET = 1, HELIX = 2, SPARKLE = 3, EMBER = 4, HEART = 5, STAR = 6, SNOW = 7, RING = 8;

    /** Lenta ning bir yozib qo'yilgan nuqtasi: dunyojoyi va ticki. Track.pts'da eng yangisi oldin. */
    private record Sample(double x, double y, double z, long tick) {}

    private static final class Track {
        final ArrayDeque<Sample> pts = new ArrayDeque<>();
        double lx, ly, lz;
        boolean has;
        long lastTick = -1;
        double ringDist;
        int rgbOverride = -1;         // do'stlik rang, -1 = yo'q
    }

    private static final class Particle {
        double x, y, z, vx, vy, vz;
        int age, life, kind;
        float size, rot, spin, ct;    // ct = rang gradientidagi o'rn (0..1)
        int rgbOverride = -1;
    }

    private final Map<UUID, Track> tracks = new HashMap<>();
    private final List<Particle> particles = new ArrayList<>();
    private final Random rng = new Random();
    private long tick;

    public KuroPlayerTrails() {
        super("Player Trails", "Cosmetic client-side trails behind players", Category.KURO);
        addSetting(style);
        addSetting(colorMode);
        addSetting(colorSpeed);
        addSetting(length);
        addSetting(width);
        addSetting(opacity);
        addSetting(density);
        addSetting(anchor);
        addSetting(glow);
        addSetting(movingOnly);
        addSetting(self);
        addSetting(firstPerson);
        addSetting(others);
        addSetting(friendColor);
        addSetting(range);
        WorldRenderEvents.END_MAIN.register(this::dispatchWorld);
    }

    @Override
    public void onEnable() { clear(); }

    @Override
    public void onDisable() { clear(); }

    private void clear() { tracks.clear(); particles.clear(); }

    private int styleIndex(String s) {
        return switch (s) {
            case "Comet" -> COMET;
            case "Helix" -> HELIX;
            case "Sparkles" -> SPARKLE;
            case "Embers" -> EMBER;
            case "Hearts" -> HEART;
            case "Stars" -> STAR;
            case "Snow" -> SNOW;
            case "Ring" -> RING;
            default -> RIBBON;
        };
    }

    private boolean eligible(AbstractClientPlayerEntity p) {
        if (p.isSpectator()) return false;
        if (p == mc.player) return self.isEnabled();
        if (!others.isEnabled()) return false;
        return p.squaredDistanceTo(mc.player) <= range.getValue() * range.getValue();
    }

    // ------------------------------------------------------------------ simulyatsiya (har tick bir marta)

    @Override
    public void onTick() {
        if (mc.world == null || mc.player == null) { clear(); return; }
        tick++;
        int st = styleIndex(style.getValue());
        int lifeTicks = length.getValue().intValue();
        Set<UUID> seen = new HashSet<>();

        for (AbstractClientPlayerEntity p : mc.world.getPlayers()) {
            if (!eligible(p)) continue;
            UUID id = p.getUuid();
            seen.add(id);
            Track tr = tracks.computeIfAbsent(id, k -> new Track());
            tr.rgbOverride = friendColor.isEnabled() && p != mc.player && Friends.isFriend(p.getName().getString())
                    ? 0xFFFFFF & FRIEND_RGB : -1;

            double x = p.getX(), y = p.getY(), z = p.getZ();
            double step = tr.has ? Math.sqrt((x - tr.lx) * (x - tr.lx) + (y - tr.ly) * (y - tr.ly) + (z - tr.lz) * (z - tr.lz)) : 0.0;
            if (step > 8.0) { tr.pts.clear(); step = 0.0; }          // teleport: xaritaga chizilmaydi
            boolean moved = step >= 0.03;
            tr.lx = x; tr.ly = y; tr.lz = z; tr.has = true;
            double ay = y + anchorOffset(p);

            if (moved || !movingOnly.isEnabled()) {
                if (st <= HELIX) {
                    tr.pts.addFirst(new Sample(x, ay, z, tick));
                    tr.lastTick = tick;
                } else if (st == RING) {
                    tr.ringDist += step;
                    if (tr.ringDist >= 0.9 && p.isOnGround()) { tr.ringDist = 0; spawnRing(tr, x, y + 0.02, z); }
                } else {
                    int n = density.getValue().intValue();
                    for (int i = 0; i < n; i++) spawn(st, tr, x, ay, z, moved);
                }
            }
            while (!tr.pts.isEmpty() && tick - tr.pts.peekLast().tick() > lifeTicks) tr.pts.removeLast();
        }
        tracks.keySet().retainAll(seen);

        for (Iterator<Particle> it = particles.iterator(); it.hasNext(); ) {
            Particle q = it.next();
            if (++q.age > q.life) { it.remove(); continue; }
            q.x += q.vx; q.y += q.vy; q.z += q.vz;
            q.vx *= 0.96; q.vz *= 0.96;
            q.rot += q.spin;
            if (q.kind == SNOW) { q.vx += (rng.nextDouble() - 0.5) * 0.004; q.vz += (rng.nextDouble() - 0.5) * 0.004; }
            if (q.kind == HEART) q.vx += Math.sin((q.age + q.ct * 20.0) * 0.35) * 0.0035;
        }
    }

    private double anchorOffset(AbstractClientPlayerEntity p) {
        return switch (anchor.getValue().intValue()) {
            case 1 -> p.getHeight() * 0.5;
            case 2 -> p.getHeight() * 0.92;
            default -> 0.08;
        };
    }

    private void spawnRing(Track tr, double x, double y, double z) {
        if (particles.size() >= MAX_PARTICLES) return;
        Particle q = new Particle();
        q.kind = RING; q.x = x; q.y = y; q.z = z; q.life = 18; q.size = 0.95f; q.ct = rng.nextFloat();
        q.rgbOverride = tr.rgbOverride;
        particles.add(q);
    }

    private void spawn(int st, Track tr, double x, double y, double z, boolean moved) {
        if (particles.size() >= MAX_PARTICLES) return;
        Particle q = new Particle();
        q.kind = st;
        double spread = st == SNOW ? 0.55 : 0.32;
        q.x = x + (rng.nextDouble() - 0.5) * spread;
        q.y = y + (rng.nextDouble() - 0.35) * (st == SNOW ? 0.9 : 0.5);
        q.z = z + (rng.nextDouble() - 0.5) * spread;
        q.vx = (rng.nextDouble() - 0.5) * 0.02;
        q.vz = (rng.nextDouble() - 0.5) * 0.02;
        q.ct = rng.nextFloat();
        q.rot = rng.nextFloat() * 6.2831f;
        q.rgbOverride = tr.rgbOverride;
        switch (st) {
            case SPARKLE -> { q.life = 14 + rng.nextInt(14); q.size = 0.07f + rng.nextFloat() * 0.09f; q.vy = (float) ((rng.nextDouble() - 0.4) * 0.02); q.spin = (rng.nextFloat() - 0.5f) * 0.3f; }
            case EMBER -> { q.life = 14 + rng.nextInt(18); q.size = 0.035f + rng.nextFloat() * 0.045f; q.vy = (float) (0.018 + rng.nextDouble() * 0.03); q.spin = (rng.nextFloat() - 0.5f) * 0.4f; }
            case HEART -> { q.life = 26 + rng.nextInt(16); q.size = 0.10f + rng.nextFloat() * 0.07f; q.vy = (float) (0.02 + rng.nextDouble() * 0.014); q.spin = 0f; q.rot = (rng.nextFloat() - 0.5f) * 0.5f; }
            case STAR -> { q.life = 20 + rng.nextInt(14); q.size = 0.09f + rng.nextFloat() * 0.08f; q.vy = (float) (-0.004 + rng.nextDouble() * 0.01); q.spin = (rng.nextFloat() - 0.5f) * 0.25f; }
            default -> { q.life = 28 + rng.nextInt(18); q.size = 0.04f + rng.nextFloat() * 0.04f; q.vy = (-0.012f - (float) (rng.nextDouble() * 0.014)); q.spin = 0f; }   // snow
        }
        particles.add(q);
    }

    // ------------------------------------------------------------------ ranglar

    /** Yo'l bo'ylab t joyidagi 0xRRGGBB rang (0 = boshi/yangi, 1 = quyruq/eski). */
    private int colorAt(double t, int override) {
        if (override >= 0) return override;
        t = MathHelper.clamp(t, 0.0, 1.0);
        switch (colorMode.getValue()) {
            case "Gradient": return lerp(SOLID_RGB, GRADIENT2_RGB, t);
            case "Rainbow": {
                double h = (System.currentTimeMillis() / 1000.0 * 0.25 * colorSpeed.getValue() - t * 0.6) % 1.0;
                if (h < 0) h += 1.0;
                return MathHelper.hsvToRgb((float) h, 0.72f, 1f) & 0xFFFFFF;
            }
            default: return SOLID_RGB;
        }
    }

    private static int lerp(int a, int b, double t) {
        int r = (int) Math.round(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) Math.round(((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) Math.round((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | bl;
    }

    private static int argb(int rgb, double alpha) {
        int a = (int) MathHelper.clamp(Math.round(alpha * 255), 0, 255);
        return (a << 24) | (rgb & 0xFFFFFF);
    }

    // ------------------------------------------------------------------ chizish (har freymda, dunyoda)

    private void dispatchWorld(WorldRenderContext wrc) {
        if (!isEnabled() || mc.world == null || mc.player == null || mc.player.getUuid() == null) return;
        Camera camera = wrc.gameRenderer().getCamera();
        if (camera == null || !camera.isReady()) return;
        MatrixStack matrices = wrc.matrices();
        VertexConsumerProvider consumers = wrc.consumers();
        if (matrices == null || consumers == null) return;
        float pt = mc.getRenderTickCounter().getTickProgress(true);
        int st = styleIndex(style.getValue());
        WorldRender3D.begin(matrices, camera);
        try {
            if (st <= HELIX) {
                for (AbstractClientPlayerEntity p : mc.world.getPlayers()) {
                    Track tr = tracks.get(p.getUuid());
                    if (tr == null || tr.pts.isEmpty()) continue;
                    if (p == mc.player && !firstPerson.isEnabled() && mc.getCameraEntity() == mc.player) continue;
                    drawRibbon(p, tr, pt, st, consumers);
                }
            }
            drawParticles(pt, consumers, camera);
        } finally {
            WorldRender3D.end(matrices);
        }
    }

    private void drawRibbon(AbstractClientPlayerEntity p, Track tr, float pt, int st, VertexConsumerProvider consumers) {
        int cap = tr.pts.size();
        double[] xs = new double[cap], ys = new double[cap], zs = new double[cap], ts = new double[cap], stks = new double[cap];
        int n = 0;
        double len = Math.max(1, length.getValue());
        boolean live = tr.lastTick == tick;                       // eng yangi namuna shu tickning joyi: silliq interpolatsiya bilan almashtiriladi
        for (Sample s : tr.pts) {
            double x = s.x(), y = s.y(), z = s.z();
            if (n == 0 && live) {
                Vec3d ip = p.getLerpedPos(pt);
                x = ip.x; y = ip.y + anchorOffset(p); z = ip.z;
            }
            if (n > 0) {
                double dx = x - xs[n - 1], dy = y - ys[n - 1], dz = z - zs[n - 1];
                if (dx * dx + dy * dy + dz * dz < 1.0e-6) continue;  // bir joy turish: nol uzunlik segment
            }
            xs[n] = x; ys[n] = y; zs[n] = z;
            ts[n] = Math.min(1.0, ((tick - s.tick()) + pt) / len);
            stks[n] = s.tick();
            n++;
        }
        if (n < 2) return;
        double w = width.getValue();
        double op = opacity.getValue() / 100.0;
        float lw = (float) Math.max(1.0, w * 6.0);                // chiziq qalinligi piksel: lenta ko'rinishi

        if (st == HELIX) {
            double[] hx = new double[n], hy = new double[n], hz = new double[n];
            for (int strand = 0; strand < 2; strand++) {
                for (int i = 0; i < n; i++) {
                    int a = Math.max(0, i - 1), b = Math.min(n - 1, i + 1);
                    double dx = xs[b] - xs[a], dz = zs[b] - zs[a];
                    double hl = Math.sqrt(dx * dx + dz * dz);
                    double sx = hl > 1e-6 ? -dz / hl : 1.0, sz = hl > 1e-6 ? dx / hl : 0.0;   // harakatning yon yo'nalishi
                    double ph = stks[i] * 0.55 + strand * Math.PI;   // yozib qo'yilgan: burilish orqaga iz qoldiradi
                    double r = w * 0.9 * (1.0 - 0.5 * ts[i]);
                    hx[i] = xs[i] + sx * Math.cos(ph) * r;
                    hy[i] = ys[i] + Math.sin(ph) * r;
                    hz[i] = zs[i] + sz * Math.cos(ph) * r;
                }
                strip(hx, hy, hz, ts, n, w * 0.42, false, op, tr.rgbOverride, lw * 0.6f, consumers);
            }
        } else {
            strip(xs, ys, zs, ts, n, w, st == COMET, op, tr.rgbOverride, lw, consumers);
            if (st == COMET) {
                int rgb = colorAt(0, tr.rgbOverride);
                double hs = w * 0.9;
                drawParticleBox(consumers, xs[0] - hs, ys[0] - hs, zs[0] - hs, hs * 2, rgb, op);
                if (glow.isEnabled())
                    for (int i = 0; i < 3; i++)
                        drawParticleBox(consumers, xs[0] - hs * (1.4 + i * 0.5), ys[0] - hs * (1.4 + i * 0.5), zs[0] - hs * (1.4 + i * 0.5),
                                hs * (2.8 + i), rgb, op * 0.1);
            }
        }
    }

    /** Nuqtalar bo'yicha kamera tomon yo'naltirilgan lenta: oldindan quyruqqa tomon shaffofligi kamayadi. */
    private void strip(double[] xs, double[] ys, double[] zs, double[] ts, int n, double w, boolean taper, double op, int override, float lineWidth, VertexConsumerProvider consumers) {
        double wf = w * (taper ? 10.0 : 6.0);
        for (int i = 0; i < n - 1; i++) {
            double t0 = ts[i], t1 = ts[i + 1];
            double w0 = bandWidth(wf, t0, taper), w1 = bandWidth(wf, t1, taper);
            int c0 = colorAt(t0, override), c1 = colorAt(t1, override);
            double a0 = op * Math.pow(1.0 - t0, 1.3), a1 = op * Math.pow(1.0 - t1, 1.3);
            WorldRender3D.drawLine(consumers, new Vec3d(xs[i], ys[i], zs[i]), new Vec3d(xs[i + 1], ys[i + 1], zs[i + 1]), argb(c0, a0), (float) Math.max(1.0, w0));
        }
    }

    private static double bandWidth(double w, double t, boolean taper) {
        return taper ? w * Math.pow(1.0 - t, 0.7) : w * (1.0 - 0.4 * t);
    }

    /** Zarrachalar: Anormalda billboard shakllar yo'q — har biri kichik quti yoki halqa segment-chiziqlari. */
    private void drawParticles(float pt, VertexConsumerProvider consumers, Camera cam) {
        if (particles.isEmpty()) return;
        Vec3d c = cam.getCameraPos();
        double max2 = (range.getValue() + 16) * (range.getValue() + 16);
        double op = opacity.getValue() / 100.0;
        for (Particle q : particles) {
            double dx = q.x - c.x, dy = q.y - c.y, dz = q.z - c.z;
            if (dx * dx + dy * dy + dz * dz > max2) continue;
            double u = Math.min(1.0, (q.age + pt) / q.life);                       // 0 = tug'ilgan, 1 = yo'qolgan
            double fade = Math.min(1.0, u * 6.0) * (1.0 - u);
            double x = q.x + q.vx * pt, y = q.y + q.vy * pt, z = q.z + q.vz * pt;   // ticklar orasida siljish
            int rgb = colorAt(q.kind == EMBER ? u : q.ct, q.rgbOverride);
            switch (q.kind) {
                case SPARKLE, EMBER, HEART, STAR, SNOW -> {
                    double tw = q.kind == SPARKLE ? 0.55 + 0.45 * Math.sin((q.age + pt) * 1.3 + q.ct * 9.0) : 1.0;
                    double s = q.size * (q.kind == HEART ? (0.7 + 0.5 * Math.min(1.0, u * 4.0)) : (1.0 - 0.3 * u));
                    drawParticleBox(consumers, x - s, y - s, z - s, s * 2, rgb, op * fade * tw);
                    if (glow.isEnabled())
                        drawParticleBox(consumers, x - s * 1.8, y - s * 1.8, z - s * 1.8, s * 3.6, rgb, op * fade * tw * 0.18);
                }
                case RING -> {
                    double e = 1.0 - (1.0 - u) * (1.0 - u);
                    double r = 0.15 + q.size * e;
                    double a = op * (1.0 - u);
                    int ac = argb(lerp(rgb, 0xFFFFFF, 0.45), a * 0.95);
                    int seg = 16;
                    double px = q.x + r, pz = q.z;
                    for (int i2 = 1; i2 <= seg; i2++) {
                        double ang = Math.PI * 2 * i2 / seg;
                        double nx = q.x + Math.cos(ang) * r, nz = q.z + Math.sin(ang) * r;
                        WorldRender3D.drawLine(consumers, new Vec3d(px, q.y, pz), new Vec3d(nx, q.y, nz), ac, 1.5f);
                        px = nx; pz = nz;
                    }
                    if (glow.isEnabled()) {
                        int gc = argb(rgb, a * 0.22);
                        double px2 = q.x + r * 1.15, pz2 = q.z;
                        for (int i2 = 1; i2 <= seg; i2++) {
                            double ang = Math.PI * 2 * i2 / seg;
                            double nx = q.x + Math.cos(ang) * r * 1.15, nz = q.z + Math.sin(ang) * r * 1.15;
                            WorldRender3D.drawLine(consumers, new Vec3d(px2, q.y, pz2), new Vec3d(nx, q.y, nz), gc, 1.0f);
                            px2 = nx; pz2 = nz;
                        }
                    }
                }
                default -> {}
            }
        }
    }

    /** Kuboqsimon zarracha: markaz joylashuv va yon uzunlik (WorldGfx billboard o'rnida). */
    private void drawParticleBox(VertexConsumerProvider consumers, double x0, double y0, double z0, double s, int rgb, double alpha) {
        if (alpha <= 0.008) return;
        WorldRender3D.drawFilledBoxExactAlpha(consumers, new Box(x0, y0, z0, x0 + s, y0 + s, z0 + s), argb(rgb, alpha));
    }
}
