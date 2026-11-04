package com.anormal.client.util.kuro;

/**
 * Kuro CombatControl birga bir port. Kabi jangovar modullar (AutoMace, SilentAura,
 * KillAura, TriggerBot...) bir-birining hujumlarini va vaqtincha eksklyuziv boshqaruvni
 * noto'g'ri tortmasligi uchun yengil koordinator.
 *
 * Rotation egaligi {@link KuroRotations} da. Bu klass faqat hozir kim "eksklyuziv
 * hujum oynasi"ga egalik qilayotgini kuzatadi (masalan, AutoMace smash ketma-ketligi
 * o'rtasida, stun-slam davomida).
 *
 * Prioritet: kattaroq raqam = muhimroq. Faol yuqori (yoki teng) claim bo'lsa,
 * pastroq claim rad etiladi.
 */
public final class KuroCombatControl {
    public static final int PRIORITY_AURA = 10;
    public static final int PRIORITY_TRIGGER = 15;
    public static final int PRIORITY_HITSWAP = 20;
    public static final int PRIORITY_SHIELD = 25;
    public static final int PRIORITY_AUTOMACE = 30;

    private static Object owner;
    private static int priority;
    private static int ticksLeft;

    private KuroCombatControl() {}

    /** Hozir hech kim eksklyuziv hujum claimini ushlab turgani yo'q bo'lsa true. */
    public static boolean free() {
        return owner == null || ticksLeft <= 0;
    }

    public static boolean ownedBy(Object who) {
        return owner == who && ticksLeft > 0;
    }

    /**
     * Eksklyuziv hujum boshqaruvini {@code ticks} klient tickigacha olishga urinish.
     * Boshqa kimning yuqoriroq yoki teng prioritetli claimi faol bo'lsa false qaytaradi.
     */
    public static boolean claim(Object who, int prio, int ticks) {
        if (owner != null && owner != who && ticksLeft > 0 && priority >= prio) return false;
        owner = who;
        priority = prio;
        ticksLeft = Math.max(1, ticks);
        return true;
    }

    /** Mavjud claimni {@code who} uchun uzaytirish. Egal bo'lmasa no-op. */
    public static void renew(Object who, int ticks) {
        if (owner != who) return;
        ticksLeft = Math.max(ticksLeft, ticks);
    }

    /** Faqat {@code who} hozir egalik qilsa ozod qiladi. */
    public static void release(Object who) {
        if (owner != who) return;
        owner = null;
        priority = 0;
        ticksLeft = 0;
    }

    /** Har klient tickda bir marta (ModuleManager'dan / AutoMace'dan) chaqiriladi. */
    public static void tick() {
        if (ticksLeft > 0) {
            ticksLeft--;
            if (ticksLeft <= 0) {
                owner = null;
                priority = 0;
            }
        }
    }

    /** Boshqa bir modul hozir eksklyuziv boshqaruvni ushlab turgani rost bo'lsa. */
    public static boolean blocked(Object who) {
        return owner != null && owner != who && ticksLeft > 0;
    }
}
