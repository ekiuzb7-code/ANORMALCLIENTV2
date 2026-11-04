package com.anormal.client.module.impl.kuro;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.kuro.KuroSwapLimit;

import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.SlotActionType;

/**
 * Ported from kuro (dev.kuro.client.module.impl.AutoArmor).
 *
 * <p>Wears the best armor you are carrying: fills empty armor slots and upgrades worse
 * pieces (netherite > diamond > iron > chain/copper > gold/turtle > leather, then the
 * most remaining durability). Never swaps out an Elytra. One swap per tick
 * ({@code KuroSwapLimit}); empty slots are shift-click equipped, occupied slots go through
 * a cursor pickup -> armor slot -> deposit-back sequence.</p>
 */
public class KuroAutoArmor extends Module {
    private static final int[] HANDLER_SLOT = {5, 6, 7, 8};                 // PlayerScreenHandler HEAD/CHEST/LEGS/FEET
    private static final String[] SUFFIX = {"_helmet", "_chestplate", "_leggings", "_boots"};

    private final NumberSetting delay = new NumberSetting("Delay (ticks)",
            "Minimum ticks between two equip actions", 2.0, 1.0, 10.0, 1.0);
    private int wait;

    public KuroAutoArmor() {
        super("AutoArmor", "Equips the best armor from the inventory (kuro port)", Category.KURO);
        addSetting(delay);
    }

    private static int score(ItemStack s, int part) {
        if (s == null || s.isEmpty()) return -1;
        String id = Registries.ITEM.getId(s.getItem()).getPath();
        if (!id.endsWith(SUFFIX[part])) return -1;
        int mat = id.contains("netherite") ? 6 : id.contains("diamond") ? 5 : id.contains("iron") ? 4
                : id.contains("chainmail") || id.contains("copper") ? 3
                : id.contains("gold") || id.contains("turtle") ? 2 : 1;
        int left = s.isDamageable() ? Math.max(0, s.getMaxDamage() - s.getDamage()) : 0;
        return mat * 10000 + Math.min(9999, left);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null || mc.player.isSpectator()) return;
        if (mc.currentScreen != null && !(mc.currentScreen instanceof InventoryScreen)) return;
        if (!mc.player.currentScreenHandler.getCursorStack().isEmpty()) return;
        if (++wait < delay.getValue().intValue()) return;

        for (int part = 0; part < 4; part++) {
            ItemStack cur = mc.player.getEquippedStack(
                    switch (part) {
                        case 0 -> net.minecraft.entity.EquipmentSlot.HEAD;
                        case 1 -> net.minecraft.entity.EquipmentSlot.CHEST;
                        case 2 -> net.minecraft.entity.EquipmentSlot.LEGS;
                        default -> net.minecraft.entity.EquipmentSlot.FEET;
                    });
            if (part == 1 && cur.isOf(Items.ELYTRA)) continue;
            int best = -1, bestScore = score(cur, part);
            for (int i = 0; i < 36; i++) {
                int sc = score(mc.player.getInventory().getStack(i), part);
                if (sc > bestScore) { best = i; bestScore = sc; }
            }
            if (best < 0) continue;

            if (!KuroSwapLimit.take()) return;                        // one swap per tick: try again next tick
            int sync = mc.player.playerScreenHandler.syncId;
            int from = best < 9 ? 36 + best : best;                   // inventory index -> player screen handler slot
            if (cur.isEmpty()) {
                mc.interactionManager.clickSlot(sync, from, 0, SlotActionType.QUICK_MOVE, mc.player);
            } else {
                mc.interactionManager.clickSlot(sync, from, 0, SlotActionType.PICKUP, mc.player);
                mc.interactionManager.clickSlot(sync, HANDLER_SLOT[part], 0, SlotActionType.PICKUP, mc.player);
                mc.interactionManager.clickSlot(sync, from, 0, SlotActionType.PICKUP, mc.player);
            }
            wait = 0;
            return;
        }
    }
}
