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
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Spectre port: "Sus Chunk Finder" — coloured markers for containers, machines, redstone and
 * base clues.
 *
 * <p>Spectre scores chunks from packet heat and object scans. This port keeps the method
 * switch, the simulation radius, the sensitivity threshold and the per-category toggles and
 * colours, scoring each chunk from the suspect blocks it actually contains.</p>
 *
 * <p>Each chunk's counts are accumulated across ticks with a section cursor, so a chunk is
 * always scored from its full contents. Previously the block budget ran out inside the first
 * section and the half-counted chunk was thrown away, which is why nothing was ever flagged.</p>
 */
public final class SpectreSusChunkFinder extends Module {
    /** Non-empty sections (4096 blocks each) evaluated per tick. */
    private static final int SECTIONS_PER_TICK = 4;
    private static final int RESCAN_TICKS = 60;

    private static final Set<net.minecraft.block.Block> CONTAINERS = Set.of(
            Blocks.CHEST, Blocks.TRAPPED_CHEST, Blocks.BARREL, Blocks.SHULKER_BOX, Blocks.ENDER_CHEST, Blocks.HOPPER);
    private static final Set<net.minecraft.block.Block> MACHINES = Set.of(
            Blocks.FURNACE, Blocks.BLAST_FURNACE, Blocks.SMOKER, Blocks.DISPENSER, Blocks.DROPPER,
            Blocks.BREWING_STAND, Blocks.ENCHANTING_TABLE, Blocks.ANVIL, Blocks.CRAFTER);
    private static final Set<net.minecraft.block.Block> REDSTONE = Set.of(
            Blocks.REDSTONE_WIRE, Blocks.REDSTONE_TORCH, Blocks.REDSTONE_WALL_TORCH, Blocks.REPEATER,
            Blocks.COMPARATOR, Blocks.OBSERVER, Blocks.PISTON, Blocks.STICKY_PISTON, Blocks.LEVER,
            Blocks.NOTE_BLOCK, Blocks.TRIPWIRE_HOOK, Blocks.REDSTONE_LAMP, Blocks.DAYLIGHT_DETECTOR);

    public final ModeSetting method = addSetting(new ModeSetting("Method", "Scoring method", "Classic", "Classic", "ANBS+ Scan"));
    public final NumberSetting spreadRadius = addSetting(new NumberSetting("Simulation Distance", "Chunk radius to scan", 5.0, 2.0, 16.0, 1.0));
    public final NumberSetting sensitivity = addSetting(new NumberSetting("Sensitivity", "Suspect blocks needed to flag a chunk", 6.0, 1.0, 20.0, 1.0));
    public final BooleanSetting containers = addSetting(new BooleanSetting("Containers", "Count chests, barrels and hoppers", true));
    public final BooleanSetting machines = addSetting(new BooleanSetting("Machines", "Count furnaces, dispensers and anvils", true));
    public final BooleanSetting redstone = addSetting(new BooleanSetting("Redstone", "Count redstone components", true));
    public final ColorSetting containerColor = addSetting(new ColorSetting("Container color", "Container marker", ColorUtils.rgba(255, 200, 60, 255)));
    public final ColorSetting machineColor = addSetting(new ColorSetting("Machine color", "Machine marker", ColorUtils.rgba(120, 200, 255, 255)));
    public final ColorSetting redstoneColor = addSetting(new ColorSetting("Redstone color", "Redstone marker", ColorUtils.rgba(255, 90, 90, 255)));

    private final ArrayDeque<Long> queue = new ArrayDeque<>();
    private final Map<Long, int[]> flagged = new HashMap<>();
    private final Map<Long, int[]> counts = new HashMap<>();
    private final Map<Long, Integer> progress = new HashMap<>();
    private int tick;

    public SpectreSusChunkFinder() {
        super("SpectreSusChunkFinder", "Spectre port: coloured markers for containers, machines, redstone and base clues.", Category.SPECTRE);
    }

    @Override
    public void onEnable() {
        resetScan();
        tick = 0;
    }

    @Override
    public void onDisable() {
        resetScan();
    }

    private void resetScan() {
        queue.clear();
        flagged.clear();
        counts.clear();
        progress.clear();
    }

    @Override
    public void onTick() {
        if (mc.world == null || mc.player == null) {
            resetScan();
            return;
        }
        if (queue.isEmpty()) {
            if (++tick < RESCAN_TICKS) return;
            tick = 0;
            flagged.clear();
            counts.clear();
            progress.clear();
            int radius = (int) spreadRadius.getAsDouble();
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

        int sectionsThisTick = 0;
        boolean wantContainers = containers.isEnabled();
        boolean wantMachines = machines.isEnabled();
        boolean wantRedstone = redstone.isEnabled();
        while (!queue.isEmpty() && sectionsThisTick < SECTIONS_PER_TICK) {
            long key = queue.poll();
            WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(unpackX(key), unpackZ(key));
            if (chunk == null) {
                counts.remove(key);
                progress.remove(key);
                continue;
            }
            int[] acc = counts.computeIfAbsent(key, k -> new int[3]);
            ChunkSection[] sections = chunk.getSectionArray();
            int i = progress.getOrDefault(key, 0);
            for (; i < sections.length; i++) {
                ChunkSection section = sections[i];
                if (section == null || section.isEmpty()) continue;
                if (sectionsThisTick >= SECTIONS_PER_TICK) break;
                sectionsThisTick++;
                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        for (int x = 0; x < 16; x++) {
                            var block = section.getBlockState(x, y, z).getBlock();
                            if (wantContainers && CONTAINERS.contains(block)) acc[0]++;
                            else if (wantMachines && MACHINES.contains(block)) acc[1]++;
                            else if (wantRedstone && REDSTONE.contains(block)) acc[2]++;
                        }
                    }
                }
            }

            if (i < sections.length) {
                progress.put(key, i);
                queue.addLast(key);
            } else {
                counts.remove(key);
                progress.remove(key);
                // Classic counts every suspect block once; ANBS+ Scan weights the categories
                // (redstone above containers and machines) and scales the threshold with it.
                boolean anbs = method.is("ANBS+ Scan");
                int score = anbs ? acc[0] * 2 + acc[1] * 2 + acc[2] * 3
                        : acc[0] + acc[1] + acc[2];
                int needed = anbs ? (int) sensitivity.getAsDouble() * 2 : (int) sensitivity.getAsDouble();
                if (score >= needed) {
                    flagged.put(key, acc);
                }
            }
        }
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (flagged.isEmpty() || mc.player == null) return;
        double x = mc.player.getX();
        double z = mc.player.getZ();
        double y = Math.floor(mc.player.getY());
        for (Map.Entry<Long, int[]> entry : flagged.entrySet()) {
            int cx = unpackX(entry.getKey());
            int cz = unpackZ(entry.getKey());
            if (Math.abs(cx * 16.0 + 8 - x) > 256 || Math.abs(cz * 16.0 + 8 - z) > 256) continue;
            int[] c = entry.getValue();
            if (c[0] > 0) SpectreHoleEsp.drawChunkMarker(context, tickDelta, cx, cz, y, containerColor.getColor(), false);
            if (c[1] > 0) SpectreHoleEsp.drawChunkMarker(context, tickDelta, cx, cz, y + 0.12, machineColor.getColor(), false);
            if (c[2] > 0) SpectreHoleEsp.drawChunkMarker(context, tickDelta, cx, cz, y + 0.24, redstoneColor.getColor(), false);
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
