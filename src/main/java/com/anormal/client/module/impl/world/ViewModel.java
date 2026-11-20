package com.anormal.client.module.impl.world;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.theme.ThemeManager;
import com.anormal.client.util.HudStyle;
import com.anormal.client.util.RenderUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;

/**
 * First-person hand / held-item transform.
 *
 * <p>Moves the held item away or closer, left/right and up/down and resizes it. The transform is
 * applied inside the held-item render hook, so it only touches the first-person hands and never the
 * third-person model or the GUI.</p>
 *
 * <p><b>Preview.</b> Turning <i>Preview</i> on draws the held item in the corner of the HUD, at the
 * position and size the configured offsets produce, with the crosshair marking the middle of your
 * view. Values can be dialled in and checked without leaving the game.</p>
 */
public final class ViewModel extends Module {
    public final NumberSetting distance = new NumberSetting("Distance",
            "Push the hand away (positive) or pull it closer (negative)", 0.0, -1.5, 2.5, 0.01);
    public final NumberSetting sideways = new NumberSetting("Sideways",
            "Move the hand right (positive) or left (negative)", 0.0, -1.5, 1.5, 0.01);
    public final NumberSetting vertical = new NumberSetting("Vertical",
            "Move the hand up (positive) or down (negative)", 0.0, -1.5, 1.5, 0.01);
    public final NumberSetting scale = new NumberSetting("Scale", "Hand size (0.05 = barely visible, 2.0 = huge)", 1.0, 0.05, 2.0, 0.01);
    public final BooleanSetting preview = new BooleanSetting("Preview",
            "Draw the held item on the HUD so changes can be checked in game", true);

    private static ViewModel active;

    public ViewModel() {
        super("ViewModel", "Moves and resizes your first-person hand and held item", Category.WORLD);
        addSetting(distance);
        addSetting(sideways);
        addSetting(vertical);
        addSetting(scale);
        addSetting(preview);
    }

    @Override
    public void onEnable() {
        active = this;
    }

    @Override
    public void onDisable() {
        if (active == this) active = null;
    }

    /**
     * Called from the held-item render hook. Only the first-person passes are touched; the item
     * pushes away along -Z (towards the camera is +Z in this space) and the offhand mirrors the
     * sideways axis so "right" means the same thing on both hands.
     */
    public static void apply(MatrixStack pose, ItemDisplayContext context) {
        if (pose == null || context == null) return;
        if (context != ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                && context != ItemDisplayContext.FIRST_PERSON_RIGHT_HAND) return;

        ViewModel module = active;
        if (module == null || !module.isEnabled()) return;

        float side = module.sideways.getValue().floatValue();
        float up = module.vertical.getValue().floatValue();
        float away = module.distance.getValue().floatValue();

        pose.translate(context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND ? -side : side,
                up, -away);

        float size = module.scale.getValue().floatValue();
        if (size != 1.0F) pose.scale(size, size, size);
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (!preview.isEnabled() || mc.textRenderer == null) return;
        try {
            int w = 200;
            int h = 140;
            int[] at = HudStyle.place(12, Math.max(12, RenderUtils.screenHeight() - h - 12), w, h);
            int x = at[0], y = at[1];

            int accent = ThemeManager.getAccentColor();
            HudStyle.panel(context, x, y, w, h, accent);
            HudStyle.title(context, mc.textRenderer, "Viewmodel", x + 10, y + 7);
            String tag = "preview";
            int tw = mc.textRenderer.getWidth(tag);
            RenderUtils.drawText(context, mc.textRenderer, tag, x + w - 10 - tw, y + 7,
                    0xFF6E7482, false);

            int vx = x + 10;
            int vy = y + 20;
            int vw = w - 20;
            int vh = 86;
            RenderUtils.fillRounded(context, vx, vy, vw, vh, 4, 0x66000000);

            int cx = vx + vw / 2;
            int cy = vy + vh / 2;
            RenderUtils.fill(context, cx - 5, cy, cx + 5, cy + 1, 0x55FFFFFF);
            RenderUtils.fill(context, cx, cy - 5, cx + 1, cy + 5, 0x55FFFFFF);

            float side = sideways.getValue().floatValue();
            float up = vertical.getValue().floatValue();
            float size = scale.getValue().floatValue();

            // 1 block unit of offset == 30 preview pixels, so the item moves like the hand does.
            int itemX = cx + Math.round(side * 30);
            int itemY = cy - Math.round(up * 30);

            ItemStack held = mc.player != null ? mc.player.getMainHandStack() : ItemStack.EMPTY;
            if (!held.isEmpty()) {
                float itemScale = 2.2f * size;
                var matrices = context.getMatrices();
                matrices.pushMatrix();
                matrices.translate((float) itemX, (float) itemY);
                matrices.rotate((float) Math.toRadians(-10));
                matrices.scale(itemScale, itemScale);
                context.drawItem(held, -8, -8);
                matrices.popMatrix();
            } else {
                String empty = "empty hand";
                int ew = mc.textRenderer.getWidth(empty);
                RenderUtils.drawText(context, mc.textRenderer, empty, cx - ew / 2, cy + 8,
                        0xFF5E6472, false);
            }

            String lineOne = String.format(java.util.Locale.ROOT, "distance %.2f    side %.2f",
                    distance.getValue(), side);
            RenderUtils.drawText(context, mc.textRenderer, lineOne, x + 10, y + h - 30,
                    0xFF868A99, false);

            String lineTwo = String.format(java.util.Locale.ROOT, "up %.2f", up);
            RenderUtils.drawText(context, mc.textRenderer, lineTwo, x + 10, y + h - 18,
                    0xFF868A99, false);

            String sizeText = String.format(java.util.Locale.ROOT, "scale %.2f", size);
            int sw = mc.textRenderer.getWidth(sizeText);
            RenderUtils.drawText(context, mc.textRenderer, sizeText, x + w - 10 - sw, y + h - 18,
                    accent, false);
        } catch (Throwable ignored) {}
    }
}
