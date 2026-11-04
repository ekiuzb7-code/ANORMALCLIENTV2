package com.anormal.client.module.impl.kuro;

import com.anormal.client.lucid.WorldRender3D;
import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.ProjectionUtil;
import com.anormal.client.util.RenderUtils;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * kuro {@code BlockEsp} port: tanlangan bloklarning hammasini yondan o'tib
 * bo'lgan chunklar ichida topadi va rangli qutida ko'rsatadi. {Range} massivi
 * atrofidagi chunklar skan bo'ladi oyin suhbati to'sib qo'ymasligi uchun
 * skan ko'p tick bo'ylab sayoz tarqatiladi; topilganlar policya eng
 * yaqingi birinchi chiziladi.
 *
 * <p>Anormaldagi farqlar: kuro'dagi {@code BlockPickerScreen} port qilinmagan —
 * blok ro'yxatini o'zgartirish uchun ochiq metodlar bor ({@link #targets()},
 * {@link #addTarget(Block)}, {@link #removeTarget(Block)}, {@link #changed()});
 * ro'yxat {@code kuro-blocks.txt} faylida saqlanadi. Ranglar doimiy
 * (Setting.Hue yo'q); {Glow} va Projector.depth yo'q; off-screen tracer yo'q —
 * faqat ko'rinishga kirgan nuqtalar chiziq bilan bog'lanadi. Kuro'ning
 * "kuro:alert" ovozi o'z resurspaklarida bo'lgani uchun Anormalda
 * tajriba sharbati ovozi ishlatiladi.</p>
 */
public class KuroBlockEsp extends Module {
    private static final int MAX_BLOCKS = 3000;
    private static final int DEFAULT_RGB = 0x40E0E0;          // kuro hue 0.5 (suv yashil); g'ildirak yo'q
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("kuro-blocks.txt");

    private record Found(BlockPos pos, Block block) {}

    /** Bir-birini tegib turgan bir xil bloklardan tashkil topgan to'plam ("Merge clusters" uchun). */
    private record Cluster(Block block, int x1, int y1, int z1, int x2, int y2, int z2, int count) {
        double cx() { return (x1 + x2 + 1) / 2.0; }
        double cy() { return (y1 + y2 + 1) / 2.0; }
        double cz() { return (z1 + z2 + 1) / 2.0; }
    }

    private final NumberSetting range = new NumberSetting("Range (chunks)", "Chunks scanned around the player", 4.0, 1.0, 8.0, 1.0);
    private final NumberSetting maxDrawn = new NumberSetting("Max blocks", "Boxes drawn at once", 150.0, 25.0, 1500.0, 25.0);
    private final NumberSetting thickness = new NumberSetting("Thickness", "Line width", 1.0, 1.0, 3.0, 1.0);
    private final BooleanSetting fill = new BooleanSetting("Fill", true);
    private final NumberSetting fillOpacity = new NumberSetting("Fill opacity", "Fill alpha", 40.0, 10.0, 200.0, 10.0);
    private final BooleanSetting merge = new BooleanSetting("Merge clusters", false);
    private final BooleanSetting tracers = new BooleanSetting("Tracers", false);
    private final NumberSetting tracerMax = new NumberSetting("Tracer count", "Tracers drawn at once", 20.0, 1.0, 60.0, 1.0);
    private final BooleanSetting labels = new BooleanSetting("Labels", false);
    private final BooleanSetting alert = new BooleanSetting("Alert sound", true);
    private final NumberSetting alertCount = new NumberSetting("Alert count", "Blocks in range to trigger", 5.0, 1.0, 300.0, 1.0);
    private final BooleanSetting countBehind = new BooleanSetting("Count behind you", true);
    private final NumberSetting alertVolume = new NumberSetting("Alert volume", "Loudness multiplier", 1.0, 0.1, 2.0, 0.1);
    private final NumberSetting alertCooldown = new NumberSetting("Alert cooldown (s)", "Time between alerts", 3.0, 0.0, 30.0, 1.0);

    /** Tanlangan bloklar — kuro'da ham ommaviy (blok tanlash ekrani yozuvchidan tegish qiladi). */
    public final Set<Block> targets = new LinkedHashSet<>();
    /** Har blok uchun ommaviy rang (ARGB katta qismi; 0 old raqami). Tugatilmaydi — default rang ishlatiladi. */
    public final Map<Block, Integer> colors = new HashMap<>();

    private boolean armed = true;                 // hisob treshold pasga tushgandan keyin qayta qurolanadi
    private long lastAlert;
    private volatile List<Found> found = List.of();
    private volatile List<Cluster> clusters = List.of();
    private boolean dirty = true;
    private int timer;

    private static final int CHUNKS_PER_TICK = 6;
    private final List<long[]> queue = new ArrayList<>();    // hali skan qilinishi kerak chunklar {x, z}
    private int queuePos;
    private List<Found> building = new ArrayList<>();

    public KuroBlockEsp() {
        super("Block ESP", "Outlines your chosen blocks in loaded chunks", Category.KURO);
        addSetting(range);
        addSetting(maxDrawn);
        addSetting(thickness);
        addSetting(fill);
        addSetting(fillOpacity);
        addSetting(merge);
        addSetting(tracers);
        addSetting(tracerMax);
        addSetting(labels);
        addSetting(alert);
        addSetting(alertCount);
        addSetting(countBehind);
        addSetting(alertVolume);
        addSetting(alertCooldown);
        load();
    }

    /** Tanlangan blok ro'yxati (kuro'dagi ommaviy maydon o'rnida). */
    public Set<Block> targets() { return targets; }

    /** Ro'yxatga blok qo'shadi va ro'yxat faylini yangilaydi. */
    public void addTarget(Block b) {
        if (b != null && targets.add(b)) changed();
    }

    /** Ro'yxatdan blokk olib tashlaydi va ro'yxat faylini yangilaydi. */
    public void removeTarget(Block b) {
        if (b != null && targets.remove(b)) changed();
    }

    @Override
    public void onEnable() { dirty = true; }

    @Override
    public void onDisable() {
        armed = true;
        found = List.of();
        clusters = List.of();
        queue.clear();
        queuePos = 0;
    }

    /** Blok tanlash o'zgargandan keyin chaqiriladi: saqlaydi va qaytadan rescan suhbatiga tayyorlanadi. */
    public void changed() { save(); dirty = true; }

    /** Bir nur ikoqlamasi blokkning rangi o'zgartirilgananda chaqiriladi (rescan kerak emas). */
    public void colorChanged() { save(); }

    @Override
    public void onTick() {
        if (mc.world == null || mc.player == null) return;

        if (dirty) {                                  // yangi sozlamalar bilan qaytadan boshlash
            queue.clear();
            queuePos = 0;
            timer = 1000;
            dirty = false;
        }
        if (queuePos >= queue.size()) {
            if (++timer < 40) return;                 // bo'sh o'tirayotganda har ~2 s yang yurish
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
        building = new ArrayList<>();
        if (targets.isEmpty()) { found = List.of(); clusters = List.of(); return; }
        ChunkPos cp = new ChunkPos(mc.player.getBlockPos());
        int rad = range.getValue().intValue();
        for (int dx = -rad; dx <= rad; dx++)
            for (int dz = -rad; dz <= rad; dz++) queue.add(new long[]{cp.x + dx, cp.z + dz});
        queue.sort((a, b) -> Long.compare(
                (a[0] - cp.x) * (a[0] - cp.x) + (a[1] - cp.z) * (a[1] - cp.z),
                (b[0] - cp.x) * (b[0] - cp.x) + (b[1] - cp.z) * (b[1] - cp.z)));    // yaqinglar birinchi
    }

    private void finishPass() {
        Vec3d p = mc.player.getLerpedPos(mc.getRenderTickCounter().getTickProgress(true));
        building.sort((a, b) -> Double.compare(dist2(a.pos(), p), dist2(b.pos(), p)));      // yaqinlar birinchi
        found = building;
        clusters = merge.isEnabled() ? buildClusters(building, p) : List.of();
        building = new ArrayList<>();
    }

    private static double dist2(BlockPos b, Vec3d p) {
        double dx = b.getX() + 0.5 - p.x, dy = b.getY() + 0.5 - p.y, dz = b.getZ() + 0.5 - p.z;
        return dx * dx + dy * dy + dz * dz;
    }

    /** Tegish (diagonal ham) bir xil bloklar greedy BFS cluster'larga ajratiladi. */
    private static List<Cluster> buildClusters(List<Found> list, Vec3d player) {
        Map<Long, Block> at = new HashMap<>();
        for (Found f : list) at.put(f.pos().asLong(), f.block());
        Set<Long> seen = new HashSet<>();
        List<Cluster> out = new ArrayList<>();
        for (Found f : list) {
            if (!seen.add(f.pos().asLong())) continue;
            Block b = f.block();
            ArrayDeque<BlockPos> q = new ArrayDeque<>();
            q.add(f.pos());
            int x1 = f.pos().getX(), y1 = f.pos().getY(), z1 = f.pos().getZ(), x2 = x1, y2 = y1, z2 = z1, n = 0;
            while (!q.isEmpty()) {
                BlockPos c = q.poll();
                n++;
                x1 = Math.min(x1, c.getX()); x2 = Math.max(x2, c.getX());
                y1 = Math.min(y1, c.getY()); y2 = Math.max(y2, c.getY());
                z1 = Math.min(z1, c.getZ()); z2 = Math.max(z2, c.getZ());
                for (int dx = -1; dx <= 1; dx++)
                    for (int dy = -1; dy <= 1; dy++)
                        for (int dz = -1; dz <= 1; dz++) {
                            if (dx == 0 && dy == 0 && dz == 0) continue;
                            BlockPos nb = c.add(dx, dy, dz);
                            long key = nb.asLong();
                            if (!seen.contains(key) && at.get(key) == b) { seen.add(key); q.add(nb); }
                        }
            }
            out.add(new Cluster(b, x1, y1, z1, x2, y2, z2, n));
        }
        out.sort((a, c) -> Double.compare(cd2(a, player), cd2(c, player)));
        return out;
    }

    private static double cd2(Cluster c, Vec3d p) {
        double dx = c.cx() - p.x, dy = c.cy() - p.y, dz = c.cz() - p.z;
        return dx * dx + dy * dy + dz * dz;
    }

    private void scanChunk(WorldChunk chunk) {
        ChunkSection[] sections = chunk.getSectionArray();
        for (int i = 0; i < sections.length; i++) {
            ChunkSection sec = sections[i];
            if (sec == null || sec.isEmpty()) continue;
            if (!sec.hasAny(state -> targets.contains(state.getBlock()))) continue;
            int baseY = (chunk.getBottomY() >> 4) + i << 4;
            int baseX = chunk.getPos().getStartX(), baseZ = chunk.getPos().getStartZ();
            for (int x = 0; x < 16; x++)
                for (int y = 0; y < 16; y++)
                    for (int z = 0; z < 16; z++) {
                        Block b = sec.getBlockState(x, y, z).getBlock();
                        if (!targets.contains(b)) continue;
                        building.add(new Found(new BlockPos(baseX + x, baseY + y, baseZ + z), b));
                        if (building.size() >= MAX_BLOCKS) return;
                    }
        }
    }

    /** Bir nurdagi RGB va fill ranglari qamrab oladi (0 = fill chizilmasin). */
    private int lineColor(Block b) { return 0xFF000000 | colorFor(b); }

    private int fillColor(Block b) { return fill.isEnabled() ? (fillOpacity.getValue().intValue() << 24) | colorFor(b) : 0; }

    private int colorFor(Block b) { return colors.getOrDefault(b, DEFAULT_RGB); }

    /** 3D yo'nalish: hamma quti aslida dunyoda chiziladi. */
    private void dispatchWorld(WorldRenderContext wrc) {
        List<Found> list = found;
        if (!isEnabled() || list.isEmpty() || mc.world == null) return;
        Camera camera = wrc.gameRenderer().getCamera();
        if (camera == null || !camera.isReady()) return;
        MatrixStack matrices = wrc.matrices();
        VertexConsumerProvider consumers = wrc.consumers();
        if (matrices == null || consumers == null) return;
        double maxD = range.getValue() * 16 + 8, max2 = maxD * maxD;
        int limit = maxDrawn.getValue().intValue(), drawn = 0;
        Vec3d c = camera.getCameraPos();
        List<Cluster> cl = clusters;
        WorldRender3D.begin(matrices, camera);
        try {
            if (merge.isEnabled() && !cl.isEmpty()) {
                for (Cluster k : cl) {
                    if (drawn >= limit) break;                       // yaqinlar birinchi tartiblik
                    double dx = k.cx() - c.x, dy = k.cy() - c.y, dz = k.cz() - c.z;
                    if (dx * dx + dy * dy + dz * dz > max2) continue;
                    int line = lineColor(k.block()), sh = fillColor(k.block());
                    drawn++;
                    WorldRender3D.drawFilledBoxExactAlpha(consumers, new Box(k.x1(), k.y1(), k.z1(), k.x2() + 1, k.y2() + 1, k.z2() + 1), sh);
                    WorldRender3D.drawBox(consumers, new Box(k.x1(), k.y1(), k.z1(), k.x2() + 1, k.y2() + 1, k.z2() + 1), line);
                }
            } else {
                for (Found f : list) {
                    if (drawn >= limit) break;                       // yaqinlar birinchi
                    BlockPos p = f.pos();
                    double dx = p.getX() + 0.5 - c.x, dy = p.getY() + 0.5 - c.y, dz = p.getZ() + 0.5 - c.z;
                    if (dx * dx + dy * dy + dz * dz > max2) continue;
                    int line = lineColor(f.block()), sh = fillColor(f.block());
                    drawn++;
                    WorldRender3D.drawFilledBoxExactAlpha(consumers, new Box(p.getX(), p.getY(), p.getZ(), p.getX() + 1, p.getY() + 1, p.getZ() + 1), sh);
                    WorldRender3D.drawBox(consumers, new Box(p.getX(), p.getY(), p.getZ(), p.getX() + 1, p.getY() + 1, p.getZ() + 1), line);
                }
            }
        } finally {
            WorldRender3D.end(matrices);
        }
    }

    /** HUD pass: faqat 2D ekstra (tracer, yozuv) va ovoz siqilishi; qutilar dispatchWorld() da. */
    @Override
    public void onRender2D(DrawContext g, float tickDelta) {
        List<Found> list = found;
        if (!isEnabled() || list.isEmpty()) { armed = true; return; }
        ProjectionUtil.beginFrame(tickDelta);
        Vec3d c = ProjectionUtil.eye(tickDelta);
        double maxD = range.getValue() * 16 + 8, max2 = maxD * maxD;
        int limit = maxDrawn.getValue().intValue(), drawn = 0, tracerLimit = tracerMax.getValue().intValue();
        int sw = RenderUtils.screenWidth(), sh = RenderUtils.screenHeight();
        List<Cluster> cl = clusters;
        if (merge.isEnabled() && !cl.isEmpty()) {
            for (Cluster k : cl) {
                if (drawn >= limit) break;
                double dx = k.cx() - c.x, dy = k.cy() - c.y, dz = k.cz() - c.z;
                double d2 = dx * dx + dy * dy + dz * dz;
                if (d2 > max2) continue;
                int line = lineColor(k.block());
                drawn++;
                if (tracers.isEnabled() && drawn <= tracerLimit && ProjectionUtil.project(new Vec3d(k.cx(), k.cy(), k.cz()), tickDelta) != null)
                    drawTracer(g, sw, sh, new Vec3d(k.cx(), k.cy(), k.cz()), tickDelta, line);
                if (k.count() > 1 || labels.isEnabled()) {
                    String text = (k.count() > 1 ? "x" + k.count() : "") + (labels.isEnabled() ? (k.count() > 1 ? "  " : "") + Math.round(Math.sqrt(d2)) + "m" : "");
                    drawLabel(g, text, k.cx(), k.y1() + 1.3, k.cz(), tickDelta, line);
                }
            }
        } else {
            for (Found f : list) {
                if (drawn >= limit) break;
                BlockPos p = f.pos();
                double dx = p.getX() + 0.5 - c.x, dy = p.getY() + 0.5 - c.y, dz = p.getZ() + 0.5 - c.z;
                double d2 = dx * dx + dy * dy + dz * dz;
                if (d2 > max2) continue;
                int line = lineColor(f.block());
                drawn++;
                if (tracers.isEnabled() && drawn <= tracerLimit && ProjectionUtil.project(new Vec3d(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5), tickDelta) != null)
                    drawTracer(g, sw, sh, new Vec3d(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5), tickDelta, line);
                if (labels.isEnabled() && drawn <= 12)
                    drawLabel(g, Math.round(Math.sqrt(d2)) + "m", p.getX() + 0.5, p.getY() + 1.3, p.getZ() + 0.5, tickDelta, line);
            }
        }

        // ovoz siqilishi: turdagi bloklar masofada hisoblanadi (oxirda sizdan behind bo'lsin),
        // {Max blocks} restrictiga qarab farqi yo'q: taqiq bo'lsa qaytarmas bildir
        int counted = 0;
        for (Found f : list) {
            BlockPos p = f.pos();
            double dx = p.getX() + 0.5 - c.x, dy = p.getY() + 0.5 - c.y, dz = p.getZ() + 0.5 - c.z;
            if (dx * dx + dy * dy + dz * dz > max2) continue;
            counted++;
        }
        int need = alertCount.getValue().intValue();
        if (counted < need) armed = true;
        else if (alert.isEnabled() && armed && mc.player != null) {
            long now = System.currentTimeMillis();
            if (now - lastAlert >= alertCooldown.getValue() * 1000) {
                mc.player.playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, alertVolume.getValue().floatValue(), 1.0f);
                lastAlert = now;
                armed = false;
            }
        }
    }

    /** To'qnashuvdan dunyodagi bir nuqtaga chiziq (faqat ko'rinishga kirgan holatlarda). */
    private void drawTracer(DrawContext g, int sw, int sh, Vec3d v, float tickDelta, int argb) {
        int[] pt = ProjectionUtil.project(v, tickDelta);
        if (pt == null) return;
        RenderUtils.drawLine(g, sw / 2, sh / 2, pt[0], pt[1], argb, 1.0f);
        int px = pt[0], py = pt[1];
        if (px > 2 && py > 2 && px < sw - 2 && py < sh - 2) {
            g.fill(px - 2, py - 2, px + 2, py + 2, argb);        // yaxshi nuqtaga kichik to'g'ni burchak
        }
    }

    private void drawLabel(DrawContext g, String text, double x, double y, double z, float tickDelta, int argb) {
        if (text.isEmpty()) return;
        int[] pt = ProjectionUtil.project(new Vec3d(x, y, z), tickDelta);
        if (pt == null) return;
        if (pt[0] < -80 || pt[1] < -20 || pt[0] > RenderUtils.screenWidth() + 80 || pt[1] > RenderUtils.screenHeight() + 20) return;
        TextRenderer tr = mc.textRenderer;
        int w = tr.getWidth(text);
        g.fill(pt[0] - w / 2 - 4, pt[1] - 10, pt[0] + w / 2 + 4, pt[1] + 2, 0xB0101214);
        g.fill(pt[0] - w / 2 - 4, pt[1] - 10, pt[0] - w / 2 - 1, pt[1] + 2, argb);
        RenderUtils.drawText(g, tr, text, pt[0] - w / 2, pt[1] - 7, 0xFFE8E8F0, false);
    }

    // ---- tanlangan bloklar ro'yxatini faylga / fayldan ----
    private void load() {
        try {
            if (Files.exists(FILE)) {
                for (String line : Files.readAllLines(FILE)) {
                    // "namespace:block"
                    String[] parts = line.trim().split("\\s+");
                    if (parts[0].isEmpty()) continue;
                    Identifier id = Identifier.tryParse(parts[0]);
                    if (id == null || !Registries.BLOCK.containsId(id)) continue;
                    Block b = Registries.BLOCK.get(id);
                    if (b != null) targets.add(b);
                }
                if (!targets.isEmpty()) return;
            }
        } catch (IOException ignored) {}
        targets.add(Blocks.DIAMOND_ORE);
        targets.add(Blocks.DEEPSLATE_DIAMOND_ORE);
    }

    private void save() {
        try {
            List<String> lines = new ArrayList<>();
            for (Block b : targets) lines.add(Registries.BLOCK.getId(b).toString());
            Files.createDirectories(FILE.getParent());
            Files.write(FILE, lines);
        } catch (IOException ignored) {}
    }
}
