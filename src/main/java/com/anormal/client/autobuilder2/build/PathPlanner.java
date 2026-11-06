package com.anormal.client.autobuilder2.build;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Bounded A* over the positions a player can stand on.
 *
 * <p>A node is a <em>stand position</em> — the block the player's feet occupy. It is valid when the
 * block underneath has collision, the feet block is free, and the head block is free, so a valid
 * node is always a spot a two-block-tall player fits in.
 *
 * <p>Moves are the four horizontal directions, each available as a flat step, a one-block step up,
 * or a step down (which also chains into longer falls one block at a time). This deliberately omits
 * parkour, sprint-jumps, swimming and ladders — the builder walks, it does not ninja.
 *
 * <p>Only pure data is involved, so this can be exercised without a game.
 */
public final class PathPlanner {
    /** The world as the planner sees it. Only collision matters here. */
    public interface Standable {
        /** True when the block has collision (a floor can rest on it). */
        boolean solid(GridPos pos);

        /** True when a player can occupy this block (no collision). */
        boolean passable(GridPos pos);
    }

    public static final int DEFAULT_MAX_NODES = 20_000;

    private static final int[][] HORIZONTAL = {
            {1, 0}, {-1, 0}, {0, 1}, {0, -1}
    };

    private PathPlanner() {
    }

    /** True when a two-block-tall player fits here and there is something to stand on. */
    public static boolean canStand(Standable world, GridPos pos) {
        return world.solid(pos.below()) && world.passable(pos) && world.passable(pos.above());
    }

    /** Manhattan-adjacent, i.e. one orthogonal step apart. */
    public static boolean adjacent(GridPos a, GridPos b) {
        return a.manhattanTo(b) == 1;
    }

    /**
     * Plans a walking route.
     *
     * @return the stand positions to pass through in order, starting with a node next to
     *         {@code start}; empty when {@code start} and {@code goal} are the same node; or
     *         {@code null} when no route was found inside the node budget
     */
    public static List<GridPos> plan(Standable world, GridPos start, GridPos goal, int maxNodes) {
        if (!canStand(world, goal) || !canStand(world, start)) {
            return null;
        }
        if (start.equals(goal)) {
            return new ArrayList<>();
        }

        Map<GridPos, Integer> gScore = new HashMap<>();
        Map<GridPos, GridPos> cameFrom = new HashMap<>();
        Set<GridPos> closed = new HashSet<>();
        PriorityQueue<Entry> open = new PriorityQueue<>();

        gScore.put(start, 0);
        open.add(new Entry(start, start.manhattanTo(goal)));

        int expanded = 0;
        while (!open.isEmpty()) {
            Entry current = open.poll();
            GridPos node = current.pos;
            if (!closed.add(node)) {
                continue;
            }
            if (node.equals(goal)) {
                return reconstruct(cameFrom, start, goal);
            }
            if (++expanded > maxNodes) {
                return null;
            }

            int base = gScore.getOrDefault(node, Integer.MAX_VALUE);
            for (int[] direction : HORIZONTAL) {
                int nx = node.x() + direction[0];
                int nz = node.z() + direction[1];
                relax(world, goal, open, closed, gScore, cameFrom, node, base,
                        new GridPos(nx, node.y(), nz), 1);
                relax(world, goal, open, closed, gScore, cameFrom, node, base,
                        new GridPos(nx, node.y() + 1, nz), 2);
                relax(world, goal, open, closed, gScore, cameFrom, node, base,
                        new GridPos(nx, node.y() - 1, nz), 1);
            }
        }
        return null;
    }

    private static void relax(Standable world, GridPos goal, PriorityQueue<Entry> open,
                              Set<GridPos> closed, Map<GridPos, Integer> gScore,
                              Map<GridPos, GridPos> cameFrom, GridPos from, int base,
                              GridPos to, int cost) {
        if (closed.contains(to) || !canStand(world, to)) {
            return;
        }
        int tentative = base + cost;
        Integer known = gScore.get(to);
        if (known != null && known <= tentative) {
            return;
        }
        gScore.put(to, tentative);
        cameFrom.put(to, from);
        open.add(new Entry(to, tentative + to.manhattanTo(goal)));
    }

    private static List<GridPos> reconstruct(Map<GridPos, GridPos> cameFrom, GridPos start, GridPos goal) {
        List<GridPos> path = new ArrayList<>();
        GridPos node = goal;
        while (node != null && !node.equals(start)) {
            path.add(node);
            node = cameFrom.get(node);
        }
        Collections.reverse(path);
        return path;
    }

    private record Entry(GridPos pos, int score) implements Comparable<Entry> {
        @Override
        public int compareTo(Entry other) {
            return Integer.compare(this.score, other.score);
        }
    }
}
