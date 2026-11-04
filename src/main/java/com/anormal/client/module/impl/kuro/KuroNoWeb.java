package com.anormal.client.module.impl.kuro;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.NumberSetting;

import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * Ported from kuro (dev.kuro.client.module.impl.NoWeb).
 *
 * <p>Moves you through cobwebs by adding speed on top of the web slowdown
 * (Grim-style: jump = up, sneak = down).</p>
 */
public class KuroNoWeb extends Module {
    private final NumberSetting speed = new NumberSetting("Speed",
            "Extra speed inside a web", 0.35, 0.35, 0.65, 0.05);

    public KuroNoWeb() {
        super("No Web", "Moves you through cobwebs (kuro port)", Category.KURO);
        addSetting(speed);
    }

    @Override
    public void onPreTick() {
        if (mc.player == null || mc.world == null || mc.currentScreen != null) return;
        if (!inWeb()) return;

        double fwd = (mc.options.forwardKey.isPressed() ? 1 : 0) - (mc.options.backKey.isPressed() ? 1 : 0);
        double side = (mc.options.leftKey.isPressed() ? 1 : 0) - (mc.options.rightKey.isPressed() ? 1 : 0);
        double x = 0, z = 0;
        if (fwd != 0 || side != 0) {
            double yaw = Math.toRadians(mc.player.getYaw());
            double len = Math.sqrt(fwd * fwd + side * side);
            fwd /= len;
            side /= len;
            x = (-Math.sin(yaw) * fwd + Math.cos(yaw) * side) * speed.getValue();
            z = (Math.cos(yaw) * fwd + Math.sin(yaw) * side) * speed.getValue();
        }
        double y = mc.options.jumpKey.isPressed() ? 0.65 : mc.options.sneakKey.isPressed() ? -0.65 : 0.0;
        Vec3d v = mc.player.getVelocity();
        mc.player.setVelocity(v.x + x, y, v.z + z);
    }

    private boolean inWeb() {
        Box box = mc.player.getBoundingBox();
        for (BlockPos p : BlockPos.iterate(
                BlockPos.ofFloored(box.minX, box.minY, box.minZ),
                BlockPos.ofFloored(box.maxX, box.maxY, box.maxZ))) {
            if (mc.world.getBlockState(p).isOf(Blocks.COBWEB)) return true;
        }
        return false;
    }
}
