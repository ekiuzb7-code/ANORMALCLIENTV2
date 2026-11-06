package com.anormal.client.mixin;

import com.anormal.client.module.impl.spectre.SpectreNoRender;
import net.minecraft.client.render.entity.EntityRenderManager;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Spectre section: No Render's Entity Fire.
 *
 * <p>Spectre clears {@code displayFireAnimation} on the extracted render state; in Yarn
 * 1.21.11 that flag is {@code EntityRenderState.onFire}, produced by
 * {@code EntityRenderManager.getAndUpdateRenderState}.</p>
 */
@Mixin(EntityRenderManager.class)
public class SpectreNoRenderMixin {

    @Inject(method = "getAndUpdateRenderState", at = @At("RETURN"), require = 0)
    private void spectre$hideEntityFire(Entity entity, float tickDelta, CallbackInfoReturnable<EntityRenderState> cir) {
        try {
            if (!SpectreNoRender.hideEntityFire()) return;
            EntityRenderState state = cir.getReturnValue();
            if (state != null) state.onFire = false;
        } catch (Throwable ignored) {
        }
    }
}
