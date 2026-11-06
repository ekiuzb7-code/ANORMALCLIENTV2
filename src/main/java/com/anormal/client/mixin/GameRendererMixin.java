package com.anormal.client.mixin;

import com.anormal.client.module.ModuleManager;
import com.anormal.client.module.impl.render.NoHurtCam;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

    @Invoker("getFov")
    public abstract float callGetFov(Camera camera, float tickDelta, boolean changingFov);

    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true, require = 0)
    private void anormal$zoomFov(Camera camera, float tickDelta, boolean changingFov, CallbackInfoReturnable<Float> cir) {
        try {
            // Spectre section: Custom FOV overrides the vanilla value directly.
            Float rawFov = cir.getReturnValue();
            Double spectreFov = com.anormal.client.module.impl.spectre.SpectreCustomFov.apply(
                    rawFov == null || rawFov <= 0 ? null : Double.valueOf(rawFov));
            if (spectreFov != null) {
                cir.setReturnValue(spectreFov.floatValue());
                return;
            }

            // Spectre section: SpectreZoom divides the FOV while zoomed (additive).
            float spectreZoomFactor = com.anormal.client.module.impl.spectre.SpectreZoom.fovFactor();
            if (spectreZoomFactor > 1.0001F) {
                Float rawZoomFov = cir.getReturnValue();
                if (rawZoomFov != null && rawZoomFov > 0) {
                    cir.setReturnValue(rawZoomFov / spectreZoomFactor);
                    return;
                }
            }

            com.anormal.client.module.impl.render.Zoom zoom =
                    com.anormal.client.module.ModuleManager.getModule(com.anormal.client.module.impl.render.Zoom.class);
            if (zoom != null && zoom.isActive()) {
                Float v = cir.getReturnValue();
                if (v != null && v > 0) cir.setReturnValue((float) (v / zoom.applyFrame()));
            } else if (zoom != null) {
                zoom.restoreSensitivity();
            }
        } catch (Throwable ignored) {}
    }

    /** Spectre section: No Render - cancel the totem pop animation overlay. */
    @Inject(method = "displayItemActivation", at = @At("HEAD"), cancellable = true, require = 0)
    private void spectre$cancelTotemAnimation(net.minecraft.item.ItemStack stack, CallbackInfo ci) {
        try {
            if (com.anormal.client.module.impl.spectre.SpectreNoRender.hideTotemAnimation()) ci.cancel();
        } catch (Throwable ignored) {
        }
    }

    @Inject(method = "tiltViewWhenHurt", at = @At("HEAD"), cancellable = true, require = 0)
    private void onTiltViewWhenHurt(MatrixStack matrices, float tickDelta, CallbackInfo ci) {
        NoHurtCam noHurtCam = ModuleManager.getModule(NoHurtCam.class);
        if (noHurtCam != null && noHurtCam.isEnabled()) {
            ci.cancel();
        }
    }
}
