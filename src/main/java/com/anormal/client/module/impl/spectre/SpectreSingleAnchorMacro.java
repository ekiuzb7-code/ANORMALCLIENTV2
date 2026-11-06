package com.anormal.client.module.impl.spectre;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Spectre port: "Single Anchor Macro" — one key, anchor placed, charged and detonated.
 *
 * <p>Spectre runs the same cycle through a queue with jitter and AC-aware delays. This port
 * keeps the mode (single/double), the safety and guard slot idea, the switch/charge/explode/
 * cycle/place delays, jitter and switch-back, and drives the macro through the interaction
 * manager with the anchor and glowstone found in the hotbar.</p>
 */
public final class SpectreSingleAnchorMacro extends Module {
    private static final int STAGE_IDLE = 0;
    private static final int STAGE_PLACE = 1;
    private static final int STAGE_CHARGE = 2;
    private static final int STAGE_DETONATE = 3;

    public final ModeSetting mode = addSetting(new ModeSetting("Mode", "Single or double anchor", "Single", "Single", "Double"));
    public final NumberSetting safetySlot = addSetting(new NumberSetting("Safety Slot", "Totem slot kept safe", 1.0, 1.0, 9.0, 1.0));
    public final NumberSetting switchDelay = addSetting(new NumberSetting("Switch Delay", "Ticks between slot switches", 1.0, 0.0, 10.0, 1.0));
    public final NumberSetting chargeDelay = addSetting(new NumberSetting("Charge Delay", "Ticks before charging", 1.0, 1.0, 10.0, 1.0));
    public final NumberSetting explodeDelay = addSetting(new NumberSetting("Explode Delay", "Ticks before detonating", 1.0, 1.0, 10.0, 1.0));
    public final NumberSetting cycleDelay = addSetting(new NumberSetting("Cycle Delay", "Ticks between cycles", 2.0, 0.0, 20.0, 1.0));
    public final NumberSetting placeDelay = addSetting(new NumberSetting("Place Delay", "Ticks before placing", 1.0, 1.0, 10.0, 1.0));
    public final BooleanSetting doubleAnchor = addSetting(new BooleanSetting("Double Anchor", "Place the second anchor", false));
    public final BooleanSetting safeAnchor = addSetting(new BooleanSetting("Safe Anchor", "Swap to the guard slot first", false));
    public final NumberSetting guardSlot = addSetting(new NumberSetting("Guard Slot", "Totem guard slot", 9.0, 1.0, 9.0, 1.0));
    public final ModeSetting ifUnguarded = addSetting(new ModeSetting("If Unguarded", "What to do without a totem", "Detonate", "Detonate", "Cancel"));
    public final BooleanSetting switchBack = addSetting(new BooleanSetting("Switch Back", "Return to the old slot", false));
    public final NumberSetting switchBackDelay = addSetting(new NumberSetting("Switch Back Delay", "Ticks before switching back", 2.0, 0.0, 10.0, 1.0));
    public final NumberSetting jitter = addSetting(new NumberSetting("Jitter", "Random delay jitter", 0.0, 0.0, 3.0, 1.0));

    private int stage = STAGE_IDLE;
    private int wait;
    private int previousSlot = -1;
    private BlockPos anchor;
    private int switchBackIn = -1;
    private boolean secondPlaced;

    public SpectreSingleAnchorMacro() {
        super("SpectreSingleAnchorMacro", "Spectre port: one key places, charges and detonates a respawn anchor.", Category.SPECTRE);
    }

    @Override
    public void onEnable() {
        previousSlot = -1;
        anchor = null;
        stage = STAGE_IDLE;
        wait = 0;
        switchBackIn = -1;
        secondPlaced = false;
    }

    @Override
    public void onDisable() {
        restore();
        stage = STAGE_IDLE;
        anchor = null;
    }

    @Override
    public void onTick() {
        var player = mc.player;
        if (player == null || mc.world == null || mc.interactionManager == null) return;
        if (wait > 0) {
            wait--;
            return;
        }

        switch (stage) {
            case STAGE_IDLE -> {
                // Switch Back Delay: the slot comes back a few ticks after the detonation.
                if (switchBackIn > 0) {
                    if (--switchBackIn == 0) {
                        restore();
                        switchBackIn = -1;
                    }
                    return;
                }
                if (!mc.options.useKey.isPressed() && !mc.options.attackKey.isPressed()) return;
                if (previousSlot < 0) previousSlot = player.getInventory().getSelectedSlot();
                if (safeAnchor.isEnabled()) {
                    int guard = (int) guardSlot.getAsDouble() - 1;
                    ItemStack stack = player.getInventory().getStack(guard);
                    if (!stack.isOf(Items.TOTEM_OF_UNDYING) && ifUnguarded.is("Cancel")) return;
                    player.getInventory().setSelectedSlot(guard);
                }
                stage = STAGE_PLACE;
                wait = ticks(placeDelay.getAsDouble());
            }
            case STAGE_PLACE -> {
                int slot = findSlot(Items.RESPAWN_ANCHOR);
                BlockPos target = placementTarget();
                if (slot < 0 || target == null) {
                    stage = STAGE_IDLE;
                    return;
                }
                player.getInventory().setSelectedSlot(slot);
                BlockHitResult hit = new BlockHitResult(new Vec3d(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5),
                        Direction.UP, target.down(), false);
                mc.interactionManager.interactBlock(player, Hand.MAIN_HAND, hit);
                player.swingHand(Hand.MAIN_HAND);
                anchor = target;
                stage = STAGE_CHARGE;
                wait = ticks(switchDelay.getAsDouble() + chargeDelay.getAsDouble());
            }
            case STAGE_CHARGE -> {
                BlockPos pos = anchor == null ? findAnchor() : anchor;
                if (pos == null) {
                    stage = STAGE_IDLE;
                    return;
                }
                int slot = findSlot(Items.GLOWSTONE);
                if (slot < 0) {
                    stage = STAGE_IDLE;
                    return;
                }
                player.getInventory().setSelectedSlot(slot);
                BlockHitResult hit = new BlockHitResult(new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5),
                        Direction.UP, pos, false);
                mc.interactionManager.interactBlock(player, Hand.MAIN_HAND, hit);
                player.swingHand(Hand.MAIN_HAND);
                stage = STAGE_DETONATE;
                wait = ticks(explodeDelay.getAsDouble());
            }
            case STAGE_DETONATE -> {
                BlockPos pos = anchor == null ? findAnchor() : anchor;
                if (pos == null) {
                    stage = STAGE_IDLE;
                    return;
                }
                BlockHitResult hit = new BlockHitResult(new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5),
                        Direction.UP, pos, false);
                mc.interactionManager.interactBlock(player, Hand.MAIN_HAND, hit);
                player.swingHand(Hand.MAIN_HAND);
                anchor = null;
                if (doubleWanted() && !secondPlaced) {
                    // Double mode runs a second placement before the macro goes idle.
                    secondPlaced = true;
                    stage = STAGE_PLACE;
                    wait = ticks(cycleDelay.getAsDouble());
                    return;
                }
                secondPlaced = false;
                stage = STAGE_IDLE;
                wait = ticks(cycleDelay.getAsDouble() + jitter.getAsDouble());
                if (switchBack.isEnabled() && previousSlot >= 0) {
                    switchBackIn = Math.max(1, ticks(switchBackDelay.getAsDouble()));
                }
            }
            default -> stage = STAGE_IDLE;
        }
    }

    private int ticks(double value) {
        int base = Math.max(0, (int) Math.round(value));
        double jit = jitter.getAsDouble();
        if (jit > 0.0) base += ThreadLocalRandom.current().nextInt((int) jit + 1);
        return base;
    }

    /** Double is either the mode or the separate switch; both mean the same extra cycle. */
    private boolean doubleWanted() {
        return mode.is("Double") || doubleAnchor.isEnabled();
    }

    private int findSlot(net.minecraft.item.Item item) {
        var inventory = mc.player.getInventory();
        // Safety Slot is reserved for the totem, so the macro never borrows that slot.
        int reserved = (int) safetySlot.getAsDouble() - 1;
        for (int i = 0; i < 9; i++) {
            if (i == reserved) continue;
            if (inventory.getStack(i).isOf(item)) return i;
        }
        return -1;
    }

    private BlockPos findAnchor() {
        BlockPos base = mc.player.getBlockPos();
        for (BlockPos pos : BlockPos.iterate(base.add(-3, -2, -3), base.add(3, 2, 3))) {
            if (mc.world.getBlockState(pos).isOf(Blocks.RESPAWN_ANCHOR)) return pos.toImmutable();
        }
        return null;
    }

    private BlockPos placementTarget() {
        var player = mc.player;
        BlockPos feet = player.getBlockPos();
        for (Direction direction : Direction.values()) {
            if (direction.getAxis().isVertical()) continue;
            BlockPos side = feet.offset(direction);
            if (!mc.world.getBlockState(side).isAir()) continue;
            if (mc.world.getBlockState(side.down()).isSolidBlock(mc.world, side.down())) {
                return side;
            }
        }
        BlockPos below = feet.down();
        return mc.world.getBlockState(below).isAir() ? null : feet;
    }

    private void restore() {
        if (switchBack.isEnabled() && previousSlot >= 0 && mc.player != null) {
            mc.player.getInventory().setSelectedSlot(previousSlot);
        }
        previousSlot = -1;
    }
}
