package com.anormal.client.mixin;

import com.anormal.client.module.impl.spectre.SpectreSlowSwing;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Spectre-section movement ticking hook on LivingEntity.
 *
 * <p>{@code getHandSwingDuration} (Yarn {@code method_6028}) decides how many ticks the
 * item swing animation lasts. Spectre's {@code Slow Swing} divides that duration by its
 * Speed setting (0.25 default = a 4x slower animation).</p>
 */
@Mixin(net.minecraft.entity.LivingEntity.class)
public class SpectreLivingMixin {

    @Inject(method = "getHandSwingDuration", at = @At("RETURN"), cancellable = true, require = 0)
    private void anormal$spectreSlowSwing(CallbackInfoReturnable<Integer> cir) {
        try {
            int modified = SpectreSlowSwing.modifyDuration(cir.getReturnValueI());
            if (modified != cir.getReturnValueI()) {
                cir.setReturnValue(modified);
            }
        } catch (Throwable ignored) {
        }
    }
}
