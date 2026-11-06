package com.anormal.client.module.impl.spectre;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.NumberSetting;

/**
 * Spectre port: "Custom Fov" — changes your field of view (FOV).
 *
 * <p>Injected at GameRenderer.getFov RETURN (same point Zoom uses) so world,
 * HUD and screen-space effects stay consistent with the OpenGL projection.</p>
 */
public final class SpectreCustomFov extends Module {
    public final NumberSetting fov = addSetting(new NumberSetting("FOV", "Field of view in degrees", 110.0, 30.0, 160.0, 1.0));

    private int appliedFov = Integer.MIN_VALUE;

    public SpectreCustomFov() {
        super("SpectreCustomFov", "Spectre port: changes your field of view (FOV).", Category.SPECTRE);
    }

    @Override
    public void onEnable() {
        appliedFov = (int) fov.getAsDouble();
        scheduleTerrainUpdate();
    }

    @Override
    public void onDisable() {
        appliedFov = Integer.MIN_VALUE;
        scheduleTerrainUpdate();
    }

    @Override
    public void onTick() {
        if (!isEnabled() && appliedFov == Integer.MIN_VALUE) return;
        int value = (int) fov.getAsDouble();
        if (value != appliedFov) {
            appliedFov = value;
            scheduleTerrainUpdate();
        }
    }

    /** Called from GameRendererMixin at getFov RETURN while this module is active. */
    public static Double apply(Double vanilla) {
        SpectreCustomFov m = com.anormal.client.module.ModuleManager.getModule(SpectreCustomFov.class);
        if (m == null || !m.isEnabled() || vanilla == null || vanilla <= 0) return null;
        return (double) m.fov.getAsDouble();
    }

    private void scheduleTerrainUpdate() {
        try {
            if (mc.world != null && mc.worldRenderer != null) {
                mc.worldRenderer.scheduleTerrainUpdate();
            }
        } catch (Throwable ignored) {
        }
    }
}
