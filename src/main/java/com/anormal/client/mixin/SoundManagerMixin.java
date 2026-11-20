package com.anormal.client.mixin;

import com.anormal.client.module.impl.client.FarmFpsOptimizer;
import com.anormal.client.util.sound.SoundEventRegistry;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundManager;
import net.minecraft.client.sound.SoundSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Lets {@code FarmFpsOptimizer} mute farm spam before it reaches the mixer.
 *
 * <p>Sound capture lives in {@link SoundSystemMixin}, which sits on the terminal sink both
 * {@code SoundManager} overloads delegate to. In 1.21.11 the single-argument overload returns
 * {@link SoundSystem.PlayResult}, so the old {@code play(SoundInstance)V} descriptor no longer
 * exists and an injection written against it silently never applies.</p>
 */
@Mixin(SoundManager.class)
public class SoundManagerMixin {

    @Inject(method = "play(Lnet/minecraft/client/sound/SoundInstance;I)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void anormal$play(SoundInstance sound, int delay, CallbackInfo ci) {
        try {
            if (FarmFpsOptimizer.shouldMuteSound(sound)) ci.cancel();
        } catch (Throwable ignored) {
        }
    }

    /**
     * Extra capture point for Sound Detector, ahead of the mixer.
     *
     * <p>Client-generated loops such as the rolling minecart never come from a packet, so if the
     * terminal {@code SoundSystem} injection is ever skipped by a version change they would be
     * invisible. Capturing at the manager as well keeps them; the registry drops the duplicate of an
     * instance that both hooks see.</p>
     */
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
