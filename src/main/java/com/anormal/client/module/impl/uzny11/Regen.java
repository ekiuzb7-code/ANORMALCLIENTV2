package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;

public class Regen extends Module {
    public final NumberSetting hunger = new NumberSetting("Hunger Level", "Eat when hunger below this", 14.0, 0.0, 20.0, 1.0);
    public final NumberSetting delay = new NumberSetting("Delay", "Ticks between eat attempts", 20.0, 1.0, 100.0, 1.0);
    private int ticks = 0;

    public Regen() {
        super("Regen", "Eats food from hotbar when hungry", Category.UZNY11);
        addSetting(hunger);
        addSetting(delay);
    }

    private static boolean isFood(String path) {
        return path.contains("apple") || path.contains("bread") || path.contains("carrot") || path.contains("potato")
                || path.contains("beef") || path.contains("pork") || path.contains("mutton") || path.contains("chicken")
                || path.contains("fish") || path.contains("salmon") || path.contains("cod") || path.contains("cookie")
                || path.contains("melon") || path.contains("berry") || path.contains("honey") || path.contains("cake")
                || path.contains("pie") || path.contains("soup") || path.contains("stew") || path.contains("chorus");
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.interactionManager == null) return;
        try {
            if (mc.player.getHungerManager().getFoodLevel() >= hunger.getValue().intValue()) {
                ticks = 0;
                return;
            }
            if (ticks++ < delay.getValue().intValue()) return;
            ticks = 0;
            for (int i = 0; i < 9; i++) {
                ItemStack stack = mc.player.getInventory().getStack(i);
                if (stack.isEmpty()) continue;
                Identifier id = Registries.ITEM.getId(stack.getItem());
                if (id != null && isFood(id.getPath())) {
                    mc.player.getInventory().setSelectedSlot(i);
                    mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                    return;
                }
            }
        } catch (Throwable ignored) {}
    }
}
