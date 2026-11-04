package com.anormal.client.module.impl.kuro;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.InputForcer;
import com.anormal.client.util.kuro.KuroFreecam;
import com.anormal.client.util.kuro.KuroSwapLimit;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;

import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/**
 * kuro {@code AutoEat} port.
 *
 * <p>Ochlik tahlida to'xtatilgan darajadan past bo'lsa, offhand'dagi yoki
 * hotbar'dagi eng to'yimli eng yaxshi taomni yeydi va keyin slotni qaytaradi.
 * Roshka (rotten flesh), chayon ko'zi, sifir baliq, zaharli kartoshka, cho'ru
 * meva va oltin olma yemaydi.</p>
 */
public class KuroAutoEat extends Module {
    private static KuroAutoEat instance;
    private final NumberSetting hunger = new NumberSetting("Eat at hunger", "Food level to eat at (20 = full)", 14.0, 1.0, 19.0, 1.0); // food level (20 = full)

    private boolean eating;
    private int ticks, prevSlot = -1;
    private Hand hand = Hand.MAIN_HAND;

    public KuroAutoEat() {
        super("Auto Eat", "Eats the best food in your inventory when you are hungry", Category.KURO);
        instance = this;
        addSetting(hunger);
    }

    public static boolean eating() { return instance != null && instance.isEnabled() && instance.eating; }

    @Override public void onDisable() { stop(); if (prevSlot >= 0 && mc.player != null) KuroSwapLimit.selectNow(prevSlot); prevSlot = -1; }

    private static boolean isFood(ItemStack s) {
        if (s.isEmpty() || s.get(DataComponentTypes.FOOD) == null) return false;
        net.minecraft.item.Item i = s.getItem();
        return i != Items.ROTTEN_FLESH && i != Items.SPIDER_EYE && i != Items.PUFFERFISH && i != Items.POISONOUS_POTATO
                && i != Items.CHORUS_FRUIT && i != Items.GOLDEN_APPLE && i != Items.ENCHANTED_GOLDEN_APPLE;
    }

    private static int nutrition(ItemStack s) {
        FoodComponent f = s.get(DataComponentTypes.FOOD);
        return f == null ? 0 : f.nutrition();
    }

    @Override
    public void onPreTick() {
        PlayerEntity p = mc.player;
        if (p == null || mc.interactionManager == null || mc.currentScreen != null || p.isSpectator() || KuroFreecam.active()) {
            stop();
            return;
        }

        if (eating) {
            ticks++;
            if (p.isUsingItem()) return;                       // still chewing
            if (ticks > 3) { stop(); return; }                 // finished (or got interrupted)
            mc.interactionManager.interactItem(p, hand);       // not started yet: try again
            return;
        }

        if (prevSlot >= 0) { restoreSlot(); return; }          // one swap per tick: the old slot is still on its way back

        if (p.getHungerManager().getFoodLevel() > hunger.getValue().intValue()) return;

        if (isFood(p.getOffHandStack())) {
            hand = Hand.OFF_HAND;
        } else {
            int best = -1, bestN = -1;
            for (int i = 0; i < 9; i++) {
                ItemStack s = p.getInventory().getStack(i);
                if (isFood(s) && nutrition(s) > bestN) { best = i; bestN = nutrition(s); }
            }
            if (best < 0) return;
            hand = Hand.MAIN_HAND;
            if (best != p.getInventory().getSelectedSlot()) {
                int cur = p.getInventory().getSelectedSlot();
                if (!KuroSwapLimit.select(best)) return;       // one swap per tick: try again next tick
                prevSlot = cur;
            }
        }

        eating = true;
        ticks = 0;
        InputForcer.use = true;                                // kuro: ForcedKeys.force(keyUse)
        mc.options.useKey.setPressed(true);
        mc.interactionManager.interactItem(p, hand);
    }

    private void stop() {
        boolean was = eating;
        eating = false;
        InputForcer.use = false;
        if (was) syncToPhysical(mc.options.useKey);            // back to what your hand is really doing
        restoreSlot();
    }

    private void restoreSlot() {
        if (prevSlot >= 0 && mc.player != null && !KuroSwapLimit.select(prevSlot)) return;   // retried next tick
        prevSlot = -1;
    }

    /**
     * Sets a key binding to whether its physical key / mouse button is really down. (KeyBinding.updatePressedStates
     * only handles keyboard keys, so a mouse button like right click would stay "held" after we let go.)
     */
    private void syncToPhysical(KeyBinding kb) {
        boolean down = false;
        if (mc.getWindow() != null) {
            long win = mc.getWindow().getHandle();
            InputUtil.Key k = com.anormal.client.util.kuro.KuroInventoryUtils.keyBindingToKey(kb);
            if (k.getCategory() == InputUtil.Type.MOUSE) down = GLFW.glfwGetMouseButton(win, k.getCode()) == GLFW.GLFW_PRESS;
            else if (k.getCategory() == InputUtil.Type.KEYSYM) down = GLFW.glfwGetKey(win, k.getCode()) == GLFW.GLFW_PRESS;
        }
        kb.setPressed(down);
    }
}
