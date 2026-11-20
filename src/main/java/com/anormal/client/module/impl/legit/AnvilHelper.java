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
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.AnvilScreenHandler;

/**
 * Anvil repair-cost readout.
 *
 * <p>Modernised from a bare text box into the shared HUD panel: item icon, item name, the cost as
 * the big value, a meter that fills as the cost approaches the "Too Expensive" limit and a colour
 * that turns amber/red with it.</p>
 */
public class AnvilHelper extends Module {
    public final NumberSetting posX = new NumberSetting("Pos X", "X Position on screen", 10.0, 0.0, 1920.0, 1.0);
    public final NumberSetting posY = new NumberSetting("Pos Y", "Y Position on screen", 240.0, 0.0, 1080.0, 1.0);
    public final NumberSetting scale = new NumberSetting("Scale", "HUD scale (0.05 = tiny, 2.0 = huge)", 1.0, 0.05, 2.0, 0.01);
    public final ColorSetting textColor = new ColorSetting("Text Color", "Anvil text color", ColorUtils.rgba(255, 255, 255, 255));

    /** Vanilla anvil refuses the operation at 40 levels. */
    private static final int MAX_COST = 40;

    public AnvilHelper() {
        super("AnvilHelper", "Shows anvil XP cost for held item", Category.LEGIT);
        addSetting(posX);
        addSetting(posY);
        addSetting(scale);
        addSetting(textColor);
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.player == null || mc.textRenderer == null) return;
        try {
            if (!(mc.player.currentScreenHandler instanceof AnvilScreenHandler)) return;
            ItemStack stack = mc.player.getMainHandStack();
            if (stack.isEmpty()) return;

            int cost;
            boolean known = true;
            try {
                Integer stored = stack.get(DataComponentTypes.REPAIR_COST);
                cost = stored == null ? 0 : stored;
            } catch (Throwable t) {
                cost = 0;
                known = false;
            }

            int accent = ThemeManager.getAccentColor();
            int costColor = cost >= MAX_COST ? 0xFFF87171 : (cost >= MAX_COST / 2 ? 0xFFFBBF24 : 0xFF4ADE80);

            int w = 196, h = 44;
            int[] at = HudStyle.place(posX.getValue().intValue(), posY.getValue().intValue(), w, h);
            int x = at[0], y = at[1];

            HudStyle.panel(context, x, y, w, h, accent);
            HudStyle.title(context, mc.textRenderer, "Anvil", x + 10, y + 7);
            String state = cost >= MAX_COST ? "too expensive" : "cost";
            int sw = mc.textRenderer.getWidth(state);
            RenderUtils.drawText(context, mc.textRenderer, state, x + w - 10 - sw, y + 7,
                    cost >= MAX_COST ? 0xFFF87171 : 0xFF868A99, false);

            context.drawItem(stack, x + 8, y + 20);
            String name = HudStyle.trim(mc.textRenderer, stack.getName().getString(), w - 88);
            RenderUtils.drawText(context, mc.textRenderer, name, x + 30, y + 22,
                    textColor.getValue(), false);

            String value = known ? cost + " XP" : "?";
            int vw = mc.textRenderer.getWidth(value);
            RenderUtils.drawText(context, mc.textRenderer, value, x + w - 10 - vw, y + 21,
                    costColor, true);

            HudStyle.bar(context, x + 10, y + h - 9, w - 20, 3,
                    Math.min(1.0f, cost / (float) MAX_COST), costColor);
        } catch (Throwable ignored) {}
    }
}
