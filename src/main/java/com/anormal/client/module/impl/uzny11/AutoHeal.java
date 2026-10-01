package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;

public class AutoHeal extends Module {
    public final NumberSetting health = new NumberSetting("Health", "Heal when HP at or below this", 10.0, 1.0, 20.0, 0.5);
    public final NumberSetting delay = new NumberSetting("Delay", "Ticks between heal attempts", 20.0, 1.0, 100.0, 1.0);
    private int ticks = 0;

    public AutoHeal() {
        super("AutoHeal", "Uses healing potions from hotbar when HP is low", Category.UZNY11);
        addSetting(health);
        addSetting(delay);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.interactionManager == null) return;
        try {
            if (mc.player.getHealth() > health.getValue().floatValue()) {
                ticks = 0;
                return;
            }
            if (ticks++ < delay.getValue().intValue()) return;
            ticks = 0;
            int fallback = -1;
            for (int i = 0; i < 9; i++) {
                ItemStack stack = mc.player.getInventory().getStack(i);
                if (stack.isEmpty()) continue;
                Identifier id = Registries.ITEM.getId(stack.getItem());
                if (id == null) continue;
                String path = id.getPath();
                if (path.contains("splash_potion") || path.contains("lingering_potion")) {
                    mc.player.getInventory().setSelectedSlot(i);
                    mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                    mc.player.swingHand(Hand.MAIN_HAND);
                    return;
                }
                if (path.contains("potion") && fallback == -1) fallback = i;
            }
            if (fallback != -1) {
                mc.player.getInventory().setSelectedSlot(fallback);
                mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            }
        } catch (Throwable ignored) {}
    }
}
