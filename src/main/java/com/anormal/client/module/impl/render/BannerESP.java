package com.anormal.client.module.impl.render;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.BlockScanner;
import com.anormal.client.util.ColorUtils;
import com.anormal.client.util.RenderUtils;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Highlights banners through walls, with a dye-colour filter.
 *
 * <p>The old implementation relied on the shared budgeted block sweep alone. Banners are very thin
 * non-solid blocks and they always carry a {@code BannerBlockEntity}, so the sweep was both slow to
 * find them (several seconds for a 32-block radius, much longer at 64) and easy to miss - which is
 * why the module looked dead. It now works like the other modern ESP modules:</p>
 *
 * <ul>
 *   <li>an instant chunk scan reads the {@link BlockEntity} map of every loaded chunk in range,
 *       so banners appear the moment the module is enabled;</li>
 *   <li>a budgeted {@link BlockScanner} pass stays as a fallback for chunks whose block entities
 *       are not yet populated - it keeps {@code Scan Speed} / {@code Recentre Distance} useful;</li>
 *   <li>markers are validated every tick, so a broken banner disappears immediately;</li>
 *   <li>drawing is nearest-first, culled with {@link RenderUtils#projectBlockBounds}, budgeted per
 *       frame and shares the standard box / outline / fill / tracer / chip label look.</li>
 * </ul>
 */
public class BannerESP extends Module {

    private static final long RENDER_BUDGET_NS = 5_000_000L;

    /** Dye -> RGB, straight from the vanilla banner colours. */
    private static final Map<String, Integer> DYE_COLORS = new HashMap<>();
    private static final List<String> FILTER_DYES =
            List.of("white", "red", "blue", "green", "yellow", "black");

    static {
        DYE_COLORS.put("white", 0xF9FFFE);
        DYE_COLORS.put("orange", 0xF9801D);
        DYE_COLORS.put("magenta", 0xC74EBD);
        DYE_COLORS.put("light_blue", 0x3AB3DA);
        DYE_COLORS.put("yellow", 0xFED83D);
        DYE_COLORS.put("lime", 0x80C71F);
        DYE_COLORS.put("pink", 0xF38BAA);
        DYE_COLORS.put("gray", 0x474F52);
        DYE_COLORS.put("light_gray", 0x9D9D97);
        DYE_COLORS.put("cyan", 0x169C9C);
        DYE_COLORS.put("purple", 0x8932B8);
        DYE_COLORS.put("blue", 0x3C44AA);
        DYE_COLORS.put("brown", 0x835432);
        DYE_COLORS.put("green", 0x5E7C16);
        DYE_COLORS.put("red", 0xB02E26);
        DYE_COLORS.put("black", 0x1D1D21);
    }

    /** A found banner: its marker colour plus the dye name used in the label. */
    private record Mark(int color, String dye) {
    }

    public final ModeSetting colorFilter = new ModeSetting("Color Filter", "Only banners of color", "All",
            "All", "White", "Red", "Blue", "Green", "Yellow", "Black", "Other");
    public final BooleanSetting outline = new BooleanSetting("Outline", "Box outline", true);
    public final BooleanSetting box = new BooleanSetting("Box", "Draw box marker", true);
    public final BooleanSetting tracer = new BooleanSetting("Tracer", "Line from crosshair", false);
    public final BooleanSetting showDistance = new BooleanSetting("Show Distance", "Show distance", true);
    public final NumberSetting range = new NumberSetting("Range", "Horizontal search radius", 32.0, 8.0, 64.0, 4.0);
    public final NumberSetting verticalRange = new NumberSetting("Vertical Range", "How far up and down to search", 32.0, 8.0, 160.0, 4.0);
    public final NumberSetting scanSpeed = new NumberSetting("Scan Speed", "Blocks checked per tick", 6000.0, 1000.0, 20000.0, 500.0);
    public final NumberSetting recentreDistance = new NumberSetting("Recentre Distance", "How far you may move before the search restarts", 8.0, 4.0, 32.0, 2.0);
    public final NumberSetting maxShown = new NumberSetting("Max Shown", "Max banners marked", 20.0, 5.0, 60.0, 5.0);
    public final ColorSetting color = new ColorSetting("Color", "Marker color", ColorUtils.rgba(255, 255, 255, 255));
    public final BooleanSetting useBannerColor = new BooleanSetting("Use Banner Color", "Tint each marker with the banner's own dye", true);
    public final BooleanSetting fill = new BooleanSetting("Fill", "Translucent fill inside the marker", true);
    public final NumberSetting fillOpacity = new NumberSetting("Fill Opacity", "Fill alpha", 45.0, 5.0, 200.0, 5.0);
    public final NumberSetting outlineWidth = new NumberSetting("Outline Width", "Outline thickness in pixels", 1.0, 1.0, 3.0, 1.0);

    private final Map<BlockPos, Mark> found = new HashMap<>();
    private final BlockScanner scanner = new BlockScanner();
    private int centreX = Integer.MIN_VALUE, centreY, centreZ;

    public BannerESP() {
        super("BannerESP", "Marks banners with color filter", Category.RENDER);
        addSetting(colorFilter);
        addSetting(outline);
        addSetting(box);
        addSetting(tracer);
        addSetting(showDistance);
        addSetting(range);
        addSetting(verticalRange);
        addSetting(scanSpeed);
        addSetting(recentreDistance);
        addSetting(maxShown);
        addSetting(color);
        addSetting(useBannerColor);
        addSetting(fill);
        addSetting(fillOpacity);
        addSetting(outlineWidth);
    }

    @Override
    public void onEnable() {
        reset();
        // Instant first pass: block entities are already in the loaded chunks.
        scanChunks();
    }

    @Override
    public void onDisable() {
        reset();
    }

    private void reset() {
        found.clear();
        scanner.resetCylinder();
        centreX = Integer.MIN_VALUE;
    }

    // ------------------------------------------------------------------ scan

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;
        scanChunks();
        scanFallback();
    }

    /** Reads banners straight from the block-entity map of every loaded chunk in range. */
    private void scanChunks() {
        if (mc.player == null || mc.world == null) return;

        BlockPos origin = mc.player.getBlockPos();
        int r = range.getValue().intValue();
        int vr = verticalRange.getValue().intValue();
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

                    for (BlockEntity be : chunk.getBlockEntities().values()) {
                        BlockPos pos = be.getPos();
                        int dx = pos.getX() - origin.getX();
                        int dz = pos.getZ() - origin.getZ();
                        if (dx * dx + dz * dz > rr) continue;
                        if (Math.abs(pos.getY() - origin.getY()) > vr) continue;

                        BlockState state = chunk.getBlockState(pos);
                        Mark mark = mark(state);
                        if (mark != null) found.put(pos.toImmutable(), mark);
                    }
                }
            }
        } catch (Throwable ignored) {
        }

        // Anything broken, filtered out or out of range goes away at once.
        found.entrySet().removeIf(e -> {
            BlockPos pos = e.getKey();
            int dx = pos.getX() - origin.getX();
            int dz = pos.getZ() - origin.getZ();
            if (dx * dx + dz * dz > rr || Math.abs(pos.getY() - origin.getY()) > vr + 2) return true;
            if (!mc.world.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) return false;
            Mark now = mark(mc.world.getBlockState(pos));
            return now == null || now.color() != e.getValue().color();
        });
    }

    /** Cheap fallback sweep: catches banners whose chunk block-entity map is not ready yet. */
    private void scanFallback() {
        if (mc.player == null || mc.world == null) return;

        BlockPos origin = mc.player.getBlockPos();
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
            scanner.scanCylinder(mc.world, centreX, centreY, centreZ,
                    range.getValue().intValue(), verticalRange.getValue().intValue(),
                    scanSpeed.getValue().intValue(), scratch,
                    state -> mark(state) != null,
                    (pos, state) -> {
                        Mark mark = mark(state);
                        if (mark != null) found.put(pos.toImmutable(), mark);
                    });
        } catch (Throwable ignored) {
        }
    }

    // ----------------------------------------------------------------- match

    /** @return the marker for a banner block, or {@code null} when it is filtered out. */
    private Mark mark(BlockState state) {
        if (state == null) return null;
        String dye = dyeOf(state);
        if (dye == null || !passesFilter(dye)) return null;
        int col;
        if (useBannerColor.isEnabled()) {
            Integer rgb = DYE_COLORS.get(dye);
            col = rgb == null ? color.getValue() : (0xFF000000 | rgb);
        } else {
            col = color.getValue();
        }
        return new Mark(col, dye);
    }

    /** Extracts the dye name from {@code <dye>_banner} / {@code <dye>_wall_banner}. */
    private static String dyeOf(BlockState state) {
        try {
            String path = Registries.BLOCK.getId(state.getBlock()).getPath();
            if (path.endsWith("_wall_banner")) return path.substring(0, path.length() - "_wall_banner".length());
            if (path.endsWith("_banner")) return path.substring(0, path.length() - "_banner".length());
        } catch (Throwable ignored) {
        }
        return null;
    }

    private boolean passesFilter(String dye) {
        if (colorFilter.is("All")) return true;
        if (colorFilter.is("Other")) return !FILTER_DYES.contains(dye);
        return dye.equals(colorFilter.getValue().toLowerCase());
    }

    private static String pretty(String dye) {
        StringBuilder sb = new StringBuilder();
        for (String part : dye.split("_")) {
            if (part.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }

    // ---------------------------------------------------------------- render

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.player == null || mc.world == null || found.isEmpty()) return;

        Vec3d eye = mc.player.getEyePos();
        int limit = maxShown.getValue().intValue();
        int alpha = fillOpacity.getValue().intValue();
        int width = outlineWidth.getValue().intValue();
        double max = range.getValue();

        List<Map.Entry<BlockPos, Mark>> entries = new ArrayList<>(found.entrySet());
        entries.sort(Comparator.comparingDouble(e -> e.getKey().getSquaredDistance(eye.x, eye.y, eye.z)));

        int centreScreenX = RenderUtils.screenWidth() / 2;
        int centreScreenY = RenderUtils.screenHeight() / 2;
        long deadline = System.nanoTime() + RENDER_BUDGET_NS;
        int drawn = 0;

        try {
            for (Map.Entry<BlockPos, Mark> e : entries) {
                if (drawn >= limit) break;
                if ((drawn & 7) == 0 && System.nanoTime() > deadline) break;

                BlockPos pos = e.getKey();
                int[] bounds = RenderUtils.projectBlockBounds(pos, tickDelta);
                if (bounds == null) continue;
                drawn++;

                Mark mark = e.getValue();
                double dist = Math.sqrt(pos.getSquaredDistance(eye.x, eye.y, eye.z));
                int col = RenderUtils.fadeByDistance(mark.color(), dist, max);

                // 1. Translucent fill.
                if (fill.isEnabled()) {
                    RenderUtils.drawBlockFill(context, pos, col, tickDelta, alpha);
                }

                // 2. Crisp block outline.
                if (outline.isEnabled()) {
                    RenderUtils.drawBlockOutline(context, pos, col, tickDelta, width);
                }

                // 3. Full 3D box marker (the old "Box" look, now with the faded colour).
                if (box.isEnabled()) {
                    RenderUtils.drawBox3D(context, new Box(pos), col, (col & 0x00FFFFFF) | 0x26000000);
                }

                int cxp = (bounds[0] + bounds[2]) / 2;
                int cyp = (bounds[1] + bounds[3]) / 2;

                // 4. Tracer from the crosshair.
                if (tracer.isEnabled()) {
                    RenderUtils.drawLine(context, centreScreenX, centreScreenY, cxp, cyp,
                            (col & 0x00FFFFFF) | 0xC8000000, 1.0f);
                }

                // 5. Chip label with the dye name and (optionally) the distance.
                if (mc.textRenderer != null) {
                    String label = pretty(mark.dye()) + " Banner";
                    if (showDistance.isEnabled()) label += "  " + Math.round(dist) + "m";
                    RenderUtils.drawChip(context, mc.textRenderer, label, cxp, bounds[1] - 16, col);
                }
            }
        } catch (Throwable ignored) {
        }
    }
}
