package com.anormal.client.module.impl.spectre;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.ColorUtils;
import net.minecraft.block.Blocks;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Spectre port: "Chunk Scanner" — marks suspicious chunks with signs of player activity.
 *
 * <p>Spectre reads stack height and blob patterns from the chunk sections. This port keeps
 * the sensitivity (stack height), spread radius, chunk colour, tracers and the sound
 * announcement, scoring chunks by the artificial blocks stacked low in the chunk.</p>
 *
 * <p>The sweep is section-atomic: a section is either counted completely or left for the
 * next tick. The old code spent its block budget inside a single section and then dropped
 * the half-counted chunk, so most chunks were never evaluated at all.</p>
 */
public final class SpectreChunkScanner extends Module {
    /** Non-empty sections (4096 blocks each) evaluated per tick. */
    private static final int SECTIONS_PER_TICK = 4;
    private static final int RESCAN_TICKS = 60;
    private static final Set<net.minecraft.block.Block> ARTIFICIAL = Set.of(
            Blocks.COBBLESTONE, Blocks.OAK_PLANKS, Blocks.SPRUCE_PLANKS, Blocks.STONE_BRICKS,
            Blocks.CHEST, Blocks.FURNACE, Blocks.TORCH, Blocks.LADDER, Blocks.GLASS, Blocks.DIRT
    );

    public final NumberSetting stackHeight = addSetting(new NumberSetting("Sensitivity", "Artificial blocks needed to flag", 2.0, 2.0, 5.0, 1.0));
    public final NumberSetting spreadRadius = addSetting(new NumberSetting("Spread Radius", "Extra chunks around you", 1.0, 0.0, 16.0, 1.0));
    public final NumberSetting minY = addSetting(new NumberSetting("Min Y", "Lowest Y scanned", 0.0, -64.0, 64.0, 1.0));
    public final ColorSetting chunkColor = addSetting(new ColorSetting("Chunk Color", "Chunk marker", ColorUtils.rgba(255, 160, 60, 90)));
    public final BooleanSetting tracers = addSetting(new BooleanSetting("Tracers", "Draw beams over flagged chunks", true));
    public final BooleanSetting sound = addSetting(new BooleanSetting("Sound", "Play a note on a new flag", true));

    private final ArrayDeque<Long> queue = new ArrayDeque<>();
    private final Set<Long> flagged = new HashSet<>();
    private final Map<Long, Integer> counts = new HashMap<>();
    private final Map<Long, Integer> progress = new HashMap<>();
    private int tick;

    public SpectreChunkScanner() {
        super("SpectreChunkScanner", "Spectre port: marks suspicious chunks with signs of player activity.", Category.SPECTRE);
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
            int radius = Math.min(mc.options.getViewDistance().getValue(), 8) + (int) spreadRadius.getAsDouble();
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
        int floor = (int) minY.getAsDouble();
        int bottom = mc.world.getBottomSectionCoord();
        while (!queue.isEmpty() && sectionsThisTick < SECTIONS_PER_TICK) {
            long key = queue.poll();
            WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(unpackX(key), unpackZ(key));
            if (chunk == null) {
                counts.remove(key);
                progress.remove(key);
                continue;
            }
            int count = counts.getOrDefault(key, 0);
            ChunkSection[] sections = chunk.getSectionArray();
            int i = progress.getOrDefault(key, 0);
            for (; i < sections.length; i++) {
                ChunkSection section = sections[i];
                if (section == null || section.isEmpty()) continue;
                if (((bottom + i) << 4) + 15 < floor) continue;
                if (sectionsThisTick >= SECTIONS_PER_TICK) break;
                sectionsThisTick++;
                for (int y = 0; y < 16; y++) {
                    int worldY = ((bottom + i) << 4) + y;
                    if (worldY < floor) continue;
                    for (int z = 0; z < 16; z++) {
                        for (int x = 0; x < 16; x++) {
                            if (ARTIFICIAL.contains(section.getBlockState(x, y, z).getBlock())) count++;
                        }
                    }
                }
            }

            if (i < sections.length) {
                counts.put(key, count);
                progress.put(key, i);
                queue.addLast(key);
            } else {
                counts.remove(key);
                progress.remove(key);
                if (count >= (int) stackHeight.getAsDouble()) {
                    if (flagged.add(key) && sound.isEnabled() && mc.player != null) {
                        mc.player.playSound(net.minecraft.sound.SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), 0.6F, 1.4F);
                        if (mc.inGameHud != null) {
                            mc.inGameHud.getChatHud().addMessage(Text.literal(
                                    "[SpectreChunkScanner] Suspicious chunk " + unpackX(key) + ", " + unpackZ(key)));
                        }
                    }
                }
            }
        }
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (flagged.isEmpty() || mc.player == null) return;
        int color = chunkColor.getColor();
        double y = Math.floor(mc.player.getY());
        for (long key : flagged) {
            int cx = unpackX(key);
            int cz = unpackZ(key);
            if (Math.abs(cx * 16.0 + 8 - mc.player.getX()) > 256
                    || Math.abs(cz * 16.0 + 8 - mc.player.getZ()) > 256) continue;
            SpectreHoleEsp.drawChunkMarker(context, tickDelta, cx, cz, y, color, tracers.isEnabled());
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
