package com.anormal.client.module.impl.kuro;

import com.anormal.client.lucid.WorldRender3D;
import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.ProjectionUtil;
import com.anormal.client.util.RenderUtils;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.chunk.ChunkSection;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * kuro {@code AncientDebris} port: server sizga yuborgan chunk'lar ichidagi
 * ancient debris'ni skanda qiladi, devorlar orasidan ko'rsatadi + tracer
 * va "Debris area ~N" yozuvlari. "Rough area" (default) har bir klaster uchun
 * qat'iy 8x8x8 quti chizadi — ore joylarini siljituvchi serverlarga qarshi.
 * Yangi topilganlar uchun chat ogohlantirish.
 *
 * <p>Server yuborgan bloklar o'qib ko'riladi — anti-xray ilohiota: yonalmagan
 * debris yok qilinadi, kobra kobraz hisobla va fake ore gigachad.</p>
 *
 * <p>Moslash: kuro world-quti'ni WorldGfx hook orqali; burda Fabric
 * {@code WorldRenderEvents.END_MAIN} bilan {@code WorldRender3D} (Anormal
 * kuro render yechimi, KuroHitbox'dagi kabi). kuro output "Finds" ro'yxati
 * (New chunks/Trail Tracker misoli Anormal'ning o'z kutuphane yardimci
 * listi yo'q) Anormal'da shuni topishdag; "Baritone" modul o'z modullari
 * orqali yordamchi kengaytirilmaydi: {@link #nearest()} BaritoneControl'da
 * ishlatiladi.</p>
 */
public class KuroAncientDebris extends Module {
    private static final int CHUNKS_PER_TICK = 8;
    private static final int MAX_SCAN = 4000;      // safety cap on debris blocks kept per pass

    private final NumberSetting range = new NumberSetting("Range (chunks)", "Distance from your chunk", 8.0, 1.0, 12.0, 1.0);
    private final BooleanSetting netherOnly = new BooleanSetting("Nether only", true);
    private final BooleanSetting rough = new BooleanSetting("Rough area", true);
    private final NumberSetting areaSize = new NumberSetting("Area size", "Rough box XZ size", 8.0, 4.0, 16.0, 1.0);
    private final NumberSetting areaHeight = new NumberSetting("Area height", "Rough box Y size", 8.0, 2.0, 64.0, 2.0);
    private final NumberSetting minY = new NumberSetting("Min Y", "Scan bottom", 0.0, -64.0, 128.0, 1.0);
    private final NumberSetting maxY = new NumberSetting("Max Y", "Scan top", 127.0, -64.0, 128.0, 1.0);
    private final BooleanSetting exposedOnly = new BooleanSetting("Only exposed", false);
    private final BooleanSetting fill = new BooleanSetting("Fill", true);
    private final NumberSetting fillOpacity = new NumberSetting("Fill opacity", "Fill alpha", 60.0, 10.0, 200.0, 10.0);
    private final BooleanSetting tracers = new BooleanSetting("Tracers", true);
    private final BooleanSetting labels = new BooleanSetting("Labels", true);
    private final NumberSetting maxShown = new NumberSetting("Max shown", "Boxes/labels drawn at once", 12.0, 1.0, 100.0, 1.0);
    private final BooleanSetting chatAlert = new BooleanSetting("Chat alert", true);

    /** A rough area: a fixed-size box centred on a cluster of debris, so a server that shifts ore positions can't mislead you. */
    private record Area(int x1, int y1, int z1, int x2, int y2, int z2, double cx, double cy, double cz, List<BlockPos> members) {}

    private final List<long[]> queue = new ArrayList<>();
    private int queuePos, timer = 1000;
    private boolean dirty = true;
    private List<BlockPos> building = new ArrayList<>();
    private volatile List<BlockPos> found = List.of();
    private volatile List<Area> areas = List.of();
    private final Set<Long> announced = new HashSet<>();
    private net.minecraft.registry.RegistryKey<World> lastWorld;
    private boolean registered;

    public KuroAncientDebris() {
        super("Ancient Debris", "Finds ancient debris in loaded chunks and marks it through walls", Category.KURO);
        addSetting(range);
        addSetting(netherOnly);
        addSetting(rough);
        addSetting(areaSize);
        addSetting(areaHeight);
        addSetting(minY);
        addSetting(maxY);
        addSetting(exposedOnly);
        addSetting(fill);
        addSetting(fillOpacity);
        addSetting(tracers);
        addSetting(labels);
        addSetting(maxShown);
        addSetting(chatAlert);
    }

    @Override public void onEnable() {
        dirty = true;
        announced.clear();
        if (registered) return;
        try {
            WorldRenderEvents.END_MAIN.register(this::anormal$dispatchWorld);
            registered = true;
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void onDisable() {
        found = List.of();
        areas = List.of();
        queue.clear();
        queuePos = 0;
        announced.clear();
    }

    private boolean inDimension() {
        return !netherOnly.isEnabled() || mc.world.getRegistryKey() == World.NETHER;
    }

    @Override
    public void onTick() {
        if (mc.world == null || mc.player == null) return;
        if (lastWorld != mc.world.getRegistryKey()) {          // changed dimension: start clean
            lastWorld = mc.world.getRegistryKey();
            found = List.of();
            areas = List.of();
            cleanPass();
            dirty = true;
        }
        if (!inDimension()) {
            if (!found.isEmpty()) { found = List.of(); areas = List.of(); }
            return;
        }
        if (dirty) { dirty = false; beginPass(); }
        if (queuePos >= queue.size()) {
            if (++timer < 30) return;                          // a new pass about every 1.5 s, spread over several ticks
            timer = 0;
            beginPass();
            if (queue.isEmpty()) return;
        }
        for (int n = 0; n < CHUNKS_PER_TICK && queuePos < queue.size(); n++) {
            long[] c = queue.get(queuePos++);
            ClientWorld w = mc.world;
            if (!w.isChunkLoaded((int) c[0], (int) c[1])) continue;
            WorldChunk chunk = w.getChunk((int) c[0], (int) c[1]);
            if (chunk != null) scanChunk(chunk);
            if (building.size() >= MAX_SCAN) { queuePos = queue.size(); break; }
        }
        if (queuePos >= queue.size()) finishPass();
    }

    private void cleanPass() {
        found = List.of();
        areas = List.of();
        announced.clear();
    }

    private void beginPass() {
        queue.clear();
        queuePos = 0;
        building = new ArrayList<>();
        net.minecraft.util.math.ChunkPos cp = new net.minecraft.util.math.ChunkPos(mc.player.getBlockPos());
        int rad = range.getValue().intValue();
        for (int dx = -rad; dx <= rad; dx++)
            for (int dz = -rad; dz <= rad; dz++) queue.add(new long[]{cp.x + dx, cp.z + dz});
        queue.sort((a, b) -> Long.compare(
                (a[0] - cp.x) * (a[0] - cp.x) + (a[1] - cp.z) * (a[1] - cp.z),
                (b[0] - cp.x) * (b[0] - cp.x) + (b[1] - cp.z) * (b[1] - cp.z)));
        // an in-progress pass restarts cleanly
        building = new ArrayList<>();
    }

    private static boolean isDebris(BlockState s) { return s.getBlock() == Blocks.ANCIENT_DEBRIS; }

    private void scanChunk(WorldChunk chunk) {
        ChunkSection[] sections = chunk.getSectionArray();
        int lo = (int) Math.min(minY.getValue(), maxY.getValue()), hi = (int) Math.max(minY.getValue(), maxY.getValue());
        int baseX = chunk.getPos().getStartX();   // local origin of the chunk
        int baseZ = chunk.getPos().getStartZ();
        for (int i = 0; i < sections.length; i++) {
            ChunkSection sec = sections[i];
            if (sec == null || sec.isEmpty()) continue;
            int baseY = (chunk.getBottomY() >> 4) + i << 4;
            if (baseY + 15 < lo || baseY > hi) continue;
            for (int x = 0; x < 16; x++)
                for (int y = 0; y < 16; y++) {
                    int wy = baseY + y;
                    if (wy < lo || wy > hi) continue;
                    for (int z = 0; z < 16; z++) {
                        if (!isDebris(sec.getBlockState(x, y, z))) continue;
                        BlockPos p = new BlockPos(baseX + x, wy, baseZ + z);
                        if (exposedOnly.isEnabled() && !exposed(p)) continue;
                        building.add(p);
                    }
                }
        }
    }

    /** True when at least one side of the block touches air, lava/water or any non-solid block. */
    private boolean exposed(BlockPos p) {
        for (net.minecraft.util.math.Direction d : net.minecraft.util.math.Direction.values()) {
            BlockPos n = p.offset(d);
            BlockState s = mc.world.getBlockState(n);
            if (s.isAir() || !s.getFluidState().isEmpty() || s.getCollisionShape(mc.world, n).isEmpty()) return true;
        }
        return false;
    }

    private void finishPass() {
        List<BlockPos> out = building;
        building = new ArrayList<>();
        Vec3d p = mc.player.getEntityPos();
        out.sort((a, b) -> Double.compare(d2(a, p), d2(b, p)));
        found = out;
        List<Area> ar = rough.isEnabled() ? cluster(out) : List.of();
        areas = ar;

        if (chatAlert.isEnabled()) {
            int fresh = 0;
            BlockPos first = null;
            if (rough.isEnabled()) {
                for (Area a : ar) {
                    boolean isNew = false;
                    for (BlockPos b : a.members()) if (announced.add(b.asLong())) isNew = true;
                    if (isNew) { fresh++; if (first == null) first = new BlockPos((int) a.cx(), (int) a.cy(), (int) a.cz()); }
                }
            } else {
                for (BlockPos b : out) if (announced.add(b.asLong())) { fresh++; if (first == null) first = b; }
            }
            if (fresh > 0 && first != null && mc.player != null)
                mc.player.sendMessage(net.minecraft.text.Text.literal("[Ancient Debris] " + fresh
                        + (rough.isEnabled() ? " new area" + (fresh == 1 ? "" : "s") : " new")
                        + ", nearest " + (rough.isEnabled() ? "around " : "") + first.getX() + " " + first.getY() + " " + first.getZ()), false);
        }
    }

    /**
     * Greedy clustering, nearest debris first: every debris within half the area size (and half the area height)
     * of a cluster's first block joins it. The area box is then centred on the average of its blocks.
     */
    private List<Area> cluster(List<BlockPos> sorted) {
        double hx = areaSize.getValue() / 2.0, hy = areaHeight.getValue() / 2.0;
        int sx = areaSize.getValue().intValue(), sy = areaHeight.getValue().intValue();
        boolean[] used = new boolean[sorted.size()];
        List<Area> out = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            if (used[i]) continue;
            BlockPos seed = sorted.get(i);
            List<BlockPos> members = new ArrayList<>();
            double sumX = 0, sumY = 0, sumZ = 0;
            for (int j = i; j < sorted.size(); j++) {
                if (used[j]) continue;
                BlockPos b = sorted.get(j);
                if (Math.abs(b.getX() - seed.getX()) > hx || Math.abs(b.getZ() - seed.getZ()) > hx
                        || Math.abs(b.getY() - seed.getY()) > hy) continue;
                used[j] = true;
                members.add(b);
                sumX += b.getX() + 0.5; sumY += b.getY() + 0.5; sumZ += b.getZ() + 0.5;
            }
            int n = members.size();
            double cx = sumX / n, cy = sumY / n, cz = sumZ / n;
            int x1 = (int) Math.floor(cx - sx / 2.0), y1 = (int) Math.floor(cy - sy / 2.0), z1 = (int) Math.floor(cz - sx / 2.0);
            out.add(new Area(x1, y1, z1, x1 + sx, y1 + sy, z1 + sx, cx, cy, cz, members));
        }
        return out;
    }

    private static double d2(BlockPos b, Vec3d p) {
        double dx = b.getX() + 0.5 - p.x, dy = b.getY() + 0.5 - p.y, dz = b.getZ() + 0.5 - p.z;
        return dx * dx + dy * dy + dz * dz;
    }

    /** Where to send Baritone: the centre of the nearest area (or the nearest block when Rough area is off), or null. */
    public BlockPos nearest() {
        if (mc.player == null || mc.world == null) return null;
        Vec3d p = mc.player.getEntityPos();
        if (rough.isEnabled()) {
            Area best = null;
            double bd = Double.MAX_VALUE;
            for (Area a : areas) {
                if (!stillThere(a)) continue;
                double dx = a.cx() - p.x, dy = a.cy() - p.y, dz = a.cz() - p.z, d = dx * dx + dy * dy + dz * dz;
                if (d < bd) { bd = d; best = a; }
            }
            return best == null ? null : new BlockPos((int) Math.round(best.cx()), (int) Math.round(best.cy()), (int) Math.round(best.cz()));
        }
        BlockPos best = null;
        double bd = Double.MAX_VALUE;
        for (BlockPos b : found) {
            if (!isDebris(mc.world.getBlockState(b))) continue;
            double d = d2(b, p);
            if (d < bd) { bd = d; best = b; }
        }
        return best;
    }

    /** An area stays up while at least one of its blocks is still debris. */
    private boolean stillThere(Area a) {
        for (BlockPos b : a.members()) if (isDebris(mc.world.getBlockState(b))) return true;
        return false;
    }

    // ------------------------------------------------------------------ boxes (world)

    private void anormal$dispatchWorld(WorldRenderContext wrc) {
        if (!isEnabled() || mc.world == null) return;
        Camera camera = wrc.gameRenderer().getCamera();
        if (camera == null || !camera.isReady()) return;
        MatrixStack matrices = wrc.matrices();
        VertexConsumerProvider consumers = wrc.consumers();
        if (matrices == null || consumers == null) return;

        int rgb = 0xFFCC7A00;   // kuro's default hue is a warm amber; ColorSetting replaced the wheel
        int line = 0xFF000000 | rgb;
        int shade = fill.isEnabled() ? (Math.min(200, fillOpacity.getValue().intValue()) << 24) | rgb : 0;
        int drawn = 0, limit = maxShown.getValue().intValue();

        if (rough.isEnabled()) {
            for (Area a : areas) {
                if (drawn >= limit) break;
                if (!stillThere(a)) continue;
                drawn++;
                net.minecraft.util.math.Box box = new net.minecraft.util.math.Box(a.x1(), a.y1(), a.z1(), a.x2(), a.y2(), a.z2());
                WorldRender3D.begin(matrices, camera);
                try {
                    if (shade != 0) WorldRender3D.drawFilledBox(consumers, box, shade);
                    WorldRender3D.drawBox(consumers, box, line);
                } finally {
                    WorldRender3D.end(matrices);
                }
            }
            return;
        }
        for (BlockPos p : found) {
            if (drawn >= limit) break;
            if (!isDebris(mc.world.getBlockState(p))) continue;     // mined since the last pass
            drawn++;
            net.minecraft.util.math.Box box = new net.minecraft.util.math.Box(p.getX(), p.getY(), p.getZ(),
                    p.getX() + 1, p.getY() + 1, p.getZ() + 1);
            WorldRender3D.begin(matrices, camera);
            try {
                if (shade != 0) WorldRender3D.drawFilledBox(consumers, box, shade);
                WorldRender3D.drawBox(consumers, box, line);
            } finally {
                WorldRender3D.end(matrices);
            }
        }
    }

    // ------------------------------------------------------------------ tracers / labels (2D)

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.world == null || mc.player == null) return;
        if (!(tracers.isEnabled() || labels.isEnabled())) return;
        if (maxShown.getValue().intValue() <= 0) return;
        float tickProgress = mc.getRenderTickCounter().getTickProgress(false);
        int line = 0xFFCC7A00;
        Vec3d c = mc.gameRenderer.getCamera().getCameraPos();
        int drawn = 0, limit = maxShown.getValue().intValue();
        float cx = RenderUtils.screenWidth() / 2f, cy = RenderUtils.screenHeight() / 2f;

        if (rough.isEnabled()) {
            for (Area a : areas) {
                if (drawn >= limit) break;
                if (!stillThere(a)) continue;
                drawn++;
                double x = a.cx(), y = a.cy(), z = a.cz();
                if (tracers.isEnabled()) {
                    int[] pt = ProjectionUtil.project(new Vec3d(x, y, z), tickProgress);
                    if (pt != null) RenderUtils.drawLine(context, (int) cx, (int) cy, pt[0], pt[1], line, 1);
                }
                if (labels.isEnabled()) {
                    int[] pt = ProjectionUtil.project(new Vec3d(x, a.y1() + 0.8, z), tickProgress);
                    if (pt != null) {
                        double dx = x - c.x, dy = y - c.y, dz = z - c.z;
                        int n = a.members().size();
                        RenderUtils.drawText(context, mc.textRenderer,
                                "Debris area ~" + n + "  " + Math.round(Math.sqrt(dx * dx + dy * dy + dz * dz)) + "m",
                                pt[0], pt[1] - 7, line, true);
                    }
                }
            }
            return;
        }
        for (BlockPos p : found) {
            if (drawn >= limit) break;
            if (!isDebris(mc.world.getBlockState(p))) continue;
            drawn++;
            double x = p.getX() + 0.5, y = p.getY() + 0.5, z = p.getZ() + 0.5;
            if (tracers.isEnabled()) {
                int[] pt = ProjectionUtil.project(new Vec3d(x, y, z), tickProgress);
                if (pt != null) RenderUtils.drawLine(context, (int) cx, (int) cy, pt[0], pt[1], line, 1);
            }
            if (labels.isEnabled()) {
                int[] pt = ProjectionUtil.project(new Vec3d(x, p.getY() + 1.6, z), tickProgress);
                if (pt != null) {
                    double dx = x - c.x, dy = y - c.y, dz = z - c.z;
                    RenderUtils.drawText(context, mc.textRenderer,
                            "Debris  Y" + p.getY() + "  " + Math.round(Math.sqrt(dx * dx + dy * dy + dz * dz)) + "m",
                            pt[0], pt[1] - 7, line, true);
                }
            }
        }
    }
}
