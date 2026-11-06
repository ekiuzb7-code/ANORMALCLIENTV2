package com.anormal.client.module.impl.spectre;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;

/**
 * Spectre port: "Zoom" — hold the key to zoom in.
 *
 * <p>Keeps Spectre's logarithmic smoothing and scroll steps; the FOV itself is applied by
 * the additive hook in {@code GameRendererMixin} through {@link #fovFactor()}. Spectre's
 * Cinematic Camera option flipped the vanilla smooth-camera flag; that flag is no longer
 * exposed on GameOptions in 1.21.11, so here it keeps the zoom at its slowest smoothing,
 * which is the part of the option you can actually see.</p>
 */
public final class SpectreZoom extends Module {
    private static final float MIN_ZOOM = 1.1F;
    private static final float MAX_ZOOM = 50.0F;
    private static final float SCROLL_STEP = 1.2F;
    private static final float MAX_FRAME_SECONDS = 0.1F;
    private static final float FAST_RATE = 30.0F;
    private static final float SLOW_RATE = 5.0F;
    private static final float CINEMATIC_SMOOTHNESS = 0.85F;

    public final NumberSetting zoom = addSetting(new NumberSetting("Zoom", "Target zoom factor", 4.0, 1.5, 20.0, 0.5));
    public final NumberSetting smoothness = addSetting(new NumberSetting("Smoothness", "How softly the zoom settles", 60.0, 0.0, 100.0, 5.0));
    public final BooleanSetting scroll = addSetting(new BooleanSetting("Scroll", "Change the zoom with the mouse wheel", true));
    public final BooleanSetting lowerSensitivity = addSetting(new BooleanSetting("Lower Sensitivity", "Scale mouse sensitivity while zoomed", true));
    public final BooleanSetting cinematic = addSetting(new BooleanSetting("Cinematic Camera", "Force the slowest, softest zoom", false));

    private static SpectreZoom instance;
    private static float target = 1.0F;
    private static float current = 1.0F;
    private static long lastNanos;

    public SpectreZoom() {
        super("SpectreZoom", "Spectre port: hold the key to zoom in.", Category.SPECTRE);
        instance = this;
    }

    @Override
    public void onEnable() {
        target = (float) zoom.getAsDouble();
    }

    @Override
    public void onDisable() {
        target = 1.0F;
    }

    /** Multiplier applied to the field of view while zoomed. */
    public static float fovFactor() {
        float factor = advance();
        return factor > 1.0001F ? factor : 1.0F;
    }

    /**
     * Multiplier for the mouse-look delta so aiming stays usable while zoomed.
     * Applied by {@code SpectreLookMixin}; {@code 1.0} means "leave vanilla alone".
     */
    public static double turnScale() {
        SpectreZoom module = instance;
        if (module == null || !module.isEnabled() || !module.lowerSensitivity.isEnabled()) return 1.0;
        float factor = advance();
        return factor <= 1.0001F ? 1.0 : 1.0 / factor;
    }

    /** @return true when the scroll was consumed by the zoom. */
    public static boolean scroll(double amount) {
        SpectreZoom module = instance;
        if (module == null || !module.isEnabled() || !module.scroll.isEnabled() || amount == 0.0) return false;
        float next = target * (amount > 0.0 ? SCROLL_STEP : 1.0F / SCROLL_STEP);
        target = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, next));
        return true;
    }

    private static float advance() {
        long now = System.nanoTime();
        float seconds = lastNanos == 0L ? 0.0F : Math.min(MAX_FRAME_SECONDS, (float) (now - lastNanos) / 1.0E9F);
        lastNanos = now;

        SpectreZoom module = instance;
        float smooth = module == null ? 0.0F : (float) (module.smoothness.getAsDouble() / 100.0);
        if (module != null && module.cinematic.isEnabled()) smooth = Math.max(smooth, CINEMATIC_SMOOTHNESS);
        if (smooth <= 0.0F || current == target) {
            current = target;
            return current;
        }
        float rate = FAST_RATE + (SLOW_RATE - FAST_RATE) * smooth;
        float blend = 1.0F - (float) Math.exp(-seconds * rate);
        double from = Math.log(current);
        double to = Math.log(target);
        current = (float) Math.exp(from + (to - from) * blend);
        if (Math.abs(current - target) < 0.002F) current = target;
        return current;
    }
}
