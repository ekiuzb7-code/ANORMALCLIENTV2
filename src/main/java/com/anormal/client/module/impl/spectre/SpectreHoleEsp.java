package com.anormal.client.module.impl.spectre;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.module.ModuleManager;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.ProjectionUtil;
import net.minecraft.block.BlockState;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSection;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Spectre port: "Hole ESP" — highlights holes, tunnels and staircases.
 *
 * <p>Faithful port of Spectre's HoleEspModule: budgeted chunk scan queue sorted by
 * distance/ahead-of-motion, the 20x20 padded passable-column grid for 1x1/3x1 holes,
 * and the tunnel/staircase column walkers. Rendering uses the shared screen-space
 * projection the rest of the client uses.</p>
 */
public final class SpectreHoleEsp extends Module {
    private static final String MODE_ALL = "All";
    private static final String MODE_HOLES_TUNNELS = "Holes + Tunnels";
    private static final String MODE_HOLES_STAIRS = "Holes + Stairs";
    private static final String MODE_TUNNELS_STAIRS = "Tunnels + Stairs";
    private static final String MODE_HOLES = "Holes";
    private static final String MODE_TUNNELS = "Tunnels";
    private static final String MODE_STAIRS = "Stairs";
    private static final int TYPE_HOLE = 0;
    private static final int TYPE_HOLE_3X1 = 1;
    private static final int TYPE_TUNNEL = 2;
    private static final int TYPE_STAIR = 3;
    private static final int MAX_HITS_PER_CHUNK = 1024;
    private static final int MAX_RENDER_BOXES = 8192;
    private static final int ELYTRA_REFRESH_TICKS = 5;
    private static final int NORMAL_REFRESH_TICKS = 20;
    private static final int ELYTRA_SCAN_RADIUS = 12;
    private static final float ELYTRA_SCAN_BUDGET_MS = 8.0f;
    private static final int[][] HORIZONTAL_DIRS = {{1, 0}, {-1, 0}, {0, -1}, {0, 1}};
    private static final int GRID_W = 20;

    public final ModeSetting sMode = addSetting(new ModeSetting("Detection", "What to look for", MODE_HOLES,
            MODE_HOLES, MODE_ALL, MODE_HOLES_TUNNELS, MODE_HOLES_STAIRS, MODE_TUNNELS_STAIRS, MODE_TUNNELS, MODE_STAIRS));
    public final NumberSetting sScanRadius = addSetting(new NumberSetting("Scan Radius", "Chunk radius", 8.0, 1.0, 24.0, 1.0));
    public final NumberSetting sChunksPerTick = addSetting(new NumberSetting("Chunks/Tick", "Chunks scanned per tick", 64.0, 1.0, 100.0, 1.0));
    public final NumberSetting sScanBudgetMs = addSetting(new NumberSetting("Scan Budget ms", "Scan time budget per tick", 3.0, 1.0, 10.0, 1.0));
    public final BooleanSetting sAirOnly = addSetting(new BooleanSetting("Only Air Blocks", "Only true air counts as passable", true));
    public final NumberSetting sMinYOffset = addSetting(new NumberSetting("Min Y Offset", "Skip blocks above build height", 0.0, 0.0, 319.0, 1.0));
    public final NumberSetting sMaxYOffset = addSetting(new NumberSetting("Max Y Offset", "Skip blocks below build height", 0.0, 0.0, 319.0, 1.0));
    public final NumberSetting sMinHoleDepth = addSetting(new NumberSetting("Min Hole Depth", "Minimum hole depth", 4.0, 1.0, 20.0, 1.0));
    public final NumberSetting sMinTunnelLength = addSetting(new NumberSetting("Min Tunnel Length", "Minimum tunnel length", 3.0, 1.0, 20.0, 1.0));
    public final NumberSetting sMinTunnelHeight = addSetting(new NumberSetting("Min Tunnel Height", "Minimum tunnel height", 2.0, 1.0, 10.0, 1.0));
    public final NumberSetting sMaxTunnelHeight = addSetting(new NumberSetting("Max Tunnel Height", "Maximum tunnel height", 3.0, 2.0, 10.0, 1.0));
    public final NumberSetting sMinStairLength = addSetting(new NumberSetting("Min Stair Length", "Minimum staircase length", 3.0, 1.0, 20.0, 1.0));
    public final NumberSetting sMinStairHeight = addSetting(new NumberSetting("Min Stair Height", "Minimum staircase height", 3.0, 2.0, 10.0, 1.0));
    public final NumberSetting sMaxStairHeight = addSetting(new NumberSetting("Max Stair Height", "Maximum staircase height", 5.0, 2.0, 10.0, 1.0));
    public final BooleanSetting sFill = addSetting(new BooleanSetting("Fill", "Draw the translucent fill", true));
    public final BooleanSetting sOutline = addSetting(new BooleanSetting("Outline", "Draw the wireframe outline", true));
    public final NumberSetting sLineWidth = addSetting(new NumberSetting("Line Width", "Outline width", 1.5, 0.5, 5.0, 0.5));
    public final ColorSetting sHoleLine = addSetting(new ColorSetting("1x1 Line", "Hole outline color", 1291845631));
    public final ColorSetting sHoleFill = addSetting(new ColorSetting("1x1 Fill", "Hole fill color", 1342177279));
    public final ColorSetting sHole3Line = addSetting(new ColorSetting("3x1 Line", "3x1 hole outline color", 644245094));
    public final ColorSetting sHole3Fill = addSetting(new ColorSetting("3x1 Fill", "3x1 hole fill color", 644245094));
    public final ColorSetting sTunnelLine = addSetting(new ColorSetting("Tunnel Line", "Tunnel outline color", 1593835775));
    public final ColorSetting sTunnelFill = addSetting(new ColorSetting("Tunnel Fill", "Tunnel fill color", 503316735));
    public final ColorSetting sStairLine = addSetting(new ColorSetting("Stair Line", "Staircase outline color", 1610547455));
    public final ColorSetting sStairFill = addSetting(new ColorSetting("Stair Fill", "Staircase fill color", 520028415));

    /** chunkKey -> detected boxes */
    private final ConcurrentHashMap<Long, List<Hit>> chunkCache = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<Long> scanQueue = new ConcurrentLinkedQueue<>();
    private final Set<Long> queued = ConcurrentHashMap.newKeySet();
    private long lastPlayerChunk = Long.MIN_VALUE;
    private int refreshTicks;

    public SpectreHoleEsp() {
        super("SpectreHoleEsp", "Spectre port: highlights holes, tunnels and staircases.", Category.SPECTRE);
    }

    @Override
    public void onEnable() {
        lastPlayerChunk = Long.MIN_VALUE;
        refreshTicks = 0;
        chunkCache.clear();
        scanQueue.clear();
        queued.clear();
    }

    @Override
    public void onDisable() {
        chunkCache.clear();
        scanQueue.clear();
        queued.clear();
        lastPlayerChunk = Long.MIN_VALUE;
        refreshTicks = 0;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;

        int cx = mc.player.getBlockX() >> 4;
        int cz = mc.player.getBlockZ() >> 4;
        long curChunk = ChunkPos.toLong(cx, cz);
        int radius = scanRadius();
        boolean fastTravel = isFastTravel();
        boolean periodicRefresh = ++refreshTicks >= (fastTravel ? ELYTRA_REFRESH_TICKS : NORMAL_REFRESH_TICKS);
        if (curChunk != lastPlayerChunk || periodicRefresh) {
            refreshTicks = 0;
            lastPlayerChunk = curChunk;
            chunkCache.keySet().removeIf(key ->
                    Math.abs(ChunkPos.getPackedX(key) - cx) > radius
                            || Math.abs(ChunkPos.getPackedZ(key) - cz) > radius);
            List<long[]> toQueue = new ArrayList<>();
            Vec3d velocity = mc.player.getVelocity();
            double vx = velocity.x;
            double vz = velocity.z;
            double horizontalSpeed = Math.sqrt(vx * vx + vz * vz);
            if (horizontalSpeed > 1.0E-4) {
                vx /= horizontalSpeed;
                vz /= horizontalSpeed;
            }

            for (int x = cx - radius; x <= cx + radius; x++) {
                for (int z = cz - radius; z <= cz + radius; z++) {
                    long key = ChunkPos.toLong(x, z);
                    if (!chunkCache.containsKey(key) && !queued.contains(key) && hasChunk(x, z)) {
                        int dx = x - cx;
                        int dz = z - cz;
                        long distScore = ((long) dx * dx + (long) dz * dz) * 1024L;
                        long aheadScore = fastTravel ? Math.round((dx * vx + dz * vz) * 1536.0) : 0L;
                        toQueue.add(new long[]{key, distScore - aheadScore});
                    }
                }
            }

            toQueue.sort(Comparator.comparingLong(a -> a[1]));
            for (long[] entry : toQueue) {
                scanQueue.add(entry[0]);
                queued.add(entry[0]);
            }
        }

        int chunksPerTick = Math.max(1, (int) Math.round(sChunksPerTick.getAsDouble()));
        float budgetMs = fastTravel ? Math.max((float) sScanBudgetMs.getAsDouble(), ELYTRA_SCAN_BUDGET_MS)
                : (float) sScanBudgetMs.getAsDouble();
        // Spectre section: SpectrePerformance's Scan Budget is the shared per-tick ceiling.
        budgetMs = Math.min(budgetMs, SpectrePerformance.scanBudgetNanos() / 1_000_000.0F);
        long deadline = System.nanoTime() + (long) (budgetMs * 1_000_000.0f);

        for (int i = 0; i < chunksPerTick && System.nanoTime() < deadline; i++) {
            Long key = scanQueue.poll();
            if (key == null) break;
            queued.remove(key);
            int sx = ChunkPos.getPackedX(key);
            int sz = ChunkPos.getPackedZ(key);
            if (hasChunk(sx, sz)) {
                scanChunk(sx, sz, key);
            }
        }
    }

    /** Chunk changed underneath (block edits) — invalidate that chunk's cache. */
    @Override
    public void onTickAlways() {
        // block-update invalidation piggybacks on the periodic refresh; Spectre hooks
        // BlockUpdateEvent, which has no cheap Yarn equivalent without a world mixin.
    }

    private static boolean hasChunk(int x, int z) {
        try {
            return mc.world.getChunkManager().isChunkLoaded(x, z);
        } catch (Throwable t) {
            return false;
        }
    }

    private void scanChunk(int chunkX, int chunkZ, long chunkKey) {
        Chunk chunk;
        try {
            chunk = mc.world.getChunk(chunkX, chunkZ, net.minecraft.world.chunk.ChunkStatus.FULL, false);
        } catch (Throwable t) {
            return;
        }
        if (chunk == null) return;

        int baseX = chunkX << 4;
        int baseZ = chunkZ << 4;
        int minBuildY = mc.world.getBottomY();
        int maxBuildY = minBuildY + chunk.getSectionArray().length * 16;
        int minY = Math.min(maxBuildY - 1, minBuildY + (int) Math.round(sMinYOffset.getAsDouble()));
        int maxY = Math.max(minY + 1, maxBuildY - (int) Math.round(sMaxYOffset.getAsDouble()));
        Mode mode = currentMode();
        boolean airOnly = sAirOnly.isEnabled();
        if (mode.holes() && !mode.tunnels() && !mode.stairs()) {
            scanHolesFast(chunkKey, baseX, baseZ, minY, maxY, airOnly);
        } else {
            List<Hit> hits = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            BlockPos.Mutable pos = new BlockPos.Mutable();
            for (int si = 0; si < chunk.getSectionArray().length; si++) {
                ChunkSection section;
                try {
                    section = chunk.getSection(si);
                } catch (Throwable t) {
                    continue;
                }
                if (section == null || section.isEmpty()) continue;
                int sectionBaseY = minBuildY + si * 16;
                for (int ly = 0; ly < 16; ly++) {
                    int y = sectionBaseY + ly;
                    if (y <= minY || y >= maxY) continue;
                    for (int z = 0; z < 16; z++) {
                        for (int x = 0; x < 16; x++) {
                            int wx = baseX + x;
                            int wz = baseZ + z;
                            pos.set(wx, y, wz);
                            if (isPassable(pos, airOnly)) {
                                if (mode.holes() && !isPassable(wx, y - 1, wz, airOnly)) {
                                    checkHole(pos, airOnly, hits, seen);
                                    check3x1Hole(pos, airOnly, hits, seen);
                                }
                                if (mode.tunnels()) {
                                    checkTunnelStarts(pos, airOnly, hits, seen);
                                }
                                if (mode.stairs()) {
                                    checkStaircaseStarts(pos, airOnly, hits, seen);
                                }
                                if (hits.size() >= MAX_HITS_PER_CHUNK) {
                                    chunkCache.put(chunkKey, List.copyOf(hits));
                                    return;
                                }
                            }
                        }
                    }
                }
            }

            if (hits.isEmpty()) chunkCache.remove(chunkKey);
            else chunkCache.put(chunkKey, List.copyOf(hits));
        }
    }

    private void scanHolesFast(long chunkKey, int baseX, int baseZ, int minY, int maxY, boolean airOnly) {
        int gridMinY = minY - 1;
        int yCount = maxY - gridMinY + 1;
        if (yCount <= 1) {
            chunkCache.remove(chunkKey);
            return;
        }
        boolean[] passable = new boolean[yCount * GRID_W * GRID_W];
        BlockPos.Mutable pos = new BlockPos.Mutable();
        for (int yi = 0; yi < yCount; yi++) {
            int y = gridMinY + yi;
            int yBase = yi * GRID_W * GRID_W;
            for (int lz = -1; lz <= 18; lz++) {
                int zBase = yBase + (lz + 1) * GRID_W;
                for (int lx = -1; lx <= 18; lx++) {
                    pos.set(baseX + lx, y, baseZ + lz);
                    passable[zBase + lx + 1] = isPassable(pos, airOnly);
                }
            }
        }

        int minDepth = (int) Math.round(sMinHoleDepth.getAsDouble());
        List<Hit> hits = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (int lz = 0; lz < 16; lz++) {
            for (int lx = 0; lx < 16; lx++) {
                int y = minY;
                while (y < maxY) {
                    if (grid(passable, y, lx, lz, gridMinY) && !grid(passable, y - 1, lx, lz, gridMinY)) {
                        int depth = count1x1Depth(passable, y, lx, lz, gridMinY, maxY);
                        if (depth >= minDepth) {
                            addHit(hits, seen,
                                    new Box(baseX + lx, y, baseZ + lz, baseX + lx + 1, y + depth, baseZ + lz + 1), TYPE_HOLE);
                            if (hits.size() >= MAX_HITS_PER_CHUNK) {
                                chunkCache.put(chunkKey, List.copyOf(hits));
                                return;
                            }
                        }

                        int depthX = count3x1DepthX(passable, y, lx, lz, gridMinY, maxY);
                        if (depthX >= minDepth) {
                            addHit(hits, seen,
                                    new Box(baseX + lx, y, baseZ + lz, baseX + lx + 3, y + depthX, baseZ + lz + 1), TYPE_HOLE_3X1);
                            if (hits.size() >= MAX_HITS_PER_CHUNK) {
                                chunkCache.put(chunkKey, List.copyOf(hits));
                                return;
                            }
                        }

                        int depthZ = count3x1DepthZ(passable, y, lx, lz, gridMinY, maxY);
                        if (depthZ >= minDepth) {
                            addHit(hits, seen,
                                    new Box(baseX + lx, y, baseZ + lz, baseX + lx + 1, y + depthZ, baseZ + lz + 3), TYPE_HOLE_3X1);
                            if (hits.size() >= MAX_HITS_PER_CHUNK) {
                                chunkCache.put(chunkKey, List.copyOf(hits));
                                return;
                            }
                        }

                        y += Math.max(1, Math.max(depth, Math.max(depthX, depthZ)));
                    } else {
                        y++;
                    }
                }
            }
        }

        if (hits.isEmpty()) chunkCache.remove(chunkKey);
        else chunkCache.put(chunkKey, List.copyOf(hits));
    }

    private static int count1x1Depth(boolean[] g, int startY, int lx, int lz, int gridMinY, int maxY) {
        int y = startY;
        while (y < maxY
                && grid(g, y, lx, lz, gridMinY)
                && !grid(g, y, lx - 1, lz, gridMinY)
                && !grid(g, y, lx + 1, lz, gridMinY)
                && !grid(g, y, lx, lz - 1, gridMinY)
                && !grid(g, y, lx, lz + 1, gridMinY)) {
            y++;
        }
        return y - startY;
    }

    private static int count3x1DepthX(boolean[] g, int startY, int lx, int lz, int gridMinY, int maxY) {
        int y = startY;
        while (y < maxY
                && grid(g, y, lx, lz, gridMinY)
                && grid(g, y, lx + 1, lz, gridMinY)
                && grid(g, y, lx + 2, lz, gridMinY)
                && !grid(g, y, lx - 1, lz, gridMinY)
                && !grid(g, y, lx + 3, lz, gridMinY)
                && !grid(g, y, lx, lz - 1, gridMinY)
                && !grid(g, y, lx, lz + 1, gridMinY)
                && !grid(g, y, lx + 1, lz - 1, gridMinY)
                && !grid(g, y, lx + 1, lz + 1, gridMinY)
                && !grid(g, y, lx + 2, lz - 1, gridMinY)
                && !grid(g, y, lx + 2, lz + 1, gridMinY)) {
            y++;
        }
        return y - startY;
    }

    private static int count3x1DepthZ(boolean[] g, int startY, int lx, int lz, int gridMinY, int maxY) {
        int y = startY;
        while (y < maxY
                && grid(g, y, lx, lz, gridMinY)
                && grid(g, y, lx, lz + 1, gridMinY)
                && grid(g, y, lx, lz + 2, gridMinY)
                && !grid(g, y, lx, lz - 1, gridMinY)
                && !grid(g, y, lx, lz + 3, gridMinY)
                && !grid(g, y, lx - 1, lz, gridMinY)
                && !grid(g, y, lx + 1, lz, gridMinY)
                && !grid(g, y, lx - 1, lz + 1, gridMinY)
                && !grid(g, y, lx + 1, lz + 1, gridMinY)
                && !grid(g, y, lx - 1, lz + 2, gridMinY)
                && !grid(g, y, lx + 1, lz + 2, gridMinY)) {
            y++;
        }
        return y - startY;
    }

    private static boolean grid(boolean[] g, int y, int lx, int lz, int gridMinY) {
        int yi = y - gridMinY;
        if (yi < 0 || yi * GRID_W * GRID_W + (lz + 1) * GRID_W + lx + 1 >= g.length) return false;
        return g[yi * GRID_W * GRID_W + (lz + 1) * GRID_W + lx + 1];
    }

    private void checkHole(BlockPos pos, boolean airOnly, List<Hit> hits, Set<String> seen) {
        if (isValidHoleSection(pos, airOnly)) {
            BlockPos current = pos;
            while (isValidHoleSection(current, airOnly)) {
                current = current.up();
            }
            int depth = current.getY() - pos.getY();
            if (depth >= Math.round(sMinHoleDepth.getAsDouble())) {
                addHit(hits, seen, new Box(pos.getX(), pos.getY(), pos.getZ(),
                        pos.getX() + 1, current.getY(), pos.getZ() + 1), TYPE_HOLE);
            }
        }
    }

    private void check3x1Hole(BlockPos pos, boolean airOnly, List<Hit> hits, Set<String> seen) {
        int minDepth = (int) Math.round(sMinHoleDepth.getAsDouble());
        int x = pos.getX(), y = pos.getY(), z = pos.getZ();
        if (isValid3x1HoleSectionX(pos, airOnly)) {
            BlockPos current = pos;
            while (isValid3x1HoleSectionX(current.up(), airOnly)) {
                current = current.up();
                if (current.getY() - pos.getY() > 32) break;
            }
            int depth = current.getY() - pos.getY();
            if (depth >= minDepth) {
                addHit(hits, seen, new Box(x, y, z, x + 3, current.getY(), z + 1), TYPE_HOLE_3X1);
            }
        }
        if (isValid3x1HoleSectionZ(pos, airOnly)) {
            BlockPos current = pos;
            while (isValid3x1HoleSectionZ(current.up(), airOnly)) {
                current = current.up();
                if (current.getY() - pos.getY() > 32) break;
            }
            int depth = current.getY() - pos.getY();
            if (depth >= minDepth) {
                addHit(hits, seen, new Box(x, y, z, x + 1, current.getY(), z + 3), TYPE_HOLE_3X1);
            }
        }
    }

    private void checkTunnelStarts(BlockPos pos, boolean airOnly, List<Hit> hits, Set<String> seen) {
        int minLength = (int) Math.round(sMinTunnelLength.getAsDouble());

        for (int[] dir : HORIZONTAL_DIRS) {
            BlockPos previous = pos.add(-dir[0], 0, -dir[1]);
            if (isTunnelSection(previous, dir, airOnly)) continue;

            BlockPos current = pos;
            BlockPos start = null;
            BlockPos end = null;
            int steps = 0;
            int maxHeight = 0;
            if (isTunnelSection(current, dir, airOnly)) {
                start = current.toImmutable();
            }
            int guard = 0;
            while (isTunnelSection(current, dir, airOnly) && guard++ < 128) {
                maxHeight = Math.max(maxHeight, getTunnelHeight(current, airOnly));
                end = current.toImmutable();
                current = current.add(dir[0], 0, dir[1]);
                steps++;
            }

            if (start != null && end != null && steps >= minLength) {
                addHit(hits, seen,
                        new Box(Math.min(start.getX(), end.getX()), start.getY(), Math.min(start.getZ(), end.getZ()),
                                Math.max(start.getX(), end.getX()) + 1, start.getY() + maxHeight,
                                Math.max(start.getZ(), end.getZ()) + 1), TYPE_TUNNEL);
            }
        }
    }

    private void checkStaircaseStarts(BlockPos pos, boolean airOnly, List<Hit> hits, Set<String> seen) {
        int minLength = (int) Math.round(sMinStairLength.getAsDouble());

        for (int[] dir : HORIZONTAL_DIRS) {
            BlockPos previous = pos.add(-dir[0], -1, -dir[1]);
            if (isStaircaseSection(previous, dir, airOnly)) continue;

            BlockPos current = pos;
            int steps = 0;
            List<Box> boxes = new ArrayList<>();
            int guard = 0;
            while (isStaircaseSection(current, dir, airOnly) && guard++ < 128) {
                int height = getStaircaseHeight(current, airOnly);
                boxes.add(new Box(current.getX(), current.getY(), current.getZ(),
                        current.getX() + 1, current.getY() + height, current.getZ() + 1));
                current = current.add(dir[0], 1, dir[1]);
                steps++;
            }

            if (steps >= minLength) {
                for (Box box : boxes) {
                    addHit(hits, seen, box, TYPE_STAIR);
                }
            }
        }
    }

    private boolean isValidHoleSection(BlockPos pos, boolean airOnly) {
        return isPassable(pos, airOnly)
                && !isPassable(new BlockPos(pos.getX(), pos.getY(), pos.getZ() - 1), airOnly)
                && !isPassable(new BlockPos(pos.getX(), pos.getY(), pos.getZ() + 1), airOnly)
                && !isPassable(new BlockPos(pos.getX() + 1, pos.getY(), pos.getZ()), airOnly)
                && !isPassable(new BlockPos(pos.getX() - 1, pos.getY(), pos.getZ()), airOnly);
    }

    private boolean isValid3x1HoleSectionX(BlockPos pos, boolean airOnly) {
        int x = pos.getX(), y = pos.getY(), z = pos.getZ();
        return isPassable(x, y, z, airOnly)
                && isPassable(x + 1, y, z, airOnly)
                && isPassable(x + 2, y, z, airOnly)
                && !isPassable(x, y, z - 1, airOnly)
                && !isPassable(x, y, z + 1, airOnly)
                && !isPassable(x + 1, y, z - 1, airOnly)
                && !isPassable(x + 1, y, z + 1, airOnly)
                && !isPassable(x + 2, y, z - 1, airOnly)
                && !isPassable(x + 2, y, z + 1, airOnly)
                && !isPassable(x - 1, y, z, airOnly)
                && !isPassable(x + 3, y, z, airOnly);
    }

    private boolean isValid3x1HoleSectionZ(BlockPos pos, boolean airOnly) {
        int x = pos.getX(), y = pos.getY(), z = pos.getZ();
        return isPassable(x, y, z, airOnly)
                && isPassable(x, y, z + 1, airOnly)
                && isPassable(x, y, z + 2, airOnly)
                && !isPassable(x - 1, y, z, airOnly)
                && !isPassable(x + 1, y, z, airOnly)
                && !isPassable(x - 1, y, z + 1, airOnly)
                && !isPassable(x + 1, y, z + 1, airOnly)
                && !isPassable(x - 1, y, z + 2, airOnly)
                && !isPassable(x + 1, y, z + 2, airOnly)
                && !isPassable(x, y, z - 1, airOnly)
                && !isPassable(x, y, z + 3, airOnly);
    }

    private boolean isTunnelSection(BlockPos pos, int[] dir, boolean airOnly) {
        int height = getTunnelHeight(pos, airOnly);
        if (height < Math.round(sMinTunnelHeight.getAsDouble())
                || height > Math.round(sMaxTunnelHeight.getAsDouble())) return false;
        if (isPassable(pos.getX(), pos.getY() - 1, pos.getZ(), airOnly)) return false;
        if (isPassable(pos.getX(), pos.getY() + height, pos.getZ(), airOnly)) return false;
        boolean alongX = dir[0] != 0;
        for (int i = 0; i < height; i++) {
            int y = pos.getY() + i;
            if (alongX) {
                if (isPassable(pos.getX(), y, pos.getZ() - 1, airOnly)
                        || isPassable(pos.getX(), y, pos.getZ() + 1, airOnly)) return false;
            } else if (isPassable(pos.getX() - 1, y, pos.getZ(), airOnly)
                    || isPassable(pos.getX() + 1, y, pos.getZ(), airOnly)) {
                return false;
            }
        }
        return true;
    }

    private boolean isStaircaseSection(BlockPos pos, int[] dir, boolean airOnly) {
        int height = getStaircaseHeight(pos, airOnly);
        if (height < Math.round(sMinStairHeight.getAsDouble())
                || height > Math.round(sMaxStairHeight.getAsDouble())) return false;
        if (isPassable(pos.getX(), pos.getY() - 1, pos.getZ(), airOnly)) return false;
        if (isPassable(pos.getX(), pos.getY() + height, pos.getZ(), airOnly)) return false;
        boolean alongX = dir[0] != 0;
        for (int i = 0; i < height; i++) {
            int y = pos.getY() + i;
            if (alongX) {
                if (isPassable(pos.getX(), y, pos.getZ() - 1, airOnly)
                        || isPassable(pos.getX(), y, pos.getZ() + 1, airOnly)) return false;
            } else if (isPassable(pos.getX() - 1, y, pos.getZ(), airOnly)
                    || isPassable(pos.getX() + 1, y, pos.getZ(), airOnly)) {
                return false;
            }
        }
        return true;
    }

    private int getTunnelHeight(BlockPos pos, boolean airOnly) {
        int height = 0;
        int max = (int) Math.round(sMaxTunnelHeight.getAsDouble());
        while (height < max && isPassable(pos.getX(), pos.getY() + height, pos.getZ(), airOnly)) height++;
        return height;
    }

    private int getStaircaseHeight(BlockPos pos, boolean airOnly) {
        int height = 0;
        int max = (int) Math.round(sMaxStairHeight.getAsDouble());
        while (height < max && isPassable(pos.getX(), pos.getY() + height, pos.getZ(), airOnly)) height++;
        return height;
    }

    private static boolean isPassable(int x, int y, int z, boolean airOnly) {
        return isPassable(new BlockPos(x, y, z), airOnly);
    }

    private static boolean isPassable(BlockPos pos, boolean airOnly) {
        BlockState state = mc.world.getBlockState(pos);
        try {
            if (state.isAir()) return true;
            if (airOnly) return false;
            VoxelShape shape = state.getCollisionShape(mc.world, pos);
            return shape.isEmpty() || !VoxelShapes.fullCube().equals(shape);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void addHit(List<Hit> hits, Set<String> seen, Box box, int type) {
        String key = type + ":" + box.minX + "," + box.minY + "," + box.minZ + "," + box.maxX + "," + box.maxY + "," + box.maxZ;
        if (!seen.add(key)) return;
        for (Hit hit : hits) {
            if (hit.type == type && hit.box.intersects(box)) return;
        }
        hits.add(new Hit(box, type));
    }

    private Mode currentMode() {
        return switch (sMode.getValue()) {
            case MODE_HOLES_TUNNELS -> new Mode(true, true, false);
            case MODE_HOLES_STAIRS -> new Mode(true, false, true);
            case MODE_TUNNELS_STAIRS -> new Mode(false, true, true);
            case MODE_HOLES -> new Mode(true, false, false);
            case MODE_TUNNELS -> new Mode(false, true, false);
            case MODE_STAIRS -> new Mode(false, false, true);
            default -> new Mode(true, true, true);
        };
    }

    private int lineColor(int type) {
        return switch (type) {
            case TYPE_HOLE_3X1 -> sHole3Line.getValue();
            case TYPE_TUNNEL -> sTunnelLine.getValue();
            case TYPE_STAIR -> sStairLine.getValue();
            default -> sHoleLine.getValue();
        };
    }

    private int fillColor(int type) {
        return switch (type) {
            case TYPE_HOLE_3X1 -> sHole3Fill.getValue();
            case TYPE_TUNNEL -> sTunnelFill.getValue();
            case TYPE_STAIR -> sStairFill.getValue();
            default -> sHoleFill.getValue();
        };
    }

    private int scanRadius() {
        int renderDistance = mc.options.getViewDistance().getValue();
        float configured = (float) sScanRadius.getAsDouble();
        if (isFastTravel()) configured = Math.max(configured, ELYTRA_SCAN_RADIUS);
        return Math.min(renderDistance, Math.max(1, Math.round(configured)));
    }

    private static boolean isFastTravel() {
        if (mc.player == null) return false;
        Vec3d velocity = mc.player.getVelocity();
        double horizontalSpeedSq = velocity.x * velocity.x + velocity.z * velocity.z;
        return mc.player.isGliding() || horizontalSpeedSq > 0.18;
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (!isEnabled() || mc.player == null) return;
        boolean fill = sFill.isEnabled();
        boolean outline = sOutline.isEnabled();
        if (!fill && !outline) return;

        int px = mc.player.getBlockX() >> 4;
        int pz = mc.player.getBlockZ() >> 4;
        int radius = scanRadius();
        int lineWidth = Math.max(1, (int) Math.round(sLineWidth.getAsDouble()));
        int rendered = 0;

        for (var entry : chunkCache.entrySet()) {
            long key = entry.getKey();
            if (Math.abs(ChunkPos.getPackedX(key) - px) > radius
                    || Math.abs(ChunkPos.getPackedZ(key) - pz) > radius) continue;
            for (Hit hit : entry.getValue()) {
                if (++rendered > MAX_RENDER_BOXES) return;
                drawBox(context, tickDelta, hit.box,
                        outline ? lineColor(hit.type) : 0,
                        fill ? fillColor(hit.type) : 0,
                        outline, fill, lineWidth);
            }
        }
    }

    /** Screen-space projection of an arbitrary world box (shared-utility style). */
    public static void drawBox(DrawContext context, float tickDelta, Box box,
                               int lineCol, int fillCol, boolean outline, boolean fill, int lineWidth) {
        double minX = box.minX, minY = box.minY, minZ = box.minZ;
        int[][] c = new int[8][];
        int i = 0;
        boolean any = false;
        int sMinX = Integer.MAX_VALUE, sMinY = Integer.MAX_VALUE, sMaxX = Integer.MIN_VALUE, sMaxY = Integer.MIN_VALUE;
        for (int k = 0; k < 8; k++) {
            double x = (k & 1) == 0 ? minX : box.maxX;
            double y = (k & 2) == 0 ? minY : box.maxY;
            double z = (k & 4) == 0 ? minZ : box.maxZ;
            int[] p = ProjectionUtil.project(new Vec3d(x, y, z), tickDelta);
            c[k] = p;
            if (p != null) {
                any = true;
                sMinX = Math.min(sMinX, p[0]);
                sMinY = Math.min(sMinY, p[1]);
                sMaxX = Math.max(sMaxX, p[0]);
                sMaxY = Math.max(sMaxY, p[1]);
            }
        }
        if (!any) return;
        if (sMaxX < 0 || sMaxY < 0 || sMinX > com.anormal.client.util.RenderUtils.screenWidth()
                || sMinY > com.anormal.client.util.RenderUtils.screenHeight()) return;

        if (fill) context.fill(sMinX, sMinY, sMaxX, sMaxY, fillCol);
        if (outline) {
            int[][] edges = {{0, 1}, {1, 2}, {2, 3}, {3, 0}, {4, 5}, {5, 6}, {6, 7}, {7, 4}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};
            for (int[] ed : edges) {
                int[] a = c[ed[0]], b = c[ed[1]];
                if (a == null || b == null) continue;
                com.anormal.client.util.RenderUtils.drawLine(context, a[0], a[1], b[0], b[1], lineCol, lineWidth);
            }
        }
    }

    /**
     * Flat 16x16 chunk cell marker drawn at {@code y} (the player's own level), with an
     * optional tracer from the camera. Chunk finders use this so their markers stay visible
     * at any height instead of sitting on a hardcoded Y layer the player never sees.
     */
    public static void drawChunkMarker(DrawContext context, float tickDelta, int chunkX, int chunkZ,
                                       double y, int color, boolean tracer) {
        double x = chunkX * 16.0;
        double z = chunkZ * 16.0;
        Box cell = new Box(x, y, z, x + 16.0, y + 0.6, z + 16.0);
        drawBox(context, tickDelta, cell, color, (color & 0x60FFFFFF) | 0x20000000, true, false, 1);
        if (!tracer) return;
        int[] from = com.anormal.client.util.ProjectionUtil.project(
                com.anormal.client.util.ProjectionUtil.eye(tickDelta), tickDelta);
        int[] to = com.anormal.client.util.ProjectionUtil.project(new Vec3d(x + 8.0, y + 0.3, z + 8.0), tickDelta);
        if (from == null || to == null) return;
        com.anormal.client.util.RenderUtils.drawLine(context, from[0], from[1], to[0], to[1], color, 1);
    }

    private record Hit(Box box, int type) {}
    private record Mode(boolean holes, boolean tunnels, boolean stairs) {}
}
