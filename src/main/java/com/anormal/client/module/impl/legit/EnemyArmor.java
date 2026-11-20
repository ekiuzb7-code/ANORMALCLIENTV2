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
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;

/**
 * Armour readout for the entity you are looking at.
 *
 * <p>Modernised to the shared HUD language: one rounded panel, a target header, then one row per
 * piece with its icon, a colour-coded durability bar and the exact percentage.</p>
 */
public class EnemyArmor extends Module {
    public final NumberSetting posX = new NumberSetting("Pos X", "X Position on screen", 500.0, 0.0, 1920.0, 1.0);
    public final NumberSetting posY = new NumberSetting("Pos Y", "Y Position on screen", 340.0, 0.0, 1080.0, 1.0);
    public final NumberSetting scale = new NumberSetting("Scale", "HUD scale (0.05 = tiny, 2.0 = huge)", 1.0, 0.05, 2.0, 0.01);
    public final BooleanSetting showDurability = new BooleanSetting("Show Durability", "Show durability percent", true);
    public final BooleanSetting showEnchants = new BooleanSetting("Show Enchants", "Mark enchanted pieces", true);
    public final BooleanSetting sticky = new BooleanSetting("Sticky", "Keep last target 5s", true);

    private LivingEntity remembered = null;
    private long rememberedAt = 0;

    private static final EquipmentSlot[] SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };
    private static final String[] SLOT_NAMES = {"Helmet", "Chest", "Legs", "Boots"};

    public EnemyArmor() {
        super("EnemyArmor", "Shows target armor and durability", Category.LEGIT);
        addSetting(posX);
        addSetting(posY);
        addSetting(scale);
        addSetting(showDurability);
        addSetting(showEnchants);
        addSetting(sticky);
    }

    private LivingEntity target() {
        try {
            if (mc.targetedEntity instanceof LivingEntity living && living.isAlive() && living != mc.player) {
                remembered = living;
                rememberedAt = System.currentTimeMillis();
                return living;
            }
        } catch (Throwable ignored) {}
        if (sticky.isEnabled() && remembered != null && remembered.isAlive()
                && System.currentTimeMillis() - rememberedAt < 5000) {
            return remembered;
        }
        return null;
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.textRenderer == null) return;
        LivingEntity target;
        try {
            target = target();
        } catch (Throwable ignored) {
            return;
        }
        if (target == null) return;

        try {
            int accent = ThemeManager.getAccentColor();
            int w = 176;
            int rowH = 20;
            int h = 24 + SLOTS.length * rowH + 6;
            int[] at = HudStyle.place(posX.getValue().intValue(), posY.getValue().intValue(), w, h);
            int x = at[0], y = at[1];

            HudStyle.panel(context, x, y, w, h, accent);
            String name = HudStyle.trim(mc.textRenderer, target.getName().getString(), w - 66);
            HudStyle.title(context, mc.textRenderer, "Armor", x + 10, y + 8);
            int nameW = mc.textRenderer.getWidth(name);
            RenderUtils.drawText(context, mc.textRenderer, name, x + w - 10 - nameW, y + 8,
                    0xFFFFFFFF, true);

            int ly = y + 24;
            for (int i = 0; i < SLOTS.length; i++) {
                EquipmentSlot slot = SLOTS[i];
                ItemStack stack = target.getEquippedStack(slot);
                if (stack == null || stack.isEmpty()) {
                    RenderUtils.drawText(context, mc.textRenderer, SLOT_NAMES[i],
                            x + 30, ly + 4, 0xFF5E6472, false);
                    RenderUtils.fill(context, x + 10, ly + 9, x + 26, ly + 10, 0x22FFFFFF);
                } else {
                    context.drawItem(stack, x + 8, ly);
                    int pct = 100;
                    if (stack.isDamageable()) {
                        int max = Math.max(1, stack.getMaxDamage());
                        pct = (int) Math.round(((double) (max - stack.getDamage()) / max) * 100.0);
                        pct = Math.max(0, Math.min(100, pct));
                    }
                    int barColor = HudStyle.durabilityColor(pct);
                    String label = HudStyle.trim(mc.textRenderer, SLOT_NAMES[i], 58);
                    RenderUtils.drawText(context, mc.textRenderer, label, x + 30, ly + 3,
                            0xFFC7CBD6, false);

                    if (showEnchants.isEnabled() && hasEnchants(stack)) {
                        RenderUtils.drawText(context, mc.textRenderer, "+", x + w - 62, ly + 3,
                                0xFFC084FC, true);
                    }

                    if (showDurability.isEnabled()) {
                        String pctText = pct + "%";
                        int pw = mc.textRenderer.getWidth(pctText);
                        RenderUtils.drawText(context, mc.textRenderer, pctText,
                                x + w - 10 - pw, ly + 3, barColor, true);
                        HudStyle.bar(context, x + 30, ly + 14, w - 44, 3, pct / 100.0f, barColor);
                    } else {
                        HudStyle.bar(context, x + 30, ly + 14, w - 44, 3, 1.0f, 0x55FFFFFF);
                    }
                }
                ly += rowH;
            }
        } catch (Throwable ignored) {}
    }

    private boolean hasEnchants(ItemStack stack) {
        try {
            return stack.hasEnchantments();
        } catch (Throwable ignored) {
            return false;
        }
    }
}
