package com.anormal.client.module.impl.kuro;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.NumberSetting;

import net.minecraft.util.math.MathHelper;

import java.awt.Color;

/**
 * kuro {@code SkyModule} port: osmonni yagona rang bilan almashtiradi
 * (standart oq, kerak bo‘lsa qora yoki tanlangan ton) va bulutlarni oq qiladi.
 * Rang almashtirish Anormaldagi mixinlar (SkyRendering/CloudRenderer) bilan
 * shu moduldan so‘raladi.
 *
 * <p>Anormaldagi farqlar: kuro'daki Setting.Hue o‘rniga rang ColorSetting
 * bilan beriladi (Setting.Hue Anormalda yo‘q); "Gray sky" + "Gray brightness %"
 * sozlamalari shu uslubda saqlanadi. "Match fog" sozlamasi portlanmagan —
 * Anormalda fog masofalari NoFog moduli bilan alohida boshqariladi.</p>
 */
public class KuroSkyModule extends Module {
    private static KuroSkyModule instance;

    private final BooleanSetting gray = new BooleanSetting("Gray Sky", "Osmon oq o‘rniga qora bo‘ladi", false);
    private final NumberSetting grayLevel = new NumberSetting("Gray Brightness %", "Qoraning yorug‘ligi %", 55.0, 15.0, 95.0, 5.0);
    private final ColorSetting tint = new ColorSetting("Sky Color", "Osmonga echiladigan rang (kuro'da hue 0.6 ≈ sian)", 0xFF00FFFF);
    private final NumberSetting tintAmount = new NumberSetting("Sky Color Amount %", "Rangning qo‘shilish miqdori % (0 = qoraoq / qora)", 0.0, 0.0, 100.0, 5.0);
    private final BooleanSetting whiteClouds = new BooleanSetting("White Clouds", "Bulutlar oq bo‘ladi", true);
    private final BooleanSetting noGlow = new BooleanSetting("Hide Sunset Glow", "Quyosh chiqish/botish nurini yashiradi", true);
    private final BooleanSetting hideSunMoon = new BooleanSetting("Hide Sun & Moon", "Quyosh, Oy va yulduzlarni yashiradi (toza tekis osmon)", true);

    public KuroSkyModule() {
        super("Sky", "Replaces the sky with a flat colour (kuro port)", Category.KURO);
        instance = this;
        addSetting(gray);
        addSetting(grayLevel);
        addSetting(tint);
        addSetting(tintAmount);
        addSetting(whiteClouds);
        addSetting(noGlow);
        addSetting(hideSunMoon);
    }

    public static boolean active() { return instance != null && instance.isEnabled(); }

    public static KuroSkyModule get() { return instance; }

    /** Osmon rangi 0xRRGGBB: o‘q (yoki qora), kerak bo‘lsa tanlangan rang bilan tint'alanadi. */
    public int skyRgb() {
        float v = gray.isEnabled() ? (float) (grayLevel.getValue() / 100.0) : 1.0f;
        float s = (float) (tintAmount.getValue() / 100.0);
        int argb = tint.getValue();
        float[] hsb = new float[3];
        Color.RGBtoHSB((argb >>> 16) & 0xFF, (argb >>> 8) & 0xFF, argb & 0xFF, hsb);
        return MathHelper.hsvToRgb(hsb[0], s, v) & 0xFFFFFF;
    }

    public boolean cloudsOn() { return whiteClouds.isEnabled(); }

    public boolean noGlow() { return this.noGlow.isEnabled(); }

    /** Quyosh/Oy/yulduzlar toza tekis osmon uchun yashitiladimi. */
    public boolean hideCelestial() { return hideSunMoon.isEnabled(); }
}
