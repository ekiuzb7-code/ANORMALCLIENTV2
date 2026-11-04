package com.anormal.client.util.kuro;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.item.ItemStack;
import net.minecraft.client.util.InputUtil;

import java.util.function.Predicate;

/**
 * Kuro InventoryUtils port (echo utils/inventory/InventoryUtils'dan Anormal mapping'larga).
 *
 * Hotbar yordamchilari: slotni tanlash (oddiy yoki input simulyatsiya bilan) va
 * hotbar'dan predikat bo'yicha item qidirish. Input simulyatsiya rejimida slot
 * almashtirish hotbar tugmasi (1-9) bosilgan holda sinchironchik orqali
 * KeyBinding.onKeyPressed yordamida o'zgartiradi, bu esa vanilla keystrokes
 * HUD'i va Click GUI logikasini chetlab o'tmaydi.
 */
public final class KuroInventoryUtils {
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    private KuroInventoryUtils() {}

    public static void setInvSlot(int slot) {
        if (mc.player == null) return;
        if (mc.player.getInventory().getSelectedSlot() == slot) return;
        mc.player.getInventory().setSelectedSlot(slot);
    }

    public static void setInvSlot(int slot, boolean inputSimulation) {
        if (slot < 0 || slot > 8) return;
        if (mc.player == null) return;
        if (mc.player.getInventory().getSelectedSlot() == slot) return;

        if (inputSimulation) {
            if (mc.options != null && mc.options.hotbarKeys != null) {
                KeyBinding hotbarKey = mc.options.hotbarKeys[slot];
                if (hotbarKey != null) KuroInputHandler.incrementClickCountForKeyMapping(hotbarKey);
            }
        } else {
            setInvSlot(slot);
        }
    }

    public static int findItemWithPredicateInHotbar(Predicate<ItemStack> predicate) {
        if (mc.player == null) return -1;
        for (int i = 0; i <= 8; i++) {
            if (predicate.test(mc.player.getInventory().getStack(i))) return i;
        }
        return -1;
    }

    /** Hotbar'dagi slotni ItemStack bo'yicha qidirish, agar slot bo'sh bo'lsa -1. */
    public static int findItemInHotbar(net.minecraft.item.Item item) {
        if (mc.player == null) return -1;
        for (int i = 0; i <= 8; i++) {
            if (mc.player.getInventory().getStack(i).getItem() == item) return i;
        }
        return -1;
    }

    /** Yarn KeyBinding'dan InputUtil.Key (echo'da accessor mixin ishlatilgan edi; Yarn'da translation key orqali). */
    public static InputUtil.Key keyBindingToKey(KeyBinding binding) {
        if (binding == null) return null;
        try {
            return InputUtil.fromTranslationKey(binding.getBoundKeyTranslationKey());
        } catch (Throwable t) {
            return null;
        }
    }
}
