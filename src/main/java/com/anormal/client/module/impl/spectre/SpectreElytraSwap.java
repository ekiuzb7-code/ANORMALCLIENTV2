package com.anormal.client.module.impl.spectre;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.EquippableComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * Spectre port: "Elytra Swap" — swaps your chestplate for an elytra with one press and
 * puts the chestplate back after you land.
 *
 * <p>Spectre binds a separate key and clicks the chest armour slot with
 * {@code ContainerInput.SWAP}. This port uses the module keybind instead (toggling the
 * module performs the swap, toggling again swaps back) and does the move with the same
 * chest-slot click Anormal's armour modules use. Landing restoration keeps Spectre's
 * delay so the swap does not fight the glide.</p>
 */
public final class SpectreElytraSwap extends Module {
    private static final int CHEST_SLOT_ID = 6;

    public final BooleanSetting swapBackOnLanding = addSetting(new BooleanSetting("Swap Back on Landing", "Put the chestplate back after landing", true));
    public final NumberSetting landingDelay = addSetting(new NumberSetting("Landing Delay", "Ticks on the ground before swapping back", 4.0, 0.0, 20.0, 1.0));

    private boolean armed;
    private boolean glided;
    private int groundTicks;

    public SpectreElytraSwap() {
        super("SpectreElytraSwap", "Spectre port: swaps between your chestplate and elytra with one key.", Category.SPECTRE);
    }

    @Override
    public void onEnable() {
        armed = false;
        glided = false;
        groundTicks = 0;
        if (mc.player == null || mc.interactionManager == null) return;
        swap();
    }

    @Override
    public void onDisable() {
        armed = false;
        glided = false;
        groundTicks = 0;
    }

    @Override
    public void onTick() {
        var player = mc.player;
        if (player == null || mc.world == null) return;
        if (!armed || !swapBackOnLanding.isEnabled()) return;

        ItemStack chest = player.getEquippedStack(EquipmentSlot.CHEST);
        boolean wearingGlider = chest.contains(DataComponentTypes.GLIDER);
        if (!wearingGlider) {
            armed = false;
            return;
        }
        if (player.isGliding()) {
            glided = true;
            groundTicks = 0;
            return;
        }
        if (!glided) return;
        if (!player.isOnGround() && !player.isTouchingWater()) {
            groundTicks = 0;
            return;
        }
        if (++groundTicks < landingDelay.getAsDouble()) return;
        if (mc.currentScreen != null) return;

        int chestplate = findBestChestplate();
        if (chestplate < 0) {
            message("Can't swap back, no chestplate in your inventory");
            armed = false;
            return;
        }
        if (equip(chestplate)) {
            armed = false;
        } else {
            message("Swap failed, the item didn't move into the chest slot");
        }
    }

    private void swap() {
        var player = mc.player;
        if (player == null || player.isSpectator() || mc.currentScreen != null) return;

        ItemStack worn = player.getEquippedStack(EquipmentSlot.CHEST);
        if (worn.contains(DataComponentTypes.GLIDER)) {
            int chestplate = findBestChestplate();
            if (chestplate < 0) {
                message("Can't swap, no chestplate in your inventory");
                return;
            }
            if (!equip(chestplate)) message("Swap failed, the item didn't move into the chest slot");
            armed = false;
            return;
        }

        int elytra = findElytra();
        if (elytra < 0) {
            message("Can't swap, no usable elytra in your inventory");
            return;
        }
        boolean hadChestplate = isChestplate(worn);
        if (!equip(elytra)) {
            message("Swap failed, the item didn't move into the chest slot");
            return;
        }
        armed = hadChestplate && swapBackOnLanding.isEnabled();
        glided = false;
        groundTicks = 0;
    }

    private int findElytra() {
        int best = -1;
        int bestLeft = -1;
        for (int index : sourceIndices()) {
            ItemStack stack = mc.player.getInventory().getStack(index);
            if (!stack.contains(DataComponentTypes.GLIDER)) continue;
            int left = durabilityLeft(stack);
            if (left > bestLeft) {
                best = index;
                bestLeft = left;
            }
        }
        return best;
    }

    private int findBestChestplate() {
        int best = -1;
        int bestLeft = -1;
        for (int index : sourceIndices()) {
            ItemStack stack = mc.player.getInventory().getStack(index);
            if (!isChestplate(stack)) continue;
            int left = durabilityLeft(stack);
            if (left > bestLeft) {
                best = index;
                bestLeft = left;
            }
        }
        return best;
    }

    private static boolean isChestplate(ItemStack stack) {
        if (stack.isEmpty() || stack.contains(DataComponentTypes.GLIDER)) return false;
        EquippableComponent equippable = stack.get(DataComponentTypes.EQUIPPABLE);
        return equippable != null && equippable.slot() == EquipmentSlot.CHEST;
    }

    private static int durabilityLeft(ItemStack stack) {
        return stack.isDamageable() ? stack.getMaxDamage() - stack.getDamage() : Integer.MAX_VALUE;
    }

    /** Player-inventory indices: hotbar 0-8, main 9-35, offhand 40. */
    private static List<Integer> sourceIndices() {
        List<Integer> indices = new ArrayList<>(37);
        for (int i = 0; i < 36; i++) indices.add(i);
        indices.add(40);
        return indices;
    }

    /** Moves inventory index {@code index} into the chest armour slot with inventory clicks. */
    private boolean equip(int index) {
        var player = mc.player;
        if (player == null || mc.interactionManager == null) return false;
        ItemStack item = player.getInventory().getStack(index);
        if (item.isEmpty()) return false;

        int syncId = player.playerScreenHandler.syncId;
        int slot = screenSlot(index);
        if (slot < 0) return false;

        mc.interactionManager.clickSlot(syncId, slot, 0, SlotActionType.PICKUP, player);
        mc.interactionManager.clickSlot(syncId, CHEST_SLOT_ID, 0, SlotActionType.PICKUP, player);
        // whatever is left in the cursor goes back where it came from
        mc.interactionManager.clickSlot(syncId, slot, 0, SlotActionType.PICKUP, player);

        return player.getEquippedStack(EquipmentSlot.CHEST).isOf(item.getItem());
    }

    private static int screenSlot(int inventoryIndex) {
        if (inventoryIndex >= 0 && inventoryIndex <= 8) return 36 + inventoryIndex;
        if (inventoryIndex >= 9 && inventoryIndex <= 35) return inventoryIndex;
        if (inventoryIndex == 40) return 45;
        return -1;
    }

    private void message(String text) {
        if (mc.inGameHud == null) return;
        mc.inGameHud.getChatHud().addMessage(Text.literal("[SpectreElytraSwap] " + text));
    }
}
