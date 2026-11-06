package com.anormal.client.module.impl.spectre;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.ColorUtils;
import net.minecraft.block.Blocks;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Spectre port: "Block Entity Debug" — renders leaked block entities as ESP.
 *
 * <p>Spectre gets its list from a Netty leak tracker; this port keeps the same idea with a
 * budgeted chunk scan over the block-entity blocks it tracks (containers, spawners, redstone
 * machines), the detection depth modes and the tracer colour rules.</p>
 */
public final class SpectreBlockEntityDebug extends Module {
    private static final int SCAN_BUDGET = 4096;
    private static final int RESCAN_TICKS = 40;
    private static final Set<net.minecraft.block.Block> TRACKED = Set.of(
            Blocks.CHEST, Blocks.TRAPPED_CHEST, Blocks.BARREL, Blocks.HOPPER, Blocks.DISPENSER,
            Blocks.DROPPER, Blocks.FURNACE, Blocks.BLAST_FURNACE, Blocks.SMOKER, Blocks.SPAWNER,
            Blocks.ENDER_CHEST, Blocks.BREWING_STAND, Blocks.ENCHANTING_TABLE, Blocks.BEACON
    );

    public final ModeSetting detection = addSetting(new ModeSetting("Detection", "Depth filter", "All", "All", "Below Zero", "Below -20", "Below -30"));
    public final BooleanSetting blockFilter = addSetting(new BooleanSetting("Block Filter", "Only track spawners and ender chests", false));
    public final BooleanSetting tracers = addSetting(new BooleanSetting("Tracers", "Draw a beam down from each block entity", true));
    public final BooleanSetting customTracerColor = addSetting(new BooleanSetting("Custom Tracer Color", "Use the tracer colour below", false));
    public final ColorSetting tracerColor = addSetting(new ColorSetting("Tracer Color", "Tracer colour", ColorUtils.rgba(255, 255, 255, 255)));
    public final ColorSetting boxColor = addSetting(new ColorSetting("Box Color", "Box colour", ColorUtils.rgba(120, 200, 255, 255)));
    public final NumberSetting range = addSetting(new NumberSetting("Range", "Maximum draw distance", 128.0, 16.0, 256.0, 1.0));
    public final NumberSetting limit = addSetting(new NumberSetting("Limit", "Maximum boxes", 256.0, 32.0, 256.0, 1.0));

    private final ArrayDeque<Long> queue = new ArrayDeque<>();
    private final Set<Long> found = new HashSet<>();
    private int tick;

    public SpectreBlockEntityDebug() {
        super("SpectreBlockEntityDebug", "Spectre port: renders tracked block entities as ESP.", Category.SPECTRE);
    }

    @Override
    public void onEnable() {
        queue.clear();
        found.clear();
        tick = 0;
    }

    @Override
    public void onDisable() {
        queue.clear();
        found.clear();
    }

    @Override
    public void onTick() {
        if (mc.world == null || mc.player == null) {
            onEnable();
            return;
        }
        if (queue.isEmpty()) {
            if (++tick < RESCAN_TICKS) return;
            tick = 0;
            found.clear();
            int radius = Math.min(mc.options.getViewDistance().getValue(), 8);
            int pcx = mc.player.getChunkPos().x;
            int pcz = mc.player.getChunkPos().z;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (mc.world.getChunkManager().getWorldChunk(pcx + dx, pcz + dz) != null) {
                        queue.add(pack(pcx + dx, pcz + dz));
                    }
                }
            }
        }

        int budget = SCAN_BUDGET;
        outer:
        while (!queue.isEmpty() && budget > 0) {
            long key = queue.poll();
            WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(unpackX(key), unpackZ(key));
            if (chunk == null) continue;
            ChunkSection[] sections = chunk.getSectionArray();
            int bottom = mc.world.getBottomSectionCoord();
            for (int i = 0; i < sections.length; i++) {
                ChunkSection section = sections[i];
                if (section == null || section.isEmpty()) continue;
                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        for (int x = 0; x < 16; x++) {
                            if (budget-- <= 0) break outer;
                            var state = section.getBlockState(x, y, z);
                            if (!TRACKED.contains(state.getBlock())) continue;
                            if (blockFilter.isEnabled() && !state.isOf(Blocks.SPAWNER) && !state.isOf(Blocks.ENDER_CHEST)) continue;
                            BlockPos pos = new BlockPos((chunk.getPos().x << 4) + x, ((bottom + i) << 4) + y, (chunk.getPos().z << 4) + z);
                            if (!passesDetection(pos)) continue;
                            found.add(pos.asLong());
                        }
                    }
                }
            }
        }
    }

    private boolean passesDetection(BlockPos pos) {
        return switch (detection.getValue()) {
            case "Below Zero" -> pos.getY() < 0;
            case "Below -20" -> pos.getY() < -20;
            case "Below -30" -> pos.getY() < -30;
            default -> true;
        };
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        var player = mc.player;
        if (player == null || found.isEmpty()) return;
        double max = range.getAsDouble();
        int cap = (int) limit.getAsDouble();
        List<BlockPos> visible = new java.util.ArrayList<>();
        for (long packed : found) {
            BlockPos pos = BlockPos.fromLong(packed);
            if (pos.getSquaredDistance(player.getBlockPos()) > max * max) continue;
            visible.add(pos);
        }
        visible.sort(Comparator.comparingDouble(pos -> pos.getSquaredDistance(player.getBlockPos())));
        int box = boxColor.getColor();
        int beam = customTracerColor.isEnabled() ? tracerColor.getColor() : box;
        int drawn = 0;
        for (BlockPos pos : visible) {
            if (drawn++ >= cap) break;
            SpectreHoleEsp.drawBox(context, tickDelta, new Box(pos), box, (box & 0x00FFFFFF) | 0x22000000, true, true, 1);
            if (tracers.isEnabled()) {
                SpectreHoleEsp.drawBox(context, tickDelta,
                        new Box(pos.getX() + 0.3, pos.getY() + 1.0, pos.getZ() + 0.3,
                                pos.getX() + 0.7, pos.getY() + 257.0, pos.getZ() + 0.7),
                        beam, 0, true, false, 1);
            }
        }
    }

    private static long pack(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    private static int unpackX(long key) {
        return (int) (key >> 32);
    }

    private static int unpackZ(long key) {
        return (int) key;
    }
}
