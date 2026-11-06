package com.anormal.client.module.impl.spectre;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.NumberSetting;

/**
 * Spectre port: "Slow Swing" — control hand swing animation speed.
 */
public final class SpectreSlowSwing extends Module {
    public final NumberSetting speed = addSetting(new NumberSetting("Speed", "Swing speed multiplier", 0.25, 0.1, 2.0, 0.05));

    public SpectreSlowSwing() {
        super("SpectreSlowSwing", "Spectre port: control hand swing animation speed.", Category.SPECTRE);
    }

    /** Called from SpectreLivingMixin on LivingEntity.getHandSwingDuration RETURN. */
    public static int modifyDuration(int vanilla) {
        SpectreSlowSwing module = com.anormal.client.module.ModuleManager.getModule(SpectreSlowSwing.class);
        if (module == null || !module.isEnabled()) return vanilla;
        double s = module.speed.getAsDouble();
        if (s <= 0.0) s = 0.1;
        return Math.max(1, Math.round((float) vanilla / (float) s));
    }
}
