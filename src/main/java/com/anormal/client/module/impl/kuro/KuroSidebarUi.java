package com.anormal.client.module.impl.kuro;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;

/**
 * kuro {@code SidebarUi} porti (Render): menyuda uslub bayroqsi — draggalaydigan
 * derazalar o'rniga yon panel menyu (chapda toifa, o'ngda modluar bilan yozuv
 * va terminal).
 *
 * <p>Anormaldagi farqlar: bu bayroqlar Anormal clickni (KURo'dagi ModuleManager)
 * shu style'ni oladi (kuro'dagi ModuleManager#onTick). Anormalda ushbu menyular
 * yo'q, shu sababli modul flag-api va sozlamalarni saqlaydi; kuni'ning
 * Anormal clickgui o'z yongan uslubiga kirmaydi.</p>
 */
public class KuroSidebarUi extends Module {
    private static KuroSidebarUi instance;
    private final BooleanSetting descriptions = new BooleanSetting("Descriptions", "Modul yozuvlari ko'rsate", true);
    private final BooleanSetting counts = new BooleanSetting("Category counts", "Toifa soni ko'rsate", true);

    public KuroSidebarUi() {
        super("Sidebar UI", "Menu style switch: the sidebar menu (kuro port flag)", Category.KURO);
        instance = this;
        addSetting(descriptions);
        addSetting(counts);
    }

    public static boolean active() { return instance != null && instance.isEnabled(); }
    public static boolean descriptions() { return instance == null || instance.descriptions.isEnabled(); }
    public static boolean counts() { return instance == null || instance.counts.isEnabled(); }
}
