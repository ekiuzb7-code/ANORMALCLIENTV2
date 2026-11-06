package com.anormal.client.module.impl.spectre;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.ColorUtils;
import com.anormal.client.util.ProjectionUtil;
import com.anormal.client.util.RenderUtils;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.LightType;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Spectre port: "Amethyst Bypass" — shows amethyst geodes that anti-xray tries to hide.
 *
 * <p>Spectre has three ways of finding them: a palette leak in the chunk data
 * ({@code Bypass}), a block-light signature of budding amethyst ({@code Light}) and the
 * ANBS+ glow scan. This port keeps the palette and light detectors with a budgeted
 * chunk queue (2 ms of work per pass, a new sweep every 20 ticks) and draws the hits as
 * boxes with optional tracers.</p>
 */
public final class SpectreAmethystBypass extends Module {
    private static final int RESCAN_TICKS = 20;
    private static final long BUDGET_NANOS = 2_000_000L;
    private static final int MAX_MARKS_PER_CHUNK = 8;
    private static final int COLOUR = ColorUtils.rgba(199, 120, 255, 255);

    public final ModeSetting method = addSetting(new ModeSetting("Method", "How geodes are detected", "Bypass", "Bypass", "Light"));
    public final NumberSetting minCells = addSetting(new NumberSetting("Min Cells", "Glow cells needed before a chunk is flagged", 12.0, 1.0, 100.0, 1.0));
    public final BooleanSetting tracer = addSetting(new BooleanSetting("Tracer", "Draw a line to each geode", false));

    private final ArrayDeque<Long> queue = new ArrayDeque<>();
    private final Set<Long> found = new HashSet<>();
    private int tick;

    public SpectreAmethystBypass() {
        super("SpectreAmethystBypass", "Spectre port: shows amethyst geodes hidden by anti-xray.", Category.SPECTRE);
    }

    @Override
    public void onEnable() {
        restart();
    }

    @Override
    public void onDisable() {
        restart();
    }

    private void restart() {
        queue.clear();
        found.clear();
        tick = 0;
    }

    @Override
    public void onTick() {
        if (mc.world == null || mc.player == null) {
            restart();
            return;
        }

        if (queue.isEmpty()) {
            if (++tick < RESCAN_TICKS) return;
            tick = 0;
            found.clear();
            int radius = mc.options.getViewDistance().getValue();
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

        long deadline = System.nanoTime() + BUDGET_NANOS;
        boolean lightMode = method.is("Light");
        while (!queue.isEmpty() && System.nanoTime() < deadline) {
            long key = queue.poll();
            int cx = unpackX(key);
            int cz = unpackZ(key);
            WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(cx, cz);
            if (chunk == null) continue;
            if (lightMode) scanLight(chunk);
            else scanPalette(chunk);
        }
    }

    /** Palette leak: anti-xray rewrites block states but the section palette still leaks buds. */
    private void scanPalette(WorldChunk chunk) {
        ChunkSection[] sections = chunk.getSectionArray();
        int bottom = mc.world.getBottomSectionCoord();
        for (int i = 0; i < sections.length; i++) {
            ChunkSection section = sections[i];
            if (section == null || section.isEmpty()) continue;
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        BlockState state = section.getBlockState(x, y, z);
                        if (!isBud(state)) continue;
                        BlockPos pos = new BlockPos((chunk.getPos().x << 4) + x,
                                ((bottom + i) << 4) + y, (chunk.getPos().z << 4) + z);
                        found.add(pos.asLong());
                        return;
                    }
                }
            }
        }
    }

    /** Block-light signature of budding amethyst: light 1/2/4/5 touching a geode, local maximum. */
    private void scanLight(WorldChunk chunk) {
        ChunkSection[] sections = chunk.getSectionArray();
        int bottom = mc.world.getBottomSectionCoord();
        int marks = 0;
        int cells = 0;
        List<BlockPos> local = new ArrayList<>();

        for (int i = 0; i < sections.length && local.size() < MAX_MARKS_PER_CHUNK; i++) {
            ChunkSection section = sections[i];
            if (section == null || section.isEmpty()) continue;
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        BlockState state = section.getBlockState(x, y, z);
                        if (!isGeode(state) && !isBud(state)) continue;
                        if (state.getLuminance() > 0) continue;
                        BlockPos pos = new BlockPos((chunk.getPos().x << 4) + x,
                                ((bottom + i) << 4) + y, (chunk.getPos().z << 4) + z);
                        int light = mc.world.getLightLevel(LightType.BLOCK, pos);
                        if (!isBudLight(light)) continue;
                        if (!touchesGeode(pos)) continue;
                        if (!isLocalMaximum(pos, light)) continue;
                        cells++;
                        if (local.size() < MAX_MARKS_PER_CHUNK) local.add(pos);
                    }
                }
            }
        }

        if (cells >= minCells.getValue().intValue()) {
            for (BlockPos pos : local) found.add(pos.asLong());
        }
        if (marks == 0 && !local.isEmpty()) marks = local.size();
    }

    private boolean touchesGeode(BlockPos pos) {
        for (net.minecraft.util.math.Direction direction : net.minecraft.util.math.Direction.values()) {
            if (isGeode(mc.world.getBlockState(pos.offset(direction)))) return true;
        }
        return false;
    }

    private boolean isLocalMaximum(BlockPos pos, int light) {
        for (net.minecraft.util.math.Direction direction : net.minecraft.util.math.Direction.values()) {
            if (mc.world.getLightLevel(LightType.BLOCK, pos.offset(direction)) >= light) return false;
        }
        return true;
    }

    private static boolean isBudLight(int light) {
        return light == 1 || light == 2 || light == 4 || light == 5;
    }

    private static boolean isBud(BlockState state) {
        Block block = state.getBlock();
        return block == Blocks.SMALL_AMETHYST_BUD
                || block == Blocks.MEDIUM_AMETHYST_BUD
                || block == Blocks.LARGE_AMETHYST_BUD;
    }

    private static boolean isGeode(BlockState state) {
        return state.isOf(Blocks.AMETHYST_BLOCK) || state.isOf(Blocks.BUDDING_AMETHYST);
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (found.isEmpty() || mc.world == null || mc.player == null) return;
        for (long packed : found) {
            BlockPos pos = BlockPos.fromLong(packed);
            Box box = new Box(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1);
            SpectreHoleEsp.drawBox(context, tickDelta, box, COLOUR, (COLOUR & 0x00FFFFFF) | 0x40000000, true, true, 1);
            if (tracer.isEnabled()) {
                int[] eye = ProjectionUtil.project(ProjectionUtil.eye(tickDelta), tickDelta);
                int[] target = ProjectionUtil.project(new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5), tickDelta);
                if (eye != null && target != null) {
                    RenderUtils.drawLine(context, eye[0], eye[1], target[0], target[1], COLOUR, 1);
                }
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
