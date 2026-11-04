package com.anormal.client.mixin;

import com.anormal.client.module.impl.kuro.KuroSkyModule;

import net.minecraft.client.render.Camera;
import net.minecraft.client.render.SkyRendering;
import net.minecraft.client.render.state.SkyRenderState;
import net.minecraft.client.world.ClientWorld;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Sky moduli uchun osmon rangini almashtirish. 1.21.11da osmon rangi
 * ClientWorld#getSkyColor'dan kelmaydi: SkyRendering#updateRenderState uni
 * (environment atributlardan) SkyRenderState'ga ko‘chirib oladi va osmon
 * passi shu holatdan chiziladi — shuning uchun shu o‘rinda almashtiriladi.
 */
@Mixin(SkyRendering.class)
public abstract class KuroSkyRenderingMixin {
    @Inject(method = "updateRenderState", at = @At("TAIL"), require = 0)
    private void kuro$sky(ClientWorld world, float tickProgress, Camera camera, SkyRenderState state, CallbackInfo ci) {
        if (!KuroSkyModule.active()) return;
        state.skyColor = 0xFF000000 | KuroSkyModule.get().skyRgb();
        if (KuroSkyModule.get().noGlow()) state.sunriseAndSunsetColor = 0;   // tekis osmon ustidagi to‘q sariq nurini o‘chirish
        // Yoqilgan bo'lsa qora "void" soyalari o'chiriladi — chunki bek default oq osmon
        // tartibida jilo qiladi; bu yoqilsa 1.21.11'da osmon qora qatlamlar bilan shar
        // (halqa) qilib oq osmonning aksini ko'rsatib qoladi.
        state.shouldRenderSkyDark = false;   // toza tekis osmon, qora qatlamsiz
    }

    /** kuro'dagi toza tekis osmon: quyosh/Oy/yulduzlarni yashirish. */
    @Inject(method = "renderCelestialBodies", at = @At("HEAD"), cancellable = true, require = 0)
    private void kuro$noCelestial(net.minecraft.client.util.math.MatrixStack matrices, float a, float b, float c,
                                  net.minecraft.world.MoonPhase phase, float d, float e, CallbackInfo ci) {
        if (KuroSkyModule.active() && KuroSkyModule.get().hideCelestial()) ci.cancel();
    }
}
