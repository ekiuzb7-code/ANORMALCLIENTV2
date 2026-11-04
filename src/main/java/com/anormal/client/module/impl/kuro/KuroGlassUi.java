package com.anormal.client.module.impl.kuro;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;

/**
 * kuro {@code GlassUi} porti (Render): menyuda uslub bayrog'i — "Glass" menyusi
 * (frosted glass panel + shaderlar + bahor animatsiyalari). Kuro'da bardiq sayvan UI temasi
 * (HUD > UI theme) bolgan; Anormalda buzilmas nardi koplik kornya emas, shu
 * sababli faqat flag-api va sozlamalarni sa qlaydi.
 */
public class KuroGlassUi extends Module {
    private static KuroGlassUi instance;
    private static boolean classicRequested;

    private final NumberSetting opacity = new NumberSetting("Opacity %", "Panel xohirlik", 100.0, 20.0, 100.0, 5.0);
    private final BooleanSetting descriptions = new BooleanSetting("Descriptions", "Modul yozuvlari ko'rsate", true);
    private final BooleanSetting cursorLight = new BooleanSetting("Cursor light", "Kursor yot uslubi", true);
    private final NumberSetting speed = new NumberSetting("Animation speed", "Animatsiya tezlik", 1.0, 0.5, 2.0, 0.1);

    public KuroGlassUi() {
        super("Glass UI", "Menu style switch: the glass menu (kuro port flag)", Category.KURO);
        instance = this;
        addSetting(opacity);
        addSetting(descriptions);
        addSetting(cursorLight);
        addSetting(speed);
    }

    public static boolean active() { return instance != null && instance.isEnabled(); }

    /** UI ko'rinuvchi: 1 = normal ko'rsatish, past bo'lsa qopqoqning noiga tushgan bunday ko'rsatadi. */
    public static float opacity() { return instance == null ? 1f : (float) (instance.opacity.getValue() / 100.0); }

    public static boolean descriptions() { return instance == null || instance.descriptions.isEnabled(); }
    public static boolean cursorLight() { return instance == null || instance.cursorLight.isEnabled(); }
    public static float speed() { return instance == null ? 1f : (float) (double) instance.speed.getValue(); }

    /** "Classic UI" tanlangan: ochiq kichik ko'rinishi menyu, faqat yon qismida work — apmon port funtsiyada uchayotkanda xuddi menyu bilan shu tarzdan yo'q bor. */
    public static void requestClassic() { classicRequested = active(); }

    public static boolean consumeClassicRequest() { boolean r = classicRequested; classicRequested = false; return r; }

    /** Glass menyudan kelib chiqish (ModuleManager keyingi tickda moselay kichik menyuga almashtiradi). */
    public static void leave() {
        if (instance != null) instance.setEnabled(false);
    }
}
