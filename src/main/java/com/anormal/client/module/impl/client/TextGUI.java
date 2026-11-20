package com.anormal.client.module.impl.client;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.module.ModuleManager;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.theme.ThemeManager;
import com.anormal.client.util.ColorUtils;
import com.anormal.client.util.RenderUtils;
import net.minecraft.client.gui.DrawContext;

import java.util.Comparator;
import java.util.List;

/**
 * Array list: every enabled module as a right-aligned row.
 *
 * <p>The rows used to be 13px apart while the background was only 12px tall, which left a visible
 * 1px seam between every box. Row height, the gap between rows and the box size are now real
 * settings (gap defaults to 0, so boxes touch), and the rest of the look is customisable too:
 * padding, accent bar width, background opacity, rounded corners, text shadow and a global scale.</p>
 */
public class TextGUI extends Module {
    public final ModeSetting sort = new ModeSetting("Sort", "Sorting method", "Length", "Length", "Alphabetical");
    public final ModeSetting colorMode = new ModeSetting("Color", "Color Mode", "Theme", "Theme", "Rainbow", "Custom");
    public final ColorSetting customColor = new ColorSetting("Custom Color", "Color when in custom mode", ColorUtils.rgba(255, 100, 30, 255));
    public final BooleanSetting background = new BooleanSetting("Background", "Draw dark background behind text", true);
    public final BooleanSetting watermark = new BooleanSetting("Watermark", "Show client brand header", false);
    public final NumberSetting posX = new NumberSetting("Pos X", "Right-edge anchor X", 0.0, 0.0, 1920.0, 1.0);
    public final NumberSetting posY = new NumberSetting("Pos Y", "Top anchor Y", 0.0, 0.0, 1080.0, 1.0);

    public final NumberSetting rowHeight = new NumberSetting("Row Height", "Height of one module row", 13.0, 9.0, 24.0, 1.0);
    public final NumberSetting gap = new NumberSetting("Gap", "Space between rows (0 = boxes touch)", 0.0, 0.0, 6.0, 1.0);
    public final NumberSetting padding = new NumberSetting("Padding", "Horizontal padding around the name", 4.0, 1.0, 14.0, 1.0);
    public final NumberSetting accentWidth = new NumberSetting("Accent Bar", "Width of the colored spine (0 = off)", 2.0, 0.0, 5.0, 1.0);
    public final NumberSetting bgAlpha = new NumberSetting("BG Opacity", "Background opacity", 200.0, 0.0, 255.0, 5.0);
    public final BooleanSetting roundCorners = new BooleanSetting("Rounded", "Rounded row corners", false);
    public final BooleanSetting textShadow = new BooleanSetting("Text Shadow", "Draw text with a shadow", true);
    public final NumberSetting scale = new NumberSetting("Scale", "Whole list scale", 1.0, 0.5, 2.0, 0.05);

    public TextGUI() {
        super("TextGUI", "Displays active modules on the HUD screen", Category.CLIENT);
        addSetting(sort);
        addSetting(colorMode);
        addSetting(customColor);
        addSetting(background);
        addSetting(watermark);
        addSetting(posX);
        addSetting(posY);
        addSetting(rowHeight);
        addSetting(gap);
        addSetting(padding);
        addSetting(accentWidth);
        addSetting(bgAlpha);
        addSetting(roundCorners);
        addSetting(textShadow);
        addSetting(scale);
        setEnabled(true);
    }

    private int lastCustomColor = Integer.MIN_VALUE;

    @Override
    public void onTick() {
        // Picking a colour in the Custom Color picker applies immediately: without this the HUD
        // kept using the Theme accent because the Color mode was still on "Theme", which made
        // colour selection look broken.
        int c = customColor.getValue();
        if (lastCustomColor != Integer.MIN_VALUE && c != lastCustomColor && !colorMode.is("Custom")) {
            colorMode.setMode("Custom");
        }
        lastCustomColor = c;
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.textRenderer == null) return;

        int screenWidth = RenderUtils.screenWidth();
        int screenHeight = RenderUtils.screenHeight();
        // Clamp inside screen borders so the list never runs off-screen.
        int rightEdge = Math.max(40, Math.min(posX.getValue().intValue(), screenWidth - 2));
        int top = Math.max(2, Math.min(posY.getValue().intValue(), screenHeight - 14));

        float s = scale.getValue().floatValue();
        boolean pushed = false;
        try {
            if (Math.abs(s - 1.0f) > 0.001f) {
                var matrices = context.getMatrices();
                matrices.pushMatrix();
                matrices.translate((float) rightEdge, (float) top);
                matrices.scale(s, s);
                matrices.translate((float) -rightEdge, (float) -top);
                pushed = true;
            }

            int rowH = rowHeight.getValue().intValue();
            int rowGap = gap.getValue().intValue();
            int pad = padding.getValue().intValue();
            int spine = accentWidth.getValue().intValue();
            boolean rounded = roundCorners.isEnabled();
            boolean shadow = textShadow.isEnabled();

            int themeBg = ThemeManager.getBackgroundColor();
            int bgColor = ((bgAlpha.getValue().intValue() & 0xFF) << 24) | (themeBg & 0x00FFFFFF);

            if (watermark.isEnabled()) {
                String clientTitle = "ANORMAL CLIENT";
                String versionText = " v1.0 [1.21.11]";
                int titleWidth = mc.textRenderer.getWidth(clientTitle + versionText);

                if (background.isEnabled()) {
                    if (rounded) {
                        RenderUtils.fillRounded(context, 4, 4, titleWidth + 6, 14, 2, bgColor);
                    } else {
                        RenderUtils.fill(context, 4, 4, 10 + titleWidth, 18, bgColor);
                        RenderUtils.drawBorder(context, 4, 4, 10 + titleWidth, 18, 1, ThemeManager.getBorderColor());
                    }
                    if (spine > 0) RenderUtils.fill(context, 4, 4, 4 + spine, 18, ThemeManager.getAccentColor());
                }

                RenderUtils.drawText(context, mc.textRenderer, clientTitle, 9, 7, ThemeManager.getAccentColor(), shadow);
                RenderUtils.drawText(context, mc.textRenderer, versionText,
                        9 + mc.textRenderer.getWidth(clientTitle), 7, 0xFFAAAAAA, shadow);
            }

            List<Module> activeModules = ModuleManager.getModules().stream()
                    .filter(Module::isEnabled)
                    .filter(m -> !(m instanceof ClientSettings))
                    .sorted(sort.is("Length") ?
                            Comparator.comparingInt((Module m) -> mc.textRenderer.getWidth(m.getName())).reversed() :
                            Comparator.comparing(Module::getName))
                    .toList();

            int y = top;
            int moduleIndex = 0;
            for (Module module : activeModules) {
                String name = module.getName();
                int textWidth = mc.textRenderer.getWidth(name);
                int rowWidth = textWidth + pad * 2;
                int left = rightEdge - rowWidth;

                int color;
                if (colorMode.is("Rainbow")) {
                    color = ColorUtils.rainbow(moduleIndex * 150, 0.8f, 1.0f);
                } else if (colorMode.is("Custom")) {
                    color = customColor.getValue();
                } else {
                    color = ThemeManager.getAccentColor();
                }

                if (background.isEnabled()) {
                    if (rounded) {
                        RenderUtils.fillRounded(context, left, y, rowWidth, rowH, 2, bgColor);
                    } else {
                        RenderUtils.fill(context, left, y, rightEdge, y + rowH, bgColor);
                    }
                    if (spine > 0) {
                        RenderUtils.fill(context, rightEdge - spine, y, rightEdge, y + rowH, color);
                    }
                }

                int textY = y + Math.max(1, (rowH - mc.textRenderer.fontHeight) / 2);
                RenderUtils.drawText(context, mc.textRenderer, name, left + pad, textY, color, shadow);

                y += rowH + rowGap;
                moduleIndex++;
            }
        } catch (Throwable ignored) {
        } finally {
            if (pushed) {
                try {
                    context.getMatrices().popMatrix();
                } catch (Throwable ignored) {
                }
            }
        }
    }
}
