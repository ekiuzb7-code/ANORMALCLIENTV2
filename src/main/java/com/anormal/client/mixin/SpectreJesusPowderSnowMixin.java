package com.anormal.client.mixin;

import com.anormal.client.module.impl.spectre.SpectreJesus;
import net.minecraft.block.PowderSnowBlock;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Spectre Jesus: "Powder Snow" setting - the local player walks on top of powder snow instead of
 * sinking in. Vanilla only lets entities with leather boots do this, so the flag is widened here.
 */
@Mixin(PowderSnowBlock.class)
public class SpectreJesusPowderSnowMixin {

    @Inject(method = "canWalkOnPowderSnow", at = @At("RETURN"), cancellable = true, require = 0)
    private static void anormal$jesusPowderSnow(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        jesusPowder(entity, cir);
    }

    @Inject(method = "canEntityWalkOnPowderSnow", at = @At("RETURN"), cancellable = true, require = 0)
    private static void anormal$jesusPowderSnowMojang(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        jesusPowder(entity, cir);
    }

    private static void jesusPowder(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        try {
            if (cir.getReturnValueZ()) return;
            SpectreJesus jesus = SpectreJesus.active;
            if (jesus == null || !jesus.isEnabled() || !jesus.canWalkOnPowderSnow()) return;
            if (entity != net.minecraft.client.MinecraftClient.getInstance().player) return;
            cir.setReturnValue(true);
        } catch (Throwable ignored) {
        }
    }
}
