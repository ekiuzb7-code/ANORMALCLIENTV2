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
import net.minecraft.item.ItemStack;

/**
 * Durability of whatever is in your main hand.
 *
 * <p>Modernised: the item icon identifies the tool, the percentage is the big value, the exact
 * durability sits next to it and the meter is colour-coded (green / amber / red).</p>
 */
public class ToolDurability extends Module {
    public final NumberSetting posX = new NumberSetting("Pos X", "X Position on screen", 10.0, 0.0, 1920.0, 1.0);
    public final NumberSetting posY = new NumberSetting("Pos Y", "Y Position on screen", 60.0, 0.0, 1080.0, 1.0);
    public final NumberSetting scale = new NumberSetting("Scale", "HUD scale (0.05 = tiny, 2.0 = huge)", 1.0, 0.05, 2.0, 0.01);
    public final BooleanSetting showBar = new BooleanSetting("Show Bar", "Draw durability bar under text", true);
    public final BooleanSetting showPercent = new BooleanSetting("Show Percent", "Append durability percent", true);
    public final ColorSetting textColor = new ColorSetting("Text Color", "Durability text color", ColorUtils.rgba(255, 255, 255, 255));

    public ToolDurability() {
        super("ToolDurability", "Shows held tool durability number and bar", Category.LEGIT);
        addSetting(posX);
        addSetting(posY);
        addSetting(scale);
        addSetting(showBar);
        addSetting(showPercent);
        addSetting(textColor);
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.player == null || mc.textRenderer == null) return;
        try {
            ItemStack stack = mc.player.getMainHandStack();
            if (stack.isEmpty() || !stack.isDamageable()) return;

            int max = Math.max(1, stack.getMaxDamage());
            int left = Math.max(0, max - stack.getDamage());
            int pct = (int) Math.round((double) left / max * 100.0);
            int barColor = HudStyle.durabilityColor(pct);
            int accent = ThemeManager.getAccentColor();

            int w = 168;
            int h = showBar.isEnabled() ? 42 : 32;
            int[] at = HudStyle.place(posX.getValue().intValue(), posY.getValue().intValue(), w, h);
            int x = at[0], y = at[1];

            HudStyle.panel(context, x, y, w, h, accent);
            context.drawItem(stack, x + 8, y + 9);

            String name = HudStyle.trim(mc.textRenderer, stack.getName().getString(), w - 84);
            RenderUtils.drawText(context, mc.textRenderer, name, x + 30, y + 6, 0xFFC7CBD6, false);
            RenderUtils.drawText(context, mc.textRenderer, left + "/" + max, x + 30,
                    y + 17, 0xFF6E7482, false);

            if (showPercent.isEnabled()) {
                String pctText = pct + "%";
                int pw = mc.textRenderer.getWidth(pctText);
                RenderUtils.drawText(context, mc.textRenderer, pctText, x + w - 10 - pw, y + 10,
                        textColor.getValue() == 0xFFFFFFFF ? barColor : textColor.getValue(), true);
            }

            if (showBar.isEnabled()) {
                HudStyle.bar(context, x + 10, y + h - 10, w - 20, 3, pct / 100.0f, barColor);
            }
        } catch (Throwable ignored) {}
    }
}
