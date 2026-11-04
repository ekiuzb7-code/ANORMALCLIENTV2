package com.anormal.client.mixin;

import com.anormal.client.module.impl.kuro.KuroHitboxExpand;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Kuro EntityMixin (mojmap getDimensions/getPickRadius) muqobil Yarn xaritasi:
 * Entity#getTargetingMargin — o'yin faqat entity-picking (crosshair raycast)
 * uchun ishlatadi. Bu yerda KuroHitboxExpand.margin() natijasi vanilla
 * margin ustiga qo'shiladi; modul yakunlangach qiymat vanilla qoladi.
 */
@Mixin(Entity.class)
public class KuroHitboxExpandMixin {
    @Inject(method = "getTargetingMargin", at = @At("RETURN"), cancellable = true)
    private void anormal$kuroMargin(CallbackInfoReturnable<Float> cir) {
        float extra = KuroHitboxExpand.margin((Entity) (Object) this);
        if (extra > 0.0f) cir.setReturnValue(cir.getReturnValue() + extra);
    }
}
