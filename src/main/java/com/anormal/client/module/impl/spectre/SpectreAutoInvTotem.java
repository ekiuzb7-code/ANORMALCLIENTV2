package com.anormal.client.module.impl.spectre;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Spectre port: "Auto Inventory Totem" — refills the offhand totem after a pop, through the
 * inventory or a hotbar swap.
 *
 * <p>Spectre walks a five-step machine (open, swap, close, hotbar swap, hotbar return). This
 * port keeps the two modes, the delays with jitter, the hotbar-refill slot and the
 * "armed after the first totem" rule. The inventory mode swaps through the player's own
 * screen handler, so no GUI has to open.</p>
 */
public final class SpectreAutoInvTotem extends Module {
    public final ModeSetting mode = addSetting(new ModeSetting("Mode", "Inventory swap or hotbar swap", "Inventory", "Inventory", "Hotbar Swap"));
    public final BooleanSetting hotbarRefill = addSetting(new BooleanSetting("Hotbar Refill", "Also refill the chosen hotbar slot", false));
    public final NumberSetting hotbarSlot = addSetting(new NumberSetting("Hotbar Slot", "Hotbar slot used by refill", 1.0, 1.0, 9.0, 1.0));
    public final NumberSetting reactionDelay = addSetting(new NumberSetting("Reaction Delay", "Ticks before reacting to a pop", 0.0, 0.0, 10.0, 1.0));
    public final NumberSetting swapDelay = addSetting(new NumberSetting("Swap Delay", "Ticks between swaps", 1.0, 1.0, 10.0, 1.0));
    public final NumberSetting switchBackDelay = addSetting(new NumberSetting("Switch Back Delay", "Ticks before returning to the old slot", 2.0, 0.0, 10.0, 1.0));
    public final NumberSetting jitter = addSetting(new NumberSetting("Jitter", "Random extra delay ticks", 0.0, 0.0, 3.0, 1.0));

    private boolean armed;
    private int tick;
    private int readyAt;
    private int reactionAt = -1;
    private int totemSlot = -1;
    private int previousSlot = -1;

    public SpectreAutoInvTotem() {
        super("SpectreAutoInvTotem", "Spectre port: refills the offhand totem after a pop, through the inventory or a hotbar swap.", Category.SPECTRE);
    }

    @Override
    public void onEnable() {
        armed = false;
        reactionAt = -1;
        readyAt = 0;
        totemSlot = -1;
        previousSlot = -1;
    }

    @Override
    public void onDisable() {
        var player = mc.player;
        if (player != null && previousSlot >= 0 && player.getInventory().getSelectedSlot() == totemSlot) {
            player.getInventory().setSelectedSlot(previousSlot);
        }
        totemSlot = -1;
        previousSlot = -1;
        reactionAt = -1;
    }

    /** True while the hotbar swap owns the selected slot, so combat macros wait. */
    public static boolean wantsHotbar() {
        SpectreAutoInvTotem module = com.anormal.client.module.ModuleManager.getModule(SpectreAutoInvTotem.class);
        return module != null && module.isEnabled() && module.mode.is("Hotbar Swap") && module.totemSlot >= 0;
    }

    @Override
    public void onTick() {
        tick++;
        var player = mc.player;
        if (player == null || mc.interactionManager == null || mc.world == null) {
            totemSlot = -1;
            previousSlot = -1;
            return;
        }
        if (player.isDead() || player.isSpectator()) return;
        if (player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) armed = true;

        if (mode.is("Hotbar Swap")) {
            hotbarTick(player);
        } else {
            inventoryTick(player);
        }
    }

    private void inventoryTick(net.minecraft.entity.player.PlayerEntity player) {
        if (tick < readyAt) return;
        if (!player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) {
            if (reactionAt < 0) reactionAt = tick + spread(reactionDelay);
            if (tick < reactionAt) return;
            int source = findTotem(player, -1);
            if (source >= 0) {
                swapSlot(source, 40, player);
                readyAt = tick + spread(swapDelay);
                reactionAt = -1;
                return;
            }
        }

        if (hotbarRefill.isEnabled()) {
            int slot = refillSlot();
            if (!player.getInventory().getStack(slot).isOf(Items.TOTEM_OF_UNDYING) && sourceAvailable(player, slot)) {
                int source = findTotem(player, slot);
                if (source >= 0) {
                    swapSlot(source, slot, player);
                    readyAt = tick + spread(swapDelay);
                }
            }
        }
    }

    private void hotbarTick(net.minecraft.entity.player.PlayerEntity player) {
        if (totemSlot >= 0) {
            if (tick < readyAt) return;
            var inventory = player.getInventory();
            if (inventory.getSelectedSlot() != totemSlot) {
                if (player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) {
                    returnSlot(inventory);
                } else {
                    totemSlot = findTotem(player, -2);
                }
                return;
            }
            if (player.getMainHandStack().isOf(Items.TOTEM_OF_UNDYING) && !player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) {
                var handler = mc.getNetworkHandler();
                if (handler != null) {
                    handler.sendPacket(new PlayerActionC2SPacket(
                            PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ORIGIN, Direction.DOWN));
                }
                readyAt = tick + 2 + spread(switchBackDelay);
            } else {
                returnSlot(inventory);
            }
            return;
        }

        if (player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) return;
        if (mc.currentScreen != null) return;
        int slot = findTotem(player, -2);
        if (slot < 0) return;
        if (reactionAt < 0) reactionAt = tick + spread(reactionDelay);
        if (tick < reactionAt) return;
        previousSlot = player.getInventory().getSelectedSlot();
        totemSlot = slot;
        player.getInventory().setSelectedSlot(slot);
        readyAt = tick + spread(swapDelay);
        reactionAt = -1;
    }

    private void returnSlot(net.minecraft.entity.player.PlayerInventory inventory) {
        if (previousSlot >= 0 && previousSlot != totemSlot) inventory.setSelectedSlot(previousSlot);
        totemSlot = -1;
        previousSlot = -1;
    }

    private void swapSlot(int source, int button, net.minecraft.entity.player.PlayerEntity player) {
        int menuSlot = source < 9 ? 36 + source : source;
        mc.interactionManager.clickSlot(player.playerScreenHandler.syncId, menuSlot, button, SlotActionType.SWAP, player);
    }

    private boolean sourceAvailable(net.minecraft.entity.player.PlayerEntity player, int reserved) {
        return findTotem(player, reserved) >= 0;
    }

    /** -2 means "hotbar only", -1 means "any slot but this one". */
    private static int findTotem(net.minecraft.entity.player.PlayerEntity player, int excluded) {
        var inventory = player.getInventory();
        for (int slot = 9; slot < 36; slot++) {
            if (slot != excluded && inventory.getStack(slot).isOf(Items.TOTEM_OF_UNDYING)) return slot;
        }
        int start = excluded == -2 ? 0 : 0;
        for (int slot = start; slot < 9; slot++) {
            if (slot != excluded && inventory.getStack(slot).isOf(Items.TOTEM_OF_UNDYING)) return slot;
        }
        return -1;
    }

    private int refillSlot() {
        return Math.max(0, Math.min(8, (int) hotbarSlot.getAsDouble() - 1));
    }

    private int spread(NumberSetting setting) {
        int base = (int) setting.getAsDouble();
        int amount = (int) jitter.getAsDouble();
        return Math.max(0, base + (amount > 0 ? ThreadLocalRandom.current().nextInt(amount + 1) : 0));
    }
}
