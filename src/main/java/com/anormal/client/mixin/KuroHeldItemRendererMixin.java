package com.anormal.client.mixin;

import com.anormal.client.knife.KnifeRenderer;
import com.anormal.client.module.impl.kuro.KuroKnifeModule;

import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * kuro'daki ItemInHandRendererMixin porti (Yarn analogiga moslashgan):
 * {@code HeldItemRenderer.renderItem} bosqichida pichoq chizilganda vanilla
 * qilich modeli o‘chiriladi (ci.cancel). Vanilla qo‘l uslubi bloklanganida
 * "Attack animation" ilova qilinmadi — kuro'dagi ModifyVariable imzosi bu
 * versiyada mavjud emas, shuning uchun usbu port faqat chizishni almashtiradi.
 */
@Mixin(HeldItemRenderer.class)
public abstract class KuroHeldItemRendererMixin {

    @Inject(
            method = "renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ItemDisplayContext;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;I)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void kuro$knife(LivingEntity entity, ItemStack stack, ItemDisplayContext context, MatrixStack pose,
                            OrderedRenderCommandQueue collector, int light, CallbackInfo ci) {
        KuroKnifeModule knife = KuroKnifeModule.get();
        if (knife == null || !knife.isEnabled()) return;
        if (KnifeRenderer.render(stack, context, pose, collector, light, knife.size(), knife.matchMaterial(),
                knife.offX(), knife.offY(), knife.offZ(), knife.knifeNumber(), knife.trailSeconds())) ci.cancel();
    }
}
