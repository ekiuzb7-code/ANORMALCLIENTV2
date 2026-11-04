package com.anormal.client.util.kuro;

/**
 * Kuro TickPhase birga bir port. Kuro: "vanilla klient hujum/interact paketlariniinput
 * paytida (harakat paketidan OLDIN) yuboradi; tick oxirida ishlovchi modullar esa
 * hujum paketini harakat paketidan KEYIN chiqarib yuboradi — real klient bunday
 * tartibni hech qachon ishlab chiqarmaydi." Shuning uchun AttackPackets bunday
 * urushlarni keyingi tick boshigacha ushlab turadi.
 */
public final class KuroTickPhase {
    private static boolean moved = true;

    private KuroTickPhase() {}

    /** Klient tick boshlanishi: ModuleManager.onTick() — ushlangan paketlar shu yerda flush qilinadi. */
    public static void startTick() {
        // Anormal irsi: END_CLIENT_TICK ichida vanilla harakat paketi allaqachon ketgan,
        // shuning uchun "harakatdan keyin" holati tick boshidayoq rost bo'ladi.
        moved = true;
    }

    public static void markMoved() {
        moved = true;
    }

    /** Hujum paketi shu tickning harakat paketidan keyin chiqadi — keyingi tickgacha ushlab turish kerak. */
    public static boolean afterMovement() {
        return moved;
    }
}
