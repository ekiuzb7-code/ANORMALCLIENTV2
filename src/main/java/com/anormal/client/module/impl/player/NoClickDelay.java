package com.anormal.client.module.impl.player;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;

public class NoClickDelay extends Module {
    public NoClickDelay() {
        super("NoClickDelay", "Removes the click delay after missing an attack", Category.PLAYER);
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;
        try {
            // 1.21.11 yarn follows the Mojang name for the miss-swing reset
            mc.player.resetAttackStrengthTicker();
        } catch (Throwable ignored) {}
    }
}
