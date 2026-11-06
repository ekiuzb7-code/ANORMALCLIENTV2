package com.anormal.client.mixin;

import com.anormal.client.module.impl.spectre.SpectreTimeChanger;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Spectre section: forces the client-side time of day while SpectreTimeChanger is on.
 *
 * <p>Additive hook: when the module is off, {@link SpectreTimeChanger#forcedTime()} returns
 * {@code null} and vanilla keeps its own value.</p>
 */
@Mixin(World.class)
public class SpectreTimeMixin {

    @Inject(method = "getTimeOfDay", at = @At("HEAD"), cancellable = true, require = 0)
    private void spectre$forcedTime(CallbackInfoReturnable<Long> cir) {
        try {
            Long forced = SpectreTimeChanger.forcedTime();
            if (forced != null) cir.setReturnValue(forced);
        } catch (Throwable ignored) {
        }
    }
}
