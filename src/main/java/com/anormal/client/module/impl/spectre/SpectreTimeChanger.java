package com.anormal.client.module.impl.spectre;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.module.ModuleManager;
import com.anormal.client.setting.NumberSetting;

/**
 * Spectre port: "Time Changer" — sets a custom client-side time.
 *
 * <p>Spectre forces the value inside the level's environment attributes. This port keeps
 * the setting and the {@link #forcedDayTime()} accessor, and a small additive mixin
 * ({@code SpectreTimeMixin}) returns that value from {@code World#getTimeOfDay} while the
 * module is on.</p>
 */
public final class SpectreTimeChanger extends Module {
    public final NumberSetting time = addSetting(new NumberSetting("Time", "Client-side time of day", 0.0, -20000.0, 20000.0, 1.0));

    public SpectreTimeChanger() {
        super("SpectreTimeChanger", "Spectre port: makes you able to set a custom time.", Category.SPECTRE);
    }

    /** @return the forced time, or {@code null} when the module is off. */
    public static Long forcedTime() {
        SpectreTimeChanger module = ModuleManager.getModule(SpectreTimeChanger.class);
        return module != null && module.isEnabled() ? module.forcedDayTime() : null;
    }

    public long forcedDayTime() {
        return (long) time.getAsDouble();
    }
}
