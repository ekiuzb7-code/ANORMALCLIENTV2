package com.anormal.client.module.impl.kuro;

import com.anormal.client.lucid.WorldRender3D;
import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.block.entity.BarrelBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.MobSpawnerBlockEntity;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.block.entity.TrialSpawnerBlockEntity;
import net.minecraft.block.entity.VaultBlockEntity;
import net.minecraft.block.enums.ChestType;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * kuro {@code StashFinder} port: yuklangan chunklar orasidan saqlash bloklari
 * (sandiqlar, tuzoq sandiqlar, barrel, shulker qutilari) zich joylashgan
 * to'plamlarni topadi. Tabiiy dungeon 1-2 sandiqdan iborat, shu uchun
 * {Min chests} yoki undan ko'p blok {Radius} ichida bo'lsa — chatga
 * xabar beriladi (faqat klient tomonda, hech kim boshqasi ko'rmaydi).
 *
 * <p>Anormaldagi farqlar: kuro'da bu ro'yxat dev.kuro.util.Finds (Stash turi)
 * orqali saqlanadi; Anormal'da unday util yo'q, shu tufayli {@link #listFinds()}
 * toplamni o'zida saqlaydi va KuroFoundList bundan so'raydi. Rang g'ildiragi
 * yo'q — doimiy amber; Projector.depth yo'q — orqa falsafada chizilmaydi.</p>
 */
public class KuroStashFinder extends Module {
    private static final int RGB = 0xFFFFB84D;                 // kuro hue 0.08 (olovli amber); g'ildirak yo'q

    private final NumberSetting minChests = new NumberSetting("Min chests", "Group size to report", 3.0, 2.0, 20.0, 1.0);
    private final NumberSetting radius = new NumberSetting("Radius", "Grouping radius (blocks)", 12.0, 4.0, 32.0, 1.0);
    private final NumberSetting range = new NumberSetting("Range (chunks)", "Scan radius", 6.0, 2.0, 12.0, 1.0);
    private final BooleanSetting barrels = new BooleanSetting("Barrels / shulkers", true);
    private final BooleanSetting ignoreDungeons = new BooleanSetting("Ignore dungeons", true);

    private final BooleanSetting highlight = new BooleanSetting("Highlight", "Draw boxes in the world", true);
    private final NumberSetting highlightRange = new NumberSetting("Highlight range", "Max draw distance", 200.0, 50.0, 1000.0, 50.0);
    private final NumberSetting maxBoxes = new NumberSetting("Max boxes", "Boxes drawn at once", 300.0, 25.0, 1500.0, 25.0);
    private final BooleanSetting fill = new BooleanSetting("Fill", true);
    private final NumberSetting fillOpacity = new NumberSetting("Fill opacity", "Fill alpha", 40.0, 10.0, 200.0, 10.0);
    private final BooleanSetting merge = new BooleanSetting("One box per stash", true);
    private final BooleanSetting labels = new BooleanSetting("Labels", true);

    /** Bitta topilgan uyum: gapirilgan joy (markaz) va bloklar soni (Kuro'da Finds.Find o'rnida). */
    public record Find(int x, int y, int z, int count) {}

    /** Topilgan hamma uyum — {@link #listFinds()} uchun saqlanadi (Kuro'dagi Finds "Stash" ro'yxatini almashtiradi). */
    private final List<Find> stashFinds = new ArrayList<>();
    private final Set<BlockPos> reported = new HashSet<>();

    /** Bir uyum: markazi va chegarasi bir marta shakllangan, chizish raqamlar bilan aylantiradi. */
    private record Stash(List<BlockPos> blocks, double cx, double cy, double cz, int x1, int y1, int z1, int x2, int y2, int z2) {
        static Stash of(List<BlockPos> g) {
            int x1 = Integer.MAX_VALUE, y1 = Integer.MAX_VALUE, z1 = Integer.MAX_VALUE, x2 = Integer.MIN_VALUE, y2 = Integer.MIN_VALUE, z2 = Integer.MIN_VALUE;
            for (BlockPos p : g) {
                x1 = Math.min(x1, p.getX()); y1 = Math.min(y1, p.getY()); z1 = Math.min(z1, p.getZ());
                x2 = Math.max(x2, p.getX()); y2 = Math.max(y2, p.getY()); z2 = Math.max(z2, p.getZ());
            }
            return new Stash(g, (x1 + x2 + 1) / 2.0, (y1 + y2 + 1) / 2.0, (z1 + z2 + 1) / 2.0, x1, y1, z1, x2, y2, z2);
        }
    }

    private final List<Stash> groups = new ArrayList<>();
    private World lastWorld;
    private int timer, sortTimer;
    private boolean registered;

    public KuroStashFinder() {
        super("Stash Finder", "Finds clusters of storage blocks in loaded chunks", Category.KURO);
        addSetting(minChests);
        addSetting(radius);
        addSetting(range);
        addSetting(barrels);
        addSetting(ignoreDungeons);
        addSetting(highlight);
        addSetting(highlightRange);
        addSetting(maxBoxes);
        addSetting(fill);
        addSetting(fillOpacity);
        addSetting(merge);
        addSetting(labels);
    }

    @Override
    public void onEnable() {
        reported.clear();
        timer = 1000;
        if (registered) return;
        registered = true;
        WorldRenderEvents.END_MAIN.register(this::dispatchWorld);
    }

    @Override
    public void onDisable() {
        reported.clear();
        groups.clear();
        stashFinds.clear();
    }

    @Override
    public void onTick() {
        if (mc.world == null || mc.player == null) return;
        if (mc.world != lastWorld) {                     // yangi dunyo / o'lcham
            lastWorld = mc.world;
            reported.clear();
            groups.clear();
            stashFinds.clear();
        }
        if (++sortTimer >= 10 && groups.size() > 1) {    // eng yaqin uyum birinchi: limit yaqinlarni saqlaydi
            sortTimer = 0;
            groups.sort(Comparator.comparingDouble(st -> mc.player.squaredDistanceTo(st.cx(), st.cy(), st.cz())));
        }
        if (++timer < 40) return;                        // taxminan har 2 soniyada skan
        timer = 0;

        List<BlockPos> stores = new ArrayList<>();
        List<BlockPos> spawners = new ArrayList<>();
        ChunkPos cp = new ChunkPos(mc.player.getBlockPos());
        int rad = range.getValue().intValue();
        for (int dx = -rad; dx <= rad; dx++) {
            for (int dz = -rad; dz <= rad; dz++) {
                net.minecraft.world.chunk.Chunk chunk;
                try { chunk = mc.world.getChunk(cp.x + dx, cp.z + dz); } catch (RuntimeException e) { continue; }
                if (!(chunk instanceof WorldChunk wc) || wc.isEmpty()) continue;
                for (Map.Entry<BlockPos, BlockEntity> be : wc.getBlockEntities().entrySet()) {
                    BlockPos pos = be.getKey();
                    BlockEntity b = be.getValue();
                    if (b instanceof MobSpawnerBlockEntity || b instanceof TrialSpawnerBlockEntity || b instanceof VaultBlockEntity) {
                        spawners.add(pos);
                        continue;
                    }
                    boolean store = b instanceof ChestBlockEntity
                            || (barrels.isEnabled() && (b instanceof BarrelBlockEntity || b instanceof ShulkerBoxBlockEntity));
                    if (!store) continue;
                    var st = b.getCachedState();
                    // ikki qavatli sandiq ikki blok-entity: bir marta hisoblanadi
                    if (st.contains(net.minecraft.block.ChestBlock.CHEST_TYPE)
                            && st.get(net.minecraft.block.ChestBlock.CHEST_TYPE) == ChestType.RIGHT) continue;
                    stores.add(pos);
                    if (stores.size() >= 3000) break;
                }
            }
        }
        if (stores.size() < minChests.getValue().intValue()) return;

        double r2 = radius.getValue() * radius.getValue();
        Set<BlockPos> used = new HashSet<>();
        for (BlockPos c : stores) {
            if (used.contains(c)) continue;
            List<BlockPos> group = new ArrayList<>();
            for (BlockPos o : stores) if (c.getSquaredDistance(o) <= r2) group.add(o);
            if (group.size() < minChests.getValue().intValue()) continue;
            used.addAll(group);

            boolean seen = false;
            for (BlockPos g : group) if (reported.contains(g)) { seen = true; break; }
            if (seen) continue;

            if (ignoreDungeons.isEnabled() && nearSpawner(group, spawners)) continue;
            reported.addAll(group);
            groups.add(Stash.of(group));
            announce(group);
        }
    }

    private void dispatchWorld(WorldRenderContext wrc) {
        if (!isEnabled() || !highlight.isEnabled() || mc.world == null) return;
        List<Stash> snapshot = new ArrayList<>(groups);
        if (snapshot.isEmpty()) return;
        Camera camera = wrc.gameRenderer().getCamera();
        if (camera == null || !camera.isReady()) return;
        MatrixStack matrices = wrc.matrices();
        VertexConsumerProvider consumers = wrc.consumers();
        if (matrices == null || consumers == null) return;
        Vec3d c = camera.getCameraPos();
        double max2 = highlightRange.getValue() * highlightRange.getValue();
        int limit = maxBoxes.getValue().intValue(), drawn = 0;
        int rgb = RGB & 0xFFFFFF;
        int line = 0xFF000000 | rgb;
        WorldRender3D.begin(matrices, camera);
        try {
            for (Stash st : snapshot) {
                if (drawn >= limit) break;
                double dx = st.cx() - c.x, dy = st.cy() - c.y, dz = st.cz() - c.z;
                if (dx * dx + dy * dy + dz * dz > max2) continue;
                if (merge.isEnabled()) {
                    drawn++;
                    drawStash(consumers, st.x1(), st.y1(), st.z1(), st.z2(), rgb, true);
                    continue;
                }
                for (BlockPos p : st.blocks()) {
                    if (drawn >= limit) break;
                    drawn++;
                    drawStash(consumers, p.getX(), p.getY(), p.getZ(), p.getZ(), rgb, false);
                }
            }
        } finally {
            WorldRender3D.end(matrices);
        }
    }

    private void drawStash(VertexConsumerProvider consumers, int x, int y, int z, int z2, int rgb, boolean merged) {
        Box box = merged
                ? new Box(x, y, z, x + 1, y + 1, z2 + 1)
                : new Box(x, y, z, x + 1, y + 1, z + 1);
        if (fill.isEnabled()) WorldRender3D.drawFilledBoxExactAlpha(consumers, box, (fillOpacity.getValue().intValue() << 24) | rgb);
        WorldRender3D.drawBox(consumers, box, 0xFF000000 | rgb);
    }

    /** HUD pass: faqat 2D qismlar (har uyumga bitta kichik teg); older qutular displaysWorld(). */
    @Override
    public void onRender2D(net.minecraft.client.gui.DrawContext g, float tickDelta) {
        if (!isEnabled() || !highlight.isEnabled() || !labels.isEnabled() || mc.world == null || mc.player == null) return;
        var snapshot = new ArrayList<>(groups);
        if (snapshot.isEmpty()) return;
        com.anormal.client.util.ProjectionUtil.beginFrame(tickDelta);
        Vec3d c = com.anormal.client.util.ProjectionUtil.eye(tickDelta);
        double max2 = highlightRange.getValue() * highlightRange.getValue();
        net.minecraft.client.font.TextRenderer tr = mc.textRenderer;
        int shown = 0;
        for (Stash st : snapshot) {
            if (shown >= 12) break;
            double dx = st.cx() - c.x, dy = st.cy() - c.y, dz = st.cz() - c.z;
            double d2 = dx * dx + dy * dy + dz * dz;
            if (d2 > max2) continue;
            int[] o = com.anormal.client.util.ProjectionUtil.project(new Vec3d(st.cx(), st.y1() + 2.3, st.cz()), tickDelta);
            if (o == null) continue;
            shown++;
            String text = "STASH x" + st.blocks().size() + "  " + Math.round(Math.sqrt(d2)) + "m";
            int w = tr.getWidth(text);
            g.fill(o[0] - w / 2 - 4, o[1] - 10, o[0] + w / 2 + 4, o[1] + 2, 0xB0101214);
            g.fill(o[0] - w / 2 - 4, o[1] - 10, o[0] - w / 2 - 1, o[1] + 2, 0xFF000000 | RGB);
            com.anormal.client.util.RenderUtils.drawText(g, tr, text, o[0] - w / 2, o[1] - 7, 0xFFE8E8F0, false);
        }
    }

    private boolean nearSpawner(List<BlockPos> group, List<BlockPos> spawners) {
        double r2 = (radius.getValue() / 2 + 6) * (radius.getValue() / 2 + 6);
        for (BlockPos s : spawners)
            for (BlockPos g : group) if (s.getSquaredDistance(g) <= r2) return true;
        return false;
    }

    private void announce(List<BlockPos> group) {
        double sx = 0, sy = 0, sz = 0;
        for (BlockPos p : group) { sx += p.getX(); sy += p.getY(); sz += p.getZ(); }
        stashFinds.add(new Find((int) (sx / group.size()), (int) (sy / group.size()), (int) (sz / group.size()), group.size()));
        if (mc.player == null) return;
        mc.player.sendMessage(Text.literal("[Stash Finder] ").formatted(Formatting.GOLD)
                .append(Text.literal(group.size() + " storage blocks within " + radius.getValue().intValue() + " blocks:")
                        .formatted(Formatting.YELLOW)), false);
        int shown = 0;
        for (BlockPos p : group) {
            if (shown++ >= 10) break;
            mc.player.sendMessage(Text.literal("  " + p.getX() + " " + p.getY() + " " + p.getZ()).formatted(Formatting.WHITE), false);
        }
        if (group.size() > 10) {
            mc.player.sendMessage(Text.literal("  ... and " + (group.size() - 10) + " more").formatted(Formatting.GRAY), false);
        }
    }

    /** Kuro'dagi chatdagi Finds ro'yxati o'rnida: barcha saqlangan topilganlarni chatga chiqaradi (KuroFoundList chaqiradi). */
    public void listFinds() {
        if (stashFinds.isEmpty()) { say("No stashes found yet."); return; }
        say("Logged stash finds (" + stashFinds.size() + "), newest last:");
        int shown = 0;
        for (int i = stashFinds.size() - 1; i >= 0 && shown < 15; i--, shown++) {
            Find f = stashFinds.get(i);
            say("  " + f.x() + " " + f.y() + " " + f.z() + "   (x" + f.count() + ")");
        }
        if (stashFinds.size() > shown) say("  ... and " + (stashFinds.size() - shown) + " older finds");
    }

    private static void say(String s) {
        if (mc.player != null) mc.player.sendMessage(Text.literal("[Stash Finder] " + s), false);
    }
}
