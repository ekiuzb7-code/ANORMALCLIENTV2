package com.anormal.client.module.impl.legit;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.theme.ThemeManager;
import com.anormal.client.util.HudStyle;
import com.anormal.client.util.RenderUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;

/**
 * Inventory contents on the HUD.
 *
 * <p>Modernised: real slot cells with subtle backgrounds, the hotbar row underneath with the
 * selected slot highlighted, stack counts on the icons and a header with the used-slot count.</p>
 */
public class InventoryOverlay extends Module {
    public final NumberSetting posX = new NumberSetting("Pos X", "X Position on screen", 1750.0, 0.0, 1920.0, 1.0);
    public final NumberSetting posY = new NumberSetting("Pos Y", "Y Position on screen", 50.0, 0.0, 1080.0, 1.0);
    public final NumberSetting scale = new NumberSetting("Scale", "HUD scale (0.05 = tiny, 2.0 = huge)", 1.0, 0.05, 2.0, 0.01);

    private static final int CELL = 18;

    public InventoryOverlay() {
        super("InventoryOverlay", "Renders your inventory contents on the HUD", Category.LEGIT);
        addSetting(posX);
        addSetting(posY);
        addSetting(scale);
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.player == null || mc.textRenderer == null) return;
        try {
            int w = 9 * CELL + 20;
            int h = 24 + 3 * CELL + 6 + CELL + 4;
            int[] at = HudStyle.place(posX.getValue().intValue(), posY.getValue().intValue(), w, h);
            int x = at[0], y = at[1];

            int accent = ThemeManager.getAccentColor();
            HudStyle.panel(context, x, y, w, h, accent);
            HudStyle.title(context, mc.textRenderer, "Inventory", x + 10, y + 7);

            int used = 0;
            for (int slot = 9; slot < 36; slot++) {
                if (!mc.player.getInventory().getStack(slot).isEmpty()) used++;
            }
            String count = used + "/27";
            int cw = mc.textRenderer.getWidth(count);
            RenderUtils.drawText(context, mc.textRenderer, count, x + w - 10 - cw, y + 7,
                    0xFF868A99, true);

            int gx = x + 10;
            int gy = y + 20;

            // Main inventory: 3 rows of 9.
            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 9; col++) {
                    int slot = 9 + row * 9 + col;
                    drawSlot(context, mc.player.getInventory().getStack(slot),
                            gx + col * CELL, gy + row * CELL, false);
                }
            }

            // Hotbar row, offset and with the selected slot highlighted.
            int hy = gy + 3 * CELL + 5;
            int selected = mc.player.getInventory().getSelectedSlot();
            for (int col = 0; col < 9; col++) {
                drawSlot(context, mc.player.getInventory().getStack(col),
                        gx + col * CELL, hy, col == selected);
            }
        } catch (Throwable ignored) {}
    }

    private void drawSlot(DrawContext context, ItemStack stack, int x, int y, boolean selected) {
        if (selected) {
            RenderUtils.fill(context, x, y, x + CELL - 1, y + CELL - 1, 0x40000000 | (ThemeManager.getAccentColor() & 0x00FFFFFF));
        } else {
            RenderUtils.fill(context, x, y, x + CELL - 1, y + CELL - 1, 0x14FFFFFF);
        }
        if (stack.isEmpty()) return;
        context.drawItem(stack, x + 1, y + 1);
        if (stack.getCount() > 1 && mc.textRenderer != null) {
            String count = String.valueOf(stack.getCount());
            int cw = mc.textRenderer.getWidth(count);
            RenderUtils.drawText(context, mc.textRenderer, count, x + CELL - 1 - cw, y + 9,
                    0xFFFFFFFF, true);
        }
    }
}
