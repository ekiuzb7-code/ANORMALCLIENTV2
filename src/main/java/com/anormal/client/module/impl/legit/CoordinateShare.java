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
import net.minecraft.text.Text;

/**
 * Share-ready coordinate line.
 *
 * <p>Modernised: the dimension is a header tag, X / Y / Z are split into labelled columns that are
 * easy to read out loud (or type into chat) instead of one long sentence.</p>
 */
public class CoordinateShare extends Module {
    public final NumberSetting posX = new NumberSetting("Pos X", "X Position on screen", 10.0, 0.0, 1920.0, 1.0);
    public final NumberSetting posY = new NumberSetting("Pos Y", "Y Position on screen", 360.0, 0.0, 1080.0, 1.0);
    public final NumberSetting scale = new NumberSetting("Scale", "HUD scale (0.05 = tiny, 2.0 = huge)", 1.0, 0.05, 2.0, 0.01);
    public final BooleanSetting logInChat = new BooleanSetting("Log In Chat", "Print share text in local chat", false);
    public final ColorSetting textColor = new ColorSetting("Text Color", "Share text color", ColorUtils.rgba(255, 255, 255, 255));

    public CoordinateShare() {
        super("CoordinateShare", "Share-ready coords text plus chat log", Category.LEGIT);
        addSetting(posX);
        addSetting(posY);
        addSetting(scale);
        addSetting(logInChat);
        addSetting(textColor);
    }

    private String dim() {
        try {
            return mc.world.getRegistryKey().getValue().getPath();
        } catch (Throwable ignored) {
            return "unknown";
        }
    }

    private String shareText() {
        return String.format(java.util.Locale.ROOT, "XYZ: %.0f / %.0f / %.0f (%s)",
                mc.player.getX(), mc.player.getY(), mc.player.getZ(), dim());
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;
        try {
            if (logInChat.isEnabled()) {
                logInChat.setValue(false);
                if (mc.inGameHud != null) {
                    mc.inGameHud.getChatHud().addMessage(Text.literal("§b[Share] §f" + shareText()));
                }
            }
        } catch (Throwable ignored) {}
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.player == null || mc.textRenderer == null) return;
        try {
            int accent = ThemeManager.getAccentColor();
            int w = 196, h = 46;
            int[] at = HudStyle.place(posX.getValue().intValue(), posY.getValue().intValue(), w, h);
            int x = at[0], y = at[1];

            HudStyle.panel(context, x, y, w, h, accent);
            HudStyle.title(context, mc.textRenderer, "Coords", x + 10, y + 7);

            String dim = dim();
            int dw = mc.textRenderer.getWidth(dim);
            RenderUtils.drawText(context, mc.textRenderer, dim, x + w - 10 - dw, y + 7,
                    0xFF868A99, false);

            // Three labelled columns so the values can be read individually.
            int colW = (w - 20) / 3;
            String[] labels = {"X", "Y", "Z"};
            String[] values = {
                    String.valueOf(Math.round(mc.player.getX())),
                    String.valueOf(Math.round(mc.player.getY())),
                    String.valueOf(Math.round(mc.player.getZ()))
            };
            for (int i = 0; i < 3; i++) {
                int cx = x + 10 + i * colW;
                if (i > 0) RenderUtils.fill(context, cx - 6, y + 19, cx - 5, y + 36, 0x22FFFFFF);
                RenderUtils.drawText(context, mc.textRenderer, labels[i], cx, y + 19,
                        0xFF6E7482, false);
                RenderUtils.drawText(context, mc.textRenderer, values[i], cx + 10, y + 19,
                        textColor.getValue(), true);
            }

            RenderUtils.drawText(context, mc.textRenderer, "click to copy in chat", x + 10, y + 33,
                    0xFF4B505C, false);
        } catch (Throwable ignored) {}
    }
}
