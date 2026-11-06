package com.anormal.client.module.impl.spectre;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.module.ModuleManager;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.util.ColorUtils;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Spectre port: "Hotkeys" — lists the modules you have bound to a key, and their keys.
 *
 * <p>Spectre draws the list through its HUD overlay provider; this port draws it in the
 * Anormal HUD pass, sorted by name and filtered to bound, enabled modules.</p>
 */
public final class SpectreHotkeys extends Module {
    public final BooleanSetting showUnbound = addSetting(new BooleanSetting("Show Unbound", "Also list modules without a key", false));
    public final BooleanSetting shadow = addSetting(new BooleanSetting("Shadow", "Draw the text with a shadow", true));
    public final ColorSetting textColor = addSetting(new ColorSetting("Text Color", "List text colour", ColorUtils.rgba(255, 255, 255, 255)));

    public SpectreHotkeys() {
        super("SpectreHotkeys", "Spectre port: lists the modules you have bound to a key, and their keys.", Category.SPECTRE);
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.player == null || mc.options.hudHidden) return;

        List<Module> bound = new ArrayList<>();
        for (Module module : ModuleManager.getModules()) {
            if (module == this) continue;
            if (!showUnbound.isEnabled() && module.getKey() <= 0) continue;
            if (!module.isEnabled()) continue;
            bound.add(module);
        }
        if (bound.isEmpty()) return;
        bound.sort(Comparator.comparing(Module::getName));

        int x = 4;
        int y = 4;
        int line = 10;
        int color = textColor.getColor();
        for (Module module : bound) {
            String key = module.getKey() > 0 ? keyName(module.getKey()) : "-";
            String text = module.getName() + "  [" + key + "]";
            context.drawText(mc.textRenderer, text, x, y, color, shadow.isEnabled());
            y += line;
        }
    }

    private static String keyName(int key) {
        if (key <= 0) return "-";
        String name = org.lwjgl.glfw.GLFW.glfwGetKeyName(key, 0);
        if (name != null) return name.toUpperCase();
        return switch (key) {
            case 32 -> "SPACE";
            case 256 -> "ESC";
            case 257 -> "ENTER";
            case 258 -> "TAB";
            case 259 -> "BACKSPACE";
            case 340 -> "LSHIFT";
            case 344 -> "RSHIFT";
            case 341 -> "LCTRL";
            case 345 -> "RCTRL";
            case 342 -> "LALT";
            case 346 -> "RALT";
            default -> "KEY" + key;
        };
    }
}
