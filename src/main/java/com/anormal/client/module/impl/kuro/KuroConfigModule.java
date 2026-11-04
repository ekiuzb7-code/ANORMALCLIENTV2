package com.anormal.client.module.impl.kuro;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.profiles.ProfileManager;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;

import net.minecraft.text.Text;

/**
 * kuro {@code ConfigModule} porti (Misc): uchta sozlamali "slot" konfiguratsiya
 * kategoriyasi — Anormalning {@link ProfileManager}'iga bog'lanadi. Mode
 * Action bilan bir profilda yangi qiymat yozadi: Save / Load / Delete / List;
 * "Slot" bilan nom tanlaydi (1–6; Anormal konfigatsiyada countli kutish mumkin).
 *
 * <p>Anormaldagi farqlar: kuro'daki Setting.Button tugmasi Anormal modul
 * sozlamasini yo'q — Action tanlashadi; faollashsa bir marta bajarilib, o'zida
 * o'chadi (.addButton analogi yo'q). Kuro'dagi Sidebar'ning Configs sahifası
 * bayroglari burdan yaqin bo'lgani uchun Anormal Profiles ekranı bilan
 * almashtirildi.</p>
 */
public class KuroConfigModule extends Module {
    private final ModeSetting action = new ModeSetting("Action", "Bajariladigan amal",
            "Save", "Save", "Load", "Delete", "List");
    private final NumberSetting slot = new NumberSetting("Slot", "Slot (1–6)", 1.0, 1.0, 6.0, 1.0);

    public KuroConfigModule() {
        super("Config", "Config slots: save / load / delete / list module profiles (kuro port)", Category.KURO);
        addSetting(action);
        addSetting(slot);
    }

    private void say(String msg) {
        if (mc.player != null) mc.player.sendMessage(Text.literal("§7[config] §f" + msg), false);
    }

    private String slotName() { return "slot" + slot.getValue().intValue(); }

    @Override
    public void onEnable() {
        String a = action.getValue();
        String name = slotName();
        String result;
        switch (a) {
            case "Save" -> {
                String err = ProfileManager.save(name);
                result = err == null ? name + " saqlandi" : err;
            }
            case "Load" -> {
                String err = ProfileManager.load(name);
                result = err == null ? name + " yuklandi" : err;
            }
            case "Delete" -> {
                String err = ProfileManager.delete(name);
                result = err == null ? name + " o‘chirildi" : err;
            }
            default -> {
                var l = ProfileManager.list();
                result = l.isEmpty() ? "Saqlangan profillar yo‘q." : "Profillar: " + String.join(", ", l);
            }
        }
        say(result);
        setEnabled(false);           // amal faollashdi — modul o'chadi
    }
}
