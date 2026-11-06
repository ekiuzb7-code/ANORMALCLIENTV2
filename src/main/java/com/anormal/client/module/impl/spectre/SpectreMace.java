package com.anormal.client.module.impl.spectre;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;

/**
 * Spectre port: "Mace" — swaps to a mace for smash hits while falling, with an optional
 * smash assist.
 *
 * <p>Spectre reads the smash window from the vanilla mace item; this port keeps the same
 * fall gate (minimum fall distance and a downward velocity) and the enchantment preference
 * scoring, which is what actually picks the best mace in the hotbar.</p>
 */
public final class SpectreMace extends Module {
    private static final int PREFERRED_WEIGHT = 100;

    public final BooleanSetting autoSwap = addSetting(new BooleanSetting("Auto Swap", "Swap to a mace before a smash hit", true));
    public final ModeSetting prefer = addSetting(new ModeSetting("Prefer", "Enchantment to prefer on the mace", "Density", "Density", "Breach", "Wind Burst", "Any"));
    public final BooleanSetting weaponsOnly = addSetting(new BooleanSetting("From Weapons Only", "Only arm from another melee weapon", true));
    public final BooleanSetting swapBack = addSetting(new BooleanSetting("Swap Back", "Return to the previous slot", true));
    public final NumberSetting backDelay = addSetting(new NumberSetting("Back Delay", "Ticks before switching back", 2.0, 1.0, 20.0, 1.0));
    public final BooleanSetting smashAssist = addSetting(new BooleanSetting("Smash Assist", "Attack while falling with a mace", false));
    public final ModeSetting smashMode = addSetting(new ModeSetting("Smash Mode", "When to land the smash", "Direct", "Direct", "Delayed"));
    public final NumberSetting minFall = addSetting(new NumberSetting("Min Fall", "Minimum fall distance in blocks", 4.0, 2.0, 30.0, 1.0));
    public final BooleanSetting players = addSetting(new BooleanSetting("Players", "Smash players", true));
    public final BooleanSetting hostiles = addSetting(new BooleanSetting("Hostiles", "Smash hostile mobs", false));
    public final BooleanSetting passives = addSetting(new BooleanSetting("Passives", "Smash passive mobs", false));
    public final BooleanSetting invisible = addSetting(new BooleanSetting("Invisible", "Smash invisible targets", false));

    private int ticks;
    private int maceSlot = -1;
    private int returnSlot = -1;
    private int backTick;

    public SpectreMace() {
        super("SpectreMace", "Spectre port: swaps to a mace for smash hits while falling, with an optional smash assist.", Category.SPECTRE);
    }

    @Override
    public void onEnable() {
        ticks = 0;
        maceSlot = -1;
        returnSlot = -1;
    }

    @Override
    public void onDisable() {
        endSwap(swapBack.isEnabled());
    }

    /** Called from the attack hook before the packet leaves, like Spectre's beforeAttack. */
    public void beforeAttack(PlayerEntity attacker, Entity target) {
        var player = mc.player;
        if (player == null || attacker != player || !(target instanceof LivingEntity)) return;
        if (!autoSwap.isEnabled() || !falling(player)) return;

        int slot = swapSlotFor(player);
        if (slot < 0) return;

        returnSlot = player.getInventory().getSelectedSlot();
        maceSlot = slot;
        player.getInventory().setSelectedSlot(slot);
        // The server has to know the slot before it processes the attack, otherwise the smash
        // is resolved with the previous item (the same order HitSwap uses).
        syncSlot(slot);
        if (swapBack.isEnabled()) {
            backTick = ticks + Math.max(1, (int) backDelay.getAsDouble());
        } else {
            maceSlot = -1;
            returnSlot = -1;
        }
    }

    private int swapSlotFor(PlayerEntity player) {
        ItemStack held = player.getMainHandStack();
        if (held.isOf(Items.MACE)) return -1;
        if (weaponsOnly.isEnabled() && !isMeleeWeapon(held)) return -1;
        return bestMace(player);
    }

    private int bestMace(PlayerEntity player) {
        RegistryKey<Enchantment> preferred = preferredEnchantment();
        int best = -1;
        int bestScore = -1;
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = player.getInventory().getStack(slot);
            if (!stack.isOf(Items.MACE)) continue;
            int score = 0;
            var enchantments = stack.getEnchantments();
            for (RegistryEntry<Enchantment> entry : enchantments.getEnchantments()) {
                int level = enchantments.getLevel(entry);
                score += preferred != null && entry.matchesKey(preferred) ? level * PREFERRED_WEIGHT : level;
            }
            if (score > bestScore) {
                best = slot;
                bestScore = score;
            }
        }
        return best;
    }

    private RegistryKey<Enchantment> preferredEnchantment() {
        return switch (prefer.getValue()) {
            case "Density" -> Enchantments.DENSITY;
            case "Breach" -> Enchantments.BREACH;
            case "Wind Burst" -> Enchantments.WIND_BURST;
            default -> null;
        };
    }

    private void endSwap(boolean restore) {
        var player = mc.player;
        if (restore && player != null && maceSlot >= 0 && returnSlot >= 0
                && player.getInventory().getSelectedSlot() == maceSlot) {
            player.getInventory().setSelectedSlot(returnSlot);
            syncSlot(returnSlot);
        }
        maceSlot = -1;
        returnSlot = -1;
    }

    @Override
    public void onTick() {
        ticks++;
        var player = mc.player;
        if (player == null || mc.world == null || mc.interactionManager == null) {
            if (maceSlot >= 0) endSwap(false);
            return;
        }

        if (smashAssist.isEnabled()) trySmash(player);

        if (maceSlot >= 0) {
            if (player.getInventory().getSelectedSlot() != maceSlot) {
                endSwap(false);
            } else if (ticks >= backTick && mc.currentScreen == null) {
                endSwap(true);
            }
        }
    }

    private void trySmash(PlayerEntity player) {
        if (!falling(player) || mc.currentScreen != null) return;
        double min = minFall.getAsDouble();
        if (player.fallDistance < min) return;
        if (!(mc.crosshairTarget instanceof EntityHitResult hit)) return;
        if (!(hit.getEntity() instanceof LivingEntity target) || !isSmashTarget(player, target)) return;

        boolean ready = player.getMainHandStack().isOf(Items.MACE) || swapSlotFor(player) >= 0;
        if (!ready) return;
        if (smashMode.is("Delayed") && player.distanceTo(target) > 2.6) return;

        mc.interactionManager.attackEntity(player, target);
        player.swingHand(Hand.MAIN_HAND);
    }

    private boolean isSmashTarget(PlayerEntity player, LivingEntity target) {
        if (!target.isAlive() || target == player) return false;
        if (target.isInvisible() && !invisible.isEnabled()) return false;
        if (target instanceof PlayerEntity) return players.isEnabled();
        return target instanceof HostileEntity ? hostiles.isEnabled() : passives.isEnabled();
    }

    private static boolean falling(PlayerEntity player) {
        return !player.isOnGround() && player.getVelocity().y < 0.0 && player.fallDistance > 0.0F;
    }

    /** Tells the server which hotbar slot is selected (syncSelectedSlot is private). */
    private static void syncSlot(int slot) {
        try {
            if (mc.getNetworkHandler() != null) {
                mc.getNetworkHandler().sendPacket(
                        new net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket(slot));
            }
        } catch (Throwable ignored) {
        }
    }

    private static boolean isMeleeWeapon(ItemStack stack) {
        return stack.isOf(Items.MACE) || stack.isOf(Items.NETHERITE_SWORD) || stack.isOf(Items.DIAMOND_SWORD)
                || stack.isOf(Items.IRON_SWORD) || stack.isOf(Items.STONE_SWORD) || stack.isOf(Items.WOODEN_SWORD)
                || stack.isOf(Items.NETHERITE_AXE) || stack.isOf(Items.DIAMOND_AXE) || stack.isOf(Items.IRON_AXE);
    }
}
