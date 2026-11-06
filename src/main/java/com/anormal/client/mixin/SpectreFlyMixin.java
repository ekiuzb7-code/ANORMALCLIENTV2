package com.anormal.client.mixin;

import com.anormal.client.module.impl.spectre.SpectreFly;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Spectre section: Fly's Velocity engine.
 *
 * <p>Spectre's velocity fly keeps the player off the ground and gives the air movement a
 * usable horizontal speed. In Yarn 1.21.11 that value comes from
 * {@code PlayerEntity.getOffGroundSpeed()}, so the module's {@code offGroundSpeed()} helper is
 * applied here instead of leaving the player with vanilla air control.</p>
 */
@Mixin(PlayerEntity.class)
public class SpectreFlyMixin {

    @Inject(method = "getOffGroundSpeed", at = @At("RETURN"), cancellable = true, require = 0)
    private void spectre$flyOffGroundSpeed(CallbackInfoReturnable<Float> cir) {
        try {
            float speed = SpectreFly.offGroundSpeedFor((PlayerEntity) (Object) this);
            if (speed >= 0.0F) cir.setReturnValue(speed);
        } catch (Throwable ignored) {
        }
    }
}
