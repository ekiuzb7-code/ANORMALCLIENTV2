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
import net.minecraft.util.math.Box;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/**
 * Spectre port: "Player Bypass" — flags chunks with signs of underground player bases.
 *
 * <p>Spectre watches block-light packets below the surface and remembers the chunk light
 * snapshots. This port keeps the exposed-light idea (a light source under the surface that
 * cannot be sky light), the chunk colour and opacity, the tracers, the sound and coordinate
 * alerts and the auto-disconnect option, scanning the sections it can read instead of the
 * packet stream.</p>
 */
public final class SpectrePlayerBypass extends Module {
    private static final int SCAN_BUDGET = 4096;
    private static final int RESCAN_TICKS = 80;
    private static final Set<net.minecraft.block.Block> LIGHT_SOURCES = Set.of(
            Blocks.TORCH, Blocks.WALL_TORCH, Blocks.LANTERN, Blocks.SOUL_LANTERN, Blocks.SOUL_TORCH,
            Blocks.SOUL_WALL_TORCH, Blocks.GLOWSTONE, Blocks.SHROOMLIGHT, Blocks.SEA_LANTERN,
            Blocks.JACK_O_LANTERN, Blocks.CAMPFIRE, Blocks.SOUL_CAMPFIRE, Blocks.REDSTONE_LAMP,
            Blocks.OCHRE_FROGLIGHT, Blocks.VERDANT_FROGLIGHT, Blocks.PEARLESCENT_FROGLIGHT
    );

    public final ColorSetting chunkColor = addSetting(new ColorSetting("Chunk Color", "Flagged chunk colour", ColorUtils.rgba(60, 200, 120, 255)));
    public final NumberSetting opacity = addSetting(new NumberSetting("Opacity", "Marker opacity", 140.0, 10.0, 255.0, 1.0));
    public final BooleanSetting tracers = addSetting(new BooleanSetting("Tracers", "Draw a beam over flagged chunks", false));
    public final BooleanSetting sound = addSetting(new BooleanSetting("Sound", "Play a note on a new flag", true));
    public final BooleanSetting showCoordinates = addSetting(new BooleanSetting("Show Coordinates", "Print the chunk centre", true));
    public final BooleanSetting disconnect = addSetting(new BooleanSetting("Auto Disconnect", "Warn in chat when a base is flagged", false));
    public final NumberSetting depth = addSetting(new NumberSetting("Depth", "Scan below this Y", 48.0, -64.0, 120.0, 1.0));

    private final ArrayDeque<Long> queue = new ArrayDeque<>();
    private final Set<Long> flagged = new HashSet<>();
    private int tick;

    public SpectrePlayerBypass() {
        super("SpectrePlayerBypass", "Spectre port: flags chunks with signs of underground player bases.", Category.SPECTRE);
    }

    @Override
    public void onEnable() {
        queue.clear();
        flagged.clear();
        tick = 0;
    }

    @Override
    public void onDisable() {
        queue.clear();
        flagged.clear();
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
        int floor = (int) depth.getAsDouble();
        outer:
        while (!queue.isEmpty() && budget > 0) {
            long key = queue.poll();
            WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(unpackX(key), unpackZ(key));
            if (chunk == null) continue;
            ChunkSection[] sections = chunk.getSectionArray();
            int bottom = mc.world.getBottomSectionCoord();
            boolean found = false;
            for (int i = 0; i < sections.length && !found; i++) {
                ChunkSection section = sections[i];
                if (section == null || section.isEmpty()) continue;
                for (int y = 0; y < 16 && !found; y++) {
                    int worldY = ((bottom + i) << 4) + y;
                    if (worldY >= floor) continue;
                    for (int z = 0; z < 16 && !found; z++) {
                        for (int x = 0; x < 16; x++) {
                            if (budget-- <= 0) break outer;
                            if (LIGHT_SOURCES.contains(section.getBlockState(x, y, z).getBlock())) {
                                found = true;
                                break;
                            }
                        }
                    }
                }
            }
            if (!found) continue;
            if (flagged.add(key) && sound.isEnabled() && mc.player != null) {
                mc.player.playSound(net.minecraft.sound.SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), 0.7F, 1.2F);
                if (mc.inGameHud != null) {
                    int cx = (unpackX(key) << 4) + 8;
                    int cz = (unpackZ(key) << 4) + 8;
                    mc.inGameHud.getChatHud().addMessage(Text.literal("[SpectrePlayerBypass] Underground light at chunk "
                            + unpackX(key) + ", " + unpackZ(key) + (showCoordinates.isEnabled() ? " (" + cx + ", " + cz + ")" : "")));
                    if (disconnect.isEnabled()) {
                        mc.inGameHud.getChatHud().addMessage(Text.literal("[SpectrePlayerBypass] Auto Disconnect would trigger here."));
                    }
                }
            }
        }
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (flagged.isEmpty() || mc.player == null) return;
        int color = chunkColor.getColor();
        int line = (color & 0x00FFFFFF) | (Math.min(255, (int) opacity.getAsDouble()) << 24);
        int fill = (color & 0x00FFFFFF) | (Math.min(255, (int) (opacity.getAsDouble() / 3.0)) << 24);
        for (long key : flagged) {
            int cx = unpackX(key);
            int cz = unpackZ(key);
            double x = cx * 16.0;
            double z = cz * 16.0;
            if (Math.abs(x + 8 - mc.player.getX()) > 272 || Math.abs(z + 8 - mc.player.getZ()) > 272) continue;
            Box slab = new Box(x, 64.0, z, x + 16.0, 65.0, z + 16.0);
            SpectreHoleEsp.drawBox(context, tickDelta, slab, line, fill, true, true, 1);
            if (tracers.isEnabled()) {
                SpectreHoleEsp.drawBox(context, tickDelta, new Box(x + 7.8, 64.5, z + 7.8, x + 8.2, 250.0, z + 8.2), line, 0, true, false, 1);
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
