package com.anormal.client.module.impl.legit;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.theme.ThemeManager;
import com.anormal.client.util.ColorUtils;
import com.anormal.client.util.HudStyle;
import com.anormal.client.util.RenderUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;

/**
 * Counts one item type across the inventory.
 *
 * <p>Modernised: the real item icon is drawn next to the label and the total is the big value, so
 * the card is identifiable at a glance instead of being a line of text.</p>
 */
public class ItemCounter extends Module {
    public final NumberSetting posX = new NumberSetting("Pos X", "X Position on screen", 10.0, 0.0, 1920.0, 1.0);
    public final NumberSetting posY = new NumberSetting("Pos Y", "Y Position on screen", 150.0, 0.0, 1080.0, 1.0);
    public final NumberSetting scale = new NumberSetting("Scale", "HUD scale (0.05 = tiny, 2.0 = huge)", 1.0, 0.05, 2.0, 0.01);
    public final ModeSetting itemMode = new ModeSetting("Item Mode", "Which item to count", "Held", "Held", "EnderPearl", "Totem", "GoldenApple");
    public final ColorSetting textColor = new ColorSetting("Text Color", "Counter text color", ColorUtils.rgba(255, 255, 255, 255));

    public ItemCounter() {
        super("ItemCounter", "Counts held item type across inventory", Category.LEGIT);
        addSetting(posX);
        addSetting(posY);
        addSetting(scale);
        addSetting(itemMode);
        addSetting(textColor);
    }

    private boolean matches(String path) {
        try {
            if (itemMode.is("EnderPearl")) return path.equals("ender_pearl");
            if (itemMode.is("Totem")) return path.equals("totem_of_undying");
            if (itemMode.is("GoldenApple")) return path.contains("golden_apple");
        } catch (Throwable ignored) {}
        return false;
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.player == null || mc.textRenderer == null) return;
        try {
            String label = itemMode.getValue();
            String heldPath = "";
            ItemStack icon = ItemStack.EMPTY;
            try {
                icon = mc.player.getMainHandStack();
                heldPath = Registries.ITEM.getId(icon.getItem()).getPath();
            } catch (Throwable ignored) {}
            if (itemMode.is("Held") && (heldPath.isEmpty() || icon.isEmpty())) return;

            int total = 0;
            for (int i = 0; i < 36; i++) {
                ItemStack s = mc.player.getInventory().getStack(i);
                if (s.isEmpty()) continue;
                String p = Registries.ITEM.getId(s.getItem()).getPath();
                boolean hit = itemMode.is("Held") ? p.equals(heldPath) : matches(p);
                if (hit) {
                    total += s.getCount();
                    if (icon.isEmpty() || !itemMode.is("Held")) icon = s;
                }
            }
            ItemStack off = mc.player.getOffHandStack();
            if (!off.isEmpty()) {
                String p = Registries.ITEM.getId(off.getItem()).getPath();
                if (itemMode.is("Held") ? p.equals(heldPath) : matches(p)) total += off.getCount();
            }

            if (itemMode.is("Held")) label = icon.getName().getString();

            int accent = ThemeManager.getAccentColor();
            int w = 138, h = 40;
            int[] at = HudStyle.place(posX.getValue().intValue(), posY.getValue().intValue(), w, h);
            int x = at[0], y = at[1];

            HudStyle.panel(context, x, y, w, h, accent);
            if (!icon.isEmpty()) context.drawItem(icon, x + 8, y + 11);
            HudStyle.title(context, mc.textRenderer,
                    HudStyle.trim(mc.textRenderer, label, w - 66), x + 30, y + 8);

            String value = String.valueOf(total);
            int vw = mc.textRenderer.getWidth(value);
            RenderUtils.drawText(context, mc.textRenderer, value, x + w - 10 - vw, y + 16,
                    textColor.getValue(), true);
            HudStyle.bar(context, x + 30, y + 31, w - 42, 3, total > 0 ? 1.0f : 0.0f, accent);
        } catch (Throwable ignored) {}
    }
}
