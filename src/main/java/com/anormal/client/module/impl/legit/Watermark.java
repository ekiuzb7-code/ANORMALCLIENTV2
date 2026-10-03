package com.anormal.client.module.impl.legit;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.theme.ThemeManager;
import com.anormal.client.util.ColorUtils;
import com.anormal.client.util.RenderUtils;
import net.minecraft.client.gui.DrawContext;

public class Watermark extends Module {
    public final NumberSetting posX = new NumberSetting("Pos X", "X Position on screen", 4.0, 0.0, 1920.0, 1.0);
    public final NumberSetting posY = new NumberSetting("Pos Y", "Y Position on screen", 4.0, 0.0, 1080.0, 1.0);
    public final ModeSetting style = new ModeSetting("Style", "Watermark text style", "Full", "Full", "Short", "Minimal");
    public final BooleanSetting showVersion = new BooleanSetting("Show Version", "Show client version", true);
    public final BooleanSetting shadow = new BooleanSetting("Shadow", "Text drop shadow", true);
    public final BooleanSetting background = new BooleanSetting("Background", "Dark background panel", true);
    public final ColorSetting textColor = new ColorSetting("Text Color", "ANORMAL text color", ColorUtils.rgba(255, 255, 255, 255));
    public final ColorSetting accentColor = new ColorSetting("Accent Color", "Version text color", ColorUtils.rgba(255, 170, 0, 255));
    public final BooleanSetting useTexture = new BooleanSetting("Use Texture", "Draw logo image instead of text", true);
    public final ModeSetting logo = new ModeSetting("Logo", "Logo 1 wordmark or Logo 2 banner", "Logo 2", "Logo 1", "Logo 2");
    public final NumberSetting scale = new NumberSetting("Scale", "Logo image scale", 0.25, 0.1, 1.0, 0.05);

    public Watermark() {
        super("Watermark", "ANORMAL logo watermark overlay", Category.LEGIT);
        addSetting(posX);
        addSetting(posY);
        addSetting(style);
        addSetting(showVersion);
        addSetting(shadow);
        addSetting(background);
        addSetting(textColor);
        addSetting(accentColor);
        addSetting(useTexture);
        addSetting(logo);
        addSetting(scale);
    }

    private static final net.minecraft.util.Identifier LOGO1 =
            net.minecraft.util.Identifier.of("anormalclient", "watermark.png");
    private static final net.minecraft.util.Identifier LOGO2 =
            net.minecraft.util.Identifier.of("anormalclient", "logo_banner.png");

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.textRenderer == null) return;
        int x = posX.getValue().intValue();
        int y = posY.getValue().intValue();

        // Logo image, scaled — exact brand artwork.
        // drawTexturedQuad samples the loose PNG directly (GUI atlas has no such sprite).
        if (useTexture.isEnabled()) {
            try {
                boolean banner = logo.is("Logo 2");
                int tw = banner ? 1024 : 512;
                int th = banner ? 256 : 128;
                int w = Math.max(8, (int) (tw * scale.getValue()));
                int h = Math.max(2, (int) (th * scale.getValue()));
                context.drawTexturedQuad(banner ? LOGO2 : LOGO1,
                        x, y, x + w, y + h, 0.0f, (float) tw, 0.0f, (float) th);
                return;
            } catch (Throwable ignored) {}
        }

        String main = style.is("Short") ? "AN" : (style.is("Minimal") ? "A" : "ANORMAL");
        String ver = showVersion.isEnabled() ? " v1.0" : "";
        int mainW = mc.textRenderer.getWidth(main);
        int verW = ver.isEmpty() ? 0 : mc.textRenderer.getWidth(ver);

        if (background.isEnabled()) {
            RenderUtils.fill(context, x - 4, y - 3, x + mainW + verW + 4, y + 11, ThemeManager.getBackgroundColor());
            RenderUtils.drawBorder(context, x - 4, y - 3, x + mainW + verW + 4, y + 11, 1, ThemeManager.getAccentColor());
        }
        RenderUtils.drawText(context, mc.textRenderer, "§l" + main, x, y, textColor.getValue(), shadow.isEnabled());
        if (!ver.isEmpty()) {
            RenderUtils.drawText(context, mc.textRenderer, ver, x + mainW + 1, y, accentColor.getValue(), shadow.isEnabled());
        }
    }
}
