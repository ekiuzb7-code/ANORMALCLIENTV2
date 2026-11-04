package com.anormal.client.module.impl.kuro;

import com.anormal.client.lucid.WorldRender3D;
import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.module.impl.client.Friends;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.util.ColorUtils;
import com.anormal.client.setting.NumberSetting;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * kuro {@code JumpBubbles} port: o'yinchi sakraganda uchib turgan joyda
 * yerga tarqaladigan, yog'ib ketayotgan halqalar (faqat klient tomonda
 * ko'rinadi). Sakrash aniqligi: avvalgi tickda yerda bo'lgan o'yinchi shu
 * tickda havoda va 0.2 blokdan ko'p ko'tarilgan (oddiy sakrash birinchi tickda
 * 0.42 ko'tarilib ketadi). Halqalar chuqurlik testini o'tib ko'rinadi.
 *
 * <p>Anormaldagi farqlar: kuro'daki {@code WorldGfx.ring} (shaderli halqa,
 * ichi to'ldirilgan emas) bu yerdaWorldRender3D segment-chiziqlari bilan
 * chiziladi; {@code Follow theme} rejimi Anormalda UI temasi yo'qligi uchun
 * olib tashlandi — rang ColorSetting bilan beriladi (kuro'daki Setting.Hue
 * uslubining o'rini); do'stlar rangida ham doimiy yashil (Anormal Friends
 * rang sozlamasi bu kuro portida yo'q).</p>
 */
public class KuroJumpBubbles extends Module {
    private static final int MAX_BUBBLES = 200;
    private static final int RING_SEGS = 28;

    private final ColorSetting color = new ColorSetting("Color", "Halqalar rangi", ColorUtils.rgba(224, 90, 184, 255));   // kuro hue 0.83
    private final NumberSetting rings = new NumberSetting("Rings", "Tarqaladigan halqalar soni", 2.0, 1.0, 3.0, 1.0);
    private final NumberSetting size = new NumberSetting("Size (blocks)", "Maksimal radius", 1.6, 0.5, 4.0, 0.1);
    private final NumberSetting life = new NumberSetting("Lifetime (ticks)", "Umur (tick)", 20.0, 8.0, 60.0, 2.0);
    private final NumberSetting range = new NumberSetting("Range", "Masofa", 48.0, 8.0, 128.0, 4.0);
    private final BooleanSetting bubble = new BooleanSetting("Filled bubble", "Ichki yorug' halqa ham ko'rsatadi", true);
    private final BooleanSetting glow = new BooleanSetting("Glow", "Yorug'lik ta'siri", true);
    private final BooleanSetting self = new BooleanSetting("Include yourself", "O'zini ham", false);
    private final BooleanSetting friends = new BooleanSetting("Friends", "Do'stlari ham sakraganda halqa", true);

    /** Sakralgan stakan nuqtasi. */
    private static final class Bubble {
        final double x, y, z;
        final int rgb;
        int age;
        Bubble(double x, double y, double z, int rgb) { this.x = x; this.y = y; this.z = z; this.rgb = rgb; }
    }

    private final List<Bubble> bubbles = new ArrayList<>();
    private final Map<UUID, double[]> last = new HashMap<>();   // {y, yerda edi (1/0)}

    public KuroJumpBubbles() {
        super("Jump Bubbles", "Glowing rings spread on the ground where players jump (kuro port)", Category.KURO);
        addSetting(color);
        addSetting(rings);
        addSetting(size);
        addSetting(life);
        addSetting(range);
        addSetting(bubble);
        addSetting(glow);
        addSetting(self);
        addSetting(friends);
        WorldRenderEvents.END_MAIN.register(this::dispatchWorld);
    }

    @Override
    public void onEnable() { bubbles.clear(); last.clear(); }

    @Override
    public void onDisable() { bubbles.clear(); last.clear(); }

    @Override
    public void onTick() {
        if (mc.world == null || mc.player == null) { bubbles.clear(); last.clear(); return; }
        int lifetime = life.getValue().intValue();
        Set<UUID> seen = new HashSet<>();
        double max2 = range.getValue() * range.getValue();

        for (AbstractClientPlayerEntity p : mc.world.getPlayers()) {
            UUID id = p.getUuid();
            seen.add(id);
            double[] prev = last.get(id);
            boolean ground = p.isOnGround();
            if (prev != null && prev[1] == 1.0 && !ground && p.getY() - prev[0] > 0.2
                    && !p.isSpectator() && !p.hasVehicle() && !p.isGliding() && !p.isSwimming()
                    && (p != mc.player || self.isEnabled())
                    && (friends.isEnabled() || !(p != mc.player && Friends.isFriend(p.getName().getString())))
                    && p.squaredDistanceTo(mc.player) <= max2
                    && bubbles.size() < MAX_BUBBLES) {
                int rgb = p != mc.player && Friends.isFriend(p.getName().getString())
                        ? (0x00FF00 & 0xFFFFFF) : (color.getValue() & 0xFFFFFF);
                bubbles.add(new Bubble(p.getX(), prev[0], p.getZ(), rgb));
            }
            if (prev == null) { prev = new double[2]; last.put(id, prev); }
            prev[0] = p.getY();
            prev[1] = ground ? 1.0 : 0.0;
        }
        last.keySet().retainAll(seen);
        for (Iterator<Bubble> it = bubbles.iterator(); it.hasNext(); ) if (++it.next().age > lifetime) it.remove();
    }

    private void dispatchWorld(WorldRenderContext wrc) {
        if (!isEnabled() || bubbles.isEmpty() || mc.world == null) return;
        Camera camera = wrc.gameRenderer().getCamera();
        if (camera == null || !camera.isReady()) return;
        MatrixStack matrices = wrc.matrices();
        VertexConsumerProvider consumers = wrc.consumers();
        if (matrices == null || consumers == null) return;
        float pt = mc.getRenderTickCounter().getTickProgress(true);
        int n = rings.getValue().intValue();
        double maxR = size.getValue();
        Vec3d cam = camera.getCameraPos();
        double max2 = (range.getValue() + 8) * (range.getValue() + 8);

        WorldRender3D.begin(matrices, camera);
        try {
            for (Bubble b : bubbles) {
                double dx = b.x - cam.x, dy = b.y - cam.y, dz = b.z - cam.z;
                if (dx * dx + dy * dy + dz * dz > max2) continue;
                double t = Math.min(1.0, (b.age + pt) / life.getValue());    // 0 = hozir sakradi, 1 = yo'qoldi
                double ease = 1.0 - (1.0 - t) * (1.0 - t);                    // avval tez, so'ng sekinlashadi
                double fade = 1.0 - t;
                double y = b.y + 0.02;
                if (bubble.isEnabled()) drawRing(consumers, b.x, y, b.z, 0.25 + maxR * 0.75 * ease, 0.05, argb(b.rgb, 0.16 * fade));
                for (int i = 0; i < n; i++) {
                    double delay = i * 0.22;
                    double ti = Math.min(1.0, Math.max(0.0, (t - delay) / (1.0 - delay)));
                    if (ti <= 0.0) continue;
                    double r = 0.2 + maxR * (1.0 - (1.0 - ti) * (1.0 - ti)) * (1.0 - 0.18 * i);
                    double w = 0.045 + 0.02 * (1.0 - ti);
                    double a = (1.0 - ti) * (1.0 - 0.15 * i);
                    if (glow.isEnabled()) drawRing(consumers, b.x, y, b.z, r + w * 3.2, w, argb(b.rgb, 0.20 * a));
                    drawRing(consumers, b.x, y, b.z, r, w, argb(blend(b.rgb, 0xFFFFFF, 0.55f), 0.95 * a));
                }
            }
        } finally {
            WorldRender3D.end(matrices);
        }
    }

    /** Halqani segment-chiziqlar sifatida chizadi (WorldGfx.ring o'rnida). */
    private void drawRing(VertexConsumerProvider consumers, double x, double y, double z, double r, double lineWidth, int argb) {
        double px = x + r, pz = z;
        for (int i = 1; i <= RING_SEGS; i++) {
            double ang = Math.PI * 2 * i / RING_SEGS;
            double nx = x + Math.cos(ang) * r, nz = z + Math.sin(ang) * r;
            WorldRender3D.drawLine(consumers, new Vec3d(px, y, pz), new Vec3d(nx, y, nz), argb, (float) Math.max(1.0, lineWidth * 10.0));
            px = nx; pz = nz;
        }
    }

    private static int argb(int rgb, double alpha) {
        int a = (int) MathHelper.clamp(Math.round(alpha * 255), 0, 255);
        return (a << 24) | (rgb & 0xFFFFFF);
    }

    private static int blend(int a, int b, float f) {
        int r = (int) (((a >> 16) & 0xFF) * (1 - f) + ((b >> 16) & 0xFF) * f);
        int g = (int) (((a >> 8) & 0xFF) * (1 - f) + ((b >> 8) & 0xFF) * f);
        int bl = (int) ((a & 0xFF) * (1 - f) + (b & 0xFF) * f);
        return (r << 16) | (g << 8) | bl;
    }
}
