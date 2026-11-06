package com.anormal.client.mixin;

import com.anormal.client.module.impl.spectre.SpectreSkinChange;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.player.SkinTextures;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Spectre section: Skin Change.
 *
 * <p>Spectre swaps the avatar render state's skin texture; in Yarn 1.21.11 the texture comes
 * from {@code AbstractClientPlayerEntity.getSkin()}, so the module's resolved skin (or the tab
 * list entry of the chosen name) is returned from there.</p>
 */
@Mixin(AbstractClientPlayerEntity.class)
public class SpectreSkinMixin {

    @Inject(method = "getSkin", at = @At("RETURN"), cancellable = true, require = 0)
    private void spectre$skinOverride(CallbackInfoReturnable<SkinTextures> cir) {
        try {
            SkinTextures skin = SpectreSkinChange.skinFor((AbstractClientPlayerEntity) (Object) this);
            if (skin != null) cir.setReturnValue(skin);
        } catch (Throwable ignored) {
        }
    }
}
