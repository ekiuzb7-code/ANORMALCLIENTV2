package com.anormal.client.mixin;

import com.anormal.client.module.impl.spectre.SpectreSpeedMine;
import net.minecraft.block.BlockState;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Spectre-section mining hook (Spectre {@code Speed Mine} dig mixin equivalent).
 *
 * <p>{@code AbstractBlockState.calcBlockBreakingDelta} decides the per-tick dig
 * progress; scaling it by {@code 1/breakAt} makes both the on-screen progress bar and
 * the client's own dig-finish timing land earlier, so the break packets leave sooner
 * (full functional parity with the Spectre behaviour: the server still validates the
 * vanilla dig cadence).</p>
 */
@Mixin(net.minecraft.block.AbstractBlock.AbstractBlockState.class)
public class SpectreMiningMixin {

    @Inject(method = "calcBlockBreakingDelta", at = @At("RETURN"), cancellable = true, require = 0)
    private void anormal$spectreSpeedMine(net.minecraft.entity.player.PlayerEntity player,
                                          net.minecraft.world.BlockView world,
                                          net.minecraft.util.math.BlockPos pos,
                                          CallbackInfoReturnable<Float> cir) {
        try {
            BlockState state = (BlockState) (Object) this;
            float raw = cir.getReturnValueF();
            SpectreSpeedMine.recordRaw(state, pos, raw);
            float scaled = SpectreSpeedMine.scaleProgress(state, raw);
            if (scaled != raw) {
                cir.setReturnValue(Math.max(0.0f, scaled));
            }
        } catch (Throwable ignored) {
        }
    }
}
