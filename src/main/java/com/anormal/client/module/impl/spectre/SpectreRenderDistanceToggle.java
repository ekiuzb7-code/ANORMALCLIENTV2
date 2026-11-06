package com.anormal.client.module.impl.spectre;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.NumberSetting;

/**
 * Spectre port: "Render Distance Toggle" — reloads chunks when you go below a Y level.
 */
public final class SpectreRenderDistanceToggle extends Module {
    private static final int RESTORE_AFTER_TICKS = 10;

    public final NumberSetting lowDistance = addSetting(new NumberSetting("Low Distance", "Render distance while reloading", 2, 2, 32, 1));
    public final NumberSetting resetY = addSetting(new NumberSetting("Reset Y", "Y level that triggers the toggle", -1, -64, 320, 1));

    private boolean armed = true;
    private boolean restoring;
    private int timer;
    private int savedDistance = -1;

    public SpectreRenderDistanceToggle() {
        super("SpectreRenderDistanceToggle", "Spectre port: reloads chunks when you go below a Y level.", Category.SPECTRE);
    }

    @Override
    public void onEnable() {
        armed = true;
        restoring = false;
        timer = 0;
        savedDistance = -1;
    }

    @Override
    public void onDisable() {
        if (restoring) restore();
    }

    @Override
    public void onTick() {
        if (mc.player != null && mc.world != null) {
            int y = mc.player.getBlockY();
            int line = (int) resetY.getAsDouble();
            if (!restoring && y > line + 2) {
                armed = true;
            }
            if (armed && y <= line) {
                armed = false;
                restoring = true;
                timer = 0;
                savedDistance = mc.options.getViewDistance().getValue();
                apply((int) lowDistance.getAsDouble());
            } else if (restoring && ++timer >= RESTORE_AFTER_TICKS) {
                restore();
            }
        } else if (restoring) {
            restore();
        }
    }

    private void restore() {
        if (savedDistance >= 0) apply(savedDistance);
        restoring = false;
    }

    private void apply(int distance) {
        mc.options.getViewDistance().setValue(distance);
        mc.options.sendClientSettings();
    }
}
