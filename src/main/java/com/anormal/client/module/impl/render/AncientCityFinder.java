package com.anormal.client.module.impl.render;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.Clusterer;
import com.anormal.client.util.ColorUtils;
import com.anormal.client.util.ProjectionUtil;
import com.anormal.client.util.RenderUtils;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.WorldChunk;

import java.util.*;

/**
 * Finds ancient cities (Deep Dark) by detecting genuine sculk structures,
 * catalysts, shriekers, and reinforced deepslate through walls.
 *
 * <p>Upgraded to instant chunk-level discovery: finds Ancient Cities at Y = -51
 * instantly from the surface without requiring the player to dig underground.</p>
 */
public class AncientCityFinder extends Module {
    public final NumberSetting range = new NumberSetting("Range", "Horizontal scan radius in blocks", 96.0, 16.0, 256.0, 8.0);
    public final NumberSetting minBlocks = new NumberSetting("Min Sculk Blocks", "Sculk indicators to count as a city", 4.0, 2.0, 30.0, 1.0);
    public final NumberSetting maxShown = new NumberSetting("Max Shown", "Max city markers drawn", 5.0, 1.0, 20.0, 1.0);
    public final BooleanSetting chatLog = new BooleanSetting("Chat Log", "Log new cities in chat", true);
    public final BooleanSetting outline = new BooleanSetting("Outline", "Draw 3D outline at city center", true);
    public final BooleanSetting tracers = new BooleanSetting("Tracers", "Draw lines towards city center", true);
    public final BooleanSetting showDistance = new BooleanSetting("Show Distance", "Show distance in meters", true);
    public final NumberSetting outlineWidth = new NumberSetting("Outline Width", "Thickness of the outline", 1.0, 1.0, 4.0, 1.0);
    public final ColorSetting color = new ColorSetting("Color", "City marker color", ColorUtils.rgba(0, 220, 200, 255));

    private static final double CLUSTER_RADIUS = 28.0;
    private static final long RENDER_BUDGET_NS = 5_000_000L;

    private final Map<BlockPos, Integer> hits = new HashMap<>();
    private final List<Clusterer.Cluster> cities = new ArrayList<>();
    private final Set<String> logged = new HashSet<>();
    private boolean dirty = true;
    private int tickCounter = 0;

    public AncientCityFinder() {
        super("AncientCityFinder", "Finds ancient cities and sculk catalysts at Y = -51", Category.RENDER);
        addSetting(range);
        addSetting(minBlocks);
        addSetting(maxShown);
        addSetting(chatLog);
        addSetting(outline);
        addSetting(tracers);
        addSetting(showDistance);
        addSetting(outlineWidth);
        addSetting(color);
    }

    @Override
    public void onEnable() {
        hits.clear();
        cities.clear();
        scanWorldChunks();
    }

    @Override
    public void onDisable() {
        hits.clear();
        cities.clear();
        logged.clear();
    }

    private static boolean isAncientCityBlock(Block block) {
        if (block == Blocks.SCULK_CATALYST || block == Blocks.SCULK_SHRIEKER
                || block == Blocks.SCULK_SENSOR || block == Blocks.CALIBRATED_SCULK_SENSOR
                || block == Blocks.REINFORCED_DEEPSLATE) {
            return true;
        }
        String path = Registries.BLOCK.getId(block).getPath();
        return path.contains("sculk_catalyst") || path.contains("sculk_shrieker") || path.contains("reinforced_deepslate");
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;

        // Scan every 5 ticks to keep CPU at ~0%
        if (++tickCounter % 5 == 0) {
            scanWorldChunks();
        }

        if (dirty) {
            rebuild();
            dirty = false;
        }
    }

    private void scanWorldChunks() {
        if (mc.player == null || mc.world == null) return;

        BlockPos origin = mc.player.getBlockPos();
        int r = range.getValue().intValue();
        int rr = r * r;

        int minChunkX = (origin.getX() - r) >> 4;
        int maxChunkX = (origin.getX() + r) >> 4;
        int minChunkZ = (origin.getZ() - r) >> 4;
        int maxChunkZ = (origin.getZ() + r) >> 4;

        try {
            for (int cx = minChunkX; cx <= maxChunkX; cx++) {
                for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                    if (!mc.world.isChunkLoaded(cx, cz)) continue;
                    WorldChunk chunk = mc.world.getChunk(cx, cz);
                    if (chunk == null) continue;

                    // 1. Instant check: inspect BlockEntities for catalysts, shriekers, sensors
                    for (BlockEntity be : chunk.getBlockEntities().values()) {
                        BlockPos pos = be.getPos();
                        // Ancient cities generate deep underground (Y <= -20)
                        if (pos.getY() > -10) continue;

                        int dx = pos.getX() - origin.getX();
                        int dz = pos.getZ() - origin.getZ();
                        if (dx * dx + dz * dz > rr) continue;

                        Block block = chunk.getBlockState(pos).getBlock();
                        if (isAncientCityBlock(block)) {
                            if (hits.put(pos.toImmutable(), 1) == null) {
                                dirty = true;
                            }
                        }
                    }

                    // 2. Targeted sample at Ancient City center depths (Y = -52 to -45)
                    BlockPos.Mutable p = new BlockPos.Mutable();
                    int baseX = cx << 4;
                    int baseZ = cz << 4;
                    for (int y = -52; y <= -45; y += 3) {
                        for (int x = 2; x < 16; x += 5) {
                            for (int z = 2; z < 16; z += 5) {
                                p.set(baseX + x, y, baseZ + z);
                                BlockState state = chunk.getBlockState(p);
                                if (state.isAir()) continue;
                                Block b = state.getBlock();
                                if (isAncientCityBlock(b) || b == Blocks.SCULK) {
                                    if (hits.put(p.toImmutable(), 1) == null) {
                                        dirty = true;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }

        // Clean up out of range hits
        hits.keySet().removeIf(pos -> {
            int dx = pos.getX() - origin.getX();
            int dz = pos.getZ() - origin.getZ();
            if (dx * dx + dz * dz > rr + 256) {
                dirty = true;
                return true;
            }
            if (mc.world.isChunkLoaded(pos)) {
                Block b = mc.world.getBlockState(pos).getBlock();
                if (!isAncientCityBlock(b) && b != Blocks.SCULK) {
                    dirty = true;
                    return true;
                }
            }
            return false;
        });
    }

    private void rebuild() {
        cities.clear();
        cities.addAll(Clusterer.cluster(hits.keySet(),
                CLUSTER_RADIUS, minBlocks.getValue().intValue(), maxShown.getValue().intValue()));

        if (!chatLog.isEnabled() || mc.inGameHud == null) return;
        for (Clusterer.Cluster c : cities) {
            BlockPos center = c.center();
            String key = (center.getX() >> 4) + "," + (center.getZ() >> 4);
            if (!logged.add(key)) continue;
            try {
                int dist = (int) Math.sqrt(mc.player.getBlockPos().getSquaredDistance(center));
                mc.inGameHud.getChatHud().addMessage(Text.literal(
                        "§b[Ancient City] §fFound near §eX: " + center.getX() + ", Y: " + center.getY() + ", Z: " + center.getZ() +
                                " §7(" + dist + "m, " + c.size + " sculk blocks)"));
            } catch (Throwable ignored) {
            }
        }
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.player == null || cities.isEmpty()) return;

        int col = color.getValue();
        int width = outlineWidth.getValue().intValue();
        long deadline = System.nanoTime() + RENDER_BUDGET_NS;
        int drawn = 0;
        int limit = maxShown.getValue().intValue();
        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();

        for (Clusterer.Cluster c : cities) {
            if (drawn++ >= limit || System.nanoTime() > deadline) break;

            BlockPos center = c.center();

            // 1. 3D outline box around center
            if (outline.isEnabled()) {
                RenderUtils.drawBlockOutline(context, center, col, tickDelta, width);
            }

            // 2. Waypoint tag
            int[] sc = ProjectionUtil.project(
                    new Vec3d(center.getX() + 0.5, center.getY() + 1.2, center.getZ() + 0.5), tickDelta);
            if (sc != null) {
                // Tracer
                if (tracers.isEnabled()) {
                    int steps = Math.min(150, Math.max(Math.abs(sc[0] - sw / 2), Math.abs(sc[1] - sh / 2)));
                    for (int i = 0; i <= steps; i++) {
                        int lx = sw / 2 + (sc[0] - sw / 2) * i / Math.max(1, steps);
                        int ly = sh / 2 + (sc[1] - sh / 2) * i / Math.max(1, steps);
                        context.fill(lx, ly, lx + 1, ly + 1, col);
                    }
                }

                if (mc.textRenderer != null) {
                    double dist = Math.sqrt(mc.player.getBlockPos().getSquaredDistance(center));
                    String label = "§bAncient City §8[" + c.size + "x]" + (showDistance.isEnabled() ? " §7" + (int) dist + "m" : "");
                    RenderUtils.drawText(context, mc.textRenderer, label, sc[0] - mc.textRenderer.getWidth(label) / 2, sc[1] - 12, 0xFFFFFFFF, true);
                    String coords = "§8(Y: " + center.getY() + ")";
                    RenderUtils.drawText(context, mc.textRenderer, coords, sc[0] - mc.textRenderer.getWidth(coords) / 2, sc[1], 0xFFCCCCCC, true);
                }
            }
        }
    }
}
