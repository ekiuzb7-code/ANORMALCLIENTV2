package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;

public class NoSlowdown extends Module {
    public final BooleanSetting sprintCheck = new BooleanSetting("Sprint Check", "Keep sprinting while using items", true);

    public NoSlowdown() {
        super("NoSlowdown", "Keeps sprint while using or blocking items", Category.UZNY11);
        addSetting(sprintCheck);
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;
        try {
            if (sprintCheck.isEnabled() && mc.player.isUsingItem() && !mc.player.isSneaking()) {
                mc.player.setSprinting(true);
            }
        } catch (Throwable ignored) {}
    }
}
