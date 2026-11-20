package com.anormal.client.module.impl.legit;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.theme.ThemeManager;
import com.anormal.client.util.HudStyle;
import com.anormal.client.util.RenderUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;

/**
 * Equipped armour with durability.
 *
 * <p>Modernised from bare icons with a number floating to their left into a single panel: four
 * slots in a row, a durability bar under each piece, the percentage on the bar and the average
 * durability in the header.</p>
 */
public class ArmorStatus extends Module {
    public final NumberSetting posX = new NumberSetting("Pos X", "X Position on screen", 1820.0, 0.0, 1920.0, 1.0);
    public final NumberSetting posY = new NumberSetting("Pos Y", "Y Position on screen", 50.0, 0.0, 1080.0, 1.0);
    public final NumberSetting scale = new NumberSetting("Scale", "HUD scale (0.05 = tiny, 2.0 = huge)", 1.0, 0.05, 2.0, 0.01);
    public final BooleanSetting showDamage = new BooleanSetting("Damage %", "Displays durability percent", true);
    public final BooleanSetting showItemCount = new BooleanSetting("Item Count", "Displays stack count", false);

    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    public ArmorStatus() {
        super("ArmorStatus", "Displays equipped armor icons and durability on HUD", Category.LEGIT);
        addSetting(posX);
        addSetting(posY);
        addSetting(scale);
        addSetting(showDamage);
        addSetting(showItemCount);
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.player == null || mc.textRenderer == null) return;
        try {
            int cell = 22;
            int w = 20 + ARMOR_SLOTS.length * cell;
            int h = 46;
            int[] at = HudStyle.place(posX.getValue().intValue(), posY.getValue().intValue(), w, h);
            int x = at[0], y = at[1];

            int accent = ThemeManager.getAccentColor();
            HudStyle.panel(context, x, y, w, h, accent);
            HudStyle.title(context, mc.textRenderer, "Armor", x + 10, y + 7);

            // Average durability across worn pieces, shown in the header.
            int sum = 0, worn = 0;
            for (EquipmentSlot slot : ARMOR_SLOTS) {
                ItemStack stack = mc.player.getEquippedStack(slot);
                if (stack.isEmpty() || !stack.isDamageable()) continue;
                int max = Math.max(1, stack.getMaxDamage());
                sum += (int) Math.round((max - stack.getDamage()) * 100.0 / max);
                worn++;
            }
            if (showDamage.isEnabled() && worn > 0) {
                int avg = sum / worn;
                String avgText = avg + "%";
                int aw = mc.textRenderer.getWidth(avgText);
                RenderUtils.drawText(context, mc.textRenderer, avgText, x + w - 10 - aw, y + 7,
                        HudStyle.durabilityColor(avg), true);
            }

            int sx = x + 10;
            int sy = y + 18;
            for (int i = 0; i < ARMOR_SLOTS.length; i++) {
                int cx = sx + i * cell;
                RenderUtils.fillRounded(context, cx, sy, 18, 18, 3, 0x1AFFFFFF);
                ItemStack stack = mc.player.getEquippedStack(ARMOR_SLOTS[i]);
                if (stack.isEmpty()) {
                    RenderUtils.fill(context, cx + 8, sy + 8, cx + 10, sy + 10, 0x33FFFFFF);
                    continue;
                }
                context.drawItem(stack, cx + 1, sy + 1);
                if (showItemCount.isEnabled() && stack.getCount() > 1) {
                    String count = String.valueOf(stack.getCount());
                    int cw = mc.textRenderer.getWidth(count);
                    RenderUtils.drawText(context, mc.textRenderer, count, cx + 17 - cw, sy + 9,
                            0xFFFFFFFF, true);
                }
                if (showDamage.isEnabled() && stack.isDamageable()) {
                    int max = Math.max(1, stack.getMaxDamage());
                    float pct = Math.max(0.0f, Math.min(1.0f, (max - stack.getDamage()) / (float) max));
                    HudStyle.bar(context, cx, sy + 21, 18, 3, pct,
                            HudStyle.durabilityColor(Math.round(pct * 100)));
                }
            }
        } catch (Throwable ignored) {}
    }
}
