package com.anormal.client.module.impl.kuro;

import com.anormal.client.lucid.WorldRender3D;
import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.module.ModuleManager;
import com.anormal.client.util.ProjectionUtil;
import com.anormal.client.util.RenderUtils;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * kuro {@code TrailTracker} port: yangi chunklar izlarini ergashtiradi.
 * Uchayotgan yoki yo'llanma bo'ylab harakatlanayotgan o'yinchi kashf etilgan
 * yerlarda uzun, ingichka bir chiziq hamda yangi qanday chunk qoldiradi.
 * Bu modul New Chunks ma'lumotini oladi (shu modulni yoqing), tegib turgan
 * chunklarni guruhlaydi, uzun ingichka guruhlarni saqlaydi, har biriga
 * chiziq moslashtiradi va sayohatning qaysi tomonga ketganini ko'rsatuvchi
 * o'q bilan chizadi (eng yangi chunklarga qarab; vaqt ma'lum bo'lmasa, sizdan
 * eng uzoq uchi igi tomonga chizadi).
 *
 * <p>Anormaldagi farqlar: kuro'daki Finds ro'yxati util'i yo'q (StashFinder
 * bilan umumiy ro'yxat kuroda bor edi) — shuning uchun topilgan izlar faqat
 * HUD'da ko'rsatiladi. "Baritone: trail head" tugmasi ochiq metod
 * {@link #gotoHead()} bo'ladi.</p>
 */
public class KuroTrailTracker extends Module {
    /** a = orqa (tail), b = old qismi (head) (dunyo koordinatalari). */
    private record Trail(double ax, double az, double bx, double bz, int count, int chunksLong, boolean timed) {}

    private static final int RGB = 0xFFFFC04F;      // kuro hue 0.12 — olovrang; g'ildirak Anormalda yo'q

    private final NumberSetting minChunks = new NumberSetting("Min chunks in trail", "Group size cut-off", 8.0, 4.0, 60.0, 1.0);
    private final NumberSetting minLength = new NumberSetting("Min length (chunks)", "Trail length cut-off", 8.0, 4.0, 80.0, 1.0);
    private final NumberSetting shape = new NumberSetting("Thinness (length / width)", "How line-like", 2.5, 1.5, 8.0, 0.5);
    private final NumberSetting gap = new NumberSetting("Gap allowed (chunks)", "BFS gap", 2.0, 1.0, 4.0, 1.0);
    private final NumberSetting range = new NumberSetting("Max range (chunks)", "Analysis range", 80.0, 20.0, 300.0, 10.0);
    private final NumberSetting thickness = new NumberSetting("Thickness", "Line width", 2.0, 1.0, 3.0, 1.0);
    private final BooleanSetting labels = new BooleanSetting("Labels", true);
    private final BooleanSetting followHeight = new BooleanSetting("Draw at my height", true);
    private final NumberSetting maxTrails = new NumberSetting("Max trails", "How many drawn", 6.0, 1.0, 20.0, 1.0);

    private volatile List<Trail> trails = List.of();
    private int timer = 1000;
    private boolean registered;

    public KuroTrailTracker() {
        super("Trail Tracker", "Follows trails of fresh chunks with an arrow to the trail head", Category.KURO);
        addSetting(minChunks);
        addSetting(minLength);
        addSetting(shape);
        addSetting(gap);
        addSetting(range);
        addSetting(thickness);
        addSetting(labels);
        addSetting(followHeight);
        addSetting(maxTrails);
    }

    @Override
    public void onEnable() {
        timer = 1000;
        if (registered) return;
        registered = true;
        WorldRenderEvents.END_MAIN.register(this::dispatchWorld);
    }

    @Override
    public void onDisable() { trails = List.of(); }

    /** kuro'da "Baritone: trail head" tugmasi: eng yaqin iz boshigacha Baritone yuradi. */
    public void gotoHead() {
        if (mc.player == null) return;
        Trail best = null;
        double bd = Double.MAX_VALUE;
        for (Trail t : trails) {
            double d = Math.hypot(t.bx() - mc.player.getX(), t.bz() - mc.player.getZ());
            if (d < bd) { bd = d; best = t; }
        }
        if (best == null) { say("No trails found yet (is New Chunks on?)"); return; }
        KuroBaritoneControl b = ModuleManager.getModule(KuroBaritoneControl.class);
        if (b != null) b.cmd("goto " + (int) best.bx() + " " + (int) best.bz());
    }

    @Override
    public void onTick() {
        if (mc.world == null || mc.player == null) return;
        if (++timer < 60) return;                       // taxminan har 3 soniyada tahlil qiladi
        timer = 0;
        KuroNewChunks nc = KuroNewChunks.get();
        if (nc == null || !nc.isEnabled()) { trails = List.of(); return; }

        int pcx = mc.player.getBlockPos().getX() >> 4, pcz = mc.player.getBlockPos().getZ() >> 4;
        int rad = range.getValue().intValue();
        Set<Long> pool = new HashSet<>();
        for (long c : new ArrayList<>(nc.chunkSet())) {
            if (Math.abs(ChunkPos.getPackedX(c) - pcx) <= rad && Math.abs(ChunkPos.getPackedZ(c) - pcz) <= rad) pool.add(c);
            if (pool.size() >= 6000) break;
        }
        Map<Long, Long> times = new HashMap<>(nc.chunkTimes());

        List<Trail> out = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        int g = gap.getValue().intValue();
        for (long start : pool) {
            if (!seen.add(start)) continue;
            List<Long> comp = new ArrayList<>();
            ArrayDeque<Long> q = new ArrayDeque<>();
            q.add(start);
            while (!q.isEmpty()) {
                long c = q.poll();
                comp.add(c);
                int cx = ChunkPos.getPackedX(c), cz = ChunkPos.getPackedZ(c);
                for (int dx = -g; dx <= g; dx++)
                    for (int dz = -g; dz <= g; dz++) {
                        if (dx == 0 && dz == 0) continue;
                        long k = ChunkPos.toLong(cx + dx, cz + dz);
                        if (pool.contains(k) && seen.add(k)) q.add(k);
                    }
            }
            if (comp.size() < minChunks.getValue().intValue()) continue;
            Trail t = fit(comp, times);
            if (t != null) out.add(t);
        }
        out.sort((x, y) -> Integer.compare(y.chunksLong(), x.chunksLong()));
        if (out.size() > maxTrails.getValue().intValue()) out = new ArrayList<>(out.subList(0, maxTrails.getValue().intValue()));
        trails = out;
    }

    /** Chunk guruhiga chiziq moslashtiradi; guruh uzun va ingichka bo'lmasa null. */
    private Trail fit(List<Long> comp, Map<Long, Long> times) {
        int n = comp.size();
        double mx = 0, mz = 0;
        for (long c : comp) { mx += ChunkPos.getPackedX(c); mz += ChunkPos.getPackedZ(c); }
        mx /= n; mz /= n;
        double cxx = 0, cxz = 0, czz = 0;
        for (long c : comp) {
            double dx = ChunkPos.getPackedX(c) - mx, dz = ChunkPos.getPackedZ(c) - mz;
            cxx += dx * dx; cxz += dx * dz; czz += dz * dz;
        }
        double ang = 0.5 * Math.atan2(2 * cxz, cxx - czz), ux = Math.cos(ang), uz = Math.sin(ang);
        double tMin = Double.MAX_VALUE, tMax = -Double.MAX_VALUE, sMin = Double.MAX_VALUE, sMax = -Double.MAX_VALUE;
        double[] ts = new double[n];
        for (int i = 0; i < n; i++) {
            long c = comp.get(i);
            double dx = ChunkPos.getPackedX(c) - mx, dz = ChunkPos.getPackedZ(c) - mz;
            double t = dx * ux + dz * uz, s = -dx * uz + dz * ux;
            ts[i] = t;
            tMin = Math.min(tMin, t); tMax = Math.max(tMax, t);
            sMin = Math.min(sMin, s); sMax = Math.max(sMax, s);
        }
        double length = tMax - tMin + 1, width = sMax - sMin + 1;
        if (length < minLength.getValue() || length / Math.max(1.0, width) < shape.getValue()) return null;

        // qaysi uchi bosh? chunklari aniq topilgan yangi nikoh
        double span = tMax - tMin, lowSum = 0, highSum = 0;
        int lowN = 0, highN = 0;
        for (int i = 0; i < n; i++) {
            Long when = times.get(comp.get(i));
            if (when == null) continue;
            if (ts[i] <= tMin + span * 0.3) { lowSum += when; lowN++; }
            else if (ts[i] >= tMax - span * 0.3) { highSum += when; highN++; }
        }
        boolean timed = lowN > 0 && highN > 0 && Math.abs(lowSum / lowN - highSum / highN) > 5000;
        boolean headHigh;
        double ex1x = (mx + tMin * ux) * 16 + 8, ex1z = (mz + tMin * uz) * 16 + 8;      // pastki uchi, dunyo koordinatalari
        double ex2x = (mx + tMax * ux) * 16 + 8, ex2z = (mz + tMax * uz) * 16 + 8;      // yuqori uchi
        if (timed) headHigh = highSum / highN > lowSum / lowN;
        else {
            double d1 = Math.hypot(ex1x - mc.player.getX(), ex1z - mc.player.getZ());
            double d2 = Math.hypot(ex2x - mc.player.getX(), ex2z - mc.player.getZ());
            headHigh = d2 >= d1;
        }
        return headHigh ? new Trail(ex1x, ex1z, ex2x, ex2z, n, (int) Math.round(length), timed)
                        : new Trail(ex2x, ex2z, ex1x, ex1z, n, (int) Math.round(length), timed);
    }

    // ------------------------------------------------------------------ chizish

    private void drawTrailLine(VertexConsumerProvider consumers, double x1, double y, double z1, double x2, double z2, int argb) {
        WorldRender3D.drawLine(consumers, new Vec3d(x1, y, z1), new Vec3d(x2, y, z2), argb);
    }

    private void dispatchWorld(WorldRenderContext wrc) {
        if (!isEnabled() || mc.world == null || mc.player == null) return;
        List<Trail> list = trails;
        if (list.isEmpty()) return;
        Camera camera = wrc.gameRenderer().getCamera();
        if (camera == null || !camera.isReady()) return;
        MatrixStack matrices = wrc.matrices();
        VertexConsumerProvider consumers = wrc.consumers();
        if (matrices == null || consumers == null) return;
        int argb = (0xE0 << 24) | (RGB & 0xFFFFFF);
        double y = followHeight.isEnabled() ? Math.floor(mc.player.getY()) + 0.05 : mc.world.getBottomY() + 0.05;
        WorldRender3D.begin(matrices, camera);
        try {
            for (Trail tr : list) {
                drawTrailLine(consumers, tr.ax(), y, tr.az(), tr.bx(), tr.bz(), argb);
                // iz oldidagi o'q boshi, iz bo'ylab qaragan tomonga
                double dx = tr.bx() - tr.ax(), dz = tr.bz() - tr.az(), len = Math.hypot(dx, dz);
                if (len > 1) {
                    dx /= len; dz /= len;
                    double back = 18, side = 9;
                    drawTrailLine(consumers, tr.bx(), y, tr.bz(), tr.bx() - dx * back - dz * side, tr.bz() - dz * back + dx * side, argb);
                    drawTrailLine(consumers, tr.bx(), y, tr.bz(), tr.bx() - dx * back + dz * side, tr.bz() - dz * back - dx * side, argb);
                }
            }
        } finally {
            WorldRender3D.end(matrices);
        }
    }

    @Override
    public void onRender2D(DrawContext g, float tickDelta) {
        if (!isEnabled() || mc.player == null) return;
        List<Trail> list = trails;
        if (list.isEmpty()) return;
        TextRenderer tr = mc.textRenderer;
        if (tr == null) return;
        ProjectionUtil.beginFrame(tickDelta);
        for (Trail t : list) {
            if (!labels.isEnabled()) break;
            int[] o = ProjectionUtil.project(new Vec3d(t.bx(), (followHeight.isEnabled() ? Math.floor(mc.player.getY()) + 2 : mc.world.getBottomY() + 2), t.bz()), tickDelta);
            if (o == null) continue;
            double d = Math.hypot(t.bx() - mc.player.getX(), t.bz() - mc.player.getZ());
            String text = "TRAIL HEAD  " + (int) d + "m  " + t.chunksLong() + " chunks";
            int w = tr.getWidth(text);
            int cx = o[0], yy = o[1] - 7;
            g.fill(cx - w / 2 - 4, yy - 3, cx + w / 2 + 4, yy + 9, 0xB0101214);
            g.fill(cx - w / 2 - 4, yy - 3, cx - w / 2 - 1, yy + 9, 0xFF000000 | RGB);
            RenderUtils.drawText(g, tr, text, cx - w / 2, yy, 0xFFE8E8F0, false);
        }
    }

    private static void say(String s) {
        if (mc.player != null) mc.player.sendMessage(net.minecraft.text.Text.literal("[Trail Tracker] " + s), false);
    }
}
