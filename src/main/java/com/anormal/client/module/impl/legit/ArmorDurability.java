package com.anormal.client.module.impl.legit;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.theme.ThemeManager;
import com.anormal.client.util.ColorUtils;
import com.anormal.client.util.HudStyle;
import com.anormal.client.util.RenderUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;

/**
 * Your own armour durability, one row per worn piece.
 *
 * <p>Modernised: icons, colour-coded meters and percentages in a single rounded panel instead of
 * four floating one-line boxes.</p>
 */
public class ArmorDurability extends Module {
    public final NumberSetting posX = new NumberSetting("Pos X", "X Position on screen", 10.0, 0.0, 1920.0, 1.0);
    public final NumberSetting posY = new NumberSetting("Pos Y", "Y Position on screen", 90.0, 0.0, 1080.0, 1.0);
    public final NumberSetting scale = new NumberSetting("Scale", "HUD scale (0.05 = tiny, 2.0 = huge)", 1.0, 0.05, 2.0, 0.01);
    public final BooleanSetting showPercent = new BooleanSetting("Show Percent", "Append durability percent per piece", true);
    public final ColorSetting textColor = new ColorSetting("Text Color", "Durability text color", ColorUtils.rgba(255, 255, 255, 255));

    private static final EquipmentSlot[] SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    public ArmorDurability() {
        super("ArmorDurability", "Lists armor pieces durability values", Category.LEGIT);
        addSetting(posX);
        addSetting(posY);
        addSetting(scale);
        addSetting(showPercent);
        addSetting(textColor);
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.player == null || mc.textRenderer == null) return;
        try {
            int worn = 0;
            for (EquipmentSlot slot : SLOTS) {
                ItemStack s = mc.player.getEquippedStack(slot);
                if (s != null && !s.isEmpty()) worn++;
            }
            if (worn == 0) return;

            int accent = ThemeManager.getAccentColor();
            int w = 176;
            int rowH = 20;
            int h = 24 + worn * rowH + 6;
            int[] at = HudStyle.place(posX.getValue().intValue(), posY.getValue().intValue(), w, h);
            int x = at[0], y = at[1];

            HudStyle.panel(context, x, y, w, h, accent);
            HudStyle.title(context, mc.textRenderer, "Armor", x + 10, y + 8);
            String count = worn + "/4";
            int cw = mc.textRenderer.getWidth(count);
            RenderUtils.drawText(context, mc.textRenderer, count, x + w - 10 - cw, y + 8,
                    0xFF868A99, true);

            int ly = y + 24;
            for (EquipmentSlot slot : SLOTS) {
                ItemStack stack = mc.player.getEquippedStack(slot);
                if (stack == null || stack.isEmpty()) continue;
                context.drawItem(stack, x + 8, ly);

                int pct = 100;
                if (stack.isDamageable()) {
                    int max = Math.max(1, stack.getMaxDamage());
                    pct = (int) Math.round((double) (max - stack.getDamage()) / max * 100.0);
                    pct = Math.max(0, Math.min(100, pct));
                }
                int barColor = HudStyle.durabilityColor(pct);
                String name = HudStyle.trim(mc.textRenderer, stack.getName().getString(), 92);
                RenderUtils.drawText(context, mc.textRenderer, name, x + 30, ly + 3, 0xFFC7CBD6, false);

                if (showPercent.isEnabled()) {
                    String pctText = pct + "%";
                    int pw = mc.textRenderer.getWidth(pctText);
                    RenderUtils.drawText(context, mc.textRenderer, pctText, x + w - 10 - pw,
                            ly + 3, barColor, true);
                }
                HudStyle.bar(context, x + 30, ly + 14, w - 44, 3, pct / 100.0f, barColor);
                ly += rowH;
            }
        } catch (Throwable ignored) {}
    }
}
