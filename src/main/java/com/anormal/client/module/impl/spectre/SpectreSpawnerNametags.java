package com.anormal.client.module.impl.spectre;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.block.Blocks;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Spectre port: "Spawner Nametags" — shows each spawner's mob and activation range over the
 * block.
 *
 * <p>Spectre reads the spawner data through a BaseSpawner accessor. This port scans the
 * loaded chunks itself (same budget pattern as the other Spectre finders) and labels each
 * spawner with its distance and height, fading out towards the range edge like Spectre.</p>
 */
public final class SpectreSpawnerNametags extends Module {
    private static final int SCAN_BUDGET = 4096;
    private static final int RESCAN_TICKS = 40;
    private static final int MAX_LABELS = 48;
    private static final double FADE_SHARE = 0.125;

    public final NumberSetting range = addSetting(new NumberSetting("Range", "Label range in blocks", 64.0, 8.0, 256.0, 1.0));
    public final BooleanSetting showHeight = addSetting(new BooleanSetting("Show Height", "Append the spawner Y level", true));
    public final BooleanSetting shadow = addSetting(new BooleanSetting("Shadow", "Draw labels with a shadow", true));

    private final ArrayDeque<Long> queue = new ArrayDeque<>();
    private final Set<Long> spawners = new HashSet<>();
    private int tick;

    public SpectreSpawnerNametags() {
        super("SpectreSpawnerNametags", "Spectre port: shows each spawner's position over the block.", Category.SPECTRE);
    }

    @Override
    public void onEnable() {
        queue.clear();
        spawners.clear();
        tick = 0;
    }

    @Override
    public void onDisable() {
        queue.clear();
        spawners.clear();
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
            spawners.clear();
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
                            spawners.add(new BlockPos((chunk.getPos().x << 4) + x, ((bottom + i) << 4) + y,
                                    (chunk.getPos().z << 4) + z).asLong());
                        }
                    }
                }
            }
        }
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        var player = mc.player;
        if (player == null || mc.world == null || spawners.isEmpty() || mc.options.hudHidden) return;

        double max = range.getAsDouble();
        double maxSq = max * max;
        List<BlockPos> visible = new java.util.ArrayList<>();
        for (long packed : spawners) {
            BlockPos pos = BlockPos.fromLong(packed);
            double dx = pos.getX() + 0.5 - player.getX();
            double dy = pos.getY() + 0.5 - player.getY();
            double dz = pos.getZ() + 0.5 - player.getZ();
            if (dx * dx + dy * dy + dz * dz <= maxSq) visible.add(pos);
        }
        if (visible.isEmpty()) return;
        visible.sort(Comparator.comparingDouble(pos -> pos.getSquaredDistance(player.getBlockPos())));

        int drawn = 0;
        for (BlockPos pos : visible) {
            if (drawn >= MAX_LABELS) break;
            float[] screen = project(pos, tickDelta);
            if (screen == null) continue;
            double distance = Math.sqrt(pos.getSquaredDistance(player.getBlockPos()));
            int alpha = (int) (255.0 * Math.max(0.0, Math.min(1.0, (max - distance) / (max * FADE_SHARE))));
            if (alpha < 8) continue;
            String text = showHeight.isEnabled()
                    ? "Spawner  " + (int) Math.round(distance) + "m  Y" + pos.getY()
                    : "Spawner  " + (int) Math.round(distance) + "m";
            int width = mc.textRenderer.getWidth(text);
            context.drawText(mc.textRenderer, text,
                    (int) screen[0] - width / 2, (int) screen[1], (alpha << 24) | 0x00FFFFFF, shadow.isEnabled());
            drawn++;
        }
    }

    private float[] project(BlockPos pos, float tickDelta) {
        var player = mc.player;
        double x = pos.getX() + 0.5 - player.getX();
        double y = pos.getY() + 1.2 - (player.getY() + player.getStandingEyeHeight());
        double z = pos.getZ() + 0.5 - player.getZ();
        double yaw = Math.toRadians(player.getYaw());
        double cos = Math.cos(-yaw);
        double sin = Math.sin(-yaw);
        double rx = x * cos - z * sin;
        double rz = x * sin + z * cos;
        if (rz < 0.1) return null;
        double fov = Math.toRadians(mc.options.getFov().getValue());
        double factor = (mc.getWindow().getScaledHeight() * 0.5) / Math.tan(fov * 0.5);
        double sx = mc.getWindow().getScaledWidth() * 0.5 + (rx / rz) * factor;
        double sy = mc.getWindow().getScaledHeight() * 0.5 - (y / rz) * factor;
        if (sx < 0 || sy < 0 || sx > mc.getWindow().getScaledWidth() || sy > mc.getWindow().getScaledHeight()) return null;
        return new float[]{(float) sx, (float) sy};
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
