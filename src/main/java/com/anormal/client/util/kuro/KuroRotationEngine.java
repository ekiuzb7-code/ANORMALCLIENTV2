package com.anormal.client.util.kuro;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Kuro RotationEngine birga bir port: sichqoncha-qadamga aniqlangan rotatsiya
 * kontrollerlari (Fixed / Adaptive), boshqariladigan yaw/pitch va SentRotation.
 */
public class KuroRotationEngine {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    public static final RotationManager INSTANCE = new RotationManager();
    public static final SentRotation SENT = new SentRotation();

    // --- SentRotation ---
    public static class SentRotation {
        private float yaw = Float.NaN;
        private float pitch = Float.NaN;
        private boolean spoofed = false;

        public void update(float y, float p, boolean s) { yaw = y; pitch = p; spoofed = s; }
        public boolean isValid() { return !Float.isNaN(yaw); }
        public boolean isSpoofed() { return spoofed && isValid(); }
        public float yaw() { return yaw; }
        public float pitch() { return pitch; }
        public void reset() { yaw = Float.NaN; pitch = Float.NaN; spoofed = false; }
    }

    // --- MouseRotationController (asboban abstrakt) ---
    public static abstract class MouseRotationController {
        public float tolerance = 3.0f;
        public float speed = 1.0f;
        public float savedYaw;
        public float savedPitch;
        public float renderedYaw;
        public float renderedPitch;
        public float pendingYawDelta;
        public float pendingPitchDelta;
        private boolean complete;
        private boolean retainAfterCompletion;

        public boolean isComplete() { return complete; }
        public void setComplete(boolean c) { complete = c; }
        public boolean shouldRetainAfterCompletion() { return retainAfterCompletion; }
        public void setRetainAfterCompletion(boolean r) { retainAfterCompletion = r; }

        public abstract boolean updateYaw();
        public abstract boolean updatePitch();

        public void update(ClientPlayerEntity player, boolean screenOpen) {
            if (player == null || screenOpen) return;
            if (updateYaw() && updatePitch() && Math.abs(pendingYawDelta) < 1.0f && Math.abs(pendingPitchDelta) < 1.0f) {
                complete = true;
            }
        }

        protected float getMouseScale() {
            float sens = mc.options != null && mc.player != null
                    ? ((Double) mc.options.getMouseSensitivity().getValue()).floatValue() * 0.6f + 0.2f
                    : 0.5f;
            return sens * sens * sens * 8.0f;
        }

        protected boolean isOutsideTolerance(double error) {
            float rotPerStep = getMouseScale() * 0.15f;
            return Math.round(error / rotPerStep) > Math.max(Math.round(tolerance / rotPerStep), 0L);
        }

        protected float addPendingStep(float pending, double error, double step) {
            return (float) (error > 0.0 ? pending + step : pending - step);
        }

        public void applyPendingMovement(boolean screenOpen) {
            int yawSteps = (int) pendingYawDelta;
            int pitchSteps = (int) pendingPitchDelta;
            float remYaw = pendingYawDelta - yawSteps;
            float remPitch = pendingPitchDelta - pitchSteps;
            float scale = getMouseScale();

            if (!screenOpen) {
                PlayerMouseRotationApplier.applyTrackedMouseDelta(yawSteps * scale, pitchSteps * scale);
            }
            pendingYawDelta = remYaw;
            pendingPitchDelta = remPitch;
        }
    }

    // --- FixedRotationController ---
    public static class FixedRotationController extends MouseRotationController {
        public static final float UNSET = -999.0f;
        public float targetYaw;
        public float targetPitch;

        public FixedRotationController(float y, float p) { targetYaw = y; targetPitch = p; }
        public void setTargetRotation(float y, float p) { targetYaw = y; targetPitch = p; setComplete(false); }
        public float getCurrentYaw() { return mc.player != null ? mc.player.getYaw() : 0.0f; }
        public float getCurrentPitch() { return mc.player != null ? mc.player.getPitch() : 0.0f; }

        @Override
        public boolean updateYaw() {
            if (targetYaw == UNSET) return true;
            float scale = getMouseScale();
            float predictedYaw = getCurrentYaw() + (int) pendingYawDelta * scale * 0.15f;
            double error = MathHelper.wrapDegrees(targetYaw - predictedYaw);
            if (!isOutsideTolerance(Math.abs(error))) return true;

            double step = speed * 0.25;
            double normErr = Math.abs(error) / 100.0;
            double shifted = normErr + 0.7;
            step *= Math.min(Math.max(1.0, 0.4 + 2.0 * Math.pow(shifted, 3.0) + Math.pow(shifted, 2.0)), 4.0);

            pendingYawDelta = addPendingStep(pendingYawDelta, error, step);
            return false;
        }

        @Override
        public boolean updatePitch() {
            if (targetPitch == UNSET) return true;
            float current = getCurrentPitch() == -90.0f ? -89.99f : getCurrentPitch();
            float scale = getMouseScale();
            float predictedPitch = current - (int) (-pendingPitchDelta) * scale * 0.15f;
            double error = MathHelper.wrapDegrees(targetPitch - predictedPitch);
            if (!isOutsideTolerance(Math.abs(error))) return true;

            double step = speed * 0.25;
            double normErr = Math.abs(error) / 75.0;
            double shifted = normErr + 0.7;
            step *= Math.max(1.0, 0.4 + 2.0 * Math.pow(shifted, 3.0) + Math.pow(shifted, 2.0));

            pendingPitchDelta = addPendingStep(pendingPitchDelta, error, step);
            return false;
        }
    }

    // --- AdaptiveRotationController ---
    public static class AdaptiveRotationController extends FixedRotationController {
        private Vec3d target;
        private boolean relativeMode;
        private float currentYaw;
        private float currentPitch;

        public AdaptiveRotationController(float y, float p) {
            super(y, p);
            currentYaw = mc.player != null ? mc.player.getYaw() : y;
            currentPitch = mc.player != null ? mc.player.getPitch() : p;
            renderedYaw = currentYaw;
            renderedPitch = currentPitch;
        }

        public void setTarget(Vec3d t) { target = t; setComplete(false); }

        @Override
        public void update(ClientPlayerEntity player, boolean screenOpen) {
            if (player == null || screenOpen) return;
            if (target != null) {
                Vec3d eye = player.getEyePos();
                double dx = target.x - eye.x, dy = target.y - eye.y, dz = target.z - eye.z;
                double hyp = Math.sqrt(dx * dx + dz * dz);
                setTargetRotation((float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f, (float) -Math.toDegrees(Math.atan2(dy, hyp)));
            }
            boolean yawDone = updateYaw();
            boolean pitchDone = updatePitch();
            setComplete(yawDone && pitchDone && Math.abs(pendingYawDelta) < 1.0f && Math.abs(pendingPitchDelta) < 1.0f);
        }

        public float getRenderedYaw() { return relativeMode && mc.player != null ? mc.player.getYaw() + currentYaw : currentYaw; }
        public float getRenderedPitch() {
            float base = relativeMode && mc.player != null ? mc.player.getPitch() + currentPitch : currentPitch;
            return MathHelper.clamp(base, -90.0f, 90.0f);
        }
    }

    // --- PlayerMouseRotationApplier ---
    public static class PlayerMouseRotationApplier {
        public static void applyTrackedMouseDelta(float yawDelta, float pitchDelta) {
            if (mc.player == null) return;
            float trackedPitch = mc.player.getPitch();
            float trackedYaw = mc.player.getYaw();
            mc.player.setYaw((float) (trackedYaw + yawDelta * 0.15));
            mc.player.setPitch((float) (trackedPitch - pitchDelta * 0.15));
            if (mc.player.getPitch() < -90.0f) mc.player.setPitch(-90.0f);
            if (mc.player.getPitch() > 90.0f) mc.player.setPitch(90.0f);
            mc.player.lastPitch += mc.player.getPitch() - trackedPitch;
            mc.player.lastYaw += mc.player.getYaw() - trackedYaw;
        }
    }

    // --- RotationManager ---
    public static class RotationManager {
        private AdaptiveRotationController activeController;
        private float managedYaw;
        private float managedPitch;

        public boolean isControllerUpdating() { return activeController != null && !activeController.isComplete(); }
        public float getManagedYaw() { return isControllerUpdating() ? managedYaw : (mc.player != null ? mc.player.getYaw() : 0.0f); }
        public float getManagedPitch() { return isControllerUpdating() ? managedPitch : (mc.player != null ? mc.player.getPitch() : 0.0f); }

        public void setController(AdaptiveRotationController c) {
            if (activeController == null && c != null) {
                managedYaw = c.getRenderedYaw();
                managedPitch = c.getRenderedPitch();
            }
            activeController = c;
        }

        public void advanceAndApplyNow(int iterations) {
            if (activeController == null || mc.player == null) return;
            for (int i = 0; i < iterations; i++) {
                activeController.update(mc.player, false);
                activeController.applyPendingMovement(false);
            }
            managedYaw = activeController.getRenderedYaw();
            managedPitch = activeController.getRenderedPitch();
            applyManagedRotation(true);
        }

        public void applyManagedRotation(boolean apply) {
            if (mc.player == null) return;
            if (apply) {
                mc.player.setYaw(managedYaw);
                mc.player.headYaw = managedYaw;
                mc.player.setPitch(managedPitch);
            }
        }

        public void release() {
            activeController = null;
            SENT.reset();
        }
    }
}
