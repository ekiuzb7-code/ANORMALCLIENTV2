package com.anormal.client.module.impl.kuro;

import com.anormal.client.lucid.WorldRender3D;
import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.ProjectionUtil;
import com.anormal.client.util.RenderUtils;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * kuro {@code GeodeEsp} port: ametist geodlarini topadi va hammasini binafsha
 * rang bilan chizadi. Geode degani — bir-biriga (2 blokka qadar) tegib turgan
 * ametist blok / unayotgan blok / kristal ko'chatlar uyumi. Kamida
 * {Min grown clusters} ta to'liq o'sgan kristal uyumi bor geod largina
 * ko'rsatiladi: to'liq o'sgan kristallar ko'p bo'lsa, o'yinchi u yerda
 * uzoq vaqt ishlagan (eski geod) degan ma'no, tabiiy yangi geod emas.
 *
 * <p>Anormaldagi farqlar: {Color} g'ildiragi yo'q — doimiy binafsha;
 * {Glow} yo'q (WorldRender3D chiziqlari allaqachon yorqin); kuro'dagi Finds
 * ro'yxatiga ham e'lon qilinadi, Anormalda faqat dunyoda chiziladi.</p>
 */
public class KuroGeodeEsp extends Module {
    private static final int RGB = 0xFFB24CEF;                // kuro hue 0.76 (binafsha); g'ildirak yo'q
    private static final int MAX_BLOCKS = 40000;
    private static final int CHUNKS_PER_TICK = 6;

    /** Bitta topilgan geod: yumshoq chegaralangan quti va uning o'sgan kristallari. */
    private record Geode(int x1, int y1, int z1, int x2, int y2, int z2, int grown, int buds, int budding, List<BlockPos> clusters) {
        double cx() { return (x1 + x2 + 1) / 2.0; }
        double cy() { return (y1 + y2 + 1) / 2.0; }
        double cz() { return (z1 + z2 + 1) / 2.0; }
    }

    private final NumberSetting range = new NumberSetting("Range (chunks)", "Chunks scanned around the player", 6.0, 1.0, 10.0, 1.0);
    private final NumberSetting minGrown = new NumberSetting("Min grown clusters", "Fully grown clusters required", 8.0, 1.0, 60.0, 1.0);
    private final BooleanSetting wholeBox = new BooleanSetting("Box whole geode", true);
    private final NumberSetting padding = new NumberSetting("Shell padding", "Grow the outer box", 2.0, 0.0, 4.0, 1.0);
    private final NumberSetting thickness = new NumberSetting("Thickness", "Line width", 2.0, 1.0, 3.0, 1.0);
    private final BooleanSetting fill = new BooleanSetting("Fill", true);
    private final NumberSetting fillOpacity = new NumberSetting("Fill opacity", "Fill alpha", 40.0, 10.0, 200.0, 10.0);
    private final BooleanSetting crystals = new BooleanSetting("Outline grown crystals", true);
    private final BooleanSetting tracers = new BooleanSetting("Tracers", true);
    private final BooleanSetting labels = new BooleanSetting("Labels", true);
    private final NumberSetting maxGeodes = new NumberSetting("Max geodes", "Geodes drawn at once", 10.0, 1.0, 40.0, 1.0);

    private final List<long[]> queue = new ArrayList<>();
    private int queuePos, timer = 1000;
    private Set<Long> building = new HashSet<>();
    private final List<BlockPos> buildClusters = new ArrayList<>();
    private final List<BlockPos> buildBuds = new ArrayList<>();
    private final List<BlockPos> buildBudding = new ArrayList<>();
    private volatile List<Geode> geodes = List.of();

    public KuroGeodeEsp() {
        super("Geode ESP", "Finds and outlines mined-out (old) amethyst geodes", Category.KURO);
        addSetting(range);
        addSetting(minGrown);
        addSetting(wholeBox);
        addSetting(padding);
        addSetting(thickness);
        addSetting(fill);
        addSetting(fillOpacity);
        addSetting(crystals);
        addSetting(tracers);
        addSetting(labels);
        addSetting(maxGeodes);
    }

    @Override
    public void onEnable() {
        queue.clear();
        queuePos = 0;
        timer = 1000;
    }

    @Override
    public void onDisable() {
        geodes = List.of();
        queue.clear();
        queuePos = 0;
    }

    private static boolean isGeodeBlock(Block b) {
        return b == Blocks.AMETHYST_BLOCK || b == Blocks.BUDDING_AMETHYST || b == Blocks.AMETHYST_CLUSTER
                || b == Blocks.LARGE_AMETHYST_BUD || b == Blocks.MEDIUM_AMETHYST_BUD || b == Blocks.SMALL_AMETHYST_BUD;
    }

    @Override
    public void onTick() {
        if (mc.world == null || mc.player == null) return;
        if (queuePos >= queue.size()) {
            if (++timer < 40) return;                        // yangi skan har ~2 s, tick'larga bo'lib
            timer = 0;
            beginPass();
            if (queue.isEmpty()) return;
        }
        for (int n = 0; n < CHUNKS_PER_TICK && queuePos < queue.size(); n++) {
            long[] c = queue.get(queuePos++);
            WorldChunk chunk;
            try { chunk = mc.world.getChunk((int) c[0], (int) c[1]); } catch (RuntimeException e) { continue; }
            if (chunk != null && !chunk.isEmpty()) scanChunk(chunk);
            if (building.size() >= MAX_BLOCKS) { queuePos = queue.size(); break; }
        }
        if (queuePos >= queue.size()) finishPass();
    }

    private void beginPass() {
        queue.clear(); queuePos = 0;
        building = new HashSet<>();
        buildClusters.clear(); buildBuds.clear(); buildBudding.clear();
        ChunkPos cp = new ChunkPos(mc.player.getBlockPos());
        int rad = range.getValue().intValue();
        for (int dx = -rad; dx <= rad; dx++)
            for (int dz = -rad; dz <= rad; dz++) queue.add(new long[]{cp.x + dx, cp.z + dz});
        queue.sort((a, b) -> Long.compare(
                (a[0] - cp.x) * (a[0] - cp.x) + (a[1] - cp.z) * (a[1] - cp.z),
                (b[0] - cp.x) * (b[0] - cp.x) + (b[1] - cp.z) * (b[1] - cp.z)));
    }

    private void scanChunk(WorldChunk chunk) {
        ChunkSection[] sections = chunk.getSectionArray();
        int baseX = chunk.getPos().getStartX();
        int baseZ = chunk.getPos().getStartZ();
        for (int i = 0; i < sections.length; i++) {
            ChunkSection sec = sections[i];
            if (sec == null || sec.isEmpty()) continue;
            if (!sec.hasAny(state -> isGeodeBlock(state.getBlock()))) continue;
            int baseY = (chunk.getBottomY() >> 4) + i << 4;
            for (int x = 0; x < 16; x++)
                for (int y = 0; y < 16; y++)
                    for (int z = 0; z < 16; z++) {
                        Block b = sec.getBlockState(x, y, z).getBlock();
                        if (!isGeodeBlock(b)) continue;
                        BlockPos p = new BlockPos(baseX + x, baseY + y, baseZ + z);
                        building.add(p.asLong());
                        if (b == Blocks.AMETHYST_CLUSTER) buildClusters.add(p);
                        else if (b == Blocks.BUDDING_AMETHYST) buildBudding.add(p);
                        else if (b != Blocks.AMETHYST_BLOCK) buildBuds.add(p);
                    }
        }
    }

    /** Tegishli (2 blokka qadar) geod bloklarini birlashtiradi, malakali geodlargina qoldiradi. */
    private void finishPass() {
        Set<Long> all = building;
        Set<Long> grown = new HashSet<>(), budding = new HashSet<>(), buds = new HashSet<>();
        for (BlockPos p : buildClusters) grown.add(p.asLong());
        for (BlockPos p : buildBudding) budding.add(p.asLong());
        for (BlockPos p : buildBuds) buds.add(p.asLong());

        Set<Long> seen = new HashSet<>();
        List<Geode> out = new ArrayList<>();
        int pad = padding.getValue().intValue();
        for (long start : all) {
            if (!seen.add(start)) continue;
            ArrayDeque<BlockPos> q = new ArrayDeque<>();
            BlockPos s0 = BlockPos.fromLong(start);
            q.add(s0);
            int x1 = s0.getX(), y1 = s0.getY(), z1 = s0.getZ(), x2 = x1, y2 = y1, z2 = z1, cnt = 0, g = 0, bu = 0, bd = 0;
            List<BlockPos> cl = new ArrayList<>();
            while (!q.isEmpty()) {
                BlockPos c = q.poll();
                cnt++;
                long ck = c.asLong();
                if (grown.contains(ck)) { g++; cl.add(c); }
                else if (budding.contains(ck)) bd++;
                else if (buds.contains(ck)) bu++;
                x1 = Math.min(x1, c.getX()); x2 = Math.max(x2, c.getX());
                y1 = Math.min(y1, c.getY()); y2 = Math.max(y2, c.getY());
                z1 = Math.min(z1, c.getZ()); z2 = Math.max(z2, c.getZ());
                for (int dx = -2; dx <= 2; dx++)
                    for (int dy = -2; dy <= 2; dy++)
                        for (int dz = -2; dz <= 2; dz++) {
                            if (dx == 0 && dy == 0 && dz == 0) continue;
                            BlockPos nb = c.add(dx, dy, dz);
                            long k = nb.asLong();
                            if (all.contains(k) && seen.add(k)) q.add(nb);
                        }
            }
            // to'liq o'sgan kristallar juda sekin o'sadi: shunday geod — u yerda uzoq vaqt ishlangan
            if (g < minGrown.getValue().intValue()) continue;
            out.add(new Geode(x1 - pad, y1 - pad, z1 - pad, x2 + pad, y2 + pad, z2 + pad, g, bu, bd, cl));
        }
        Vec3d p = mc.player.getLerpedPos(mc.getRenderTickCounter().getTickProgress(true));
        out.sort((a, b) -> Double.compare(d2(a, p), d2(b, p)));
        geodes = out;
        building = new HashSet<>();
    }

    private static double d2(Geode g, Vec3d p) {
        double dx = g.cx() - p.x, dy = g.cy() - p.y, dz = g.cz() - p.z;
        return dx * dx + dy * dy + dz * dz;
    }

    private void dispatchWorld(WorldRenderContext wrc) {
        List<Geode> list = geodes;
        if (!isEnabled() || list.isEmpty() || mc.world == null) return;
        Camera camera = wrc.gameRenderer().getCamera();
        if (camera == null || !camera.isReady()) return;
        MatrixStack matrices = wrc.matrices();
        VertexConsumerProvider consumers = wrc.consumers();
        if (matrices == null || consumers == null) return;
        int rgb = RGB & 0xFFFFFF;
        int line = 0xFF000000 | rgb;
        int shade = fill.isEnabled() ? (fillOpacity.getValue().intValue() << 24) | rgb : 0;
        int drawn = 0, limit = maxGeodes.getValue().intValue();
        double maxD = range.getValue() * 16 + 24, max2 = maxD * maxD;
        Vec3d c = camera.getCameraPos();
        WorldRender3D.begin(matrices, camera);
        try {
            for (Geode g : list) {
                if (drawn >= limit) break;
                double dx = g.cx() - c.x, dy = g.cy() - c.y, dz = g.cz() - c.z;
                if (dx * dx + dy * dy + dz * dz > max2) continue;
                drawn++;
                if (wholeBox.isEnabled())
                    WorldRender3D.drawFilledBoxExactAlpha(consumers, new Box(g.x1(), g.y1(), g.z1(), g.x2() + 1, g.y2() + 1, g.z2() + 1), shade);
                if (crystals.isEnabled()) {
                    for (BlockPos p : g.clusters())
                        WorldRender3D.drawBox(consumers, new Box(p.getX(), p.getY(), p.getZ(), p.getX() + 1, p.getY() + 1, p.getZ() + 1), line);
                }
            }
        } finally {
            WorldRender3D.end(matrices);
        }
    }

    /** HUD pass: tracer'lar va yozuvlar; 3D qutilar dispatchWorld() da chiziladi. */
    @Override
    public void onRender2D(DrawContext g, float tickDelta) {
        List<Geode> list = geodes;
        if (!isEnabled() || list.isEmpty() || (!tracers.isEnabled() && !labels.isEnabled()) || mc.world == null || mc.player == null) return;
        ProjectionUtil.beginFrame(tickDelta);
        Vec3d c = ProjectionUtil.eye(tickDelta);
        int drawn = 0, limit = maxGeodes.getValue().intValue();
        double maxD = range.getValue() * 16 + 24, max2 = maxD * maxD;
        int line = 0xFF000000 | (RGB & 0xFFFFFF);
        TextRenderer tr = mc.textRenderer;
        int sw = RenderUtils.screenWidth(), sh = RenderUtils.screenHeight();
        for (Geode ge : list) {
            if (drawn >= limit) break;
            double dx = ge.cx() - c.x, dy = ge.cy() - c.y, dz = ge.cz() - c.z, dsq = dx * dx + dy * dy + dz * dz;
            if (dsq > max2) continue;
            drawn++;
            if (tracers.isEnabled()) {
                int[] pt = ProjectionUtil.project(new Vec3d(ge.cx(), ge.cy(), ge.cz()), tickDelta);
                if (pt != null)
                    RenderUtils.drawLine(g, sw / 2, sh / 2, pt[0], pt[1], 0x99FFFFFF | (line & 0xFFFFFF), 1.0f);
            }
            if (labels.isEnabled()) {
                int[] pt = ProjectionUtil.project(new Vec3d(ge.cx(), ge.y1() + 1.8, ge.cz()), tickDelta);
                if (pt != null) {
                    String text = "Old geode  " + Math.round(Math.sqrt(dsq)) + "m  " + ge.grown() + " grown";
                    int w = tr.getWidth(text);
                    g.fill(pt[0] - w / 2 - 4, pt[1] - 10, pt[0] + w / 2 + 4, pt[1] + 2, 0xB0101214);
                    g.fill(pt[0] - w / 2 - 4, pt[1] - 10, pt[0] - w / 2 - 1, pt[1] + 2, line);
                    RenderUtils.drawText(g, tr, text, pt[0] - w / 2, pt[1] - 7, 0xFFE8E8F0, false);
                }
            }
        }
    }
}
