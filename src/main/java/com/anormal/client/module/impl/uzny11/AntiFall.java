package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public class AntiFall extends Module {
    public final BooleanSetting useWater = new BooleanSetting("Use Water", "Place water bucket to catch falls", true);
    public final NumberSetting delay = new NumberSetting("Delay", "Ticks between catch attempts", 10.0, 1.0, 60.0, 1.0);
    private int ticks = 0;

    public AntiFall() {
        super("AntiFall", "Simplified MLG water catch on fast falls", Category.UZNY11);
        addSetting(useWater);
        addSetting(delay);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;
        try {
            if (mc.player.isOnGround() || mc.player.getVelocity().y >= -0.6) {
                ticks = 0;
                return;
            }
            if (!useWater.isEnabled()) return;
            if (ticks++ < delay.getValue().intValue()) return;
            ticks = 0;
            int slot = -1;
            for (int i = 0; i < 9; i++) {
                ItemStack stack = mc.player.getInventory().getStack(i);
                if (stack.isEmpty()) continue;
                Identifier id = Registries.ITEM.getId(stack.getItem());
                if (id != null && id.getPath().equals("water_bucket")) {
                    slot = i;
                    break;
                }
            }
            if (slot == -1) return;
            mc.player.getInventory().setSelectedSlot(slot);
            BlockPos groundPos = mc.player.getBlockPos().down();
            BlockPos airPos = mc.player.getBlockPos();
            BlockHitResult bhr = new BlockHitResult(new Vec3d(airPos.getX() + 0.5, groundPos.getY() + 1.0, airPos.getZ() + 0.5), Direction.UP, groundPos, false);
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, bhr);
            mc.player.swingHand(Hand.MAIN_HAND);
        } catch (Throwable ignored) {}
    }
}
