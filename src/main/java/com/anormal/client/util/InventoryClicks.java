package com.anormal.client.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;

/**
 * Server safe inventory clicks.
 *
 * <p>Every module that moves items goes through here so three rules always hold:</p>
 * <ul>
 *   <li>only the handler the server currently has open is clicked - a click carrying another
 *       screen's sync id is a protocol violation and gets players kicked on strict servers,</li>
 *   <li>one inventory action per tick: a burst of two or three clicks inside a single tick
 *       trips inventory rate checks on survival servers and desyncs the cursor,</li>
 *   <li>cursor based swaps re-check the cursor before the next click instead of assuming the
 *       previous one was accepted.</li>
 * </ul>
 */
public final class InventoryClicks {
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    private InventoryClicks() {
    }

    /** Screen slot id of a hotbar (0-8) or main inventory (9-35) index in the player handler. */
    public static int screenSlot(int inventoryIndex) {
        return inventoryIndex < 9 ? 36 + inventoryIndex : inventoryIndex;
    }

    /** True when no container is open, so the player's own handler is the current one. */
    public static boolean playerHandlerOpen() {
        return mc.player != null && mc.player.currentScreenHandler == mc.player.playerScreenHandler;
    }

    /** One click on the player's own inventory; ignored while a container is open. */
    public static boolean playerClick(int slot, int button, SlotActionType type) {
        if (mc.player == null || mc.interactionManager == null || !playerHandlerOpen()) return false;
        mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, slot, button, type, mc.player);
        return true;
    }

    /** One click on an open container handler, only while that exact handler is still current. */
    public static boolean click(ScreenHandler handler, int slot, int button, SlotActionType type) {
        if (handler == null || mc.player == null || mc.interactionManager == null) return false;
        if (mc.player.currentScreenHandler != handler) return false;
        mc.interactionManager.clickSlot(handler.syncId, slot, button, type, mc.player);
        return true;
    }

    /**
     * Moves one stack between two player-handler slots with a cursor swap, one click per tick.
     * Works for occupied destinations (armor slot, offhand) where a shift-click does nothing.
     */
    public static final class SlotMover {
        /** Ticks a single step may wait for the server before the swap is abandoned. */
        private static final int STEP_TIMEOUT = 40;

        private int from = -1;
        private int to = -1;
        private int step = 0;
        private int waited = 0;

        public void begin(int fromSlot, int toSlot) {
            from = fromSlot;
            to = toSlot;
            step = 0;
            waited = 0;
        }

        public boolean isActive() {
            return from != -1;
        }

        public void cancel() {
            from = -1;
            to = -1;
            step = 0;
            waited = 0;
        }

        /** @return {@code true} when there is nothing left to do (finished, aborted or idle). */
        public boolean tick() {
            if (from == -1) return true;
            if (!playerHandlerOpen()) {
                cancel();
                return true;
            }
            ScreenHandler handler = mc.player.playerScreenHandler;
            switch (step) {
                case 0 -> {
                    if (handler.getSlot(from).getStack().isEmpty()) {
                        cancel();
                        return true;
                    }
                    if (!click(handler, from, 0, SlotActionType.PICKUP)) {
                        cancel();
                        return true;
                    }
                    step = 1;
                    return false;
                }
                case 1 -> {
                    // Wait for the server to confirm the pick-up before clicking the destination -
                    // a second click with a stale cursor would pick the destination up instead.
                    if (handler.getCursorStack().isEmpty()) {
                        if (++waited > STEP_TIMEOUT) {
                            cancel();
                            return true;
                        }
                        return false;
                    }
                    waited = 0;
                    if (!click(handler, to, 0, SlotActionType.PICKUP)) {
                        cancel();
                        return true;
                    }
                    step = 2;
                    return false;
                }
                default -> {
                    // Whatever the destination held goes back where the swap started.
                    if (!handler.getCursorStack().isEmpty()) click(handler, from, 0, SlotActionType.PICKUP);
                    cancel();
                    return true;
                }
            }
        }
    }
}
