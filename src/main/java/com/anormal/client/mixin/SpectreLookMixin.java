package com.anormal.client.mixin;

import com.anormal.client.module.impl.spectre.SpectreFreelook;
import com.anormal.client.module.impl.spectre.SpectreZoom;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Spectre section mouse-look hook (Spectre's {@code MouseInputEvent} consumers).
 *
 * <p>Vanilla's {@code Entity.changeLookDirection(deltaX, deltaY)} is where
 * {@code Mouse} hands the per-frame cursor delta to the player and vanilla applies
 * {@code delta * 0.15} degrees. Spectre reads the raw delta to rotate its detached
 * freelook camera and to scale the turn while zoomed; this hook sits on the same spot.
 * Both modules are checked only for the local player, and when neither is active the
 * vanilla rotation is left untouched.</p>
 */
@Mixin(Entity.class)
public abstract class SpectreLookMixin {

    @Inject(method = "changeLookDirection", at = @At("HEAD"), cancellable = true, require = 0)
    private void spectre$detachedLook(double deltaX, double deltaY, CallbackInfo ci) {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null || (Object) this != mc.player) return;

            // Same conversion vanilla applies in changeLookDirection.
            double scale = SpectreZoom.turnScale();
            double yawDegrees = deltaX * 0.15 * scale;
            double pitchDegrees = deltaY * 0.15 * scale;

            // Freelook (Player mode) keeps the body still and turns only the camera.
            if (SpectreFreelook.routeLook(yawDegrees, pitchDegrees)) {
                ci.cancel();
                return;
            }

            // Zoom's Lower Sensitivity: apply the scaled turn ourselves.
            if (scale != 1.0) {
                Entity self = (Entity) (Object) this;
                self.setYaw(self.getYaw() + (float) yawDegrees);
                self.setPitch(MathHelper.clamp(self.getPitch() + (float) pitchDegrees, -90.0F, 90.0F));
                ci.cancel();
            }
        } catch (Throwable ignored) {
        }
    }
}
