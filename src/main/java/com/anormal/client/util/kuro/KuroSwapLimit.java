package com.anormal.client.util.kuro;

import net.minecraft.client.MinecraftClient;

/**
 * Kuro SwapLimit birga bir port: barcha modullar uchun HAR KLIYENT TICKDA BITTA
 * almashtirish. "Swap" — hotbar sloti o'zgarishi (Auto Weapon, Auto Tool...)
 * yoki inventory swap (Auto Totem, Auto Armor, Auto Replenish...). Allaqachon
 * qo'lida turgan slotni tanlash swap emas va doim ruxsat etiladi. Rad etilgan
 * modul keyingi tickda yana urinadi.
 */
public final class KuroSwapLimit {
    private static int tick;
    private static int last = -1;

    private KuroSwapLimit() {}

    /** Har klient tick boshida chaqiriladi. */
    public static void nextTick() { tick++; }

    /** Shu tickning swapini oladi. Shu tickda swap allaqachon qilingan bo'lsa false. */
    public static boolean take() {
        if (last == tick) return false;
        last = tick;
        return true;
    }

    /** Hotbar slotni tanlaydi (tick uchun bitta swap). Shundan keyin slot qo'lida bo'lsa true, rad etilgan bo'lsa false. */
    public static boolean select(int slot) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return false;
        if (mc.player.getInventory().getSelectedSlot() == slot) return true;
        if (!take()) return false;
        mc.player.getInventory().setSelectedSlot(slot);
        return true;
    }

    /** Modul o'chirilganda tozalash: tickda swap qilingan bo'lsa ham slotni tanlaydi. */
    public static void selectNow(int slot) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.player.getInventory().getSelectedSlot() == slot) return;
        last = tick;
        mc.player.getInventory().setSelectedSlot(slot);
    }
}
