package com.anormal.client.module.impl.spectre;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import net.minecraft.block.BlockState;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;

/**
 * Spectre port: "Legit Auto Tool" — switches hotbar slots like a player would when
 * mining, including the "Shard Pickaxe" special case and the instabreak drill rule.
 */
public final class SpectreLegitAutoTool extends Module {
    private static final int SWITCH_DELAY_TICKS = 2;
    private static final String SHARD_NAME = "shard pickaxe";
    private static final String SHARD_LORE = "breaks 9 blocks at once";

    public final BooleanSetting drill = addSetting(new BooleanSetting("Drill Instabreak", "Prefer a pickaxe that instantly breaks the block", true));
    public final BooleanSetting delayed = addSetting(new BooleanSetting("Delay", "Wait two ticks before switching", true));

    private BlockPos target;
    private int pending = -1;
    private int delay;
    private int origin = -1;
    private int ourSlot = -1;
    private int returnDelay;

    public SpectreLegitAutoTool() {
        super("SpectreLegitAutoTool", "Spectre port: auto tool that only switches hotbar slots like a player.", Category.SPECTRE);
    }

    @Override
    public void onEnable() {
        reset();
    }

    @Override
    public void onDisable() {
        if (origin != -1 && mc.player != null) select(origin);
        reset();
    }

    private void reset() {
        target = null;
        pending = -1;
        delay = 0;
        origin = -1;
        ourSlot = -1;
        returnDelay = 0;
    }

    @Override
    public void onTick() {
        var player = mc.player;
        if (player == null || mc.world == null) {
            reset();
            return;
        }
        PlayerInventory inventory = player.getInventory();
        int selected = inventory.getSelectedSlot();
        if (origin != -1 && selected != ourSlot) origin = -1;

        BlockPos aimed = mc.currentScreen == null && mc.options.attackKey.isPressed() ? aimedBlock() : null;
        BlockState state = aimed == null ? null : mc.world.getBlockState(aimed);
        if (aimed != null && state != null && !state.isAir()) {
            if (!aimed.equals(target)) {
                target = aimed.toImmutable();
                pending = -1;
                delay = 0;
            }

            int shard = shardSlot(inventory, state, selected);
            if (drill.isEnabled()) {
                int drillSlot = drillSlot(state, aimed, shard, inventory);
                if (drillSlot != -1) {
                    if (drillSlot != selected) select(drillSlot);
                    pending = -1;
                    return;
                }
            }

            int best = bestSlot(state, aimed, selected, shard, inventory);
            if (best == selected) {
                pending = -1;
            } else {
                if (pending != best) {
                    pending = best;
                    delay = switchDelay();
                }
                if (delay > 0) {
                    delay--;
                } else {
                    select(best);
                    pending = -1;
                }
            }
        } else {
            target = null;
            pending = -1;
            delay = 0;
            giveBack();
        }
    }

    private BlockPos aimedBlock() {
        return mc.crosshairTarget instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK
                ? hit.getBlockPos() : null;
    }

    private int drillSlot(BlockState state, BlockPos pos, int shard, PlayerInventory inventory) {
        if (shard != -1) {
            return breakingDelta(shard, state, pos) >= 1.0F ? shard : -1;
        }
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = inventory.getStack(slot);
            if (stack.isOf(Items.NETHERITE_PICKAXE) && !nextDamageWillBreak(stack)
                    && breakingDelta(slot, state, pos) >= 1.0F) {
                return slot;
            }
        }
        return -1;
    }

    private int bestSlot(BlockState state, BlockPos pos, int selected, int shard, PlayerInventory inventory) {
        ItemStack held = inventory.getStack(selected);
        boolean dropHeld = selected == ourSlot && origin != -1 && nextDamageWillBreak(held);
        int best = selected;
        float bestDelta = dropHeld ? -1.0F : breakingDelta(selected, state, pos);

        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = inventory.getStack(slot);
            if (slot == selected || stack.isEmpty() || nextDamageWillBreak(stack)) continue;
            float delta = breakingDelta(slot, state, pos);
            if (delta > bestDelta) {
                bestDelta = delta;
                best = slot;
            }
        }
        return shard != -1 && inventory.getStack(best).isIn(ItemTags.PICKAXES) ? shard : best;
    }

    private static int shardSlot(PlayerInventory inventory, BlockState state, int selected) {
        if (isShardFor(inventory.getStack(selected), state)) return selected;
        for (int slot = 0; slot < 9; slot++) {
            if (slot != selected && isShardFor(inventory.getStack(slot), state)) return slot;
        }
        return -1;
    }

    private static boolean isShardFor(ItemStack stack, BlockState state) {
        return !nextDamageWillBreak(stack) && stack.isSuitableFor(state) && isShardPickaxe(stack);
    }

    private static boolean isShardPickaxe(ItemStack stack) {
        if (!stack.isIn(ItemTags.PICKAXES)) return false;
        if (normalize(stack.getName().getString()).contains(SHARD_NAME)) return true;
        LoreComponent lore = stack.get(DataComponentTypes.LORE);
        if (lore == null) return false;
        for (Text line : lore.lines()) {
            if (normalize(line.getString()).contains(SHARD_LORE)) return true;
        }
        return false;
    }

    private static String normalize(String text) {
        return text == null ? "" : text.toLowerCase(java.util.Locale.ROOT).trim();
    }

    private static boolean nextDamageWillBreak(ItemStack stack) {
        return stack.isDamageable() && stack.getMaxDamage() - stack.getDamage() <= 1;
    }

    private float breakingDelta(int slot, BlockState state, BlockPos pos) {
        var player = mc.player;
        PlayerInventory inventory = player.getInventory();
        int previous = inventory.getSelectedSlot();
        inventory.setSelectedSlot(slot);
        try {
            return Math.max(0.0F, state.calcBlockBreakingDelta(player, mc.world, pos));
        } finally {
            inventory.setSelectedSlot(previous);
        }
    }

    private void select(int slot) {
        if (mc.player == null) return;
        PlayerInventory inventory = mc.player.getInventory();
        if (origin == -1) origin = inventory.getSelectedSlot();
        inventory.setSelectedSlot(slot);
        ourSlot = slot;
        returnDelay = switchDelay();
    }

    private int switchDelay() {
        return delayed.isEnabled() ? SWITCH_DELAY_TICKS : 0;
    }

    private void giveBack() {
        if (origin == -1) return;
        if (returnDelay > 0) {
            returnDelay--;
        } else {
            select(origin);
            origin = -1;
        }
    }
}
