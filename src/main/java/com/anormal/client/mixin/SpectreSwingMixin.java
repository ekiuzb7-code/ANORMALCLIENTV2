package com.anormal.client.mixin;

import com.anormal.client.module.impl.spectre.SpectreSwingAnimation;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Spectre section: Swing Animation. Scales the vanilla hand-swing duration by the module's
 * Tempo, which is what makes the swing look faster or slower.
 */
@Mixin(LivingEntity.class)
public class SpectreSwingMixin {

    @Inject(method = "getHandSwingDuration", at = @At("RETURN"), cancellable = true, require = 0)
    private void spectre$swingDuration(CallbackInfoReturnable<Integer> cir) {
        try {
            int vanilla = cir.getReturnValue();
            int scaled = SpectreSwingAnimation.swingTicks(vanilla);
            if (scaled != vanilla) cir.setReturnValue(scaled);
        } catch (Throwable ignored) {
        }
    }
}
