package com.anormal.client.module.impl.spectre;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.module.ModuleManager;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;

/**
 * Spectre port: "Trident Util" — use tridents out of water and charge them faster.
 *
 * <p>Spectre exposes two accessors that its trident code reads; this port exposes the same
 * two and wires them into {@code SpectreTridentMixin} through {@link #allowOutOfWater()} and
 * {@link #minChargeTicks(int)}.</p>
 */
public final class SpectreTridentUtil extends Module {
    public final NumberSetting chargeScale = addSetting(new NumberSetting("Charge Scale", "Multiplier for the trident charge time (0 = instant)", 0.0, 0.0, 1.0, 0.1));
    public final BooleanSetting noWater = addSetting(new BooleanSetting("No Water", "Allow tridents out of water", true));

    public SpectreTridentUtil() {
        super("SpectreTridentUtil", "Spectre port: use tridents out of water and charge them faster.", Category.SPECTRE);
    }

    private static SpectreTridentUtil active() {
        SpectreTridentUtil module = ModuleManager.getModule(SpectreTridentUtil.class);
        return module != null && module.isEnabled() ? module : null;
    }

    public static boolean allowOutOfWater() {
        SpectreTridentUtil module = active();
        return module != null && module.noWater.isEnabled();
    }

    public static int minChargeTicks(int vanilla) {
        SpectreTridentUtil module = active();
        if (module == null) return vanilla;
        return Math.max(1, Math.round(vanilla * module.chargeScale.getValue().floatValue()));
    }
}
