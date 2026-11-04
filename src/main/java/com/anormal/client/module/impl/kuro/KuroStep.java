package com.anormal.client.module.impl.kuro;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;

import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;

/**
 * Ported from kuro (dev.kuro.client.module.impl.Step).
 *
 * <p>Walk up blocks taller than a slab by raising your step height (vanilla 0.6).
 * Servers with movement checks will flag this.</p>
 */
public class KuroStep extends Module {
    private static final double VANILLA = 0.6;

    private final NumberSetting height = new NumberSetting("Height",
            "Step height in blocks", 1.0, 1.0, 3.0, 0.1);
    private final BooleanSetting disableOnSneak = new BooleanSetting("Disable on sneak",
            "Use the vanilla step height while sneaking", false);

    public KuroStep() {
        super("Step", "Walk up full blocks by raising the step height (kuro port)", Category.KURO);
        addSetting(height);
        addSetting(disableOnSneak);
    }

    @Override
    public void onDisable() {
        set(VANILLA);
    }

    @Override
    public void onPreTick() {
        if (mc.player == null) return;
        set(disableOnSneak.isEnabled() && mc.player.isSneaking() ? VANILLA : height.getValue());
    }

    private void set(double value) {
        if (mc.player == null) return;
        try {
            EntityAttributeInstance a = mc.player.getAttributeInstance(EntityAttributes.STEP_HEIGHT);
            if (a != null && a.getBaseValue() != value) a.setBaseValue(value);
        } catch (Throwable ignored) {
        }
    }
}
