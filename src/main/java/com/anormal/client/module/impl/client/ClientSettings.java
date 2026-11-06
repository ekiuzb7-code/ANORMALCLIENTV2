package com.anormal.client.module.impl.client;

import com.anormal.client.i18n.I18n;
import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.theme.Theme;
import com.anormal.client.theme.ThemeManager;
import com.anormal.client.util.ColorUtils;

public class ClientSettings extends Module {
    /** Default language, and the one used when nothing else is selected. */
    public static final String DEFAULT_LANGUAGE = "English";

    /** Shu modulning bindi hech qachon o'chirilmasligi kerak (GUI ochish kaliti). */
    public static boolean isBindLocked(com.anormal.client.module.Module m) {
        return m instanceof ClientSettings;
    }

    /** Shu sozlama boshqa modulga ogohlantirishning bindini qulflash: faqat shu modulning Keybind'i. */
    public static boolean isKeybindLocked(com.anormal.client.setting.Setting<?> s) {
        if (s == null) return false;
        for (com.anormal.client.module.Module m : com.anormal.client.module.ModuleManager.getModules()) {
            if (m instanceof ClientSettings && m.getKeybindSetting() == s) return true;
        }
        return false;
    }

    public final ModeSetting language = new ModeSetting(
            "Language",
            "Interface language for the GUI, module list, tooltips and the action wheel",
            DEFAULT_LANGUAGE, "English", "Uzbek", "Russian");

    public final ModeSetting themeSetting = new ModeSetting("Theme", "Active GUI Design Style", "Anormal", "Anormal", "Glassmorphism", "Brutalism");
    public final ColorSetting accentColor = new ColorSetting("Accent Color", "Accent Color for menus and HUD", ColorUtils.rgba(255, 85, 0, 255));
    public final BooleanSetting blurBackground = new BooleanSetting("Blur Background", "Blurs the game world behind the GUI", true);
    public final BooleanSetting showTooltips = new BooleanSetting("Tooltips", "Shows descriptions when hovering over settings", false);
    public final NumberSetting guiScale = new NumberSetting("GUI Scale", "Scale multiplier for GUI elements", 1.1, 0.7, 1.5, 0.1);

    public ClientSettings() {
        super("ClientSettings", "Configure GUI themes, appearance and global options", Category.CLIENT, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT);
        addSetting(language);
        addSetting(themeSetting);
        addSetting(accentColor);
        addSetting(blurBackground);
        addSetting(showTooltips);
        addSetting(guiScale);
        setEnabled(true);
    }

    @Override
    public void onEnable() {
        applyLanguage();
    }

    /**
     * Bind bosilganda modul YOQILMAYDI/O'CHMAYDI — o'rniga GUI ochiladi.
     * RShift'ni almashtirib qo'ysangiz ham yangi tugma UI'ni ochadi.
     */
    @Override
    public boolean onKeyPressed() {
        try {
            if (mc.currentScreen == null) {
                mc.setScreen(new com.anormal.client.gui.ClickGuiScreen());
            } else if (mc.currentScreen instanceof com.anormal.client.gui.ClickGuiScreen gui) {
                mc.currentScreen.close();
            }
        } catch (Throwable ignored) {
        }
        return true;
    }

    /** Pushes the selected mode value into the translation layer. */
    private void applyLanguage() {
        I18n.setLangById(language.getValue());
    }

    @Override
    public void onTick() {
        applyLanguage();

        if (themeSetting.is("Anormal")) {
            ThemeManager.setActiveTheme(Theme.VAPE_V4);
            ThemeManager.setCustomAccentColor(accentColor.getValue());
        } else if (themeSetting.is("Brutalism")) {
            ThemeManager.setActiveTheme(Theme.BRUTALISM);
            ThemeManager.setCustomAccentColor(accentColor.getValue());
        } else {
            ThemeManager.setActiveTheme(Theme.GLASSMORPHISM);
        }
    }
}
