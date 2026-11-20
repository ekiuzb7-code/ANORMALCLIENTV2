package com.anormal.client.mixin;

import com.anormal.client.util.sound.SoundEventRegistry;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Captures sounds at the mixer sink.
 *
 * <p>{@code SoundManager} has two entry points and both simply delegate here, so this is the one
 * place every played instance must pass through - including calls that bypass the manager. The
 * detector only reads the instance; nothing is cancelled or changed here.</p>
 */
@Mixin(SoundSystem.class)
public class SoundSystemMixin {

    static {
        // Merged into SoundSystem's static initializer: one line in logs/latest.log proves the
        // capture hook is live, without needing any in-game setting.
        org.slf4j.LoggerFactory.getLogger("Anormal/Sound")
                .info("[Anormal] SoundSystem capture hook applied");
    }

    @Inject(method = "play(Lnet/minecraft/client/sound/SoundInstance;)Lnet/minecraft/client/sound/SoundSystem$PlayResult;",
            at = @At("HEAD"), require = 0)
    private void anormal$capture(SoundInstance sound, CallbackInfoReturnable<SoundSystem.PlayResult> cir) {
        try {
            SoundEventRegistry.capture(sound);
        } catch (Throwable ignored) {
        }
    }

    @Inject(method = "play(Lnet/minecraft/client/sound/SoundInstance;I)V",
            at = @At("HEAD"), require = 0)
    private void anormal$captureDelayed(SoundInstance sound, int delay, CallbackInfo ci) {
        try {
            SoundEventRegistry.capture(sound);
        } catch (Throwable ignored) {
        }
    }
}
