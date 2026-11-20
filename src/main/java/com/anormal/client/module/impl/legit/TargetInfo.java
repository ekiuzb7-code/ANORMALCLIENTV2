package com.anormal.client.module.impl.legit;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.theme.ThemeManager;
import com.anormal.client.util.HudStyle;
import com.anormal.client.util.RenderUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.LivingEntity;

/**
 * Info panel for the entity under the crosshair.
 *
 * <p>Modernised: name header, big health value, a health bar that also shows absorption in a second
 * colour, and a footer row with distance and armour points.</p>
 */
public class TargetInfo extends Module {
    public final NumberSetting posX = new NumberSetting("Pos X", "X Position on screen", 500.0, 0.0, 1920.0, 1.0);
    public final NumberSetting posY = new NumberSetting("Pos Y", "Y Position on screen", 300.0, 0.0, 1080.0, 1.0);
    public final NumberSetting scale = new NumberSetting("Scale", "Target info scale (0.05 = tiny, 2.0 = huge)", 1.0, 0.05, 2.0, 0.01);

    public TargetInfo() {
        super("TargetInfo", "Displays target player health, name and armor on HUD", Category.LEGIT);
        addSetting(posX);
        addSetting(posY);
        addSetting(scale);
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.player == null || mc.textRenderer == null) return;
        try {
            if (!(mc.targetedEntity instanceof LivingEntity target) || !target.isAlive()) return;

            float sc = Math.max(0.5f, Math.min(2.0f, scale.getValue().floatValue()));
            int w = (int) (196 * sc);
            int h = (int) (56 * sc);
            int[] at = HudStyle.place(posX.getValue().intValue(), posY.getValue().intValue(), w, h);
            int x = at[0], y = at[1];

            float maxHp = Math.max(1.0f, target.getMaxHealth());
            float hp = Math.max(0.0f, target.getHealth());
            float absorption = Math.max(0.0f, target.getAbsorptionAmount());
            float frac = Math.min(1.0f, hp / maxHp);
            int hpColor = HudStyle.durabilityColor(Math.round(frac * 100));

            int accent = ThemeManager.getAccentColor();
            HudStyle.panel(context, x, y, w, h, accent);

            String name = com.anormal.client.module.impl.client.NameProtect.replaceName(target);
            RenderUtils.drawText(context, mc.textRenderer,
                    HudStyle.trim(mc.textRenderer, name, w - 70), x + 10, y + 7, 0xFFEDEFF5, true);

            String hpText = String.format(java.util.Locale.ROOT, "%.1f", hp);
            int hw = mc.textRenderer.getWidth(hpText);
            RenderUtils.drawText(context, mc.textRenderer, hpText, x + w - 10 - hw, y + 6,
                    hpColor, true);

            // Health track; absorption is drawn on top in gold where it overlaps.
            int barY = y + 22;
            int barW = w - 20;
            RenderUtils.fillRounded(context, x + 10, barY, barW, 5, 2, 0x33FFFFFF);
            int fill = (int) (barW * frac);
            if (fill > 0) {
                RenderUtils.fillRounded(context, x + 10, barY, Math.min(barW, fill), 5, 2, hpColor);
            }
            if (absorption > 0) {
                int absW = (int) (barW * Math.min(1.0f, absorption / maxHp));
                RenderUtils.fillRounded(context, x + 10, barY, Math.min(barW, absW), 2, 1, 0xFFFBBF24);
            }

            String hpLine = String.format(java.util.Locale.ROOT, "%.1f / %.1f hp", hp, maxHp);
            RenderUtils.drawText(context, mc.textRenderer, hpLine, x + 10, y + 31,
                    0xFF868A99, false);

            int armor = 0;
            try {
                armor = target.getArmor();
            } catch (Throwable ignored) {}
            String footer = "dist " + (int) mc.player.distanceTo(target) + "m  ·  armor " + armor;
            RenderUtils.drawText(context, mc.textRenderer, footer, x + 10, y + 43,
                    0xFF6E7482, false);
        } catch (Throwable ignored) {}
    }
}
