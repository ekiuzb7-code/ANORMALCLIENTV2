package com.anormal.client.module.impl.kuro;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;

import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;

/**
 * kuro {@code Fullbright} port: kunduz ko'rish effekatini klient tomonda
 * qo'shadi (1200 tick — fade-out miltillashishini oldini oluvchi uzunlik).
 * Modul o'chanda effect olib tashlanadi. Server hech narsa sezmaydi: effekt
 * faqat klient tomonda ko'rinadi va paket yuborilmaydi.
 */
public class KuroFullbright extends Module {
    private boolean applied;

    public KuroFullbright() { super("Fullbright", "Client-side night vision so caves stay bright", Category.KURO); }

    @Override
    public void onTick() {
        if (mc.player == null) return;
        StatusEffectInstance cur = mc.player.getStatusEffect(StatusEffects.NIGHT_VISION);
        if (cur == null || cur.getDuration() < 400) {
            // client-side only; long duration avoids the fade-out flicker
            mc.player.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 1200, 0, true, false, false));
            applied = true;
        }
    }

    @Override
    public void onDisable() {
        if (applied && mc.player != null) mc.player.removeStatusEffect(StatusEffects.NIGHT_VISION);
        applied = false;
    }
}
