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

/**
 * Compass facing readout.
 *
 * <p>Modernised: the cardinal letter and full name sit next to each other, the yaw is on the right,
 * and a compact compass strip underneath shows where you are facing across all 360 degrees.</p>
 */
public class DirectionHUD extends Module {
    public final NumberSetting posX = new NumberSetting("Pos X", "X Position on screen", 960.0, 0.0, 1920.0, 1.0);
    public final NumberSetting posY = new NumberSetting("Pos Y", "Y Position on screen", 40.0, 0.0, 1080.0, 1.0);
    public final NumberSetting scale = new NumberSetting("Scale", "HUD scale (0.05 = tiny, 2.0 = huge)", 1.0, 0.05, 2.0, 0.01);
    public final BooleanSetting showDegrees = new BooleanSetting("Show Degrees", "Append yaw degrees", true);
    public final ColorSetting textColor = new ColorSetting("Text Color", "Direction text color", ColorUtils.rgba(255, 220, 0, 255));

    public DirectionHUD() {
        super("DirectionHUD", "Big compass facing letters display", Category.LEGIT);
        addSetting(posX);
        addSetting(posY);
        addSetting(scale);
        addSetting(showDegrees);
        addSetting(textColor);
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.player == null || mc.textRenderer == null) return;
        try {
            float yaw = (mc.player.getYaw() % 360 + 360) % 360;
            // Minecraft yaw: 0 = south, 90 = west, 180 = north, 270 = east.
            String dir = yaw >= 315 || yaw < 45 ? "S" : (yaw >= 45 && yaw < 135 ? "W"
                    : (yaw >= 135 && yaw < 225 ? "N" : "E"));
            String full = dir.equals("N") ? "North" : dir.equals("S") ? "South"
                    : dir.equals("E") ? "East" : "West";
            int dirColor = textColor.getValue();
            int accent = ThemeManager.getAccentColor();

            int w = 176, h = 46;
            int[] at = HudStyle.place(posX.getValue().intValue(), posY.getValue().intValue(), w, h);
            int x = at[0], y = at[1];

            HudStyle.panel(context, x, y, w, h, accent);
            HudStyle.title(context, mc.textRenderer, "Facing", x + 10, y + 7);

            RenderUtils.drawText(context, mc.textRenderer, dir, x + 10, y + 18, dirColor, true);
            RenderUtils.drawText(context, mc.textRenderer, full, x + 26, y + 18, 0xFFC7CBD6, false);

            if (showDegrees.isEnabled()) {
                String deg = Math.round(yaw) + "\u00b0";
                int dw = mc.textRenderer.getWidth(deg);
                RenderUtils.drawText(context, mc.textRenderer, deg, x + w - 10 - dw, y + 18,
                        0xFF868A99, false);
            }

            // Compass strip: four ticks (S/W/N/E) plus a marker at the current yaw.
            int sx = x + 10;
            int sy = y + h - 12;
            int sw = w - 20;
            RenderUtils.fill(context, sx, sy + 2, sx + sw, sy + 3, 0x30FFFFFF);
            for (int i = 0; i < 4; i++) {
                int tx = sx + (int) (sw * (i / 4.0));
                int tickColor = (i == 0 || i == 2) ? 0xFF9AA0AE : 0x669AA0AE;
                RenderUtils.fill(context, tx, sy, tx + 1, sy + 5, tickColor);
            }
            for (int i = 0; i <= 8; i++) {
                int tx = sx + (int) (sw * (i / 8.0));
                RenderUtils.fill(context, tx, sy + 2, tx + 1, sy + 4, 0x33FFFFFF);
            }
            int mx = sx + (int) (sw * (yaw / 360.0f));
            RenderUtils.fill(context, mx - 1, sy - 1, mx + 2, sy + 6, dirColor);
        } catch (Throwable ignored) {}
    }
}
