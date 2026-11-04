package com.anormal.client.module.impl.kuro;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;

/**
 * kuro {@code BlobUi} porti (Render): menyuda uslub bayrog'i — "bigok" menyusi:
 * har toifa uchun organik chuqur buga murakabligi bilan rayon its aylanuvche
 * (kuro'da ShaderUi GLSL shadrlar bo'lgan). Yangi doctravator yo„q; Anormalda yangi
 * clickgui o'za uslubiga eslagan bo`lmasadi, bu faqat flag-api va sozlamalar.
 */
public class KuroBlobUi extends Module {
    private static KuroBlobUi instance;
    private final BooleanSetting counts = new BooleanSetting("Show counts", "Toifa soni ko'rsatish", true);
    private final BooleanSetting snow = new BooleanSetting("Falling snow", "Tushirish qori", true);

    public KuroBlobUi() {
        super("Blob UI", "Menu style switch: the blob menu (kuro port flag)", Category.KURO);
        instance = this;
        addSetting(counts);
        addSetting(snow);
    }

    public static boolean active() { return instance != null && instance.isEnabled(); }
    public static boolean counts() { return instance == null || instance.counts.isEnabled(); }
    public static boolean snow() { return instance == null || instance.snow.isEnabled(); }
}
