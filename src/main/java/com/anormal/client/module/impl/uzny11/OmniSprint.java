package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;

public class OmniSprint extends Module {
    public final BooleanSetting sprintCheck = new BooleanSetting("Sprint Check", "Only sprint while moving", true);

    public OmniSprint() {
        super("OmniSprint", "Sprints in all movement directions", Category.UZNY11);
        addSetting(sprintCheck);
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;
        try {
            boolean moving = mc.player.forwardSpeed != 0 || mc.player.sidewaysSpeed != 0;
            if (sprintCheck.isEnabled() && !moving) return;
            if (moving && !mc.player.isSneaking()) mc.player.setSprinting(true);
        } catch (Throwable ignored) {}
    }
}
