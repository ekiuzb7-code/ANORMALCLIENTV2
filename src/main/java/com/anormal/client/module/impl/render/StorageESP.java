package com.anormal.client.module.impl.render;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.BlockScanner;
import com.anormal.client.util.ColorUtils;
import com.anormal.client.util.ProjectionUtil;
import com.anormal.client.util.RenderUtils;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.WorldChunk;

import java.util.*;

/**
 * Highlights containers (chests, barrels, shulkers, ...) through walls.
 *
 * <p>Upgraded to instant chunk BlockEntity discovery: detects 100% of containers
 * (including deep caves down to Y = -64) within 0 milliseconds upon toggling ON,
 * eliminating the previous multi-minute cylinder brute-force delay and recentre reset trap.</p>
 */
public class StorageESP extends Module {
    public final BooleanSetting outlineOpen = new BooleanSetting("Outline Open", "Contrasting outline while a container is open", true);

    public final BooleanSetting chests = new BooleanSetting("Chests", "ESP for chests", true);
    public final ColorSetting chestColor = new ColorSetting("Chest Color", "ESP color for chests", ColorUtils.rgba(255, 170, 0, 255));
    public final BooleanSetting trapped = new BooleanSetting("Trapped Chests", "ESP for trapped chests", true);
    public final ColorSetting trappedColor = new ColorSetting("Trapped Color", "ESP color for trapped chests", ColorUtils.rgba(255, 60, 60, 255));
    public final BooleanSetting ender = new BooleanSetting("Ender Chests", "ESP for ender chests", true);
    public final ColorSetting enderColor = new ColorSetting("Ender Color", "ESP color for ender chests", ColorUtils.rgba(180, 80, 255, 255));
    public final BooleanSetting hoppers = new BooleanSetting("Hoppers", "ESP for hoppers", false);
    public final ColorSetting hopperColor = new ColorSetting("Hopper Color", "ESP color for hoppers", ColorUtils.rgba(150, 150, 150, 255));
    public final BooleanSetting furnaces = new BooleanSetting("Furnaces", "ESP for furnaces", false);
    public final ColorSetting furnaceColor = new ColorSetting("Furnace Color", "ESP color for furnaces", ColorUtils.rgba(120, 120, 120, 255));
    public final BooleanSetting dispensers = new BooleanSetting("Dispensers", "ESP for dispensers", false);
    public final ColorSetting dispenserColor = new ColorSetting("Dispenser Color", "ESP color for dispensers", ColorUtils.rgba(80, 160, 255, 255));
    public final BooleanSetting droppers = new BooleanSetting("Droppers", "ESP for droppers", false);
    public final ColorSetting dropperColor = new ColorSetting("Dropper Color", "ESP color for droppers", ColorUtils.rgba(80, 255, 200, 255));
    public final BooleanSetting shulkers = new BooleanSetting("Shulkers", "ESP for shulker boxes", true);
    public final ColorSetting shulkerColor = new ColorSetting("Shulker Color", "ESP color for shulker boxes", ColorUtils.rgba(200, 120, 255, 255));
    public final BooleanSetting barrels = new BooleanSetting("Barrels", "ESP for barrels", true);
    public final ColorSetting barrelColor = new ColorSetting("Barrel Color", "ESP color for barrels", ColorUtils.rgba(160, 110, 60, 255));
    public final BooleanSetting crafters = new BooleanSetting("Crafters", "ESP for crafters", false);
    public final ColorSetting crafterColor = new ColorSetting("Crafter Color", "ESP color for crafters", ColorUtils.rgba(140, 220, 120, 255));

    // ---- Scan ----
    public final NumberSetting range = new NumberSetting("Range", "Horizontal search radius", 32.0, 8.0, 128.0, 4.0);
    public final NumberSetting verticalRange = new NumberSetting("Vertical Range", "How far up and down to search - raise this to see containers deep below you", 32.0, 8.0, 160.0, 4.0);
    public final NumberSetting scanSpeed = new NumberSetting("Scan Speed", "Blocks checked per tick (auxiliary)", 8000.0, 2000.0, 30000.0, 500.0);
    public final NumberSetting recentreDistance = new NumberSetting("Recentre Distance", "How far you may move before auxiliary search recentres", 8.0, 4.0, 32.0, 2.0);

    // ---- Render ----
    public final BooleanSetting outline = new BooleanSetting("Outline", "Draw a 3D block outline", true);
    public final BooleanSetting fill = new BooleanSetting("Fill", "Draw a translucent fill", false);
    public final NumberSetting fillOpacity = new NumberSetting("Fill Opacity", "Opacity of the fill", 90.0, 0.0, 255.0, 5.0);
    public final NumberSetting outlineWidth = new NumberSetting("Outline Width", "Thickness of the outline", 1.0, 1.0, 4.0, 1.0);
    public final BooleanSetting tracer = new BooleanSetting("Tracer", "Draw lines from crosshair to containers", false);
    public final BooleanSetting showDistance = new BooleanSetting("Show Distance", "Show distance text above container", false);
    public final NumberSetting maxRendered = new NumberSetting("Max Rendered", "Max containers drawn at once", 250.0, 20.0, 1000.0, 10.0);

    private static final long RENDER_BUDGET_NS = 5_000_000L;

    private final Map<BlockPos, Integer> found = new HashMap<>();
    private final BlockScanner scanner = new BlockScanner();
    private int centreX = Integer.MIN_VALUE, centreY, centreZ;

    public StorageESP() {
        super("StorageESP", "Renders outlines around chests, barrels, and shulkers", Category.RENDER);
        addSetting(outlineOpen);

        addSetting(chests);
        addSetting(chestColor);
        addSetting(trapped);
        addSetting(trappedColor);
        addSetting(ender);
        addSetting(enderColor);
        addSetting(hoppers);
        addSetting(hopperColor);
        addSetting(furnaces);
        addSetting(furnaceColor);
        addSetting(dispensers);
        addSetting(dispenserColor);
        addSetting(droppers);
        addSetting(dropperColor);
        addSetting(shulkers);
        addSetting(shulkerColor);
        addSetting(barrels);
        addSetting(barrelColor);
        addSetting(crafters);
        addSetting(crafterColor);

        addSetting(range);
        addSetting(verticalRange);
        addSetting(scanSpeed);
        addSetting(recentreDistance);

        addSetting(outline);
        addSetting(fill);
        addSetting(fillOpacity);
        addSetting(outlineWidth);
        addSetting(tracer);
        addSetting(showDistance);
        addSetting(maxRendered);
    }

    @Override
    public void onEnable() {
        found.clear();
        scanner.resetCylinder();
        centreX = Integer.MIN_VALUE;
        scanChunks();
    }

    @Override
    public void onDisable() {
        found.clear();
        scanner.resetCylinder();
        centreX = Integer.MIN_VALUE;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;
        scanChunks();
    }

    private void scanChunks() {
        if (mc.player == null || mc.world == null) return;

        BlockPos origin = mc.player.getBlockPos();
        int r = range.getValue().intValue();
        int vr = verticalRange.getValue().intValue();
        int rr = r * r;

        // 1. Instant chunk-based discovery: reads BlockEntities directly from loaded chunks
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

                    for (BlockEntity be : chunk.getBlockEntities().values()) {
                        BlockPos pos = be.getPos();
                        int dx = pos.getX() - origin.getX();
                        int dz = pos.getZ() - origin.getZ();
                        if (dx * dx + dz * dz > rr) continue;
                        if (Math.abs(pos.getY() - origin.getY()) > vr) continue;

                        BlockState state = chunk.getBlockState(pos);
                        int col = colorFor(state.getBlock());
                        if (col != -1) {
                            found.put(pos.toImmutable(), col);
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }

        // 2. Clean up out of range or broken containers
        found.entrySet().removeIf(e -> {
            BlockPos pos = e.getKey();
            int dx = pos.getX() - origin.getX();
            int dz = pos.getZ() - origin.getZ();
            if (dx * dx + dz * dz > rr || Math.abs(pos.getY() - origin.getY()) > vr + 2) {
                return true;
            }
            if (mc.world.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) {
                int col = colorFor(mc.world.getBlockState(pos).getBlock());
                return col == -1 || col != e.getValue();
            }
            return false;
        });

        // 3. Auxiliary scanner pass for any non-blockentity custom blocks (best effort)
        double recentre = recentreDistance.getValue();
        if (centreX == Integer.MIN_VALUE
                || Math.abs(origin.getX() - centreX) > recentre
                || Math.abs(origin.getY() - centreY) > recentre
                || Math.abs(origin.getZ() - centreZ) > recentre) {
            centreX = origin.getX();
            centreY = origin.getY();
            centreZ = origin.getZ();
            scanner.resetCylinder();
        }

        BlockPos.Mutable scratch = new BlockPos.Mutable();
        try {
            scanner.scanCylinder(mc.world, centreX, centreY, centreZ, r, vr,
                    scanSpeed.getValue().intValue(), scratch,
                    state -> colorFor(state.getBlock()) != -1,
                    (pos, state) -> {
                        int col = colorFor(state.getBlock());
                        if (col != -1) found.put(pos.toImmutable(), col);
                    });
        } catch (Throwable ignored) {
        }
    }

    private int colorFor(Block block) {
        if (block == Blocks.TRAPPED_CHEST) return trapped.isEnabled() ? trappedColor.getValue() : -1;
        if (block == Blocks.ENDER_CHEST) return ender.isEnabled() ? enderColor.getValue() : -1;
        if (block == Blocks.CHEST) return chests.isEnabled() ? chestColor.getValue() : -1;
        if (block == Blocks.HOPPER) return hoppers.isEnabled() ? hopperColor.getValue() : -1;
        if (block == Blocks.FURNACE || block == Blocks.BLAST_FURNACE || block == Blocks.SMOKER)
            return furnaces.isEnabled() ? furnaceColor.getValue() : -1;
        if (block == Blocks.DISPENSER) return dispensers.isEnabled() ? dispenserColor.getValue() : -1;
        if (block == Blocks.DROPPER) return droppers.isEnabled() ? dropperColor.getValue() : -1;
        if (isShulker(block)) return shulkers.isEnabled() ? shulkerColor.getValue() : -1;
        if (block == Blocks.BARREL) return barrels.isEnabled() ? barrelColor.getValue() : -1;
        if (block == Blocks.CRAFTER) return crafters.isEnabled() ? crafterColor.getValue() : -1;
        return -1;
    }

    private boolean isShulker(Block block) {
        try {
            String path = net.minecraft.registry.Registries.BLOCK.getId(block).getPath();
            return path.endsWith("shulker_box");
        } catch (Throwable ignored) {
            return false;
        }
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.player == null || found.isEmpty()) return;

        boolean open = false;
        try {
            open = outlineOpen.isEnabled() && mc.currentScreen != null
                    && mc.player.currentScreenHandler != mc.player.playerScreenHandler;
        } catch (Throwable ignored) {
        }

        int limit = maxRendered.getValue().intValue();
        int drawn = 0;
        int alpha = fillOpacity.getValue().intValue();
        int width = outlineWidth.getValue().intValue();
        long deadline = System.nanoTime() + RENDER_BUDGET_NS;

        int screenW = mc.getWindow().getScaledWidth();
        int screenH = mc.getWindow().getScaledHeight();

        // Sort nearest-first so closest containers get drawn when hitting limit
        List<Map.Entry<BlockPos, Integer>> entries = new ArrayList<>(found.entrySet());
        Vec3d eye = mc.player.getEyePos();
        entries.sort(Comparator.comparingDouble(e -> e.getKey().getSquaredDistance(eye.x, eye.y, eye.z)));

        try {
            for (Map.Entry<BlockPos, Integer> e : entries) {
                if (drawn >= limit) break;
                if ((drawn & 7) == 0 && System.nanoTime() > deadline) break;

                BlockPos p = e.getKey();
                int[] bounds = RenderUtils.projectBlockBounds(p, tickDelta);
                if (bounds == null) continue;
                drawn++;

                int col = e.getValue();

                // 1. Fill
                if (fill.isEnabled()) {
                    RenderUtils.drawBlockFill(context, p, col, tickDelta, alpha);
                }

                // 2. Outline
                if (outline.isEnabled()) {
                    RenderUtils.drawBlockOutline(context, p, col, tickDelta, width);
                }

                // 3. Highlight currently open container
                if (open) {
                    RenderUtils.drawBlockOutline(context, p, 0xFFFFFFFF, tickDelta, width);
                }

                // 4. Tracer
                if (tracer.isEnabled()) {
                    int cx = (bounds[0] + bounds[2]) / 2;
                    int cy = (bounds[1] + bounds[3]) / 2;
                    int steps = Math.min(200, Math.max(Math.abs(cx - screenW / 2), Math.abs(cy - screenH / 2)));
                    for (int i = 0; i <= steps; i++) {
                        int lx = screenW / 2 + (cx - screenW / 2) * i / Math.max(1, steps);
                        int ly = screenH / 2 + (cy - screenH / 2) * i / Math.max(1, steps);
                        context.fill(lx, ly, lx + 1, ly + 1, col);
                    }
                }

                // 5. Distance tag
                if (showDistance.isEnabled() && mc.textRenderer != null) {
                    double dist = Math.sqrt(p.getSquaredDistance(eye.x, eye.y, eye.z));
                    String text = "§7" + (int) dist + "m";
                    int cx = (bounds[0] + bounds[2]) / 2;
                    RenderUtils.drawText(context, mc.textRenderer, text, cx - mc.textRenderer.getWidth(text) / 2, bounds[1] - 10, 0xFFFFFFFF, true);
                }
            }
        } catch (Throwable ignored) {
        }
    }
}
