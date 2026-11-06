package com.anormal.client.module.impl.spectre;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.block.Blocks;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.BlockPos;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Spectre port: "Crystal Macro" — breaks and places crystals while you hold right-click.
 *
 * <p>Spectre plans a break/place/obsidian step per tick with confirmation windows. This port
 * keeps the same three actions, the break range, both delays, the jitter and the auto
 * obsidian fallback (placing obsidian first when the crosshair is not on a crystal base).</p>
 */
public final class SpectreCrystalMacro extends Module {
    public final NumberSetting breakDelay = addSetting(new NumberSetting("Break Delay", "Ticks between crystal breaks", 0.0, 0.0, 20.0, 1.0));
    public final NumberSetting placeDelay = addSetting(new NumberSetting("Place Delay", "Ticks between crystal places", 0.0, 0.0, 20.0, 1.0));
    public final NumberSetting breakRange = addSetting(new NumberSetting("Break Range", "Maximum break distance", 3.0, 1.0, 4.5, 0.5));
    public final BooleanSetting autoObsidian = addSetting(new BooleanSetting("Auto Obsidian", "Place obsidian when the crosshair is not on a base", true));
    public final NumberSetting obsidianDelay = addSetting(new NumberSetting("Obsidian Delay", "Ticks between obsidian places", 3.0, 1.0, 20.0, 1.0));
    public final NumberSetting jitter = addSetting(new NumberSetting("Jitter", "Random extra delay ticks", 0.0, 0.0, 4.0, 1.0));
    public final BooleanSetting switchBack = addSetting(new BooleanSetting("Switch Back", "Return to the old hotbar slot", true));
    public final ModeSetting acProfile = addSetting(new ModeSetting("AC Profile", "Anti-cheat timing profile", "Grim-Aldenz", "Off", "Grim-Aldenz"));

    private int savedSlot = -1;
    private int ownSlot = -1;
    private int ticksSinceBreak = 1000;
    private int ticksSincePlace = 1000;
    private int extraDelay;

    public SpectreCrystalMacro() {
        super("SpectreCrystalMacro", "Spectre port: breaks and places crystals while you hold right-click.", Category.SPECTRE);
    }

    @Override
    public void onEnable() {
        savedSlot = -1;
        ownSlot = -1;
        ticksSinceBreak = 1000;
        ticksSincePlace = 1000;
    }

    @Override
    public void onDisable() {
        var player = mc.player;
        if (player != null && ownSlot >= 0 && savedSlot >= 0
                && player.getInventory().getSelectedSlot() == ownSlot && switchBack.isEnabled()) {
            player.getInventory().setSelectedSlot(savedSlot);
        }
        savedSlot = -1;
        ownSlot = -1;
    }

    @Override
    public void onTick() {
        ticksSinceBreak++;
        ticksSincePlace++;
        var player = mc.player;
        if (player == null || mc.world == null || mc.interactionManager == null || mc.currentScreen != null) return;
        if (!player.isAlive() || player.isSpectator()) return;
        if (!mc.options.useKey.isPressed()) return;

        if (mc.crosshairTarget instanceof EntityHitResult hit && hit.getEntity() instanceof EndCrystalEntity crystal) {
            if (ticksSinceBreak < delay(breakDelay)) return;
            if (player.getEyePos().distanceTo(hit.getPos()) > breakRange.getAsDouble()) return;
            if (!crystal.isAlive()) return;
            mc.interactionManager.attackEntity(player, crystal);
            player.swingHand(Hand.MAIN_HAND);
            ticksSinceBreak = 0;
            extraDelay = jitterRoll();
            ticksSincePlace = Math.max(ticksSincePlace - 1, 0);
            return;
        }

        if (!(mc.crosshairTarget instanceof BlockHitResult blockHit)) return;
        if (mc.crosshairTarget.getType() != net.minecraft.util.hit.HitResult.Type.BLOCK) return;
        BlockPos pos = blockHit.getBlockPos();
        var state = mc.world.getBlockState(pos);
        boolean base = state.isOf(Blocks.OBSIDIAN) || state.isOf(Blocks.BEDROCK);

        if (base) {
            if (ticksSincePlace < delay(placeDelay)) return;
            if (!selectItem(Items.END_CRYSTAL, player)) return;
            mc.interactionManager.interactBlock(player, Hand.MAIN_HAND, blockHit);
            player.swingHand(Hand.MAIN_HAND);
            ticksSincePlace = 0;
            extraDelay = jitterRoll();
            return;
        }

        if (!autoObsidian.isEnabled()) return;
        if (ticksSincePlace < delay(obsidianDelay)) return;
        if (!selectItem(Items.OBSIDIAN, player)) return;
        mc.interactionManager.interactBlock(player, Hand.MAIN_HAND, blockHit);
        player.swingHand(Hand.MAIN_HAND);
        ticksSincePlace = 0;
        extraDelay = jitterRoll();
    }

    private boolean selectItem(net.minecraft.item.Item item, net.minecraft.entity.player.PlayerEntity player) {
        var inventory = player.getInventory();
        if (player.getMainHandStack().isOf(item)) return true;
        int slot = -1;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = inventory.getStack(i);
            if (stack.isOf(item)) {
                slot = i;
                break;
            }
        }
        if (slot < 0) return false;
        if (savedSlot < 0) savedSlot = inventory.getSelectedSlot();
        inventory.setSelectedSlot(slot);
        ownSlot = slot;
        return true;
    }

    private int delay(NumberSetting setting) {
        int base = (int) setting.getAsDouble() + extraDelay;
        if (acProfile.is("Grim-Aldenz")) base = Math.max(1, base);
        return base;
    }

    private int jitterRoll() {
        int amount = (int) jitter.getAsDouble();
        return amount > 0 ? ThreadLocalRandom.current().nextInt(amount + 1) : 0;
    }
}
