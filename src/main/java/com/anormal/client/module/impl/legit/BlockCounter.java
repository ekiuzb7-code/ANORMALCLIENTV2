package com.anormal.client.module.impl.legit;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.theme.ThemeManager;
import com.anormal.client.util.ColorUtils;
import com.anormal.client.util.HudStyle;
import com.anormal.client.util.RenderUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;

/**
 * Counts building blocks in the inventory.
 *
 * <p>Modernised into the same counter card as Item Counter, with the block's own icon so the entry
 * is recognisable without reading it.</p>
 */
public class BlockCounter extends Module {
    public final NumberSetting posX = new NumberSetting("Pos X", "X Position on screen", 10.0, 0.0, 1920.0, 1.0);
    public final NumberSetting posY = new NumberSetting("Pos Y", "Y Position on screen", 180.0, 0.0, 1080.0, 1.0);
    public final NumberSetting scale = new NumberSetting("Scale", "HUD scale (0.05 = tiny, 2.0 = huge)", 1.0, 0.05, 2.0, 0.01);
    public final ModeSetting blockMode = new ModeSetting("Block Mode", "Which blocks to count", "Obsidian", "Obsidian", "All Building");
    public final ColorSetting textColor = new ColorSetting("Text Color", "Counter text color", ColorUtils.rgba(255, 255, 255, 255));

    public BlockCounter() {
        super("BlockCounter", "Counts building blocks in inventory", Category.LEGIT);
        addSetting(posX);
        addSetting(posY);
        addSetting(scale);
        addSetting(blockMode);
        addSetting(textColor);
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.player == null || mc.textRenderer == null) return;
        try {
            int total = 0;
            ItemStack icon = ItemStack.EMPTY;
            for (int i = 0; i < 36; i++) {
                ItemStack s = mc.player.getInventory().getStack(i);
                if (s.isEmpty()) continue;
                boolean hit;
                if (blockMode.is("Obsidian")) {
                    String p = Registries.ITEM.getId(s.getItem()).getPath();
                    hit = p.equals("obsidian") || p.equals("crying_obsidian");
                } else {
                    hit = s.getItem() instanceof BlockItem;
                }
                if (hit) {
                    total += s.getCount();
                    if (icon.isEmpty()) icon = s;
                }
            }

            int accent = ThemeManager.getAccentColor();
            int w = 138, h = 40;
            int[] at = HudStyle.place(posX.getValue().intValue(), posY.getValue().intValue(), w, h);
            int x = at[0], y = at[1];

            HudStyle.panel(context, x, y, w, h, accent);
            if (!icon.isEmpty()) context.drawItem(icon, x + 8, y + 11);
            HudStyle.title(context, mc.textRenderer,
                    HudStyle.trim(mc.textRenderer, blockMode.getValue(), w - 66), x + 30, y + 8);

            String value = String.valueOf(total);
            int vw = mc.textRenderer.getWidth(value);
            RenderUtils.drawText(context, mc.textRenderer, value, x + w - 10 - vw, y + 16,
                    textColor.getValue(), true);
            HudStyle.bar(context, x + 30, y + 31, w - 42, 3, total > 0 ? 1.0f : 0.0f, accent);
        } catch (Throwable ignored) {}
    }
}
