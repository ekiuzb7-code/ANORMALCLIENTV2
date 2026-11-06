package com.anormal.client.module.impl.spectre;

import com.anormal.client.mixin.GammaAccessor;
import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.NumberSetting;

/**
 * Spectre port: "Fullbright" — brightens dark areas by overriding the lightmap gamma.
 *
 * <p>Spectre exposes a gamma value that its lightmap mixin reads. This port keeps the same
 * setting and writes it straight into the vanilla gamma option while the module is on,
 * restoring the previous value when it turns off. The write bypasses
 * {@code SimpleOption.setValue}, which clamps gamma to 0..1, so values above 1 (the whole
 * point of fullbright) actually stick — same raw write Anormal's own Fullbright uses.</p>
 */
public final class SpectreFullbright extends Module {
    public final NumberSetting gamma = addSetting(new NumberSetting("Gamma", "Target gamma", 8.0, 1.0, 16.0, 0.5));

    private Double previous;

    public SpectreFullbright() {
        super("SpectreFullbright", "Spectre port: brighten dark areas by overriding the client gamma.", Category.SPECTRE);
    }

    @Override
    public void onEnable() {
        previous = mc.options.getGamma().getValue();
        apply();
    }

    @Override
    public void onDisable() {
        if (previous != null) rawGamma(previous);
        previous = null;
    }

    @Override
    public void onTick() {
        apply();
    }

    private void apply() {
        rawGamma(Math.max(1.0, gamma.getAsDouble()));
    }

    /** Vanilla clamps {@code setValue} to 0..1; writing the raw field keeps values above 1. */
    private void rawGamma(double value) {
        try {
            ((GammaAccessor) (Object) mc.options.getGamma()).setRawValue(value);
        } catch (Throwable t) {
            try {
                mc.options.getGamma().setValue(value);
            } catch (Throwable ignored) {
            }
        }
    }

    /** Effective gamma, for anything that wants to read it (Spectre's {@code gamma()}). */
    public float gamma() {
        return (float) Math.max(1.0, gamma.getAsDouble());
    }
}
