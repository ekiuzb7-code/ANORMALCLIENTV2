package com.anormal.client.autobuilder2.build;

import com.anormal.client.autobuilder2.litematic.LitematicaFile;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.FluidBlock;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A decoded schematic pinned to a world position, together with the order its blocks will be
 * placed in.
 *
 * <p>{@code anchor} is the world position that grid cell {@code (0,0,0)} maps to, so a block at
 * grid cell {@code (x,y,z)} goes to {@code anchor + (x,y,z)}. Ordering is delegated to
 * {@link BuildOrder}, which works bottom-up layer by layer.
 */
public final class BuildPlan {
    /** One block the builder should end up placing. */
    public record Target(GridPos world, String blockId, Map<String, String> properties, BlockState state) {
        /**
         * False when the cell cannot be built at all: this game version has no such block, or it is
         * a fluid, which no hand or item can put down — lava and water in a schematic are scenery
         * from wherever the author built it, not something to click into place.
         */
        public boolean supported() {
            return this.state != null && !this.state.isAir()
                    && !(this.state.getBlock() instanceof FluidBlock);
        }
    }

    private final LitematicaFile file;
    private final GridPos anchor;
    private final List<GridPos> order;

    /**
     * Every cell the plan wants a block in, for asking whether a cell is one of them.
     *
     * <p>Needed before anything temporary is put down: a placeholder or a tower block standing in a
     * cell the plan has a block for stops that block from ever being placed there, and a build with a
     * hole in it is worse than one that took a little longer.
     */
    private final Set<GridPos> wanted = new HashSet<>();

    /**
     * Lowest grid layer that actually holds a block.
     *
     * <p>A schematic's bounding box often includes empty layers at the bottom, because the region
     * the author selected was taller than the structure. Building from the bounding box corner
     * would then leave the whole structure hovering above the ground.
     */
    public static int lowestFilledLayer(LitematicaFile file) {
        for (int y = 0; y < file.height; y++) {
            for (int z = 0; z < file.length; z++) {
                for (int x = 0; x < file.width; x++) {
                    if (file.voxels[(y * file.length + z) * file.width + x] > 0) {
                        return y;
                    }
                }
            }
        }
        return 0;
    }

    /** A layer this thin, with far more above it, is a protrusion rather than a base. */
    private static final int STRAY_LAYER_MAX = 3;

    /** Number of blocks in one grid layer. */
    private static int countLayer(LitematicaFile file, int layer) {
        int count = 0;
        for (int z = 0; z < file.length; z++) {
            for (int x = 0; x < file.width; x++) {
                if (file.voxels[(layer * file.length + z) * file.width + x] > 0) {
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * The layer the structure actually stands on.
     *
     * <p>Usually that is simply the lowest layer holding a block, but not always: a selection box is
     * drawn by hand, and anything caught inside it belongs to the schematic. A stray block or two
     * sitting at the very bottom — a corner marker, a floating decoration — is not something a build
     * can stand on. Seating on one leaves the real base hovering a layer up, with nothing beside it,
     * and the builder has no face to click and stops.
     *
     * <p>So a layer only counts as a protrusion when it is a handful of blocks <em>and</em> far
     * thinner than the layer above it. A genuine thin base (a wide slab, a low wall) clears that bar
     * comfortably, so ordinary schematics are unaffected.
     */
    public static int baseLayer(LitematicaFile file) {
        int layer = lowestFilledLayer(file);
        while (layer < file.height - 1) {
            int here = countLayer(file, layer);
            int above = countLayer(file, layer + 1);
            if (here <= STRAY_LAYER_MAX && here * 4 < above) {
                layer++;
                continue;
            }
            break;
        }
        return layer;
    }

    /**
     * How far above and below the player a build site is looked for.
     *
     * <p>Looking below matters on hills and in caves, looking above matters when the player stands
     * in a hole; either way the search stays near the player so the build goes where they are.
     */
    private static final int SITE_SEARCH_UP = 3;
    private static final int SITE_SEARCH_DOWN = 10;

    /**
     * Finds a flat, solid surface to build on, near where the player is standing.
     *
     * <p>Seating a build at the player's own feet assumes they are standing on the floor of the
     * site, which is not true on ground that is uneven or merely decorated. Grass, snow layers,
     * sea pickles, carpets and slabs all sit above the real floor, and building on top of them
     * leaves the schematic stepping up and down with the scenery — and leaves the builder trying
     * to place blocks against a plant.
     *
     * <p>So candidates are scored on the whole {@linkplain #supportFootprint footprint} of the
     * structure's lowest layer: a site is flat when every cell has a full solid cube directly
     * under it. Decoration above the floor is deliberately ignored, since the builder clears what
     * it needs to as it goes. Ties break towards the player's own level, then towards the level
     * with the least to clear.
     *
     * @param file  the schematic, for its footprint
     * @param near  the player's position, which the chosen site is kept close to
     * @param world the world to inspect
     * @return the anchor to build from
     */
    public static GridPos findFlatSite(LitematicaFile file, GridPos near, BlockView world) {
        List<GridPos> footprint = supportFootprint(file);
        GridPos best = null;
        int bestUnsupported = Integer.MAX_VALUE;
        int bestBlocked = Integer.MAX_VALUE;
        int bestDistance = Integer.MAX_VALUE;

        for (int dy = SITE_SEARCH_UP; dy >= -SITE_SEARCH_DOWN; dy--) {
            int y = near.y() + dy;
            int unsupported = 0;
            int blocked = 0;
            for (GridPos cell : footprint) {
                BlockState ground = world.getBlockState(
                        new BlockPos(near.x() + cell.x(), y - 1, near.z() + cell.z()));
                // A full solid cube is what makes a surface flat: it fills its block exactly, with
                // no step, slope or plant poking up through the build.
                if (!ground.isOpaqueFullCube()) {
                    unsupported++;
                    continue;
                }
                BlockState above = world.getBlockState(
                        new BlockPos(near.x() + cell.x(), y, near.z() + cell.z()));
                if (!above.isAir() && !above.isReplaceable()) {
                    blocked++;
                }
            }
            // Two levels with the same score are the same site one block apart, so this is a
            // lexicographic pick rather than a fuzzy one.
            int distance = Math.abs(dy);
            boolean better = unsupported < bestUnsupported
                    || (unsupported == bestUnsupported && blocked < bestBlocked)
                    || (unsupported == bestUnsupported && blocked == bestBlocked
                            && distance < bestDistance);
            if (better) {
                bestUnsupported = unsupported;
                bestBlocked = blocked;
                bestDistance = distance;
                best = new GridPos(near.x(), y, near.z());
                if (unsupported == 0 && blocked == 0 && dy == 0) {
                    // A perfectly flat, perfectly clear site at the player's own level.
                    break;
                }
            }
        }
        if (best == null) {
            best = new GridPos(near.x(), near.y(), near.z());
        }
        // The site level is where the base layer's blocks go, so the anchor is the grid corner
        // that puts that layer there.
        return best.offset(0, -baseLayer(file), 0);
    }

    /**
     * The grid cells of the lowest populated layer, relative to the grid corner: the cells that
     * actually need something solid underneath. Empty cells of the bounding box are skipped, so a
     * footprint that is mostly hollow is not tested against ground it never touches.
     */
    private static List<GridPos> supportFootprint(LitematicaFile file) {
        int layer = baseLayer(file);
        List<GridPos> cells = new ArrayList<>();
        for (int z = 0; z < file.length; z++) {
            for (int x = 0; x < file.width; x++) {
                if (file.voxels[(layer * file.length + z) * file.width + x] > 0) {
                    cells.add(new GridPos(x, layer, z));
                }
            }
        }
        if (cells.isEmpty()) {
            cells.add(new GridPos(0, layer, 0));
        }
        return cells;
    }

    /**
     * @param anchor     world position of grid cell {@code (0,0,0)}
     * @param localStart where the builder is, in grid-local coordinates; only steers the first layer
     */
    public BuildPlan(LitematicaFile file, GridPos anchor, GridPos localStart) {
        this.file = file;
        this.anchor = anchor;
        this.order = BuildOrder.order(new GridOrder(file), localStart);
        for (GridPos local : this.order) {
            this.wanted.add(new GridPos(this.anchor.x() + local.x(), this.anchor.y() + local.y(),
                    this.anchor.z() + local.z()));
        }
    }

    /** True when the plan has a block of its own for this world cell. */
    public boolean wanted(GridPos world) {
        return this.wanted.contains(world);
    }

    public LitematicaFile file() {
        return this.file;
    }

    public GridPos anchor() {
        return this.anchor;
    }

    /** Number of blocks to place. */
    public int size() {
        return this.order.size();
    }

    public GridPos localPos(int index) {
        return this.order.get(index);
    }

    public GridPos worldPos(int index) {
        GridPos local = this.order.get(index);
        return new GridPos(this.anchor.x() + local.x(), this.anchor.y() + local.y(),
                this.anchor.z() + local.z());
    }

    /** The block for a step, resolved against the current game version. */
    public Target target(int index) {
        GridPos local = this.order.get(index);
        String id = this.file.identifierAt(local.x(), local.y(), local.z());
        Map<String, String> properties = this.file.propertiesAt(local.x(), local.y(), local.z());
        return new Target(worldPos(index), id, properties, BlockStateResolver.resolve(id, properties));
    }

    /** True when the schematic itself needs this block, so it must not be used as scaffolding. */
    public boolean needs(Block block) {
        String id = Registries.BLOCK.getId(block).toString();
        for (LitematicaFile.Entry entry : this.file.materials) {
            if (entry.id.equals(id)) {
                return true;
            }
        }
        return false;
    }

    /** The axis-aligned world box the build occupies, as {@code {minX,minY,minZ,maxX,maxY,maxZ}}. */
    public int[] worldBounds() {
        return new int[] {
                this.anchor.x(),
                this.anchor.y(),
                this.anchor.z(),
                this.anchor.x() + this.file.width - 1,
                this.anchor.y() + this.file.height - 1,
                this.anchor.z() + this.file.length - 1
        };
    }

    private record GridOrder(LitematicaFile file) implements BuildOrder.Targets {
        @Override
        public int width() {
            return this.file.width;
        }

        @Override
        public int height() {
            return this.file.height;
        }

        @Override
        public int length() {
            return this.file.length;
        }

        @Override
        public boolean filled(int x, int y, int z) {
            return this.file.voxels[(y * this.file.length + z) * this.file.width + x] > 0;
        }
    }
}
