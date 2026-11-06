package com.anormal.client.mixin;

import com.anormal.client.module.impl.spectre.SpectreAtmosphere;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Spectre section: Atmosphere. The vanilla sky colour is graded by the module, everything
 * else (biome blending, sunrise, rain) is left untouched.
 */
@Mixin(ClientWorld.class)
public class SpectreAtmosphereMixin {

    @Inject(method = "getSkyColor", at = @At("RETURN"), cancellable = true, require = 0)
    private void spectre$atmosphereSky(Vec3d cameraPos, float tickDelta, CallbackInfoReturnable<Integer> cir) {
        try {
            int original = cir.getReturnValue();
            int graded = SpectreAtmosphere.graded(original);
            if (graded != original) cir.setReturnValue(graded);
        } catch (Throwable ignored) {
        }
    }
}
