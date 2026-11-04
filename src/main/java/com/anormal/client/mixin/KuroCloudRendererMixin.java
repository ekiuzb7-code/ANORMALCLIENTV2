package com.anormal.client.mixin;

import com.anormal.client.module.impl.kuro.KuroSkyModule;

import net.minecraft.client.render.CloudRenderer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Sky moduli uchun oq bulutlar: renderClouds(int color, ...) uslubiga oq
 * rangning katta oktabrini (o‘z alpha'ni saqlarken, masalan yomg‘ir uchun) beradi.
 */
@Mixin(CloudRenderer.class)
public abstract class KuroCloudRendererMixin {
    @ModifyVariable(method = "renderClouds", at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0)
    private int kuro$clouds(int color) {
        if (!KuroSkyModule.active() || !KuroSkyModule.get().cloudsOn()) return color;
        return (color & 0xFF000000) | 0xFFFFFF;
    }
}
