package com.anormal.client.module;

import com.anormal.client.i18n.I18n;

public enum Category {
    COMBAT("Combat", "⚔"),
    MOVEMENT("Movement", "⚡"),
    RENDER("Render", "👁"),
    PLAYER("Player", "👤"),
    WORLD("World", "🌍"),
    INVENTORY("Inventory", "🎒"),
    OPTIMIZATION("Optimization", "🚀"),
    LEGIT("Legit HUD", "🛡"),
    CLIENT("Client", "⚙"),
    MISC("Misc", "✦"),
    /**
     * Ported-source tabs: selecting these shows modules imported from the
     * Spectre / Kuro source trees. Hidden from the sidebar until the ▼
     * source-view toggle (bottom of the category sidebar) is enabled.
     */
    SPECTRE("Spectre", "👻"),
    KURO("Kuro", "🌑"),
    /**
     * Not a module list - selecting this tab shows the author credits instead.
     * The GUI checks for this constant explicitly.
     */
    DEVELOPER("Developer", "★"),
    /**
     * Not a module list either - shows every module that currently carries a keybind, with the
     * bound key, a clear button and a global "Unbind All".
     */
    KEYBINDS("Keybinds", "⌨");

    private final String name;
    private final String icon;

    Category(String name, String icon) {
        this.name = name;
        this.icon = icon;
    }

    /** English name, used as the translation key. */
    public String getRawName() {
        return name;
    }

    /** Translated display name. */
    public String getName() {
        return I18n.tr(name);
    }

    public String getIcon() {
        return icon;
    }

    /** True when this tab shows the credits panel instead of modules. */
    public boolean isInfoOnly() {
        return this == DEVELOPER;
    }

    /** True when this tab is drawn by its own panel instead of the module list. */
    public boolean isCustomPanel() {
        return this == DEVELOPER || this == KEYBINDS;
    }
}
