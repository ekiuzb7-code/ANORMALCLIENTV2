package com.anormal.client.module.impl.spectre;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;

/**
 * Spectre port: "Auto Replenish" — refills a hotbar or offhand slot from the main
 * inventory once it runs low.
 *
 * <p>Spectre's Instant mode swap is ported value-for-value (SWAP click on the player
 * screen handler with the hotbar button / 40 for offhand). The Legit mode adds the
 * quiet-tick gating and delay between swaps.</p>
 */
public final class SpectreAutoReplenish extends Module {
    private static final int TRACKED_SLOTS = 10;
    private static final int OFFHAND_TRACKED_SLOT = 9;
    private static final int OFFHAND_SWAP_BUTTON = 40;
    private static final double FAST_MOVEMENT_THRESHOLD_SQ = 0.06;

    public final ModeSetting mode = addSetting(new ModeSetting("Mode", "Instant swap or paced swap", "Instant", "Instant", "Legit"));
    public final NumberSetting delay = addSetting(new NumberSetting("Delay", "Ticks between instant swaps", 1.0, 0.0, 20.0, 1.0));
    public final NumberSetting threshold = addSetting(new NumberSetting("Threshold", "Refill below this count", 8.0, 1.0, 64.0, 1.0));
    public final BooleanSetting offhand = addSetting(new BooleanSetting("Offhand", "Also refill the offhand slot", true));

    private final ItemStack[] lastKnown = new ItemStack[TRACKED_SLOTS];
    private final ItemStack[] pendingRefill = new ItemStack[TRACKED_SLOTS];
    private net.minecraft.client.network.ClientPlayerEntity trackedPlayer;
    private int delayTimer;
    private int quietTicks = 1000;

    public SpectreAutoReplenish() {
        super("SpectreAutoReplenish", "Spectre port: refills a hotbar or offhand slot from the main inventory once it runs low.", Category.SPECTRE);
    }

    @Override
    public void onEnable() {
        trackedPlayer = null;
        delayTimer = 0;
        quietTicks = 0;
    }

    @Override
    public void onDisable() {
        trackedPlayer = null;
        delayTimer = 0;
    }

    private void resetJob() {
        delayTimer = 0;
        quietTicks = 1000;
    }

    @Override
    public void onTick() {
        var player = mc.player;
        if (player == null || mc.getNetworkHandler() == null) return;

        if (player != trackedPlayer) {
            resync(player);
        }

        updateTracking(player);
        // Legit mode gate: the tracked slots must stay untouched for 10 ticks before a swap
        if (quietTicks < 1000) quietTicks++;

        if (delayTimer > 0) {
            delayTimer--;
        } else {
            refillNow(player);
        }
    }

    private void resync(net.minecraft.client.network.ClientPlayerEntity player) {
        trackedPlayer = player;
        for (int i = 0; i < TRACKED_SLOTS; i++) {
            lastKnown[i] = currentStack(player, i).copy();
            pendingRefill[i] = ItemStack.EMPTY;
        }
        resetJob();
    }

    private ItemStack currentStack(net.minecraft.client.network.ClientPlayerEntity player, int i) {
        return i == OFFHAND_TRACKED_SLOT ? player.getOffHandStack()
                : player.getInventory().getStack(i);
    }

    private void updateTracking(net.minecraft.client.network.ClientPlayerEntity player) {
        for (int i = 0; i < TRACKED_SLOTS; i++) {
            if (i == OFFHAND_TRACKED_SLOT && !offhand.isEnabled()) {
                lastKnown[i] = player.getOffHandStack().copy();
                pendingRefill[i] = ItemStack.EMPTY;
                continue;
            }
            ItemStack current = currentStack(player, i);
            ItemStack previous = lastKnown[i];

            if (!ItemStack.areEqual(previous, current)) quietTicks = 0;

            lastKnown[i] = current.copy();
            if (current.isOf(Items.TOTEM_OF_UNDYING) || previous.isOf(Items.TOTEM_OF_UNDYING)) continue;

            boolean sameItem = previous.isEmpty() || current.getItem() == previous.getItem();
            if (current.isEmpty() && !previous.isEmpty()) {
                pendingRefill[i] = previous.copy();
            } else if (!previous.isEmpty() && !previous.isStackable() && !sameItem) {
                pendingRefill[i] = previous.copy();
            } else if (!current.isStackable() || current.isEmpty() || !sameItem
                    || current.getCount() > (int) threshold.getAsDouble()
                    || (!pendingRefill[i].isEmpty() && pendingRefill[i].getItem() != current.getItem())) {
                if (!current.isEmpty() && current.getCount() > (int) threshold.getAsDouble()) {
                    pendingRefill[i] = ItemStack.EMPTY;
                }
            } else {
                pendingRefill[i] = current.copy();
            }
        }
    }

    private void refillNow(net.minecraft.client.network.ClientPlayerEntity player) {
        if (mc.currentScreen != null || player.isUsingItem()) return;
        if (mode.is("Legit") && quietTicks < 10) return;

        int slot = nextPending(player);
        if (slot < 0) return;

        ItemStack reference = pendingRefill[slot];
        pendingRefill[slot] = ItemStack.EMPTY;

        PlayerScreenHandler menu = player.playerScreenHandler;
        Slot source = findSourceSlot(menu, player, slot, reference);
        if (source != null && menu.getCursorStack().isEmpty()) {
            int button = slot == OFFHAND_TRACKED_SLOT ? OFFHAND_SWAP_BUTTON : slot;
            try {
                mc.interactionManager.clickSlot(menu.syncId, source.id, button,
                        net.minecraft.screen.slot.SlotActionType.SWAP, player);
                delayTimer = (int) delay.getAsDouble();
            } catch (Throwable ignored) {
            }
        }
    }

    private int nextPending(net.minecraft.client.network.ClientPlayerEntity player) {
        for (int i = 0; i < TRACKED_SLOTS; i++) {
            if (!pendingRefill[i].isEmpty() && hasSource(player, i, pendingRefill[i])) return i;
        }
        return -1;
    }

    private boolean hasSource(net.minecraft.client.network.ClientPlayerEntity player, int trackedSlot, ItemStack reference) {
        ItemStack targetNow = currentStack(player, trackedSlot);
        int have = ItemStack.areEqual(targetNow, reference) ? targetNow.getCount() : 0;
        for (int i = 9; i <= 35; i++) {
            ItemStack candidate = player.getInventory().getStack(i);
            if (matchesReference(candidate, reference) && candidate.getCount() > have) return true;
        }
        return false;
    }

    private Slot findSourceSlot(PlayerScreenHandler menu, net.minecraft.client.network.ClientPlayerEntity player,
                                int trackedSlot, ItemStack reference) {
        ItemStack targetNow = currentStack(player, trackedSlot);
        int have = ItemStack.areEqual(targetNow, reference) ? targetNow.getCount() : 0;
        Slot best = null;
        for (Slot slot : menu.slots) {
            ItemStack candidate = slot.getStack();
            if (slot.id >= 9 && slot.id <= 35 && matchesReference(candidate, reference)
                    && candidate.getCount() > have) {
                best = slot;
            }
        }
        return best;
    }

    private static boolean matchesReference(ItemStack candidate, ItemStack reference) {
        if (candidate.isEmpty()) return false;
        return reference.isStackable()
                ? ItemStack.areItemsAndComponentsEqual(candidate, reference)
                : ItemStack.areItemsEqual(candidate, reference);
    }
}
