package com.anormal.client.autobuilder2.build;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Decides the order in which a schematic's blocks are placed.
 *
 * <p>Blocks are grouped into layers and placed strictly bottom-up (y ascending), so every block
 * always has the support below it already in place before it is placed. Inside a layer:
 *
 * <ul>
 *   <li>small layers get a greedy <b>nearest-neighbour tour</b> starting from wherever the builder
 *       currently is — this is the "optimised pathing" behaviour, and it keeps travel short;
 *   <li>large layers fall back to a <b>serpentine sweep</b> (boustrophedon) so planning stays
 *       near-linear instead of quadratic in the layer size.
 * </ul>
 *
 * <p>Only pure data is involved, so this can be tested without a game.
 */
public final class BuildOrder {
    /** Layers with at most this many blocks get the (quadratic) nearest-neighbour tour. */
    public static final int NEAREST_NEIGHBOUR_LIMIT = 1024;

    /** The schematic being ordered. Coordinates are grid-local, not world coordinates. */
    public interface Targets {
        int width();

        int height();

        int length();

        boolean filled(int x, int y, int z);
    }

    private BuildOrder() {
    }

    /**
     * Orders every filled cell, bottom layer first.
     *
     * @param start where the builder is standing when the build begins; only affects how the first
     *              layer is traversed
     */
    public static List<GridPos> order(Targets targets, GridPos start) {
        List<GridPos> result = new ArrayList<>();
        GridPos cursor = start;
        for (int y = 0; y < targets.height(); y++) {
            List<GridPos> layer = collectLayer(targets, y);
            if (layer.isEmpty()) {
                continue;
            }
            if (layer.size() <= NEAREST_NEIGHBOUR_LIMIT) {
                nearestNeighbour(layer, cursor);
            } else {
                serpentine(layer);
            }
            cursor = layer.get(layer.size() - 1);
            result.addAll(layer);
        }
        return result;
    }

    private static List<GridPos> collectLayer(Targets targets, int y) {
        List<GridPos> layer = new ArrayList<>();
        for (int z = 0; z < targets.length(); z++) {
            for (int x = 0; x < targets.width(); x++) {
                if (targets.filled(x, y, z)) {
                    layer.add(new GridPos(x, y, z));
                }
            }
        }
        return layer;
    }

    /** Greedy nearest-neighbour tour: repeatedly hop to the closest remaining block. */
    private static void nearestNeighbour(List<GridPos> layer, GridPos start) {
        List<GridPos> remaining = new ArrayList<>(layer);
        layer.clear();
        GridPos cursor = start;
        while (!remaining.isEmpty()) {
            int bestIndex = 0;
            long bestDistance = Long.MAX_VALUE;
            for (int i = 0; i < remaining.size(); i++) {
                long distance = cursor.squaredDistanceTo(remaining.get(i));
                if (distance < bestDistance) {
                    bestDistance = distance;
                    bestIndex = i;
                }
            }
            cursor = remaining.remove(bestIndex);
            layer.add(cursor);
        }
    }

    /** Serpentine sweep: walk each z row in turn, reversing direction every row. */
    private static void serpentine(List<GridPos> layer) {
        layer.sort(Comparator.comparingInt(GridPos::z).thenComparingInt(GridPos::x));
        int index = 0;
        while (index < layer.size()) {
            int rowEnd = index;
            int z = layer.get(index).z();
            while (rowEnd < layer.size() && layer.get(rowEnd).z() == z) {
                rowEnd++;
            }
            if ((z & 1) != 0) {
                for (int i = index, j = rowEnd - 1; i < j; i++, j--) {
                    GridPos swap = layer.get(i);
                    layer.set(i, layer.get(j));
                    layer.set(j, swap);
                }
            }
            index = rowEnd;
        }
    }
}
