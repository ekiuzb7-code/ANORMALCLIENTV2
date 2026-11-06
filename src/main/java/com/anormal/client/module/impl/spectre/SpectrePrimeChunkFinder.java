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
 * Spectre port: "Prime Chunk Finder" — flags chunks with light and redstone changes below Y 0.
 *
 * <p>Spectre watches light and block-update packets under y=0. This port keeps the below-surface
 * requirement and the chunk colour, tracers and sound, scanning the sections under y=0 for
 * powered/light-emitting redstone blocks.</p>
 *
 * <p>Like the other chunk scanners it now counts one section at a time and carries the
 * remaining sections to the next tick, so a chunk is judged on its full contents.</p>
 */
public final class SpectrePrimeChunkFinder extends Module {
    /** Non-empty sections (4096 blocks each) evaluated per tick. */
    private static final int SECTIONS_PER_TICK = 4;
    private static final int RESCAN_TICKS = 60;
    private static final Set<net.minecraft.block.Block> REDSTONE = Set.of(
            Blocks.REDSTONE_LAMP, Blocks.REDSTONE_TORCH, Blocks.REDSTONE_WALL_TORCH, Blocks.REPEATER,
            Blocks.COMPARATOR, Blocks.OBSERVER, Blocks.PISTON, Blocks.STICKY_PISTON,
            Blocks.REDSTONE_BLOCK, Blocks.DAYLIGHT_DETECTOR, Blocks.LEVER, Blocks.NOTE_BLOCK);

    public final ColorSetting chunkColor = addSetting(new ColorSetting("Chunk Color", "Chunk marker", ColorUtils.rgba(255, 200, 60, 110)));
    public final BooleanSetting tracers = addSetting(new BooleanSetting("Tracers", "Draw beams over flagged chunks", true));
    public final BooleanSetting sound = addSetting(new BooleanSetting("Sound", "Play a note on a new flag", true));
    public final NumberSetting minRedstone = addSetting(new NumberSetting("Min Redstone", "Redstone blocks needed to flag", 1.0, 1.0, 8.0, 1.0));

    private final ArrayDeque<Long> queue = new ArrayDeque<>();
    private final Set<Long> flagged = new HashSet<>();
    private final Map<Long, Integer> counts = new HashMap<>();
    private final Map<Long, Integer> progress = new HashMap<>();
    private int tick;

    public SpectrePrimeChunkFinder() {
        super("SpectrePrimeChunkFinder", "Spectre port: flags chunks with light and redstone activity below Y 0.", Category.SPECTRE);
    }

    @Override
    public void onEnable() {
        resetScan();
        tick = 0;
        if (mc.player != null && mc.player.getY() >= 0.0) {
            if (mc.inGameHud != null) {
                mc.inGameHud.getChatHud().addMessage(Text.literal("[SpectrePrimeChunkFinder] Works below Y 0 only."));
            }
            setEnabled(false);
        }
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
        if (mc.player.getY() >= 0.0) return;
        if (queue.isEmpty()) {
            if (++tick < RESCAN_TICKS) return;
            tick = 0;
            flagged.clear();
            counts.clear();
            progress.clear();
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

        int sectionsThisTick = 0;
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
                if (((bottom + i) << 4) >= 0) continue;
                if (sectionsThisTick >= SECTIONS_PER_TICK) break;
                sectionsThisTick++;
                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        for (int x = 0; x < 16; x++) {
                            if (REDSTONE.contains(section.getBlockState(x, y, z).getBlock())) count++;
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
                if (count >= (int) minRedstone.getAsDouble()) {
                    if (flagged.add(key) && sound.isEnabled() && mc.player != null) {
                        mc.player.playSound(net.minecraft.sound.SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), 0.7F, 0.8F);
                        if (mc.inGameHud != null) {
                            mc.inGameHud.getChatHud().addMessage(Text.literal(
                                    "[SpectrePrimeChunkFinder] Redstone below 0 at chunk " + unpackX(key) + ", " + unpackZ(key)));
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
