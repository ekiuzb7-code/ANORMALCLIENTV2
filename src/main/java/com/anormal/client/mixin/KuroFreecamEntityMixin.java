package com.anormal.client.mixin;

import com.anormal.client.module.impl.kuro.KuroFreecam;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * kuro EntityMixin'dagi Freecam qismi porti: kamera ajratilganda sichqoncha
 * ko'rish harakati real o'yinchini emas, freecam kamerani buradi.
 */
@Mixin(Entity.class)
public abstract class KuroFreecamEntityMixin {

    @Inject(method = "changeLookDirection(DD)V", at = @At("HEAD"), cancellable = true)
    private void kuro$freecamLook(double dx, double dy, CallbackInfo ci) {
        if (KuroFreecam.active() && (Object) this == MinecraftClient.getInstance().player) {
            KuroFreecam.get().look(dx, dy);
            ci.cancel();
        }
    }
}
