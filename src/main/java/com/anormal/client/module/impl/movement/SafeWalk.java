package com.anormal.client.module.impl.movement;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;

import com.anormal.client.setting.BooleanSetting;
import net.minecraft.util.math.BlockPos;

public class SafeWalk extends Module {
    public final BooleanSetting onGroundOnly = new BooleanSetting("On Ground Only", "Only prevent edge falls while on ground", true);
    public final BooleanSetting blocksOnly = new BooleanSetting("Blocks Only", "Only sneak on empty edges when holding blocks", false);

    private boolean sneakedByModule = false;

    public SafeWalk() {
        super("SafeWalk", "Prevents walking off block edges without slowing down", Category.MOVEMENT);
        addSetting(onGroundOnly);
        addSetting(blocksOnly);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;
        if (onGroundOnly.isEnabled() && !mc.player.isOnGround()) {
            release();
            return;
        }
        if (blocksOnly.isEnabled() && !(mc.player.getMainHandStack().getItem() instanceof net.minecraft.item.BlockItem)) {
            release();
            return;
        }

        BlockPos under = mc.player.getBlockPos().down();
        boolean atEdge = false;
        try {
            atEdge = mc.world.isAir(under) && mc.player.isOnGround();
        } catch (Throwable ignored) {}
        if (atEdge) {
            // At edge: sneak (stand still safely)
            if (!mc.options.sneakKey.isPressed()) {
                mc.options.sneakKey.setPressed(true);
                sneakedByModule = true;
            }
        } else {
            // Safe ground: stand up again (only if WE made you sneak)
            release();
        }
    }

    private void release() {
        if (sneakedByModule) {
            try {
                mc.options.sneakKey.setPressed(false);
            } catch (Throwable ignored) {}
            sneakedByModule = false;
        }
    }

    @Override
    public void onDisable() {
        release();
    }
}
