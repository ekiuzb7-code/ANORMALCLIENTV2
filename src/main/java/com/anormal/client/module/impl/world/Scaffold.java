package com.anormal.client.module.impl.world;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.item.BlockItem;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public class Scaffold extends Module {
    public final ModeSetting mode = new ModeSetting("Mode", "Bridging mode", "Legit", "Legit", "GodBridge", "TellyBridge");
    public final BooleanSetting showBlockCount = new BooleanSetting("Block Count", "Displays blocks left in hotbar", true);
    public final NumberSetting pitchCheck = new NumberSetting("Pitch Check", "Only when aiming below this pitch", 50.0, 0.0, 90.0, 1.0);
    public final NumberSetting activationBlocks = new NumberSetting("Activation Blocks", "Manual blocks before takeover", 3.0, 0.0, 20.0, 1.0);

    public Scaffold() {
        super("Scaffold", "Assists with bridging techniques by placing blocks below", Category.WORLD);
        addSetting(mode);
        addSetting(showBlockCount);
        addSetting(pitchCheck);
        addSetting(activationBlocks);
    }

    @Override
    public void onDisable() {
        if (mc.options != null) mc.options.sneakKey.setPressed(false);
        placedStreak = 0;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;
        if (mc.player.getPitch() < pitchCheck.getValue()) return;

        if (mode.is("Legit")) {
            BlockPos under = mc.player.getBlockPos().down();
            if (mc.world.isAir(under) && mc.player.isOnGround()) {
                mc.options.sneakKey.setPressed(true);
            } else {
                mc.options.sneakKey.setPressed(false);
            }
        } else {
            // Auto place after bridging progress passes Activation Blocks
            BlockPos below = mc.player.getBlockPos().down();
            if (mc.world.isAir(below)) {
                placedStreak++;
                if (placedStreak < activationBlocks.getValue()) return;
                if (mc.player.getMainHandStack().getItem() instanceof BlockItem) {
                    BlockHitResult bhr = new BlockHitResult(new Vec3d(below.getX() + 0.5, below.getY() + 1.0, below.getZ() + 0.5), Direction.UP, below, false);
                    mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, bhr);
                    mc.player.swingHand(Hand.MAIN_HAND);
                }
            } else {
                placedStreak = 0;
            }
        }
    }

    private int placedStreak = 0;
}
