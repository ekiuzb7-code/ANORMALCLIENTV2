package com.anormal.client.util.kuro;

import com.anormal.client.module.Module;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Kuro SwapStateManager port (echo handlers/SwapStateManager'dan Anormal mapping'larga).
 *
 * Kechiktirilgan restore bilan hotbar swap steki. Modul o'z slotiga swap qiladi,
 * ish tugagach asl slotga qaytadi. Ikki Handle-Input hodisasi o'rniga
 * {@link #tickEarly()} / {@link #tickPost()} ishlatiladi — ModuleManager har bir
 * client tick boshida va oxirida chaqiradi; respawn / pozitsiya paketi reseti
 * {@link #clear()} orqali amalga oshiriladi.
 */
public final class KuroSwapStateManager {

    private enum RestorePhase { EARLY, POST }

    private static final Deque<SwapState> stack = new ArrayDeque<>();

    private KuroSwapStateManager() {}

    public static boolean swapTo(Module owner, int targetSlot, boolean inputSimulation) {
        return swapTo(owner, targetSlot, inputSimulation, 0, true, null);
    }

    public static boolean swapTo(Module owner, int targetSlot, boolean inputSimulation, int restoreDelayTicks) {
        return swapTo(owner, targetSlot, inputSimulation, restoreDelayTicks, true, null);
    }

    public static boolean swapTo(Module owner, int targetSlot, boolean inputSimulation, int restoreDelayTicks, boolean restoreOnEnd) {
        return swapTo(owner, targetSlot, inputSimulation, restoreDelayTicks, restoreOnEnd, null);
    }

    public static boolean swapTo(Module owner, int targetSlot, boolean inputSimulation, int restoreDelayTicks, Runnable onEnd) {
        return swapTo(owner, targetSlot, inputSimulation, restoreDelayTicks, true, onEnd);
    }

    public static boolean swapTo(Module owner, int targetSlot, boolean inputSimulation, int restoreDelayTicks, boolean restoreOnEnd, Runnable onEnd) {
        if (owner == null || mcPlayer() == null) return false;
        if (targetSlot < 0 || targetSlot > 8) return false;

        SwapState top = stack.peek();
        if (top != null && top.owner == owner) {
            top.targetSlot = targetSlot;
            top.inputSimulation = inputSimulation;
            top.restoreOnEnd = restoreOnEnd;
            top.restoreTick = restoreDelayTicks < 0 ? Long.MAX_VALUE : mcPlayer().age + restoreDelayTicks;
            top.restorePhase = restoreDelayTicks <= 0 ? RestorePhase.POST : RestorePhase.EARLY;
            top.onEnd = onEnd;
            KuroInventoryUtils.setInvSlot(targetSlot, inputSimulation);
            return true;
        }

        if (isActive(owner)) return false;

        int previousSlot = getSelectedSlot();
        if (previousSlot == targetSlot) return false;

        SwapState rootState = stack.peekLast();
        int rootSlot = rootState != null ? rootState.rootSlot : previousSlot;
        boolean rootInputSimulation = rootState != null ? rootState.rootInputSimulation : inputSimulation;

        long restoreTick = restoreDelayTicks < 0 ? Long.MAX_VALUE : mcPlayer().age + restoreDelayTicks;
        RestorePhase restorePhase = restoreDelayTicks <= 0 ? RestorePhase.POST : RestorePhase.EARLY;

        stack.push(new SwapState(owner, rootSlot, rootInputSimulation, targetSlot, inputSimulation,
                restoreOnEnd, restoreTick, restorePhase, onEnd));
        KuroInventoryUtils.setInvSlot(targetSlot, inputSimulation);
        return true;
    }

    public static boolean swapToIfNeeded(Module owner, int targetSlot, boolean inputSimulation) {
        return swapToIfNeeded(owner, targetSlot, inputSimulation, 0, true, null);
    }

    public static boolean swapToIfNeeded(Module owner, int targetSlot, boolean inputSimulation, int restoreDelayTicks) {
        return swapToIfNeeded(owner, targetSlot, inputSimulation, restoreDelayTicks, true, null);
    }

    public static boolean swapToIfNeeded(Module owner, int targetSlot, boolean inputSimulation, int restoreDelayTicks, boolean restoreOnEnd) {
        return swapToIfNeeded(owner, targetSlot, inputSimulation, restoreDelayTicks, restoreOnEnd, null);
    }

    public static boolean swapToIfNeeded(Module owner, int targetSlot, boolean inputSimulation, int restoreDelayTicks, Runnable onEnd) {
        return swapToIfNeeded(owner, targetSlot, inputSimulation, restoreDelayTicks, true, onEnd);
    }

    public static boolean swapToIfNeeded(Module owner, int targetSlot, boolean inputSimulation, int restoreDelayTicks, boolean restoreOnEnd, Runnable onEnd) {
        if (owner == null || mcPlayer() == null) return false;
        if (targetSlot < 0 || targetSlot > 8) return false;

        int selectedSlot = getSelectedSlot();
        if (selectedSlot == targetSlot && !isActive(owner)) return true;

        return swapTo(owner, targetSlot, inputSimulation, restoreDelayTicks, restoreOnEnd, onEnd);
    }

    public static boolean isActive(Module owner) {
        if (owner == null) return false;
        for (SwapState state : stack) if (state.owner == owner) return true;
        return false;
    }

    public static boolean isActive(Class<? extends Module> ownerClass) {
        if (ownerClass == null) return false;
        for (SwapState state : stack) if (ownerClass.isInstance(state.owner)) return true;
        return false;
    }

    public static boolean isOwnerActive(Module owner) { return isActive(owner); }
    public static boolean isOwnerActive(Class<? extends Module> ownerClass) { return isActive(ownerClass); }
    public static boolean hasActiveSwaps() { return !stack.isEmpty(); }

    public static boolean updateSlot(Module owner, int targetSlot) {
        if (owner == null) return false;
        SwapState state = stack.peek();
        if (state == null || state.owner != owner) return false;
        state.targetSlot = targetSlot;
        return true;
    }

    public static int getActiveTargetSlot(Module owner) {
        if (owner == null) return -1;
        SwapState state = stack.peek();
        if (state == null || state.owner != owner) return -1;
        return state.targetSlot;
    }

    public static int getSelectedSlot() {
        if (mcPlayer() == null) return -1;
        SwapState state = stack.peek();
        return state != null ? state.targetSlot : mcPlayer().getInventory().getSelectedSlot();
    }

    public static void cancel(Module owner) { cancel(owner, true); }

    public static void cancel(Module owner, boolean restore) {
        if (owner == null || stack.isEmpty()) return;
        if (!isActive(owner)) return;

        if (restore) {
            restoreRootAndClear(stack.peekLast());
            return;
        }

        while (!stack.isEmpty()) {
            SwapState state = stack.pop();
            endState(state, false, true);
            if (state.owner == owner) break;
        }
    }

    public static void clear() { stack.clear(); }

    /** Client tick boshi (echo'da EventHandleInput.Early, eng past prioritet). */
    public static void tickEarly() { processDueRestores(RestorePhase.EARLY); }

    /** Client tick oxiri (echo'da EventHandleInput.Post, eng past prioritet). */
    public static void tickPost() { processDueRestores(RestorePhase.POST); }

    private static void processDueRestores(RestorePhase phase) {
        if (mcPlayer() == null) {
            clear();
            return;
        }

        while (!stack.isEmpty()) {
            SwapState state = stack.peek();
            if (!state.isDue(mcPlayer().age, phase)) break;

            stack.pop();
            if (state.restoreOnEnd) {
                restoreRootAndClear(state);
                runEnd(state);
                return;
            }

            endState(state, false, false);
        }
    }

    private static void restoreRootAndClear(SwapState fallbackRoot) {
        SwapState root = stack.peekLast();
        if (root == null) root = fallbackRoot;

        if (root != null && mcPlayer() != null) {
            KuroInventoryUtils.setInvSlot(root.rootSlot, root.rootInputSimulation);
        }

        while (!stack.isEmpty()) {
            SwapState state = stack.pop();
            runEnd(state);
        }
    }

    private static void endState(SwapState state, boolean restore, boolean forceRestore) {
        if (state == null) return;

        if (restore && mcPlayer() != null && (forceRestore || state.restoreOnEnd)) {
            KuroInventoryUtils.setInvSlot(state.rootSlot, state.rootInputSimulation);
        }

        runEnd(state);
    }

    private static void runEnd(SwapState state) {
        if (state == null || state.onEnd == null) return;
        try {
            state.onEnd.run();
        } catch (Throwable error) {
            error.printStackTrace();
        }
    }

    private static net.minecraft.client.network.ClientPlayerEntity mcPlayer() {
        return net.minecraft.client.MinecraftClient.getInstance().player;
    }

    private static final class SwapState {
        private final Module owner;
        private final int rootSlot;
        private final boolean rootInputSimulation;
        private int targetSlot;
        private boolean inputSimulation;
        private boolean restoreOnEnd;
        private long restoreTick;
        private RestorePhase restorePhase;
        private Runnable onEnd;

        private SwapState(Module owner, int rootSlot, boolean rootInputSimulation, int targetSlot,
                          boolean inputSimulation, boolean restoreOnEnd, long restoreTick,
                          RestorePhase restorePhase, Runnable onEnd) {
            this.owner = owner;
            this.rootSlot = rootSlot;
            this.rootInputSimulation = rootInputSimulation;
            this.targetSlot = targetSlot;
            this.inputSimulation = inputSimulation;
            this.restoreOnEnd = restoreOnEnd;
            this.restoreTick = restoreTick;
            this.restorePhase = restorePhase;
            this.onEnd = onEnd;
        }

        private boolean isDue(long currentTick, RestorePhase phase) {
            return restoreTick != Long.MAX_VALUE
                    && currentTick >= restoreTick
                    && phase.ordinal() >= restorePhase.ordinal();
        }
    }
}
