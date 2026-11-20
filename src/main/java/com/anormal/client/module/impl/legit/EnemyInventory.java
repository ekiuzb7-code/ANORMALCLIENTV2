package com.anormal.client.module.impl.legit;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.theme.ThemeManager;
import com.anormal.client.util.HudStyle;
import com.anormal.client.util.RenderUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;

/**
 * Visible loadout of the targeted entity.
 *
 * <p>Modernised into the shared panel style: a "Loadout" header with the target name, then one
 * row per visible slot showing the real item icon, its name and stack count.</p>
 */
public class EnemyInventory extends Module {
    public final NumberSetting posX = new NumberSetting("Pos X", "X Position on screen", 640.0, 0.0, 1920.0, 1.0);
    public final NumberSetting posY = new NumberSetting("Pos Y", "Y Position on screen", 340.0, 0.0, 1080.0, 1.0);
    public final NumberSetting scale = new NumberSetting("Scale", "HUD scale (0.05 = tiny, 2.0 = huge)", 1.0, 0.05, 2.0, 0.01);
    public final BooleanSetting showMainHand = new BooleanSetting("Main Hand", "Show held item", true);
    public final BooleanSetting showOffHand = new BooleanSetting("Off Hand", "Show offhand item", true);
    public final BooleanSetting showArmor = new BooleanSetting("Show Armor", "Show equipped armor row", true);
    public final BooleanSetting sticky = new BooleanSetting("Sticky", "Keep last target 5s", true);

    private LivingEntity remembered = null;
    private long rememberedAt = 0;

    public EnemyInventory() {
        super("EnemyInventory", "Shows target visible equipment", Category.LEGIT);
        addSetting(posX);
        addSetting(posY);
        addSetting(scale);
        addSetting(showMainHand);
        addSetting(showOffHand);
        addSetting(showArmor);
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

        int rows = 0;
        if (showMainHand.isEnabled()) rows++;
        if (showOffHand.isEnabled()) rows++;
        if (showArmor.isEnabled() && target instanceof PlayerEntity) rows++;
        if (rows == 0) return;

        try {
            int accent = ThemeManager.getAccentColor();
            int w = 186;
            int rowH = 20;
            int h = 24 + rows * rowH + 6;
            int[] at = HudStyle.place(posX.getValue().intValue(), posY.getValue().intValue(), w, h);
            int x = at[0], y = at[1];

            HudStyle.panel(context, x, y, w, h, accent);
            String name = HudStyle.trim(mc.textRenderer, target.getName().getString(), w - 76);
            HudStyle.title(context, mc.textRenderer, "Loadout", x + 10, y + 8);
            int nameW = mc.textRenderer.getWidth(name);
            RenderUtils.drawText(context, mc.textRenderer, name, x + w - 10 - nameW, y + 8,
                    0xFFFFFFFF, true);

            int ly = y + 24;
            if (showMainHand.isEnabled()) {
                row(context, target.getMainHandStack(), "Main", x, ly, w, accent);
                ly += rowH;
            }
            if (showOffHand.isEnabled()) {
                row(context, target.getOffHandStack(), "Off", x, ly, w, accent);
                ly += rowH;
            }
            if (showArmor.isEnabled() && target instanceof PlayerEntity p) {
                row(context, p.getEquippedStack(net.minecraft.entity.EquipmentSlot.CHEST),
                        "Body", x, ly, w, accent);
            }
        } catch (Throwable ignored) {}
    }

    private void row(DrawContext context, ItemStack stack, String tag, int x, int y, int w, int accent) {
        try {
            RenderUtils.fill(context, x + 10, y + 9, x + 26, y + 10, 0x22FFFFFF);
            if (stack == null || stack.isEmpty()) {
                RenderUtils.drawText(context, mc.textRenderer, tag, x + 30, y + 4, 0xFF5E6472, false);
                RenderUtils.drawText(context, mc.textRenderer, "empty", x + 64, y + 4, 0xFF3E434F, false);
                return;
            }
            context.drawItem(stack, x + 8, y);
            RenderUtils.drawText(context, mc.textRenderer, tag, x + 30, y + 4, 0xFF868A99, false);

            String count = stack.getCount() > 1 ? "x" + stack.getCount() : "";
            int countW = mc.textRenderer.getWidth(count);
            if (!count.isEmpty()) {
                RenderUtils.drawText(context, mc.textRenderer, count, x + w - 10 - countW, y + 4,
                        0xFFB9BEC9, false);
            }
            int avail = w - 46 - countW - (hasEnchants(stack) ? 12 : 0);
            String name = HudStyle.trim(mc.textRenderer, stack.getName().getString(), avail);
            RenderUtils.drawText(context, mc.textRenderer, name, x + 56, y + 4, 0xFFFFFFFF, false);
            if (hasEnchants(stack)) {
                RenderUtils.drawText(context, mc.textRenderer, "+",
                        x + w - 12 - countW, y + 4, 0xFFC084FC, true);
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
