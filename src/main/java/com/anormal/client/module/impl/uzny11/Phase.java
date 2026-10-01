package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;

public class Phase extends Module {
    public final NumberSetting delay = new NumberSetting("Delay", "Ticks of noclip before restore", 60.0, 20.0, 100.0, 5.0);
    public final BooleanSetting autoDisable = new BooleanSetting("Auto Disable", "Disable after delay expires", true);
    private int ticks = 0;

    public Phase() {
        super("Phase", "Noclips for a few seconds on enable then restores", Category.UZNY11);
        addSetting(delay);
        addSetting(autoDisable);
    }

    @Override
    public void onEnable() {
        ticks = 0;
        try {
            if (mc.player != null) mc.player.setNoClip(true);
        } catch (Throwable ignored) {}
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;
        try {
            ticks++;
            if (ticks >= delay.getValue().intValue()) {
                mc.player.setNoClip(false);
                if (autoDisable.isEnabled()) setEnabled(false);
            }
        } catch (Throwable ignored) {}
    }

    @Override
    public void onDisable() {
        try {
            if (mc.player != null) mc.player.setNoClip(false);
        } catch (Throwable ignored) {}
        ticks = 0;
    }
}
