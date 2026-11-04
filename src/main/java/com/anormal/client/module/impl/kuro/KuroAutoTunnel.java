package com.anormal.client.module.impl.kuro;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.InputForcer;
import com.anormal.client.util.kuro.KuroFreecam;
import com.anormal.client.util.kuro.KuroSwapLimit;

import net.minecraft.client.option.KeyBinding;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.block.BlockState;

/**
 * kuro {@code AutoTunnel} port.
 *
 * <p>Qaragan yo'nalishingizda to'g'ri tunnel qazadi (1 yoki 2 blok balandlik)
 * va ochilganda oldinga yuradi. Freecam yoqilgan paytda ham ishlaydi: tunnel
 * real tanangiz qaragan tomonga boradi (Freecam faqat kamerani boshqaradi),
 * shuning uchun qazib turish paytida erkin qaray olasiz. Qazish bloki yonida
 * suyuqlik bo'lsa va barbmaslik bo'lsa to'xtaydi.</p>
 */
public class KuroAutoTunnel extends Module {
    private static KuroAutoTunnel instance;

    private final NumberSetting reach = new NumberSetting("Reach", "Dig distance ahead", 4.0, 1.0, 4.5, 0.5);
    private final BooleanSetting tall = new BooleanSetting("2 high", true);
    private final BooleanSetting walk = new BooleanSetting("Walk forward", true);
    private final BooleanSetting autoTool = new BooleanSetting("Auto tool", true);

    private BlockPos target;
    private boolean pressedByUs;
    private int prevSlot = -1;

    public KuroAutoTunnel() {
        super("Auto Tunnel", "Digs a tunnel straight ahead in the direction you face", Category.KURO);
        instance = this;
        addSetting(reach);
        addSetting(tall);
        addSetting(walk);
        addSetting(autoTool);
    }

    /** True while a block is being dug (read by the interaction manager mixin). */
    public static boolean breaking() { return instance != null && instance.isEnabled() && instance.target != null; }

    @Override public void onDisable() { stopAll(); if (prevSlot >= 0 && mc.player != null) KuroSwapLimit.selectNow(prevSlot); prevSlot = -1; }

    @Override
    public void onPreTick() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null
                || mc.currentScreen != null || mc.player.isSpectator() || KuroAutoEat.eating()) {
            stopAll();
            return;
        }

        Direction dir = mc.player.getHorizontalFacing();
        BlockPos base = mc.player.getBlockPos();
        int height = tall.isEnabled() ? 2 : 1;
        int ahead = (int) Math.floor(reach.getValue());

        BlockPos next = null;
        boolean hazard = false;
        outer:
        for (int i = 1; i <= ahead; i++) {
            for (int h = 0; h < height; h++) {
                BlockPos pos = base.add(dir.getOffsetX() * i, h, dir.getOffsetZ() * i);
                BlockState st = mc.world.getBlockState(pos);
                if (!st.getFluidState().isEmpty()) { hazard = true; break outer; }
                if (st.isAir() || st.getCollisionShape(mc.world, pos).isEmpty()) continue;   // already open
                if (st.getHardness(mc.world, pos) < 0 || liquidNextTo(pos)) { hazard = true; break outer; }
                next = pos;
                break outer;
            }
        }

        // walk once the square directly ahead is open
        boolean clear1 = true;
        for (int h = 0; h < height; h++) {
            BlockPos pos = base.add(dir.getOffsetX(), h, dir.getOffsetZ());
            BlockState st = mc.world.getBlockState(pos);
            if (!st.getFluidState().isEmpty() || !st.getCollisionShape(mc.world, pos).isEmpty()) clear1 = false;
        }
        // forced, so Freecam releasing the real keys can't stop it
        if (walk.isEnabled() && clear1 && !hazard) { InputForcer.forward = true; pressedByUs = true; }
        else releaseKey();

        if (next == null || mc.player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(next)) > reach.getValue() * reach.getValue()) {
            cancelDig();
            return;
        }

        if (autoTool.isEnabled()) selectTool(mc.world.getBlockState(next));
        target = next;
        mc.interactionManager.updateBlockBreakingProgress(next, dir.getOpposite());
        mc.player.swingHand(Hand.MAIN_HAND);
    }

    private boolean liquidNextTo(BlockPos pos) {
        for (Direction d : Direction.values()) {
            if (!mc.world.getFluidState(pos.offset(d)).isEmpty()) return true;
        }
        return false;
    }

    private void selectTool(BlockState st) {
        int best = mc.player.getInventory().getSelectedSlot();
        float bestSpeed = mc.player.getInventory().getStack(best).getMiningSpeedMultiplier(st);
        for (int i = 0; i < 9; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            float sp = s.getMiningSpeedMultiplier(st);
            if (sp > bestSpeed) { best = i; bestSpeed = sp; }
        }
        if (best != mc.player.getInventory().getSelectedSlot()) {
            int cur = mc.player.getInventory().getSelectedSlot();
            if (!KuroSwapLimit.select(best)) return;                    // one swap per tick: try again next tick
            if (prevSlot < 0) prevSlot = cur;
        }
    }

    private void cancelDig() {
        if (target == null) return;
        target = null;                                         // before cancelling, so the mixin lets it through
        if (mc.interactionManager != null) mc.interactionManager.cancelBlockBreaking();
    }

    private void releaseKey() {
        if (pressedByUs) {
            pressedByUs = false;
            InputForcer.forward = false;
            try { KeyBinding.updatePressedStates(); } catch (Throwable ignored) {}   // kuro: KeyMapping.setAll()
        }
    }

    private void stopAll() {
        releaseKey();
        cancelDig();
        if (prevSlot >= 0 && mc.player != null && !KuroSwapLimit.select(prevSlot)) return;   // retried next tick
        prevSlot = -1;
    }
}
