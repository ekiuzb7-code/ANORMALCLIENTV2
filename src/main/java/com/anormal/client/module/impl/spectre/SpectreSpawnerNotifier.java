package com.anormal.client.module.impl.spectre;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.util.ColorUtils;
import net.minecraft.block.Blocks;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/**
 * Spectre port: "Spawner Notifier" — scans chunks for mob spawners, announces and
 * highlights them.
 *
 * <p>Spectre feeds this from its shared ChunkScanner; this port runs its own budgeted
 * section scan (the same pattern as the Spectre block finders), announces each new spawner
 * once and draws a box around it.</p>
 */
public final class SpectreSpawnerNotifier extends Module {
    private static final int SCAN_BUDGET = 4096;
    private static final int RESCAN_TICKS = 40;
    private static final int COLOUR = ColorUtils.rgba(60, 220, 255, 255);

    public final BooleanSetting sound = addSetting(new BooleanSetting("Sound", "Play a note when a spawner is found", true));
    public final BooleanSetting render = addSetting(new BooleanSetting("Render", "Draw a box around found spawners", true));
    public final ColorSetting color = addSetting(new ColorSetting("Color", "Spawner box colour", ColorUtils.rgba(60, 220, 255, 255)));

    private final ArrayDeque<Long> queue = new ArrayDeque<>();
    private final Set<Long> found = new HashSet<>();
    private final Set<Long> notified = new HashSet<>();
    private int tick;

    public SpectreSpawnerNotifier() {
        super("SpectreSpawnerNotifier", "Spectre port: scans chunks for mob spawners and highlights them.", Category.SPECTRE);
    }

    @Override
    public void onEnable() {
        queue.clear();
        found.clear();
        notified.clear();
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
                            if (!section.getBlockState(x, y, z).isOf(Blocks.SPAWNER)) continue;
                            BlockPos pos = new BlockPos((chunk.getPos().x << 4) + x, ((bottom + i) << 4) + y, (chunk.getPos().z << 4) + z);
                            long packed = pos.asLong();
                            found.add(packed);
                            if (!notified.add(packed)) continue;
                            announce(pos);
                        }
                    }
                }
            }
        }
    }

    private void announce(BlockPos pos) {
        if (mc.inGameHud != null) {
            int distance = mc.player == null ? 0 : (int) Math.round(Math.sqrt(
                    Math.pow(pos.getX() + 0.5 - mc.player.getX(), 2)
                            + Math.pow(pos.getY() + 0.5 - mc.player.getY(), 2)
                            + Math.pow(pos.getZ() + 0.5 - mc.player.getZ(), 2)));
            mc.inGameHud.getChatHud().addMessage(Text.literal(
                    "[SpectreSpawnerNotifier] Spawner found at " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + " (" + distance + "m)"));
        }
        if (sound.isEnabled() && mc.player != null) {
            mc.player.playSound(net.minecraft.sound.SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), 1.0F, 1.0F);
        }
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (!render.isEnabled() || found.isEmpty() || mc.world == null) return;
        int line = color.getColor();
        for (long packed : found) {
            BlockPos pos = BlockPos.fromLong(packed);
            Box box = new Box(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1);
            SpectreHoleEsp.drawBox(context, tickDelta, box, line, (line & 0x00FFFFFF) | 0x33000000, true, true, 1);
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
