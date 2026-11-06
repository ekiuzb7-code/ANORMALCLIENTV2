package com.anormal.client.module.impl.spectre;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.ColorUtils;
import net.minecraft.block.Blocks;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.WorldChunk;

import java.util.HashMap;
import java.util.Map;

/**
 * Spectre port: "Hive Map" — explores nearby chunks in a map with underground and Y-slice views.
 *
 * <p>Spectre opens a full 3D map screen. This port keeps the chunk survey (top block per column,
 * the Y slice filter and the underground view) and draws it as a compact honeycomb panel in the
 * HUD, which needs no extra screen.</p>
 */
public final class SpectreHiveMap extends Module {
    public final NumberSetting sliceY = addSetting(new NumberSetting("Slice Y", "Y level the map is cut at", 64.0, -64.0, 320.0, 1.0));
    public final NumberSetting scale = addSetting(new NumberSetting("Scale", "Pixels per chunk", 6.0, 3.0, 12.0, 1.0));
    public final BooleanSetting underground = addSetting(new BooleanSetting("Underground", "Read the column below the slice", false));
    public final NumberSetting opacity = addSetting(new NumberSetting("Opacity", "Panel opacity", 210.0, 40.0, 255.0, 5.0));

    private final Map<Long, Integer> colors = new HashMap<>();
    private int tick;

    public SpectreHiveMap() {
        super("SpectreHiveMap", "Spectre port: maps nearby chunks with underground and Y-slice views.", Category.SPECTRE);
    }

    @Override
    public void onEnable() {
        colors.clear();
        tick = 0;
    }

    @Override
    public void onDisable() {
        colors.clear();
    }

    @Override
    public void onTick() {
        if (mc.world == null || mc.player == null) return;
        if (++tick % 20 != 0) return;
        colors.clear();
        int radius = Math.min(mc.options.getViewDistance().getValue(), 6);
        int pcx = mc.player.getChunkPos().x;
        int pcz = mc.player.getChunkPos().z;
        int slice = (int) sliceY.getAsDouble();
        int floor = underground.isEnabled() ? mc.world.getBottomY() : slice;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(pcx + dx, pcz + dz);
                if (chunk == null) continue;
                colors.put(((long) (pcx + dx) << 32) | ((pcz + dz) & 0xFFFFFFFFL), sample(chunk, slice, floor));
            }
        }
    }

    private int sample(WorldChunk chunk, int slice, int floor) {
        int startX = chunk.getPos().getStartX();
        int startZ = chunk.getPos().getStartZ();
        int top = Math.min(slice, 319);
        for (int y = top; y >= floor; y--) {
            var state = mc.world.getBlockState(new BlockPos(startX + 8, y, startZ + 8));
            if (state.isAir()) continue;
            if (state.isOf(Blocks.WATER)) return ColorUtils.rgba(40, 110, 220, 230);
            if (state.isOf(Blocks.LAVA)) return ColorUtils.rgba(230, 110, 30, 230);
            if (state.isOf(Blocks.GRASS_BLOCK) || state.isOf(Blocks.SHORT_GRASS)) return ColorUtils.rgba(90, 170, 80, 230);
            if (state.isOf(Blocks.SAND)) return ColorUtils.rgba(220, 210, 140, 230);
            if (state.isOf(Blocks.GRAVEL)) return ColorUtils.rgba(150, 145, 140, 230);
            if (state.isOf(Blocks.SNOW) || state.isOf(Blocks.SNOW_BLOCK)) return ColorUtils.rgba(235, 240, 245, 230);
            if (state.isOf(Blocks.NETHERRACK)) return ColorUtils.rgba(120, 40, 40, 230);
            if (state.isOf(Blocks.END_STONE)) return ColorUtils.rgba(220, 220, 170, 230);
            if (state.isOf(Blocks.DEEPSLATE) || state.isOf(Blocks.STONE)) return ColorUtils.rgba(120, 120, 125, 230);
            return ColorUtils.rgba(140, 130, 110, 230);
        }
        return 0;
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        var player = mc.player;
        if (player == null || colors.isEmpty() || mc.options.hudHidden) return;
        int cell = (int) scale.getAsDouble();
        int radius = Math.min(mc.options.getViewDistance().getValue(), 6);
        int size = (radius * 2 + 1) * cell;
        int left = 4;
        int top = 4;
        int alpha = (int) opacity.getAsDouble();
        context.fill(left - 3, top - 12, left + size + 3, top + size + 3, ColorUtils.rgba(16, 16, 20, alpha));
        context.drawText(mc.textRenderer, "Hive Map (Y " + (int) sliceY.getAsDouble() + ")", left, top - 10, 0xFFFFFFFF, true);

        int pcx = player.getChunkPos().x;
        int pcz = player.getChunkPos().z;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                Integer color = colors.get(((long) (pcx + dx) << 32) | ((pcz + dz) & 0xFFFFFFFFL));
                int x = left + (dx + radius) * cell;
                int y = top + (dz + radius) * cell;
                if (color == null) {
                    context.fill(x, y, x + cell - 1, y + cell - 1, ColorUtils.rgba(0, 0, 0, 90));
                } else {
                    context.fill(x, y, x + cell - 1, y + cell - 1, (color & 0x00FFFFFF) | (alpha << 24));
                }
            }
        }
        int centre = left + radius * cell;
        context.fill(centre, centre, centre + cell - 1, centre + cell - 1, ColorUtils.rgba(255, 255, 255, 255));
    }
}
