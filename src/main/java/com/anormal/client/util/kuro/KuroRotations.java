package com.anormal.client.util.kuro;

import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Kuro Rotations birga bir port: harakat paketlarini buzmagan holda server-side
 * (silent) rotatsiya.
 *
 * Grim (va vanilla fizika simulyatsiyasi) pozitsiya-delta bilan yo'nalishi mos
 * kelmaydigan PosRot paketlarni belgilaydi. Shu sababli silent
 * yaw/pitch hech qachon oddiy harakat paketiga solinmaydi:
 *  - Harakat paketlari har doim haqiqiy kamera burchaklarini ko'taradi (WASD vanilla qoladi).
 *  - Modul serverga boshqa yo'nalishda raycast qilishni xohlaganda
 *    {@link #sendLookPacket()} chaqiradi — bu sof LookAndOnGround paketini qo'shadi.
 *
 * Klient-side look vektor (ray cast / crosshair helper'lari) silent burchaklarni
 * faol bo'lganda EntityMixin#getRotationVec orqali kuzatadi.
 */
public final class KuroRotations {
    private static Object owner;
    /** Silent burchaklar shu modul bo'yicha o'yinchi modelining (F5) uchinchi qavatida ko'rsatiladi. */
    public static Object modelOwner;
    private static boolean active;
    private static float yaw;
    private static float pitch;
    private static float lastSentYaw = Float.NaN;
    private static float lastSentPitch = Float.NaN;

    private KuroRotations() {}

    public static boolean active() { return active; }
    public static float yaw() { return yaw; }
    public static float pitch() { return pitch; }
    public static boolean ownedBy(Object o) { return owner == o; }
    public static boolean free() { return owner == null; }

    /** (yoki saqlagan) egalik olib, silent burchaklarni o'rnatadi. Boshqasi egallagan bo'lsa "false" qaytaradi. */
    public static boolean set(Object who, float newYaw, float newPitch) {
        if (owner != null && owner != who) return false;
        owner = who;
        active = true;
        yaw = newYaw;
        pitch = MathHelper.clamp(newPitch, -90.0f, 90.0f);
        return true;
    }

    /** Rotatsiyani qaytarish (faqat "who" egallasa). */
    public static void release(Object who) {
        if (owner != who) return;
        owner = null;
        active = false;
        if (modelOwner == who) modelOwner = null;
        lastSentYaw = Float.NaN;
        lastSentPitch = Float.NaN;
    }

    /** Silent burchaklar o'yinchi modeliga ko'rsatilsa "true" (uchinchi qavat) — SilentModelMixin'ga qarang. */
    public static boolean showOnModel() { return active && modelOwner != null && owner == modelOwner; }

    public static Vec3d viewVector() { return Vec3d.fromPolar(pitch, yaw); }

    /**
     * Hozirgi silent burchaklarga ega ServerboundMovePlayerPacket.Rot ekvivalenti
     * (LookAndOnGround) paketini yuboradi. Harakat paketidan KEYIN, hujumdan OLDIN
     * chaqiring — server raycast bu burchaklarni pozitsiya yangilanishiga
     * bog'lamasdan ishlatadi (Grim xavfsiz).
     *
     * @return paket yuborilsa (yoki burchaklar allaqachon yuborilgan bo'lsa) "true"
     */
    public static boolean sendLookPacket() { return sendLookPacket(false); }

    private static boolean restorePending;

    /** True once after a forced silent look packet: the next movement packet must carry the real camera angles again. */
    public static boolean consumeRestore() { boolean r = restorePending; restorePending = false; return r; }

    /**
     * force: burchaklar allaqachon yuborilgan bo'lsa ham yuboradi. Hujumdan avval
     * kerak — orasidagi harakat paketlari haqiqiy kamera burchaklarini ko'tarsa,
     * "last sent" server ushlab turgan bilan mos kelmaydi.
     */
    public static boolean sendLookPacket(boolean force) {
        if (!active) return false;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.getNetworkHandler() == null) return false;

        // bu sesiyada allaqachon xuddi shu burchaklarni serverga aytgan bo'lsak — almashtirib yubormaymiz
        if (!force && !Float.isNaN(lastSentYaw)
                && Math.abs(MathHelper.wrapDegrees(yaw - lastSentYaw)) < 0.01f
                && Math.abs(pitch - lastSentPitch) < 0.01f) {
            return true;
        }

        try {
            // LookAndOnGround(yaw, pitch, onGround, horizontalCollided)
            PlayerMoveC2SPacket packet = new PlayerMoveC2SPacket.LookAndOnGround(yaw, pitch, mc.player.isOnGround(), false);
            mc.getNetworkHandler().sendPacket(packet);
            lastSentYaw = yaw;
            lastSentPitch = pitch;
            restorePending = true;
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
