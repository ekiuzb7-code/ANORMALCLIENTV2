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
 * Player coordinates.
 *
 * <p>Modernised into labelled X / Y / Z columns with the facing direction in the header and an
 * optional biome footer, instead of a single long sentence in a box.</p>
 */
public class Coords extends Module {
    public final NumberSetting posX = new NumberSetting("Pos X", "X Position on screen", 10.0, 0.0, 1920.0, 1.0);
    public final NumberSetting posY = new NumberSetting("Pos Y", "Y Position on screen", 965.0, 0.0, 1080.0, 1.0);
    public final NumberSetting scale = new NumberSetting("Scale", "HUD scale (0.05 = tiny, 2.0 = huge)", 1.0, 0.05, 2.0, 0.01);
    public final BooleanSetting showDirection = new BooleanSetting("Direction", "Displays facing direction", true);
    public final BooleanSetting showBiome = new BooleanSetting("Biome", "Displays current biome name", false);
    public final ColorSetting textColor = new ColorSetting("Text Color", "Coordinates text color", ColorUtils.rgba(255, 255, 255, 255));

    public Coords() {
        super("Coords", "Displays player coordinates and direction on HUD", Category.LEGIT);
        addSetting(posX);
        addSetting(posY);
        addSetting(scale);
        addSetting(showDirection);
        addSetting(showBiome);
        addSetting(textColor);
    }

    private String biome() {
        try {
            return mc.world.getBiome(mc.player.getBlockPos()).getKey()
                    .map(k -> k.getValue().getPath().replace('_', ' '))
                    .orElse("unknown");
        } catch (Throwable ignored) {
            return "unknown";
        }
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.player == null || mc.textRenderer == null) return;
        try {
            int w = 204;
            int h = showBiome.isEnabled() ? 58 : 46;
            int[] at = HudStyle.place(posX.getValue().intValue(), posY.getValue().intValue(), w, h);
            int x = at[0], y = at[1];

            int accent = ThemeManager.getAccentColor();
            HudStyle.panel(context, x, y, w, h, accent);
            HudStyle.title(context, mc.textRenderer, "Position", x + 10, y + 7);

            if (showDirection.isEnabled()) {
                String dir = mc.player.getHorizontalFacing().asString().toUpperCase(java.util.Locale.ROOT);
                int dw = mc.textRenderer.getWidth(dir);
                RenderUtils.drawText(context, mc.textRenderer, dir, x + w - 10 - dw, y + 7,
                        accent, true);
            }

            int colW = (w - 20) / 3;
            String[] labels = {"X", "Y", "Z"};
            String[] values = {
                    String.valueOf(Math.round(mc.player.getX())),
                    String.valueOf(Math.round(mc.player.getY())),
                    String.valueOf(Math.round(mc.player.getZ()))
            };
            for (int i = 0; i < 3; i++) {
                int cx = x + 10 + i * colW;
                if (i > 0) RenderUtils.fill(context, cx - 6, y + 19, cx - 5, y + 33, 0x22FFFFFF);
                RenderUtils.drawText(context, mc.textRenderer, labels[i], cx, y + 19,
                        0xFF6E7482, false);
                RenderUtils.drawText(context, mc.textRenderer, values[i], cx + 10, y + 19,
                        textColor.getValue(), true);
            }

            if (showBiome.isEnabled()) {
                RenderUtils.drawText(context, mc.textRenderer,
                        HudStyle.trim(mc.textRenderer, "biome: " + biome(), w - 20),
                        x + 10, y + 40, 0xFF868A99, false);
            }
        } catch (Throwable ignored) {}
    }
}
