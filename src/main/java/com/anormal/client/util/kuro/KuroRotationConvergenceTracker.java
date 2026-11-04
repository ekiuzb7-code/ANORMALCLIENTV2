package com.anormal.client.util.kuro;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.MathHelper;

/**
 * Kuro RotationConvergenceTracker port (echo utils/rotation/RotationConvergenceTracker'dan Anormal mapping'larga).
 *
 * Grim DuplicateRotPlace bypass uchun har bir tick'dagi yaw/pitch delta kuzatuvchisi:
 * tekshiruv {@code deltaX > 2} uchun ochiladi, shuning uchun o'tgan tick'dagi delta
 * kichik bo'lgan tickda interaksiya o'tkazsak — tekshiruv hech qachon ishga tushmaydi.
 *
 * <p>{@link #update()} har bir onTick HEAD'ida (shu tickning rotatsiyasi
 * MouseHandler.turnPlayer orqali qo'llanishidan oldin) chaqirilishi shart. U
 * ko'rinadigan o'yinchi rotatsiyasini va server-side silent rotatsiyani
 * yozib boradi, shunda {@link #isSettled} shu tickda yuborilayotgani haqiqiysini
 * tanlab oladi.
 */
public final class KuroRotationConvergenceTracker {

    private static final KuroRotationConvergenceTracker INSTANCE = new KuroRotationConvergenceTracker();

    private KuroRotationConvergenceTracker() {}

    public static void update() { INSTANCE.updateState(); }
    public static void reset() { INSTANCE.resetState(); }
    public static boolean isSettled(float threshold) { return INSTANCE.isSettledState(threshold); }
    public static boolean isDuplicateRotPlaceSafe(float threshold) { return INSTANCE.isDuplicateRotPlaceSafeState(threshold); }
    public static void markInteraction() { INSTANCE.markInteractionState(); }

    private float prevPlayerYaw;
    private float prevPlayerPitch;
    private float prevServerYaw;
    private float prevServerPitch;

    private float lastDeltaPlayerYaw;
    private float lastDeltaPlayerPitch;
    private float lastDeltaServerYaw;
    private float lastDeltaServerPitch;

    private float lastInteractionPitchDelta;
    private boolean primed;
    private boolean hasInteractionPitchDelta;

    public void resetState() {
        primed = false;
        hasInteractionPitchDelta = false;
    }

    public void updateState() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) {
            primed = false;
            return;
        }

        float curPlayerYaw = mc.player.getYaw();
        float curPlayerPitch = mc.player.getPitch();
        // Serverga o'tgan tickda yuborilgan rotatsiya: silent faol bo'lmasa harakat
        // paketlari haqiqiy kamera burchaklarini ko'taradi.
        float curServerYaw = KuroRotations.active() ? KuroRotations.yaw() : curPlayerYaw;
        float curServerPitch = KuroRotations.active() ? KuroRotations.pitch() : curPlayerPitch;

        if (primed) {
            lastDeltaPlayerYaw = Math.abs(MathHelper.wrapDegrees(curPlayerYaw - prevPlayerYaw));
            lastDeltaPlayerPitch = Math.abs(curPlayerPitch - prevPlayerPitch);
            lastDeltaServerYaw = Math.abs(MathHelper.wrapDegrees(curServerYaw - prevServerYaw));
            lastDeltaServerPitch = Math.abs(curServerPitch - prevServerPitch);
        } else {
            // Sessiyada birinchi namuna: delta yo'q, "settled emas" deb qo'yadi.
            lastDeltaPlayerYaw = lastDeltaPlayerPitch = Float.MAX_VALUE;
            lastDeltaServerYaw = lastDeltaServerPitch = Float.MAX_VALUE;
        }

        prevPlayerYaw = curPlayerYaw;
        prevPlayerPitch = curPlayerPitch;
        prevServerYaw = curServerYaw;
        prevServerPitch = curServerPitch;
        primed = true;
    }

    public boolean isSettledState(float threshold) {
        if (!primed) return false;
        boolean silent = KuroRotations.active();
        float deltaYaw = silent ? lastDeltaServerYaw : lastDeltaPlayerYaw;
        float deltaPitch = silent ? lastDeltaServerPitch : lastDeltaPlayerPitch;
        return deltaYaw < threshold && deltaPitch < threshold;
    }

    /**
     * Grimning DuplicateRotPlace tekshiruvi faqat XRot/pitch delta orqali ishlaydi.
     * Barqaror past pitch delta doim yaxshi, lekin tushish paytida kerakli pitch
     * o'zgarishi mumkin; oldingi interaksiyaning pitch deltasi bilan
     * takrorlanmasa, bunga ruxsat beriladi.
     */
    public boolean isDuplicateRotPlaceSafeState(float threshold) {
        if (!primed) return false;
        float deltaPitch = currentPitchDelta();
        if (deltaPitch < threshold) return true;
        return !hasInteractionPitchDelta || Math.abs(deltaPitch - lastInteractionPitchDelta) >= 0.001f;
    }

    public void markInteractionState() {
        if (!primed) return;
        lastInteractionPitchDelta = currentPitchDelta();
        hasInteractionPitchDelta = true;
    }

    private float currentPitchDelta() {
        return KuroRotations.active() ? lastDeltaServerPitch : lastDeltaPlayerPitch;
    }
}
