package com.anormal.client.module.impl.kuro;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.InputForcer;
import com.anormal.client.util.kuro.KuroBlockPlacer;

import net.minecraft.client.option.KeyBinding;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/**
 * Ported from kuro (dev.kuro.client.module.impl.Scaffold).
 *
 * <p>Keeps a block in your hand and places it at the block you are looking at, so you can bridge
 * without switching slots. "Legit mode" holds the sneak key while the ground under the spot you are
 * about to walk into disappears, which is what a player bridging by hand does.</p>
 *
 * <p>Everything is 1:1 with kuro: the same {@code BlockPlacer.selectForce}, the same eye raycast
 * (4.5 blocks, outline shapes, no fluids), the same {@code MovementHelper.hasBlockBelow} box and the
 * same {@code releaseAllKeys()} on disable. Only the phase differs in name - kuro reacts to its
 * {@code MotionEvent.PRE}, this runs in {@code onPreTick}, which is the same point of the tick.</p>
 *
 * <p>kuro presses keys with {@code setDown}; Anormal's {@link InputForcer} is applied again after
 * vanilla rebuilds the key states, so a fake press is not silently dropped.</p>
 */
public class KuroScaffold extends Module {
    private static final double REACH = 4.5;

    private final BooleanSetting legitMode = new BooleanSetting("Legit Mode",
            "Sneak while there is no block below you", true);
    private final NumberSetting sneakDelay = new NumberSetting("Sneak Delay (ms)",
            "How long to keep sneaking after the edge", 100, 0, 500, 10);

    private long lastSneakTime;
    private boolean sneaking;

    public KuroScaffold() {
        super("Scaffold", "Places blocks where you look so you can bridge (kuro port)", Category.KURO);
        addSetting(legitMode);
        addSetting(sneakDelay);
    }

    @Override
    public void onDisable() {
        releaseAllKeys();
        sneaking = false;
    }

    @Override
    public void onPreTick() {
        if (mc.player == null || mc.world == null || mc.currentScreen != null || mc.interactionManager == null) return;
        if (mc.player.isGliding() || mc.player.isSpectator()) return;

        // 1. Ensure we are holding a block.
        ItemStack held = mc.player.getMainHandStack();
        if (!(held.getItem() instanceof BlockItem)) {
            for (int i = 0; i < 9; i++) {
                if (mc.player.getInventory().getStack(i).getItem() instanceof BlockItem) {
                    KuroBlockPlacer.selectForce(i);
                    break;
                }
            }
        }

        // 2. Sneak logic.
        if (legitMode.isEnabled()) {
            Vec3d motion = mc.player.getVelocity();
            double nextX = mc.player.getX() + motion.x;
            double nextZ = mc.player.getZ() + motion.z;

            boolean hasBlockBelow = hasBlockBelow(nextX, nextZ);
            if (!hasBlockBelow && mc.player.isOnGround()) {
                if (!sneaking) {
                    setKey(mc.options.sneakKey, true, true);
                    sneaking = true;
                    lastSneakTime = System.currentTimeMillis();
                }
            } else if (sneaking && System.currentTimeMillis() - lastSneakTime > sneakDelay.getValue()) {
                setKey(mc.options.sneakKey, false, true);
                sneaking = false;
            }
        }

        // 3. Place a block at whatever we are looking at.
        Vec3d eye = mc.player.getEyePos();
        Vec3d look = mc.player.getRotationVec(1.0f);
        Vec3d end = eye.add(look.multiply(REACH));

        BlockHitResult hit = mc.world.raycast(new RaycastContext(eye, end, RaycastContext.ShapeType.OUTLINE,
                RaycastContext.FluidHandling.NONE, mc.player));

        if (hit.getType() != HitResult.Type.BLOCK) return;

        var placePos = hit.getBlockPos().offset(hit.getSide());
        if (placePos.equals(mc.player.getBlockPos()) || placePos.equals(mc.player.getBlockPos().up())) return;

        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
        mc.player.swingHand(Hand.MAIN_HAND);
    }

    /** kuro MovementHelper.hasBlockBelow: a 0.6 x 1.0 x 0.6 box under the given spot must have collisions. */
    private boolean hasBlockBelow(double x, double z) {
        Box box = new Box(x - 0.3, mc.player.getY() - 1.0, z - 0.3,
                x + 0.3, mc.player.getY(), z + 0.3);
        return !mc.world.isSpaceEmpty(mc.player, box);
    }

    /** kuro MovementHelper.setKey; {@code forced} additionally keeps the press alive across vanilla's key refresh. */
    private void setKey(KeyBinding key, boolean pressed, boolean forced) {
        if (key == null) return;
        key.setPressed(pressed);
        if (forced && key == mc.options.sneakKey) InputForcer.sneak = pressed;
    }

    /** kuro MovementHelper.releaseAllKeys. */
    private void releaseAllKeys() {
        if (mc.options == null) return;
        InputForcer.clear();
        setKey(mc.options.forwardKey, false, false);
        setKey(mc.options.backKey, false, false);
        setKey(mc.options.leftKey, false, false);
        setKey(mc.options.rightKey, false, false);
        setKey(mc.options.jumpKey, false, false);
        setKey(mc.options.sneakKey, false, false);
        setKey(mc.options.sprintKey, false, false);
    }
}
