package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ModeSetting;
import net.minecraft.util.Hand;

public class Animations extends Module {
    public final ModeSetting style = new ModeSetting("Style", "Blockhit swing style", "1.7", "1.7", "1.8");
    public final BooleanSetting offhand = new BooleanSetting("Offhand Swing", "Swing offhand on 1.7 style", true);
    private boolean wasAttacking = false;

    public Animations() {
        super("Animations", "Old blockhit style swing on attack", Category.UZNY11);
        addSetting(style);
        addSetting(offhand);
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;
        try {
            boolean attacking = mc.options.attackKey.isPressed();
            if (attacking && !wasAttacking) {
                if (style.is("1.7") && offhand.isEnabled()) mc.player.swingHand(Hand.OFF_HAND);
                else mc.player.swingHand(Hand.MAIN_HAND);
            }
            wasAttacking = attacking;
        } catch (Throwable ignored) {}
    }
}
