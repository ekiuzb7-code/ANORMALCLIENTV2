package com.anormal.client.mixin;

import com.anormal.client.module.impl.spectre.SpectreFreelook;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Spectre section: Freelook. After vanilla has aimed the camera at the player's rotation,
 * the module's own yaw/pitch overwrite it so the body and the camera turn apart.
 */
@Mixin(Camera.class)
public abstract class SpectreFreelookMixin {

    @Shadow
    protected abstract void setRotation(float yaw, float pitch);

    @Inject(method = "update", at = @At("TAIL"), require = 0)
    private void spectre$freelook(World area, Entity focusedEntity, boolean thirdPerson,
                                  boolean inverseView, float tickDelta, CallbackInfo ci) {
        try {
            if (SpectreFreelook.active()) {
                setRotation(SpectreFreelook.activeYaw(), SpectreFreelook.activePitch());
            }
        } catch (Throwable ignored) {
        }
    }
}
