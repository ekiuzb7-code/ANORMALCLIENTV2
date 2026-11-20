package com.anormal.client.module.impl.legit;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.ColorUtils;
import com.anormal.client.util.HudStyle;
import com.anormal.client.util.RenderUtils;
import net.minecraft.client.gui.DrawContext;

/**
 * Hunger + saturation readout.
 *
 * <p>Modernised into two meters with live values: hunger is coloured by how safe it is, and
 * saturation gets its own bar instead of being a number inside a sentence.</p>
 */
public class FoodStatus extends Module {
    public final NumberSetting posX = new NumberSetting("Pos X", "X Position on screen", 10.0, 0.0, 1920.0, 1.0);
    public final NumberSetting posY = new NumberSetting("Pos Y", "Y Position on screen", 120.0, 0.0, 1080.0, 1.0);
    public final NumberSetting scale = new NumberSetting("Scale", "HUD scale (0.05 = tiny, 2.0 = huge)", 1.0, 0.05, 2.0, 0.01);
    public final ColorSetting textColor = new ColorSetting("Text Color", "Hunger text color", ColorUtils.rgba(255, 255, 255, 255));

    public FoodStatus() {
        super("FoodStatus", "Shows hunger and saturation levels", Category.LEGIT);
        addSetting(posX);
        addSetting(posY);
        addSetting(scale);
        addSetting(textColor);
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.player == null || mc.textRenderer == null) return;
        try {
            int food = mc.player.getHungerManager().getFoodLevel();
            float sat = 0.0f;
            try {
                sat = mc.player.getHungerManager().getSaturationLevel();
            } catch (Throwable ignored) {}

            int foodColor = food > 12 ? 0xFF4ADE80 : (food > 6 ? 0xFFFBBF24 : 0xFFF87171);
            int accent = 0xFFFBBF24;

            int w = 158;
            int h = 48;
            int[] at = HudStyle.place(posX.getValue().intValue(), posY.getValue().intValue(), w, h);
            int x = at[0], y = at[1];

            HudStyle.panel(context, x, y, w, h, accent);
            HudStyle.title(context, mc.textRenderer, "Hunger", x + 10, y + 7);
            String foodText = food + "/20";
            int fw = mc.textRenderer.getWidth(foodText);
            RenderUtils.drawText(context, mc.textRenderer, foodText, x + w - 10 - fw, y + 7,
                    textColor.getValue() == 0xFFFFFFFF ? foodColor : textColor.getValue(), true);
            HudStyle.bar(context, x + 10, y + 19, w - 20, 4, food / 20.0f, foodColor);

            HudStyle.title(context, mc.textRenderer, "Saturation", x + 10, y + 28);
            String satText = String.format(java.util.Locale.ROOT, "%.1f", sat);
            int sw = mc.textRenderer.getWidth(satText);
            RenderUtils.drawText(context, mc.textRenderer, satText, x + w - 10 - sw, y + 28,
                    0xFFC7CBD6, false);
            HudStyle.bar(context, x + 10, y + 40, w - 20, 3,
                    Math.max(0.0f, Math.min(1.0f, sat / 20.0f)), 0xFF60A5FA);
        } catch (Throwable ignored) {}
    }
}
