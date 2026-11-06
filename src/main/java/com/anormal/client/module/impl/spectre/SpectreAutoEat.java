package com.anormal.client.module.impl.spectre;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;

import java.util.Set;

/**
 * Spectre port: "Auto Eat" — eats hotbar or offhand food once hunger or health drops too
 * low.
 *
 * <p>Same thresholds, same never-eat list, same best-food scoring
 * ({@code nutrition + saturation}) as Spectre; the item use is driven through the local
 * use key and offhand interaction so the eating animation and packets stay vanilla.</p>
 */
public final class SpectreAutoEat extends Module {
    private static final Set<Item> NEVER_EAT = Set.of(
            Items.ROTTEN_FLESH, Items.SPIDER_EYE, Items.POISONOUS_POTATO, Items.PUFFERFISH,
            Items.CHICKEN, Items.CHORUS_FRUIT, Items.SUSPICIOUS_STEW, Items.ENCHANTED_GOLDEN_APPLE);

    private static final int NOT_FOUND = -1;
    private static final int OFFHAND_SLOT = -2;

    public final NumberSetting hunger = addSetting(new NumberSetting("Hunger", "Eat at or below this food level", 16.0, 0.0, 20.0, 1.0));
    public final NumberSetting health = addSetting(new NumberSetting("Health", "Eat at or below this health (0 = never)", 10.0, 0.0, 20.0, 1.0));

    private boolean eating;
    private int previousSlot;

    public SpectreAutoEat() {
        super("SpectreAutoEat", "Spectre port: eats hotbar or offhand food once hunger or health drops too low.", Category.SPECTRE);
    }

    @Override
    public void onDisable() {
        stop();
    }

    public boolean isEating() {
        return isEnabled() && eating;
    }

    @Override
    public void onTick() {
        var player = mc.player;
        if (player == null || mc.world == null || mc.interactionManager == null || mc.currentScreen != null) {
            stop();
            return;
        }
        if (eating && player.isUsingItem()) {
            mc.options.useKey.setPressed(true);
            return;
        }
        if (mc.interactionManager.isBreakingBlock() || !shouldEat()) {
            stop();
            return;
        }
        int slot = findBestSlot();
        if (slot == NOT_FOUND) {
            stop();
            return;
        }
        if (!eating) {
            previousSlot = player.getInventory().getSelectedSlot();
            eating = true;
        }
        if (slot == OFFHAND_SLOT) {
            mc.interactionManager.interactItem(player, Hand.OFF_HAND);
        } else {
            player.getInventory().setSelectedSlot(slot);
        }
        mc.options.useKey.setPressed(true);
    }

    private boolean shouldEat() {
        var player = mc.player;
        boolean hungry = player.getHungerManager().getFoodLevel() <= hunger.getAsDouble();
        boolean hurt = health.getAsDouble() > 0 && player.getHealth() <= health.getAsDouble();
        return hungry || hurt;
    }

    private int findBestSlot() {
        var player = mc.player;
        var inventory = player.getInventory();
        int best = NOT_FOUND;
        float bestScore = -1.0F;

        for (int slot = 0; slot < 9; slot++) {
            FoodComponent food = foodOf(inventory.getStack(slot));
            if (food == null) continue;
            float score = food.nutrition() + food.saturation();
            if (score > bestScore) {
                bestScore = score;
                best = slot;
            }
        }

        FoodComponent offhand = foodOf(player.getOffHandStack());
        if (offhand != null && offhand.nutrition() + offhand.saturation() > bestScore) {
            best = OFFHAND_SLOT;
        }
        return best;
    }

    private static FoodComponent foodOf(ItemStack stack) {
        if (stack.isEmpty() || NEVER_EAT.contains(stack.getItem())) return null;
        FoodComponent food = stack.get(DataComponentTypes.FOOD);
        if (food == null) return null;
        var player = mc.player;
        return player != null && player.canConsume(food.canAlwaysEat()) ? food : null;
    }

    private void stop() {
        if (!eating) return;
        eating = false;
        mc.options.useKey.setPressed(false);
        if (mc.player != null && previousSlot >= 0) {
            mc.player.getInventory().setSelectedSlot(previousSlot);
        }
    }
}
