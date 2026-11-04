package com.anormal.client.module.impl.kuro;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.util.NotificationManager;
import com.anormal.client.util.kuro.KuroSwapLimit;

import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;

/**
 * Ported from kuro (dev.kuro.client.module.impl.ElytraSwap).
 *
 * <p>Click it (or press its keybind) to wear the Elytra with the most durability left from your
 * hotbar. Whatever you were wearing on your chest goes back into that hotbar slot. Broken elytras
 * are ignored, and nothing happens if the one you already wear is as good or better. Works with no
 * inventory open. It never stays "on".</p>
 */
public class KuroElytraSwap extends Module {
    /** Chest armour slot in the player's own screen handler. */
    private static final int CHEST_HANDLER_SLOT = 6;

    public KuroElytraSwap() {
        super("Elytra Swap", "Wears the best elytra from your hotbar (kuro port)", Category.KURO);
    }

    @Override
    public void toggle() {
        swap();
    }

    @Override
    public void setEnabled(boolean value) {
        if (value) swap();
    }

    private static int left(ItemStack s) {
        return s.getMaxDamage() - s.getDamage();
    }

    /** An elytra with 1 durability left is already "broken". */
    private static boolean usable(ItemStack s) {
        return s.isOf(Items.ELYTRA) && left(s) > 1;
    }

    private void swap() {
        if (mc.player == null || mc.interactionManager == null || mc.player.isSpectator()) return;
        if (mc.currentScreen != null
                && !(mc.currentScreen instanceof net.minecraft.client.gui.screen.ingame.InventoryScreen)) return;
        if (!mc.player.playerScreenHandler.getCursorStack().isEmpty()) {
            NotificationManager.info("Elytra Swap", "Put down the item on your cursor first");
            return;
        }

        int best = -1, bestLeft = -1;
        for (int i = 0; i < 9; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (usable(s) && left(s) > bestLeft) {
                best = i;
                bestLeft = left(s);
            }
        }
        if (best < 0) {
            NotificationManager.info("Elytra Swap", "No usable elytra in your hotbar");
            return;
        }

        ItemStack worn = mc.player.getEquippedStack(EquipmentSlot.CHEST);
        if (usable(worn) && left(worn) >= bestLeft) {
            NotificationManager.info("Elytra Swap", "You already wear the best elytra");
            return;
        }

        if (!KuroSwapLimit.take()) {
            NotificationManager.info("Elytra Swap", "Another item swap happened this tick, try again");
            return;                                            // one swap per tick
        }
        // SWAP with a hotbar button exchanges the armour slot and that hotbar slot in one click
        mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId,
                CHEST_HANDLER_SLOT, best, SlotActionType.SWAP, mc.player);
        NotificationManager.info("Elytra Swap", "Swapped to an elytra with " + bestLeft + " durability left");
    }
}
