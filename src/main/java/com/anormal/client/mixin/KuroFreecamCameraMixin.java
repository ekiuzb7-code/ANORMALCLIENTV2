package com.anormal.client.mixin;

import com.anormal.client.module.impl.kuro.KuroFreecam;

import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * kuro CameraMixin'dagi Freecam qismi porti: kamera setup tugagach freecam
 * burchaklari va interpolatsiyalangan pozitsiyasi qo'yiladi; freecam faol
 * bo'lganda uchinchi shaxs rejimi majburan yoqiladi, shuning uchun real
 * o'yinchi modeli ko'rinib turadi.
 */
@Mixin(Camera.class)
public abstract class KuroFreecamCameraMixin {

    @Shadow protected abstract void setPos(double x, double y, double z);
    @Shadow protected abstract void setRotation(float yaw, float pitch);

    @Inject(method = "update(Lnet/minecraft/world/World;Lnet/minecraft/entity/Entity;ZZF)V", at = @At("TAIL"))
    private void kuro$freecamUpdate(World area, Entity focusedEntity, boolean thirdPerson, boolean inverseView,
                                    float tickProgress, CallbackInfo ci) {
        if (KuroFreecam.active()) {
            KuroFreecam f = KuroFreecam.get();
            setRotation(f.yaw, f.pitch);
            setPos(f.lx(tickProgress), f.ly(tickProgress), f.lz(tickProgress));
        }
    }

    // kamera uzilganda real o'yinchi modeli chizilsin
    @Inject(method = "isThirdPerson", at = @At("HEAD"), cancellable = true)
    private void kuro$freecamThirdPerson(CallbackInfoReturnable<Boolean> cir) {
        if (KuroFreecam.active()) cir.setReturnValue(true);
    }
}
