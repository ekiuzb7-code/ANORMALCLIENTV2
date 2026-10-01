package com.anormal.client.mixin;

import com.anormal.client.module.ModuleManager;
import com.anormal.client.module.impl.world.Freecam;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public class CameraMixin {

    @Shadow
    protected void setPos(double x, double y, double z) {}

    @Shadow
    protected void setRotation(float yaw, float pitch) {}

    @Inject(method = "update", at = @At("HEAD"), cancellable = true, require = 0)
    private void onCameraUpdate(World area, Entity focusedEntity, boolean thirdPerson, boolean inverseView, float tickProgress, CallbackInfo ci) {
        try {
            Freecam freecam = ModuleManager.getModule(Freecam.class);
            if (freecam != null && freecam.isEnabled() && freecam.isCameraActive()) {
                setPos(freecam.getCamX(), freecam.getCamY(), freecam.getCamZ());
                setRotation(freecam.getCamYaw(), freecam.getCamPitch());
                ci.cancel();
            }
        } catch (Throwable ignored) {}
    }
}
