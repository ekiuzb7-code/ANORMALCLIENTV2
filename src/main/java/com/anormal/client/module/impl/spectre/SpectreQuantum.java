package com.anormal.client.module.impl.spectre;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.util.ColorUtils;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Spectre port: "Quantum" — finds player activity in chunks the server re-sends.
 *
 * <p>Spectre snapshots every section of the chunks around the player and compares the
 * snapshot when a full chunk packet arrives again. A block that changed between the two
 * copies can only come from a player (or a machine they built), which is what makes this
 * a base-finding tool. This port keeps the same idea with a compact per-section hash
 * snapshot so the memory cost stays small, and it defers the comparison to the next tick
 * because the incoming chunk is not applied to the world yet while the packet is handled.</p>
 */
public final class SpectreQuantum extends Module {
    private static final int SCAN_RADIUS = 8;
    private static final int MARKER_Y = 63;
    private static final int MAX_MARKERS = 64;
    private static final int SNAPSHOT_BUDGET = 6;

    private static final Set<Block> IGNORED_NEW = Set.of(
            Blocks.FIRE, Blocks.FROSTED_ICE,
            Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE,
            Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE,
            Blocks.COPPER_ORE, Blocks.DEEPSLATE_COPPER_ORE,
            Blocks.GOLD_ORE, Blocks.DEEPSLATE_GOLD_ORE,
            Blocks.REDSTONE_ORE, Blocks.DEEPSLATE_REDSTONE_ORE,
            Blocks.EMERALD_ORE, Blocks.DEEPSLATE_EMERALD_ORE,
            Blocks.LAPIS_ORE, Blocks.DEEPSLATE_LAPIS_ORE,
            Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE,
            Blocks.OAK_LEAVES, Blocks.ACACIA_LEAVES, Blocks.BIRCH_LEAVES,
            Blocks.JUNGLE_LEAVES, Blocks.DARK_OAK_LEAVES, Blocks.CHERRY_LEAVES,
            Blocks.AZALEA_LEAVES, Blocks.FLOWERING_AZALEA_LEAVES,
            Blocks.MOSSY_COBBLESTONE, Blocks.RAW_COPPER_BLOCK,
            Blocks.STONE, Blocks.OBSERVER, Blocks.REPEATER, Blocks.COMPARATOR,
            Blocks.REDSTONE_TORCH, Blocks.REDSTONE_LAMP, Blocks.REDSTONE_WIRE, Blocks.DROPPER);

    public final BooleanSetting render = addSetting(new BooleanSetting("Render", "Draw detected activity", true));
    public final BooleanSetting advancedDebug = addSetting(new BooleanSetting("Advanced Debug", "Show the changed block and re-sent chunks", false));
    public final ColorSetting fillColor = addSetting(new ColorSetting("Fill Color", "Marker fill", ColorUtils.rgba(60, 180, 255, 70)));
    public final ColorSetting lineColor = addSetting(new ColorSetting("Line Color", "Marker outline", ColorUtils.rgba(60, 180, 255, 255)));

    /** chunk key -> per-section hashes (index = sectionY - bottom). */
    private final Map<Long, byte[][]> snapshots = new HashMap<>();
    private final Set<Long> pendingChunks = new HashSet<>();
    private final Set<Long> sentChunks = new HashSet<>();
    private final List<BlockPos> markers = new ArrayList<>();
    private long lastChunkKey = Long.MIN_VALUE;

    public SpectreQuantum() {
        super("SpectreQuantum", "Spectre port: finds player activity in re-sent chunks.", Category.SPECTRE);
    }

    /** Additive hook from the chunk-packet mixin; comparison happens on the next tick. */
    public static void onChunkData(int chunkX, int chunkZ) {
        SpectreQuantum module = com.anormal.client.module.ModuleManager.getModule(SpectreQuantum.class);
        if (module != null && module.isEnabled()) {
            module.pendingChunks.add(chunkKey(chunkX, chunkZ));
        }
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
        snapshots.clear();
        pendingChunks.clear();
        sentChunks.clear();
        markers.clear();
        lastChunkKey = Long.MIN_VALUE;
    }

    @Override
    public void onTick() {
        var player = mc.player;
        if (player == null || mc.world == null || !overworld()) {
            if (mc.world == null) reset();
            return;
        }
        int pcx = player.getChunkPos().x;
        int pcz = player.getChunkPos().z;
        long key = chunkKey(pcx, pcz);

        if (key != lastChunkKey) {
            takeMissingSnapshots(pcx, pcz);
            Set<Long> range = new HashSet<>();
            for (long[] c : chunksInRange(pcx, pcz)) range.add(chunkKey((int) c[0], (int) c[1]));
            snapshots.keySet().removeIf(k -> !range.contains(k));
            sentChunks.removeIf(k -> !range.contains(k));
            lastChunkKey = key;
        }

        processPending();
    }

    private void takeMissingSnapshots(int pcx, int pcz) {
        int budget = SNAPSHOT_BUDGET;
        for (long[] c : chunksInRange(pcx, pcz)) {
            if (budget <= 0) break;
            int cx = (int) c[0], cz = (int) c[1];
            long k = chunkKey(cx, cz);
            if (snapshots.containsKey(k)) continue;
            if (mc.world.getChunkManager().getWorldChunk(cx, cz) == null) continue;
            snapshots.put(k, snapshot(cx, cz));
            budget--;
        }
    }

    private void processPending() {
        if (pendingChunks.isEmpty()) return;
        List<Long> work = new ArrayList<>(pendingChunks);
        pendingChunks.clear();
        int pcx = mc.player.getChunkPos().x;
        int pcz = mc.player.getChunkPos().z;
        Set<Long> range = new HashSet<>();
        for (long[] c : chunksInRange(pcx, pcz)) range.add(chunkKey((int) c[0], (int) c[1]));

        for (long k : work) {
            if (!range.contains(k)) continue;
            int cx = (int) (k >> 32);
            int cz = (int) k;
            if (mc.world.getChunkManager().getWorldChunk(cx, cz) == null) continue;
            byte[][] fresh = snapshot(cx, cz);
            byte[][] old = snapshots.put(k, fresh);
            sentChunks.add(k);
            if (old != null) compare(cx, cz, old, fresh);
        }
    }

    private void compare(int cx, int cz, byte[][] old, byte[][] fresh) {
        int bottom = mc.world.getBottomSectionCoord();
        int length = Math.min(old.length, fresh.length);
        for (int i = 0; i < length; i++) {
            byte[] before = old[i];
            byte[] after = fresh[i];
            if (before == null && after == null) continue;
            for (int idx = 0; idx < 4096; idx++) {
                byte b = before == null ? 0 : before[idx];
                byte a = after == null ? 0 : after[idx];
                if (a == b) continue;
                BlockPos pos = new BlockPos(
                        (cx << 4) + (idx & 15),
                        ((bottom + i) << 4) + ((idx >> 8) & 15),
                        (cz << 4) + ((idx >> 4) & 15));
                BlockState state = mc.world.getBlockState(pos);
                if (state.isAir() || IGNORED_NEW.contains(state.getBlock())) continue;
                report(pos);
                return;
            }
        }
    }

    private void report(BlockPos pos) {
        markers.add(pos);
        if (markers.size() > MAX_MARKERS) markers.remove(0);
        int cx = pos.getX() >> 4;
        int cz = pos.getZ() >> 4;
        String message = advancedDebug.isEnabled()
                ? "Activity detected in chunk " + cx + ", " + cz
                + " at " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ()
                : "Activity detected in chunk " + cx + ", " + cz;
        if (mc.inGameHud != null) {
            mc.inGameHud.getChatHud().addMessage(Text.literal("[SpectreQuantum] " + message));
        }
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (!render.isEnabled() || mc.world == null || mc.player == null) return;
        int line = lineColor.getColor();
        int fill = fillColor.getColor();
        if (!markers.isEmpty()) {
            for (BlockPos pos : markers) {
                Box box = new Box(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1);
                SpectreHoleEsp.drawBox(context, tickDelta, box, line, fill, true, true, 1);
            }
        }
        if (advancedDebug.isEnabled() && !sentChunks.isEmpty()) {
            for (long k : sentChunks) {
                int cx = (int) (k >> 32);
                int cz = (int) k;
                Box slab = new Box(cx << 4, MARKER_Y, cz << 4, (cx << 4) + 16, MARKER_Y + 0.2, (cz << 4) + 16);
                SpectreHoleEsp.drawBox(context, tickDelta, slab, ColorUtils.rgba(0, 255, 0, 255), ColorUtils.rgba(0, 255, 0, 40), true, true, 1);
            }
        }
    }

    private boolean overworld() {
        return mc.world != null && mc.world.getRegistryKey() == net.minecraft.world.World.OVERWORLD;
    }

    private byte[][] snapshot(int cx, int cz) {
        WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(cx, cz);
        int bottom = mc.world.getBottomSectionCoord();
        int top = mc.world.getTopSectionCoord();
        byte[][] out = new byte[top - bottom][];
        if (chunk == null) return out;
        ChunkSection[] sections = chunk.getSectionArray();
        for (int i = 0; i < out.length && i < sections.length; i++) {
            ChunkSection section = sections[i];
            if (section == null || section.isEmpty()) continue;
            byte[] data = new byte[4096];
            int idx = 0;
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        BlockState state = section.getBlockState(x, y, z);
                        data[idx++] = (byte) (Block.getRawIdFromState(state) * 31 >>> 3);
                    }
                }
            }
            out[i] = data;
        }
        return out;
    }

    private static Set<long[]> chunksInRange(int centerX, int centerZ) {
        Set<long[]> range = new LinkedHashSet<>();
        for (int dz = -SCAN_RADIUS; dz <= SCAN_RADIUS; dz++) {
            int cut = Math.max(0, 3 - (SCAN_RADIUS - Math.abs(dz)));
            int halfWidth = SCAN_RADIUS - cut;
            for (int dx = -halfWidth; dx <= halfWidth; dx++) {
                range.add(new long[]{centerX + dx, centerZ + dz});
            }
        }
        return range;
    }

    private static long chunkKey(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
    }
}
