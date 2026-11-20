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

    /**
     * The one key that opens and closes the GUI: this module's bind. The native ClickGUI keybinding
     * was removed because it kept opening the GUI on its own default (Right Shift) after the bind
     * was moved to another key.
     */
    public static int menuKey() {
        try {
            for (com.anormal.client.module.Module m : com.anormal.client.module.ModuleManager.getModules()) {
                if (m instanceof ClientSettings) return m.getKey();
            }
        } catch (Throwable ignored) {
        }
        return org.lwjgl.glfw.GLFW.GLFW_KEY_UNKNOWN;
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

    public final ModeSetting themeSetting = new ModeSetting("Theme", "Active GUI Design Style", "Glassmorphism", "Anormal", "Frozen", "Glassmorphism", "Brutalism");
    public final ModeSetting layoutType = new ModeSetting("Type", "ClickGUI layout: Anormal classic frame or Vape frames", "Vape", "Anormal", "Vape");
    public final ColorSetting accentColor = new ColorSetting("Accent Color", "Accent Color for menus and HUD", ColorUtils.rgba(255, 85, 0, 255));
    public final BooleanSetting blurBackground = new BooleanSetting("Blur Background", "Blurs the game world behind the GUI", false);
    public final BooleanSetting rememberPosition = new BooleanSetting("Remember Position",
            "Reopens the GUI at the section and module you closed it on", true);
    public final BooleanSetting showTooltips = new BooleanSetting("Tooltips", "Shows descriptions when hovering over settings", false);
    public final NumberSetting guiScale = new NumberSetting("GUI Scale", "Scale multiplier for GUI elements", 1.1, 0.7, 1.5, 0.1);

    public ClientSettings() {
        super("ClientSettings", "Configure GUI themes, appearance and global options", Category.CLIENT, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT);
        addSetting(language);
        addSetting(themeSetting);
        addSetting(layoutType);
        addSetting(accentColor);
        addSetting(blurBackground);
        addSetting(rememberPosition);
        addSetting(showTooltips);
        addSetting(guiScale);
        setEnabled(true);
    }

    @Override
    public void onEnable() {
        applyLanguage();
        applyTheme();
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

        // The theme lives in ThemeManager (static), so it has to be pushed every tick: profile
        // loads and the persisted choice both arrive after construction.
        applyTheme();

    }

    /** Resolves the selected mode name into the static {@link ThemeManager} state. */
    public void applyTheme() {
        // Older configs stored "Vape" as a theme: keep its frame layout on the Anormal palette.
        if (themeSetting.is("Vape")) {
            ThemeManager.setActiveTheme(Theme.VAPE);
            ThemeManager.setVapeLayout(true);
            return;
        }
        ThemeManager.setVapeLayout(layoutType.is("Vape"));
        if (themeSetting.is("Brutalism")) {
            ThemeManager.setActiveTheme(Theme.BRUTALISM);
            ThemeManager.setCustomAccentColor(accentColor.getValue());
        } else if (themeSetting.is("Frozen")) {
            ThemeManager.setActiveTheme(Theme.FROZEN);
        } else if (themeSetting.is("Glassmorphism")) {
            ThemeManager.setActiveTheme(Theme.GLASSMORPHISM);
        } else {
            ThemeManager.setActiveTheme(Theme.VAPE_V4);
            ThemeManager.setCustomAccentColor(accentColor.getValue());
        }
    }
}
