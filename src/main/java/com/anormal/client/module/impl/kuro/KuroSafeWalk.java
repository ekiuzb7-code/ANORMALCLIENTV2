package com.anormal.client.module.impl.kuro;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.kuro.KuroFreecam;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Ported from kuro (dev.kuro.client.module.impl.SafeWalk).
 *
 * <p>Stops you walking off an edge when the drop would hurt. Where the ground below is within
 * "Max drop" blocks (or there is water) you walk normally; where it is further, vanilla itself is
 * asked to edge-clip your movement exactly as if you were sneaking
 * (see {@code PlayerEntityMixin#anormal$clipAtLedge}, which also honours {@link #clip()}).</p>
 *
 * <p>The default of 3 matches vanilla: a 4+ block fall damages you.</p>
 */
public class KuroSafeWalk extends Module {
    private static KuroSafeWalk instance;

    private final NumberSetting maxDrop = new NumberSetting("Max drop",
            "Fall distance that still counts as safe", 3, 1, 10, 1);

    private boolean danger;

    public KuroSafeWalk() {
        super("Safe Walk", "Keeps you from walking off edges that would hurt (kuro port)", Category.KURO);
        instance = this;
        addSetting(maxDrop);
    }

    /** True while the player should be edge-clipped this tick. */
    public static boolean clip() {
        return instance != null && instance.isEnabled() && instance.danger;
    }

    @Override
    public void onDisable() {
        danger = false;
    }

    @Override
    public void onPreTick() {
        danger = false;
        if (mc.player == null || mc.world == null || mc.currentScreen != null || KuroFreecam.active()) return;
        if (!mc.player.isOnGround() || mc.player.getAbilities().flying || mc.player.isTouchingWater()) return;

        // Directions we are about to move in: held keys and current momentum.
        double f = (mc.options.forwardKey.isPressed() ? 1 : 0) - (mc.options.backKey.isPressed() ? 1 : 0);
        double s = (mc.options.leftKey.isPressed() ? 1 : 0) - (mc.options.rightKey.isPressed() ? 1 : 0);
        double yaw = Math.toRadians(mc.player.getYaw());
        double ix = -Math.sin(yaw) * f + Math.cos(yaw) * s;
        double iz = Math.cos(yaw) * f + Math.sin(yaw) * s;
        Vec3d vel = mc.player.getVelocity();

        if (check(ix, iz) || check(vel.x, vel.z)) danger = true;
    }

    /** Samples the ground just ahead (and slightly to each side) in direction (dx, dz). */
    private boolean check(double dx, double dz) {
        double len = Math.hypot(dx, dz);
        if (len < 0.02) return false;
        dx /= len;
        dz /= len;
        double px = mc.player.getX(), pz = mc.player.getZ();
        int feetY = MathHelper.floor(mc.player.getY() + 0.001);
        for (double dist : new double[]{0.35, 0.8}) {
            for (double side : new double[]{-0.3, 0.0, 0.3}) {
                double x = px + dx * dist - dz * side;
                double z = pz + dz * dist + dx * side;
                if (drops(x, z, feetY)) return true;
            }
        }
        return false;
    }

    /** True if there is nothing solid (and no water) within maxDrop blocks below the feet at (x, z). */
    private boolean drops(double x, double z, int feetY) {
        int bx = MathHelper.floor(x), bz = MathHelper.floor(z);
        for (int j = 0; j <= maxDrop.getValue().intValue(); j++) {
            BlockPos pos = new BlockPos(bx, feetY - 1 - j, bz);
            BlockState st = mc.world.getBlockState(pos);
            if (!st.getCollisionShape(mc.world, pos).isEmpty() || !st.getFluidState().isEmpty()) return false;
        }
        return true;
    }
}
