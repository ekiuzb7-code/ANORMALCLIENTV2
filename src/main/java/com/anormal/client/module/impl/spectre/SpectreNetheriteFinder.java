package com.anormal.client.module.impl.spectre;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.BlockScanner;
import com.anormal.client.util.NotificationManager;
import com.anormal.client.util.ColorUtils;
import com.anormal.client.util.ProjectionUtil;
import com.anormal.client.util.RenderUtils;
import net.minecraft.block.Blocks;
import net.minecraft.block.BlockState;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Spectre port: "Netherite Finder" — boxes Nether chunk sections hiding ancient debris.
 *
 * <p>The classic mode scans loaded nether chunk data budgeted per tick and boxes every
 * section that contains ancient debris (the block data is already present client-side).
 * The tracer path draws lines to the nearest N sections like Spectre.</p>
 */
public final class SpectreNetheriteFinder extends Module {
    private static final int MAX_SECTIONS = 128;
    private static final int SCAN_BUDGET = 6000;
    private static final long SCAN_CACHE_MS = 30_000L;
    private static final long NOTIFY_COOLDOWN_MS = 2000L;

    public final ColorSetting color = addSetting(new ColorSetting("Near Color", "Near-section color", 0xFFFF7B00));
    public final ColorSetting farColor = addSetting(new ColorSetting("Far Color", "Far-section color", 0xFF48D6B5));
    public final NumberSetting fade = addSetting(new NumberSetting("Fade Distance", "Distance where near fades to far", 192.0, 32.0, 512.0, 8.0));
    public final NumberSetting range = addSetting(new NumberSetting("Range", "Scan radius in blocks", 512.0, 64.0, 1024.0, 1.0));
    public final BooleanSetting tracers = addSetting(new BooleanSetting("Chunk Tracers", "Draw tracer lines to sections", true));
    public final NumberSetting tracerCount = addSetting(new NumberSetting("Tracer Count", "Lines to nearest sections", 3.0, 1.0, 8.0, 1.0));
    public final BooleanSetting notifySetting = addSetting(new BooleanSetting("Notifications", "Notify on newly found sections", false));

    /** sectionKey -> world-space centre pos of a section that contains ancient debris */
    private final Map<Long, BlockPos> sections = new ConcurrentHashMap<>();
    /** sectionKey -> timestamp of the last completed scan, so passes resume instead of restarting */
    private final Map<Long, Long> scannedAt = new ConcurrentHashMap<>();
    private final BlockScanner scanner = new BlockScanner();
    private final BlockPos.Mutable scratch = new BlockPos.Mutable();
    private int newCount = 0;
    private BlockPos nearestPending;
    private double nearestPendingDistSq = Double.MAX_VALUE;
    private long lastNotifyAt = 0L;

    public SpectreNetheriteFinder() {
        super("SpectreNetheriteFinder", "Spectre port: boxes Nether chunk sections hiding ancient debris.", Category.SPECTRE);
    }

    @Override
    public void onEnable() {
        sections.clear();
        scannedAt.clear();
        newCount = 0;
        nearestPending = null;
        nearestPendingDistSq = Double.MAX_VALUE;
        lastNotifyAt = 0L;
        scanner.reset();
    }

    @Override
    public void onDisable() {
        sections.clear();
        scannedAt.clear();
        newCount = 0;
        nearestPending = null;
        nearestPendingDistSq = Double.MAX_VALUE;
        scanner.reset();
    }

    @Override
    public void onTick() {
        if (mc.world == null || mc.player == null) return;
        if (!isNether()) {
            sections.clear();
            scannedAt.clear();
            return;
        }

        long now = System.currentTimeMillis();
        prune(now);

        int budget = SCAN_BUDGET;
        outer:
        for (ChunkPos chunk : loadedNetherChunks()) {
            for (int secY = mc.world.getBottomY() >> 4; secY < ((mc.world.getBottomY() + mc.world.getHeight()) >> 4); secY++) {
                long key = sectionKey(chunk.x, secY, chunk.z);
                Long stamp = scannedAt.get(key);
                if (stamp != null && now - stamp < SCAN_CACHE_MS) continue;

                int baseX = chunk.x << 4;
                int baseY = secY << 4;
                int baseZ = chunk.z << 4;
                boolean has = false;
                for (int x = 0; x < 16 && !has; x++) {
                    for (int y = 0; y < 16 && !has; y++) {
                        for (int z = 0; z < 16 && !has; z++) {
                            if (budget-- <= 0) break outer;
                            BlockState state = mc.world.getBlockState(scratch.set(baseX + x, baseY + y, baseZ + z));
                            if (state.getBlock() == Blocks.ANCIENT_DEBRIS) has = true;
                        }
                    }
                }
                scannedAt.put(key, now);

                if (has) {
                    BlockPos centre = sectionCentre(chunk.x, secY, chunk.z);
                    if (sections.put(key, centre) == null) {
                        newCount++;
                        double distSq = mc.player.squaredDistanceTo(centre.getX(), centre.getY(), centre.getZ());
                        if (distSq < nearestPendingDistSq) {
                            nearestPendingDistSq = distSq;
                            nearestPending = centre;
                        }
                    }
                }
                if (sections.size() >= MAX_SECTIONS) break outer;
            }
        }

        flushNotifications();
    }

    /** Drop cached sections (and their scan stamps) that fell out of range, expire old scans. */
    private void prune(long now) {
        if (mc.player == null) return;
        double rangeSq = range.getAsDouble() * range.getAsDouble();
        var iterator = sections.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Long, BlockPos> entry = iterator.next();
            BlockPos centre = entry.getValue();
            if (mc.player.squaredDistanceTo(centre.getX(), centre.getY(), centre.getZ()) > rangeSq) {
                iterator.remove();
                scannedAt.remove(entry.getKey());
            }
        }
        scannedAt.entrySet().removeIf(e -> now - e.getValue() > SCAN_CACHE_MS);
    }

    private void flushNotifications() {
        if (newCount == 0) {
            resetPending();
            return;
        }
        long now = System.currentTimeMillis();
        if (notifySetting.isEnabled() && now - lastNotifyAt >= NOTIFY_COOLDOWN_MS) {
            latestNotify();
        }
        if (!notifySetting.isEnabled()) {
            resetPending();
        }
    }

    private void resetPending() {
        newCount = 0;
        nearestPending = null;
        nearestPendingDistSq = Double.MAX_VALUE;
    }

    private void latestNotify() {
        if (nearestPending == null) {
            newCount = 0;
            return;
        }
        lastNotifyAt = System.currentTimeMillis();
        NotificationManager.push("Netherite Finder", newCount + " new debris sections found", ColorUtils.rgba(255, 170, 30, 255));
        resetPending();
    }

    private static BlockPos sectionCentre(int cx, int secY, int cz) {
        return new BlockPos((cx << 4) + 8, (secY << 4) + 8, (cz << 4) + 8);
    }

    private static long sectionKey(int cx, int secY, int cz) {
        return ((long) (cx & 0x1FFFFF) << 42) | ((long) (secY & 0xFFFF) << 21) | ((long) (cz & 0x1FFFFF));
    }

    private boolean isNether() {
        return mc.world != null && mc.world.getRegistryKey().getValue().toString().contains("the_nether");
    }

    private List<ChunkPos> loadedNetherChunks() {
        List<ChunkPos> out = new ArrayList<>();
        if (mc.player == null || mc.world == null) return out;
        int r = (int) Math.ceil(Math.min(16, range.getAsDouble() / 16.0));
        int cx = mc.player.getBlockX() >> 4;
        int cz = mc.player.getBlockZ() >> 4;
        for (int x = cx - r; x <= cx + r; x++) {
            for (int z = cz - r; z <= cz + r; z++) {
                try {
                    if (mc.world.getChunkManager().isChunkLoaded(x, z)) out.add(new ChunkPos(x, z));
                } catch (Throwable ignored) {
                }
            }
        }
        return out;
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (!isEnabled() || mc.player == null || sections.isEmpty()) return;
        double fadeDist = fade.getAsDouble();
        int near = color.getValue() | 0xFF000000;
        int farVal = farColor.getValue() | 0xFF000000;
        Vec3d eye = ProjectionUtil.eye(tickDelta);

        List<double[]> sortedDist = new ArrayList<>();
        for (BlockPos centre : sections.values()) {
            double distSq = mc.player.squaredDistanceTo(centre.getX(), centre.getY(), centre.getZ());
            double dist = Math.sqrt(distSq);
            double t = Math.min(1.0, dist / fadeDist);
            int col = lerpColor(near, farVal, t);
            Box box = new Box(centre.getX() - 8, centre.getY() - 8, centre.getZ() - 8,
                    centre.getX() + 8, centre.getY() + 8, centre.getZ() + 8);
            SpectreHoleEsp.drawBox(context, tickDelta, box, col, (col & 0x30FFFFFF) | 0x14000000, true, true, 1);
            if (tracers.isEnabled()) {
                sortedDist.add(new double[]{centre.getX(), centre.getY(), centre.getZ(), dist});
            }
        }
        if (tracers.isEnabled()) {
            sortedDist.sort((a, b) -> Double.compare(a[3], b[3]));
            int count = (int) Math.max(1, tracerCount.getAsDouble());
            for (int i = 0; i < Math.min(count, sortedDist.size()); i++) {
                double[] p = sortedDist.get(i);
                int[] from = ProjectionUtil.project(eye, tickDelta);
                int[] to = ProjectionUtil.project(new Vec3d(p[0], p[1], p[2]), tickDelta);
                if (from == null || to == null) continue;
                RenderUtils.drawLine(context, from[0], from[1], to[0], to[1],
                        color.getValue() | 0xFF000000, 1);
            }
        }
    }

    /** Simple linear color blend for the near/far fade. */
    private static int lerpColor(int a, int b, double t) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int r = (int) (ar + (br - ar) * t);
        int g = (int) (ag + (bg - ag) * t);
        int bl = (int) (ab + (bb - ab) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }
}
