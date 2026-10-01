package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Hand;

public class LeftClicker extends Module {
    public final NumberSetting minCps = new NumberSetting("Min CPS", "Minimum clicks per second", 8.0, 1.0, 20.0, 1.0);
    public final NumberSetting maxCps = new NumberSetting("Max CPS", "Maximum clicks per second", 12.0, 1.0, 20.0, 1.0);
    public final BooleanSetting holdToClick = new BooleanSetting("Hold To Click", "Only click while holding attack", true);
    private long nextClick = 0L;

    public LeftClicker() {
        super("LeftClicker", "Auto left clicks at randomized CPS", Category.UZNY11);
        addSetting(minCps);
        addSetting(maxCps);
        addSetting(holdToClick);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.interactionManager == null) return;
        try {
            if (holdToClick.isEnabled() && !mc.options.attackKey.isPressed()) return;
            long now = System.currentTimeMillis();
            if (now < nextClick) return;
            double lo = Math.min(minCps.getValue(), maxCps.getValue());
            double hi = Math.max(minCps.getValue(), maxCps.getValue());
            double cps = lo + Math.random() * Math.max(0.0, hi - lo);
            nextClick = now + (long) (1000.0 / Math.max(1.0, cps));
            if (mc.targetedEntity instanceof LivingEntity le && le.isAlive() && le != mc.player) {
                mc.interactionManager.attackEntity(mc.player, le);
            }
            mc.player.swingHand(Hand.MAIN_HAND);
        } catch (Throwable ignored) {}
    }
}
