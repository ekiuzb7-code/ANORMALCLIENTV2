package com.anormal.client.module.impl.kuro;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.kuro.KuroFreecam;
import com.anormal.client.util.kuro.KuroSwapLimit;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.state.property.Properties;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.block.BlockState;

/**
 * kuro {@code AutoTool} port.
 *
 * <p>Hujum tugmasi blokda tutilgan holatda bo'lsa, shu blok uchun eng
 * tezkor asbob hotbar'dan tanlanadi (pickaxe tosh uchun, axe yog'och uchun...).
 * Qazish tugaganda oldingi slot qaytadi. Entity'ni ursa, menyuvni ochiq
 * bo'lsa, yarim ijod/yakka bo'lsa va yeyayotgan bo'lsa slotga tegmaydi.</p>
 */
public class KuroAutoTool extends Module {
    private final BooleanSetting switchBack = new BooleanSetting("Switch back", true);
    private final NumberSetting backDelay = new NumberSetting("Switch back delay (ticks)", "Ticks before restoring the old slot", 4.0, 0.0, 20.0, 1.0);
    private final BooleanSetting saveDurability = new BooleanSetting("Save durability", true);

    private int previous = -1, restoreIn;

    public KuroAutoTool() {
        super("Auto Tool", "Selects the fastest tool for the block you are mining", Category.KURO);
        addSetting(switchBack);
        addSetting(backDelay);
        addSetting(saveDurability);
    }

    @Override
    public void onDisable() {
        if (previous >= 0 && switchBack.isEnabled() && mc.player != null) KuroSwapLimit.selectNow(previous);
        previous = -1;
    }

    @Override
    public void onPreTick() {
        PlayerEntity p = mc.player;
        if (p == null || mc.world == null || mc.interactionManager == null) { previous = -1; return; }
        boolean blocked = mc.currentScreen != null || p.isCreative() || p.isSpectator() || KuroFreecam.active() || KuroAutoEat.eating();
        BlockHitResult hit = null;
        if (!blocked && mc.options.attackKey.isPressed() && !p.getMainHandStack().isEmpty()
                && mc.crosshairTarget instanceof BlockHitResult b && b.getType() == HitResult.Type.BLOCK) hit = b;

        if (hit != null) {
            select(p, mc.world.getBlockState(hit.getBlockPos()), hit);
            restoreIn = backDelay.getValue().intValue();
        } else if (previous >= 0 && --restoreIn <= 0) {
            restore();
        }
    }

    private void select(PlayerEntity p, BlockState st, BlockHitResult hit) {
        if (st.isAir() || st.getHardness(mc.world, hit.getBlockPos()) < 0) return;        // nothing to mine / unbreakable
        net.minecraft.entity.player.PlayerInventory inv = p.getInventory();
        int cur = inv.getSelectedSlot(), best = cur;
        float bestSpeed = speed(inv.getStack(cur), st);
        for (int i = 0; i < 9; i++) {
            if (i == cur) continue;
            ItemStack s = inv.getStack(i);
            if (saveDurability.isEnabled() && nearlyBroken(s)) continue;
            float sp = speed(s, st);
            if (sp > bestSpeed + 0.01f) { best = i; bestSpeed = sp; }
        }
        if (best != cur) {
            if (!KuroSwapLimit.select(best)) return;      // one swap per tick: try again next tick
            if (previous < 0) previous = cur;
        }
    }

    private static float speed(ItemStack s, BlockState st) { return s.isEmpty() ? 1f : s.getMiningSpeedMultiplier(st); }

    private static boolean nearlyBroken(ItemStack s) { return s.isDamageable() && s.getMaxDamage() - s.getDamage() <= 2; }

    private void restore() {
        if (previous >= 0 && switchBack.isEnabled() && mc.player != null && !KuroSwapLimit.select(previous)) return;   // retried next tick
        previous = -1;
    }
}
