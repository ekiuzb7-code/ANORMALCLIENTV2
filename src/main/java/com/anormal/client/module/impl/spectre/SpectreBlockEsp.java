package com.anormal.client.module.impl.spectre;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.MultiSelectSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.ProjectionUtil;
import com.anormal.client.util.RenderUtils;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Spectre port: "Block ESP" — highlights chosen blocks found in loaded chunks.
 *
 * <p>Spectre keeps a live layer fed by its chunk scanner. This port sweeps the nearby chunk
 * sections with a persistent cursor and a fixed section budget per tick, so a cycle covers
 * <b>every</b> section in range instead of only the first few thousand blocks.</p>
 *
 * <p>Results are published <b>as the sweep runs</b> and a new sweep starts on a short timer even
 * when the player stands still. The old version only swapped results in when a whole cycle
 * finished and only restarted a cycle when the player crossed a chunk border, which is why a
 * chest placed in front of you never appeared: nothing ever re-scanned that area. Stale entries
 * (mined, replaced, unloaded or out of range) are pruned every tick.</p>
 */
public final class SpectreBlockEsp extends Module {
    /** Non-empty sections scanned per tick; each one costs 4096 block lookups. */
    private static final int SECTIONS_PER_TICK = 8;
    /** Ticks to wait between two sweeps, so a fresh block shows up within about a second. */
    private static final int SWEEP_COOLDOWN = 15;
    private static final int MAX_BLOCKS = 2000;

    public final MultiSelectSetting kinds = addSetting(new MultiSelectSetting("Blocks",
            "Which block kinds to highlight", new String[]{"Ancient Debris"},
            "Chest", "Ender Chest", "Ancient Debris", "Emerald", "Diamond"));
    public final BooleanSetting tracers = addSetting(new BooleanSetting("Tracers", "Draw a line to each found block", false));
    public final NumberSetting range = addSetting(new NumberSetting("Range", "Horizontal scan radius (chunks)", 4.0, 1.0, 12.0, 1.0));

    // packed BlockPos -> argb
    private final Map<Long, Integer> found = new HashMap<>();
    private final List<ChunkSectionPos> sections = new ArrayList<>();
    private int sectionCursor;
    private int cooldown;

    public SpectreBlockEsp() {
        super("SpectreBlockEsp", "Spectre port: highlights chosen blocks; touching blocks merge into one shape.", Category.SPECTRE);
    }

    @Override
    public void onEnable() {
        reset();
    }

    @Override
    public void onDisable() {
        reset();
    }

    private void reset() {
        found.clear();
        sections.clear();
        sectionCursor = 0;
        cooldown = 0;
    }

    @Override
    public void onTick() {
        ClientWorld world = mc.world;
        if (world == null || mc.player == null) {
            reset();
            return;
        }
        if (!kinds.anySelected()) {
            found.clear();
            return;
        }

        // Drop anything that was mined, replaced, unloaded or is now out of range.
        prune(world);

        if (sections.isEmpty()) {
            if (cooldown-- > 0) return;
            // Start a new sweep. The section list is rebuilt every time, so a block placed into a
            // previously empty section is found as well.
            rebuildSectionList(world);
            cooldown = 0;
            return;
        }
        scan(world);
    }

    private void prune(ClientWorld world) {
        if (found.isEmpty()) return;
        double px = mc.player.getX();
        double py = mc.player.getY();
        double pz = mc.player.getZ();
        double reach = range.getAsDouble() * 16.0 + 16.0;
        double maxSq = reach * reach;
        found.keySet().removeIf(key -> {
            BlockPos p = BlockPos.fromLong(key);
            if (!world.isChunkLoaded(p)) return true;
            if (blockColor(world.getBlockState(p).getBlock()) == 0) return true;
            double dx = p.getX() + 0.5 - px;
            double dy = p.getY() + 0.5 - py;
            double dz = p.getZ() + 0.5 - pz;
            return dx * dx + dy * dy + dz * dz > maxSq;
        });
    }

    private void scan(ClientWorld world) {
        int bottom = world.getBottomSectionCoord();
        int scanned = 0;
        while (scanned < SECTIONS_PER_TICK && sectionCursor < sections.size()) {
            ChunkSectionPos spos = sections.get(sectionCursor++);
            WorldChunk chunk = world.getChunkManager().getWorldChunk(spos.getX(), spos.getZ());
            if (chunk == null) continue;
            int index = spos.getY() - bottom;
            ChunkSection[] all = chunk.getSectionArray();
            if (index < 0 || index >= all.length) continue;
            ChunkSection section = all[index];
            if (section == null || section.isEmpty()) continue;

            scanned++;
            BlockPos.Mutable pos = new BlockPos.Mutable();
            for (int x = 0; x < 16; x++) {
                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        int color = blockColor(section.getBlockState(x, y, z).getBlock());
                        if (color == 0) continue;
                        pos.set(spos.getMinX() + x, spos.getMinY() + y, spos.getMinZ() + z);
                        // Published immediately: a block placed while the sweep runs shows up
                        // on the very tick its section is read instead of after a full pass.
                        if (found.size() < MAX_BLOCKS) found.put(pos.asLong(), color);
                    }
                }
            }
        }

        if (sectionCursor >= sections.size()) {
            sections.clear();
            sectionCursor = 0;
            cooldown = SWEEP_COOLDOWN;
        }
    }

    /** Sections of the chunks in range, nearest chunk first, so close finds show up early. */
    private void rebuildSectionList(ClientWorld world) {
        sections.clear();
        sectionCursor = 0;
        List<ChunkSectionPos> list = new ArrayList<>();
        int radius = (int) range.getAsDouble();
        int pcx = mc.player.getChunkPos().x;
        int pcz = mc.player.getChunkPos().z;
        int bottom = world.getBottomSectionCoord();
        int top = world.getTopSectionCoord();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                WorldChunk chunk = world.getChunkManager().getWorldChunk(pcx + dx, pcz + dz);
                if (chunk == null) continue;
                ChunkSection[] all = chunk.getSectionArray();
                for (int sy = bottom; sy < top; sy++) {
                    int index = sy - bottom;
                    if (index < 0 || index >= all.length) continue;
                    ChunkSection section = all[index];
                    if (section == null || section.isEmpty()) continue;
                    list.add(ChunkSectionPos.from(pcx + dx, sy, pcz + dz));
                }
            }
        }
        list.sort((a, b) -> {
            int da = Math.abs(a.getX() - pcx) + Math.abs(a.getZ() - pcz);
            int db = Math.abs(b.getX() - pcx) + Math.abs(b.getZ() - pcz);
            if (da != db) return Integer.compare(da, db);
            return Integer.compare(Math.abs(a.getY() - mc.player.getBlockY()), Math.abs(b.getY() - mc.player.getBlockY()));
        });
        sections.addAll(list);
    }

    private int blockColor(Block block) {
        if (kinds.isSelected("Chest") && block == Blocks.CHEST) return 0xE8A33DFF;
        if (kinds.isSelected("Ender Chest") && block == Blocks.ENDER_CHEST) return 0x7FE8C6FF;
        if (kinds.isSelected("Ancient Debris") && block == Blocks.ANCIENT_DEBRIS) return 0xB46A43FF;
        if (kinds.isSelected("Emerald") && (block == Blocks.EMERALD_ORE || block == Blocks.DEEPSLATE_EMERALD_ORE)) return 0x17DD62FF;
        if (kinds.isSelected("Diamond") && (block == Blocks.DIAMOND_ORE || block == Blocks.DEEPSLATE_DIAMOND_ORE)) return 0x4AEDD9FF;
        return 0;
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.world == null || mc.player == null || found.isEmpty()) return;
        ProjectionUtil.beginFrame(tickDelta);

        for (Map.Entry<Long, Integer> e : found.entrySet()) {
            BlockPos p = BlockPos.fromLong(e.getKey());
            Box box = new Box(p.getX(), p.getY(), p.getZ(), p.getX() + 1, p.getY() + 1, p.getZ() + 1);
            int color = e.getValue();
            SpectreHoleEsp.drawBox(context, tickDelta, box, darken(color), translucent(color), true, true, 1);
            if (tracers.isEnabled()) {
                tracerTo(context, tickDelta, box.getCenter(), color);
            }
        }
    }

    private void tracerTo(DrawContext context, float tickDelta, Vec3d center, int color) {
        // The line starts at the crosshair: projecting the camera position itself always
        // returned null (depth 0), so tracers were invisible.
        int[] b = ProjectionUtil.project(center, tickDelta);
        if (b == null) return;
        RenderUtils.drawLine(context, RenderUtils.screenWidth() / 2, RenderUtils.screenHeight() / 2,
                b[0], b[1], darken(color), 1);
    }

    private static int darken(int argb) {
        int a = (argb >>> 24) & 0xFF;
        int r = (((argb >>> 16) & 0xFF) * 3) / 4;
        int g = (((argb >>> 8) & 0xFF) * 3) / 4;
        int b = ((argb & 0xFF) * 3) / 4;
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int translucent(int argb) {
        return darken(argb) & 0x40FFFFFF;
    }
}
