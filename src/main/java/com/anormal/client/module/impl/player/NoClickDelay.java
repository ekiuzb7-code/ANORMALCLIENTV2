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
            // Reset miss-swing cooldown every tick: attacks always ready
            mc.player.resetLastAttackedTicks();
        } catch (Throwable ignored) {}
    }
}
