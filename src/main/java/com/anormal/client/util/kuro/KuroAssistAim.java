package com.anormal.client.util.kuro;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Kuro AssistAim birga bir port: Aim Assist'ning nishonga olishi — takroran
 * ishlatiladigan klass. Oldinga chiqish (lead/predict), ko'zlarga moslangan
 * nishon balandligi, tezlikka bog'liq yumshatish, sichqoncha ustidagi qo'lning
 * sekin silkinishi va joriy sensitivity bo'yicha sichqoncha-qadamiga aniqlanish.
 *
 * O'yinchiga bevosita tegmaydi: step(...) "shu frame/tickda qancha daraja
 * burilish" javobini qaytaradi va chaqiruvchi uni o'zi egalik qilgan holatga
 * qo'llaydi (Aim Assist va Trigger Bot real view'ni, Silent Aura silent
 * burchaklarini). Har chaqiruvchi o'z instansini ishlatadi; smoothing holati
 * o'sha bitta instanstda saqlanadi.
 *
 * strength = 1 Aim Assist asl holati bilan bir. 1'dan yuqori qiymat nishonga
 * tortishni ko'paytiradi (bitta tur qolgan xatoning 95% ortiq qoplamaydi,
 * overshoot bo'lmaydi).
 */
public final class KuroAssistAim {
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    private long lastFrameNanos;
    private long noiseStartNanos;
    private float aimStrength;
    private float lastYawDiff;
    private float lastTargetYaw;
    private float lastTargetPitch;
    private double lastEyeY;
    private double lastGroundEyeY;
    private float verticalVelocity;
    private float airFactor;
    private double aimX;
    private double aimY;
    private double aimZ;
    private double leadX;
    private double leadY;
    private double leadZ;
    private boolean initialized;
    private float pendingYaw;
    private float pendingPitch;

    public KuroAssistAim() { reset(); }

    public void reset() {
        pendingYaw = 0; pendingPitch = 0;
        aimStrength = 0; lastYawDiff = 0;
        lastTargetYaw = 0; lastTargetPitch = 0;
        initialized = false; lastFrameNanos = 0;
        aimX = aimY = aimZ = leadX = leadY = leadZ = 0;
        lastEyeY = lastGroundEyeY = 0;
        verticalVelocity = 0; airFactor = 0;
        noiseStartNanos = System.nanoTime();
    }

    /**
     * @param curYaw / curPitch  burilanayotgan burchaklar (real view, yoki Silent Aura'ninki)
     * @param hSetting / vSetting  "Horizontal / Vertical Speed" 1..10 (Aim Assist default 5)
     * @param vertical     yuqori / pastga ham burilsinmi
     * @param strength     1 = Aim Assist, ko'proq = kuchliroq tortish
     * @param noise        0..1 qo'l silkinichi shkalasi (1 = Aim Assist)
     * @return {yaw daraja, pitch daraja} hozirgi burchaklarga QO'SHILISHI kerak
     *         (pitch musbat = pastga qarash, o'yindek), yo'q bo'lsa null
     */
    public float[] step(LivingEntity target, float curYaw, float curPitch, double hSetting, double vSetting,
                        boolean vertical, float strength, float noise) {
        if (mc.player == null || target == null) return null;

        long nowNanos = System.nanoTime();
        float deltaTime = (!initialized || lastFrameNanos == 0) ? 0.016666668f : (float) (nowNanos - lastFrameNanos) / 1.0E9f;
        deltaTime = Math.max(0.008333334f, Math.min(0.12f, deltaTime));
        lastFrameNanos = nowNanos;
        if (noiseStartNanos == 0) noiseStartNanos = nowNanos;

        double playerEyeY = mc.player.getEyePos().y;
        double targetMinY = target.getBoundingBox().minY;
        double targetMaxY = target.getBoundingBox().maxY;
        double targetCenterY = targetMinY + (targetMaxY - targetMinY) * 0.65;
        double eyeOffset = 0.1 + Math.min(0.85, airFactor);
        double eyeAlignedY = Math.max(targetMinY + 0.01, Math.min(targetMaxY - 0.01, playerEyeY - eyeOffset));
        double centerBlend = Math.max(0.0, Math.min(1.0, airFactor / 0.55));
        double targetY = eyeAlignedY + (targetCenterY - eyeAlignedY) * centerBlend;
        targetY = Math.max(targetMinY + 0.01, Math.min(targetMaxY - 0.01, targetY));

        Vec3d tMotion = target.getVelocity();
        Vec3d pMotion = mc.player.getVelocity();
        double relMotionX = tMotion.x - pMotion.x;
        double relMotionY = tMotion.y - pMotion.y;
        double relMotionZ = tMotion.z - pMotion.z;

        double targetLeadX = tMotion.x + relMotionX * 0.08;
        double targetLeadY = tMotion.y + relMotionY * 0.1;
        double targetLeadZ = tMotion.z + relMotionZ * 0.08;

        double resolvedAimX = target.getX();
        double resolvedAimZ = target.getZ();

        if (!initialized) {
            aimX = resolvedAimX; aimY = targetY; aimZ = resolvedAimZ;
            leadX = targetLeadX; leadY = targetLeadY; leadZ = targetLeadZ;
            initialized = true;
        }

        double tSpeed = Math.sqrt(tMotion.x * tMotion.x + tMotion.z * tMotion.z);
        double relSpeed = Math.sqrt(relMotionX * relMotionX + relMotionZ * relMotionZ);
        double dist = mc.player.distanceTo(target);

        double aimSmoothing = Math.max(0.08, Math.min(0.75, 0.28 + Math.min(0.35, relSpeed * 0.5)));
        double leadSmoothing = Math.max(0.12, Math.min(0.68, 0.18 + Math.min(0.42, (tSpeed + relSpeed * 0.25) * 0.72)));
        double frameScale = Math.max(0.45, Math.min(2.4, (double) deltaTime * 60.0));

        // kuchliroq tortish nishonning haqiqiy joyiga yaqinroq kuzatadi (kamroq smoothing = kamroq pozitvakat)
        double tight = Math.min(0.9, (strength - 1.0f) * 0.12);
        double aimBlend = 1.0 - Math.pow(1.0 - Math.min(0.98, aimSmoothing + (1.0 - aimSmoothing) * tight), frameScale);
        double leadBlend = 1.0 - Math.pow(1.0 - leadSmoothing, frameScale);

        aimX += (resolvedAimX - aimX) * aimBlend;
        aimY += (targetY - aimY) * aimBlend;
        aimZ += (resolvedAimZ - aimZ) * aimBlend;
        leadX += (targetLeadX - leadX) * leadBlend;
        leadY += (targetLeadY - leadY) * leadBlend;
        leadZ += (targetLeadZ - leadZ) * leadBlend;

        double leadAmount = Math.max(0.05, Math.max(0.1, Math.min(1.05, 0.35 + dist * 0.045 + tSpeed * 0.95 + Math.min(0.18, relSpeed * 0.12))) * (0.3 + 0.7 * Math.max(0.0, Math.min(1.0, (dist - 0.8) / 2.5))));

        float measuredVerticalVelocity = (lastEyeY != 0.0) ? (float) ((playerEyeY - lastEyeY) / deltaTime) : 0.0f;
        verticalVelocity += (measuredVerticalVelocity - verticalVelocity) * 0.65f;
        lastEyeY = playerEyeY;
        if (mc.player.isOnGround()) {
            lastGroundEyeY = playerEyeY;
            airFactor *= 0.35f;
        } else {
            if (lastGroundEyeY == 0.0) lastGroundEyeY = playerEyeY;
            airFactor = Math.max(airFactor * 0.92f, Math.max((float) Math.min(0.7, Math.max(0.0, verticalVelocity) * 0.34), (float) Math.min(0.82, Math.max(0.0, playerEyeY - lastGroundEyeY) * 0.58)));
        }

        double verticalLead = leadY * Math.min(leadAmount, 0.7 + dist * 0.04) * (0.28 + 0.52 * Math.max(0.0, Math.min(1.0, Math.abs(aimY - playerEyeY) / 0.7)));
        verticalLead = Math.max(-(0.16 + dist * 0.055), Math.min(0.16 + dist * 0.055, verticalLead));

        double adjustedAimY = aimY + verticalLead;
        double aimDeltaX = aimX - mc.player.getX();
        double aimDeltaZ = aimZ - mc.player.getZ();
        double aimDeltaY = adjustedAimY - playerEyeY;
        double horizontalDistance = Math.sqrt(aimDeltaX * aimDeltaX + aimDeltaZ * aimDeltaZ);

        float targetYaw = (float) (Math.toDegrees(Math.atan2(aimDeltaZ, aimDeltaX)) - 90.0);
        float targetPitch = (float) (-Math.toDegrees(Math.atan2(aimDeltaY, Math.max(horizontalDistance, 1.0E-4))));
        float yawTargetChange = Math.abs(MathHelper.wrapDegrees(targetYaw - lastTargetYaw));
        float pitchTargetChange = Math.abs(MathHelper.wrapDegrees(targetPitch - lastTargetPitch));
        float abruptFactor = Math.max(0.0f, Math.min(1.0f, (Math.max(yawTargetChange, pitchTargetChange) - 12.0f) / 50.0f));
        if (abruptFactor > 0.3f) {
            aimStrength *= 0.3f;
            aimX = resolvedAimX; aimY = targetY; aimZ = resolvedAimZ;
        }

        return turn(targetYaw, targetPitch, curYaw, curPitch, hSetting, vSetting, vertical, strength, noise, deltaTime, nowNanos);
    }

    /**
     * step() bilan birga, lekin o'yindagi aniq bir nuqtaga qarab (bir bloklicha
     * yuzini klikkilash, sandiq) buriladi: lead/predict yo'q, faqat yengil
     * tortish, silkinayar va sichqoncha-qadami aniqlanishidir.
     */
    public float[] stepToPoint(Vec3d point, float curYaw, float curPitch, double hSetting, double vSetting,
                               boolean vertical, float strength, float noise) {
        if (mc.player == null || point == null) return null;
        long nowNanos = System.nanoTime();
        float deltaTime = (!initialized || lastFrameNanos == 0) ? 0.016666668f : (float) (nowNanos - lastFrameNanos) / 1.0E9f;
        deltaTime = Math.max(0.008333334f, Math.min(0.12f, deltaTime));
        lastFrameNanos = nowNanos;
        initialized = true;
        if (noiseStartNanos == 0) noiseStartNanos = nowNanos;

        Vec3d eye = mc.player.getEyePos();
        double dx = point.x - eye.x, dy = point.y - eye.y, dz = point.z - eye.z;
        float targetYaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float targetPitch = (float) (-Math.toDegrees(Math.atan2(dy, Math.max(Math.sqrt(dx * dx + dz * dz), 1.0E-4))));

        // oxirgi nuqtadan ko'p uzoq yangi hicbuvchi (keyingi blok ratchasi) tortishga
        // yumshoq qayta boshlanadi, snap qilmaydi
        float change = Math.max(Math.abs(MathHelper.wrapDegrees(targetYaw - lastTargetYaw)), Math.abs(MathHelper.wrapDegrees(targetPitch - lastTargetPitch)));
        if (change > 12.0f) aimStrength *= 0.3f;

        return turn(targetYaw, targetPitch, curYaw, curPitch, hSetting, vSetting, vertical, strength, noise, deltaTime, nowNanos);
    }

    private float[] turn(float targetYaw, float targetPitch, float curYaw, float curPitch, double hSetting, double vSetting,
                         boolean vertical, float strength, float noise, float deltaTime, long nowNanos) {
        float yawError = MathHelper.wrapDegrees(targetYaw - curYaw);
        float pitchError = vertical ? MathHelper.wrapDegrees(targetPitch - curPitch) : 0.0f;

        float absYawErr = Math.abs(yawError);
        float absPitchErr = Math.abs(pitchError);
        float combinedErr = (float) Math.sqrt(absYawErr * absYawErr + absPitchErr * absPitchErr);

        float desiredAimStrength = 1.0f - smoothStep(1.5f, 8.0f, combinedErr);
        desiredAimStrength += Math.max(-0.3f, Math.min(0.3f, -(absYawErr - Math.abs(lastYawDiff)) / deltaTime / 20.0f));
        desiredAimStrength = Math.max(0.0f, Math.min(1.0f, desiredAimStrength));

        float aimStrengthBlend = desiredAimStrength > aimStrength ? Math.max(0.01f, Math.min(0.25f, deltaTime * 3.0f)) : Math.max(0.05f, Math.min(0.8f, deltaTime * 20.0f));
        aimStrength += (desiredAimStrength - aimStrength) * aimStrengthBlend;

        // Tez o'yinchilar (bhop/dash) uchun yuqori tezlik rusumlari: chek 10'dan 60'ga ko'tarildi.
        // Gain formulasi o'zgarmadi, shuning uchun eski modullar (max 10) aynandagi lenis xulqini saqlaydi.
        float hSpeed = Math.min((float) hSetting * 0.75f, 60.0f);
        float vSpeed = Math.min((float) vSetting * 0.75f, 60.0f);
        // Gain 10-band'dan yuqori qiymatlar uchun ham o'sadi (har qo'shimcha unit +0.02),
        // lekin 1.6 bilan chegaralanadi — jadi max-tezlikda ham aylantirish tashkariga chıqmaydi.
        float hGain = 0.15f + 0.85f * Math.max(0.0f, Math.min(1.0f, (hSpeed - 1.0f) / 9.0f))
                + Math.max(0.0f, hSpeed - 10.0f) * 0.02f;
        float vGain = 0.15f + 0.85f * Math.max(0.0f, Math.min(1.0f, (vSpeed - 1.0f) / 9.0f))
                + Math.max(0.0f, vSpeed - 10.0f) * 0.02f;
        hGain = Math.min(1.6f, hGain) * hGain;
        vGain = Math.min(1.6f, vGain) * vGain;

        float yawRate = lerp(aimStrength, 8.0f, 2.5f + hSpeed * 0.15f) * hGain * yawError;
        float pitchRate = lerp(aimStrength, 7.0f, 2.2f + vSpeed * 0.13f) * vGain * pitchError;

        float noiseElapsed = (float) (nowNanos - noiseStartNanos) / 1.0E9f;
        float yawNoise = (float) (Math.sin(noiseElapsed * 62.83) * 0.4 + Math.sin(noiseElapsed * 47.12) * 0.25 + Math.sin(noiseElapsed * 78.54) * 0.15 + Math.sin(noiseElapsed * 12.57) * 0.8 + Math.sin(noiseElapsed * 7.85) * 0.5 + Math.sin(noiseElapsed * 1.26) * 0.3);
        float pitchNoise = (float) (Math.sin(noiseElapsed * 56.55 + 1.3) * 0.35 + Math.sin(noiseElapsed * 43.98 + 0.7) * 0.2 + Math.sin(noiseElapsed * 72.26 + 2.1) * 0.12 + Math.sin(noiseElapsed * 10.47 + 0.9) * 0.6 + Math.sin(noiseElapsed * 5.65 + 1.8) * 0.4 + Math.sin(noiseElapsed * 0.94 + 0.5) * 0.2);

        yawRate += yawNoise * (0.15f + 0.85f * aimStrength) * (0.5f + 0.5f * (1.0f - hGain)) * 1.5f * noise;
        if (vertical) pitchRate += pitchNoise * (0.15f + 0.85f * aimStrength) * (0.5f + 0.5f * (1.0f - vGain)) * noise;

        float yawFrameDelta = yawRate * deltaTime;
        float pitchFrameDelta = pitchRate * deltaTime;

        if (strength > 1.0f) {
            // tortish ko'paytiriladi, lekin bitta tur qolgan xatoning 95% ortiq qoplamaydi (oshirib ketmaydi)
            yawFrameDelta = limit(yawFrameDelta * strength, yawError * 0.95f);
            pitchFrameDelta = limit(pitchFrameDelta * strength, pitchError * 0.95f);
        }

        // joriy sensitivity bo'yicha bujer sichqoncha qadami bo'yicha aniqlashg, qolgani keyin qoladigan
        float sensitivityBase = ((Double) mc.options.getMouseSensitivity().getValue()).floatValue() * 0.6f + 0.2f;
        float sensitivityScale = sensitivityBase * sensitivityBase * sensitivityBase * 8.0f;
        float mouseAngleUnit = sensitivityScale * 0.15f;
        if (mouseAngleUnit > 1.0E-5f) {
            pendingYaw += yawFrameDelta / mouseAngleUnit;
            pendingPitch += pitchFrameDelta / mouseAngleUnit;
        }
        int yawSteps = (int) pendingYaw;
        int pitchSteps = (int) pendingPitch;
        pendingYaw -= yawSteps;
        pendingPitch -= pitchSteps;

        lastYawDiff = yawError;
        lastTargetYaw = targetYaw;
        lastTargetPitch = targetPitch;

        if (yawSteps == 0 && pitchSteps == 0) return null;
        return new float[]{yawSteps * mouseAngleUnit, pitchSteps * mouseAngleUnit};
    }

    /** v qiymatini [-|max|, +|max|] oraliqda cheklab beradi. */
    private static float limit(float v, float max) {
        float m = Math.abs(max);
        return MathHelper.clamp(v, -m, m);
    }

    private static float lerp(float factor, float start, float end) { return start + factor * (end - start); }

    private static float smoothStep(float edgeStart, float edgeEnd, float value) {
        float normalized = Math.max(0.0f, Math.min(1.0f, (value - edgeStart) / (edgeEnd - edgeStart)));
        return normalized * normalized * (3.0f - 2.0f * normalized);
    }
}
