package com.anormal.client.module.impl.legit;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.theme.ThemeManager;
import com.anormal.client.util.ColorUtils;
import com.anormal.client.util.HudStyle;
import com.anormal.client.util.RenderUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Enchantments of the held item.
 *
 * <p>Modernised: one rounded panel with the item icon and name in the header, then a clean list
 * where each entry has an accent dot and the level in Roman numerals on the right.</p>
 */
public class EnchantHelper extends Module {
    public final NumberSetting posX = new NumberSetting("Pos X", "X Position on screen", 10.0, 0.0, 1920.0, 1.0);
    public final NumberSetting posY = new NumberSetting("Pos Y", "Y Position on screen", 210.0, 0.0, 1080.0, 1.0);
    public final NumberSetting scale = new NumberSetting("Scale", "HUD scale (0.05 = tiny, 2.0 = huge)", 1.0, 0.05, 2.0, 0.01);
    public final NumberSetting maxLines = new NumberSetting("Max Lines", "Max enchant lines shown", 4.0, 1.0, 8.0, 1.0);
    public final ColorSetting textColor = new ColorSetting("Text Color", "Enchant text color", ColorUtils.rgba(255, 255, 255, 255));

    public EnchantHelper() {
        super("EnchantHelper", "Lists enchantments on held item", Category.LEGIT);
        addSetting(posX);
        addSetting(posY);
        addSetting(scale);
        addSetting(maxLines);
        addSetting(textColor);
    }

    private String roman(int n) {
        if (n >= 10) return "X";
        if (n == 9) return "IX";
        if (n >= 5) return "V" + roman(n - 5);
        if (n == 4) return "IV";
        if (n >= 1) return "I" + roman(n - 1);
        return "";
    }

    /** {@code {name, level}} pairs, level already romanised. */
    private List<String[]> lines(ItemStack stack) {
        List<String[]> out = new ArrayList<>();
        try {
            ItemEnchantmentsComponent comp = stack.getEnchantments();
            if (comp == null || comp.isEmpty()) return out;
            for (net.minecraft.registry.entry.RegistryEntry<Enchantment> e : comp.getEnchantments()) {
                try {
                    String path = e.getKey().map(k -> k.getValue().getPath().replace('_', ' '))
                            .orElse("enchantment");
                    int lvl = comp.getLevel(e);
                    out.add(new String[]{
                            path.substring(0, 1).toUpperCase() + path.substring(1),
                            lvl > 0 ? roman(lvl) : ""
                    });
                } catch (Throwable ignored) {}
                if (out.size() >= maxLines.getValue().intValue()) break;
            }
        } catch (Throwable ignored) {}
        return out;
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.player == null || mc.textRenderer == null) return;
        try {
            ItemStack stack = mc.player.getMainHandStack();
            if (stack.isEmpty()) return;

            List<String[]> rows = lines(stack);
            int accent = ThemeManager.getAccentColor();
            int w = 182;
            int rowH = 13;
            int h = rows.isEmpty() ? 38 : 26 + rows.size() * rowH + 4;
            int[] at = HudStyle.place(posX.getValue().intValue(), posY.getValue().intValue(), w, h);
            int x = at[0], y = at[1];

            HudStyle.panel(context, x, y, w, h, accent);
            HudStyle.title(context, mc.textRenderer, "Enchants", x + 10, y + 7);
            String itemName = HudStyle.trim(mc.textRenderer, stack.getName().getString(), w - 78);
            int nw = mc.textRenderer.getWidth(itemName);
            RenderUtils.drawText(context, mc.textRenderer, itemName, x + w - 10 - nw, y + 7,
                    0xFFC7CBD6, false);

            if (rows.isEmpty()) {
                RenderUtils.drawText(context, mc.textRenderer, "No enchantments", x + 10, y + 22,
                        0xFF5E6472, false);
                return;
            }

            int ly = y + 24;
            for (String[] row : rows) {
                RenderUtils.fill(context, x + 10, ly + 3, x + 12, ly + 9, accent);
                String name = HudStyle.trim(mc.textRenderer, row[0], w - 62);
                RenderUtils.drawText(context, mc.textRenderer, name, x + 18, ly + 2,
                        textColor.getValue(), false);
                if (!row[1].isEmpty()) {
                    int lw = mc.textRenderer.getWidth(row[1]);
                    RenderUtils.drawText(context, mc.textRenderer, row[1], x + w - 10 - lw, ly + 2,
                            0xFFC084FC, true);
                }
                ly += rowH;
            }
        } catch (Throwable ignored) {}
    }
}
