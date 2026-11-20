package com.anormal.client.mixin;

import com.anormal.client.module.impl.spectre.SpectreJesus;
import net.minecraft.entity.LivingEntity;
import net.minecraft.fluid.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Spectre Jesus: makes the fluid surface answer "yes" for the local player.
 *
 * <p>This is the same hook vanilla uses for Striders ({@code canWalkOnFluid}): the entity then
 * treats the fluid surface as ground, so jumping, step-up and gravity all keep working without
 * any packet tricks. Both the Yarn and the Mojang name are injected, and {@code require = 0}
 * keeps a mapping rename from breaking the mod.</p>
 */
@Mixin(LivingEntity.class)
public class SpectreJesusLivingMixin {

    @Inject(method = "canWalkOnFluid", at = @At("RETURN"), cancellable = true, require = 0)
    private void anormal$jesusCanWalkOnFluid(FluidState fluid, CallbackInfoReturnable<Boolean> cir) {
        jesusFluid(fluid, cir);
    }

    @Inject(method = "canStandOnFluid", at = @At("RETURN"), cancellable = true, require = 0)
    private void anormal$jesusCanStandOnFluid(FluidState fluid, CallbackInfoReturnable<Boolean> cir) {
        jesusFluid(fluid, cir);
    }

    private void jesusFluid(FluidState fluid, CallbackInfoReturnable<Boolean> cir) {
        try {
            if (cir.getReturnValueZ()) return;
            SpectreJesus jesus = SpectreJesus.active;
            if (jesus == null || !jesus.isEnabled()) return;
            if ((Object) this != net.minecraft.client.MinecraftClient.getInstance().player) return;
            if (jesus.canWalkOnFluid(fluid)) cir.setReturnValue(true);
        } catch (Throwable ignored) {
        }
    }
}
