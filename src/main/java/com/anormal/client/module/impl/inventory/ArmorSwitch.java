package com.anormal.client.module.impl.inventory;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.InventoryClicks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.EquippableComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;

public class ArmorSwitch extends Module {
    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };
    /** Player-handler screen slots for head, chest, legs and feet. */
    private static final int[] ARMOR_SCREEN_SLOTS = {5, 6, 7, 8};

    public final ModeSetting sets = new ModeSetting("Sets", "Armor material pair to swap between", "Diamond <> Gold", "Diamond <> Gold", "Netherite <> Gold", "Iron <> Diamond");
    public final NumberSetting delay = new NumberSetting("Delay", "Ticks between equipping pieces", 3.0, 0.0, 20.0, 1.0);

    /** Cursor swap spread over ticks, so a survival server accepts it. */
    private final InventoryClicks.SlotMover mover = new InventoryClicks.SlotMover();
    private String target = "";
    private int cooldown = 0;
    private int pendingArmorSlot = -1;

    public ArmorSwitch() {
        super("ArmorSwitch", "Switches between two configured sets of armor on keypress", Category.INVENTORY);
        addSetting(sets);
        addSetting(delay);
    }

    @Override
    public void onEnable() {
        mover.cancel();
        pendingArmorSlot = -1;
        if (mc.player == null) {
            setEnabled(false);
            return;
        }
        String[] pair = sets.getValue().toLowerCase().split("<>");
        String a = pair[0].trim();
        String current = majorityWorn();
        target = current.equals(a) && pair.length > 1 ? pair[1].trim() : a;
        cooldown = 0;
    }

    @Override
    public void onDisable() {
        mover.cancel();
        pendingArmorSlot = -1;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) {
            setEnabled(false);
            return;
        }

        // Finish the in-flight swap before starting another one.
        if (mover.isActive()) {
            if (!mover.tick()) return;
            cooldown = Math.max(1, delay.getValue().intValue());
            return;
        }

        // Clicking the player handler while a container is open is an invalid packet - wait it out.
        if (!InventoryClicks.playerHandlerOpen()) return;

        if (cooldown > 0) {
            cooldown--;
            return;
        }

        int src = findPiece();
        if (src == -1) {
            setEnabled(false);
            return;
        }
        int armorSlot = pendingArmorSlot;
        pendingArmorSlot = -1;
        if (armorSlot == -1) return;
        mover.begin(src, armorSlot);
    }

    /**
     * Finds the next piece to equip and remembers which armour slot it belongs in.
     *
     * @return player-handler screen slot of the item, or -1 when nothing needs swapping
     */
    private int findPiece() {
        pendingArmorSlot = -1;
        for (int k = 0; k < ARMOR_SLOTS.length; k++) {
            EquipmentSlot slot = ARMOR_SLOTS[k];
            ItemStack worn = mc.player.getEquippedStack(slot);
            if (!worn.isEmpty() && materialOf(worn).equals(target)) continue;
            for (int i = 9; i < 36; i++) {
                ItemStack s = mc.player.getInventory().getStack(i);
                if (s.isEmpty() || !isArmorFor(s, slot) || !materialOf(s).equals(target)) continue;
                pendingArmorSlot = ARMOR_SCREEN_SLOTS[k];
                return i;
            }
            for (int i = 0; i < 9; i++) {
                ItemStack s = mc.player.getInventory().getStack(i);
                if (s.isEmpty() || !isArmorFor(s, slot) || !materialOf(s).equals(target)) continue;
                pendingArmorSlot = ARMOR_SCREEN_SLOTS[k];
                return InventoryClicks.screenSlot(i);
            }
        }
        return -1;
    }

    private String majorityWorn() {
        String best = "";
        int bestCount = 0;
        String[] mats = sets.getValue().toLowerCase().split("<>");
        for (String m : mats) {
            int n = 0;
            for (EquipmentSlot slot : ARMOR_SLOTS) {
                ItemStack worn = mc.player.getEquippedStack(slot);
                if (!worn.isEmpty() && materialOf(worn).equals(m.trim())) n++;
            }
            if (n > bestCount) {
                bestCount = n;
                best = m.trim();
            }
        }
        return best.isEmpty() ? mats[0].trim() : best;
    }

    private static boolean isArmorFor(ItemStack s, EquipmentSlot slot) {
        EquippableComponent eq = s.get(DataComponentTypes.EQUIPPABLE);
        return eq != null && eq.slot() == slot;
    }

    private static String materialOf(ItemStack s) {
        String p = Registries.ITEM.getId(s.getItem()).getPath();
        int cut = p.lastIndexOf('_');
        return cut <= 0 ? p : p.substring(0, cut);
    }
}
