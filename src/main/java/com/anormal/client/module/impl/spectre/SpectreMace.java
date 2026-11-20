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
 * <p>The smash window is the vanilla one ({@code MaceItem.canSmashAttack}): airborne, falling and
 * past the 1.5 block fall distance. The old port used its own looser test, so it swapped while
 * merely walking off a step and then hit with the mace outside the window - no smash damage.</p>
 *
 * <p>Smash Assist now swaps <b>before</b> it attacks (it used to swing with whatever was in hand,
 * which dealt sword damage), respects the attack cooldown and has a per-target cooldown so it does
 * not spam weak hits. "Delayed" waits for the moment the target is actually below you instead of
 * the old distance gate that blocked every hit beyond 2.6 blocks.</p>
 */
public final class SpectreMace extends Module {
    private static final int PREFERRED_WEIGHT = 100;
    /** Vanilla mace smash needs this much fall distance. */
    private static final double SMASH_FALL = 1.5;
    private static final double REACH_MARGIN = 0.35;
    private static final int TARGET_COOLDOWN = 10;

    public final BooleanSetting autoSwap = addSetting(new BooleanSetting("Auto Swap", "Swap to a mace before a smash hit", true));
    public final ModeSetting prefer = addSetting(new ModeSetting("Prefer", "Enchantment to prefer on the mace", "Density", "Density", "Breach", "Wind Burst", "Any"));
    public final BooleanSetting weaponsOnly = addSetting(new BooleanSetting("From Weapons Only", "Only arm from another melee weapon", true));
    public final BooleanSetting swapBack = addSetting(new BooleanSetting("Swap Back", "Return to the previous slot", true));
    public final NumberSetting backDelay = addSetting(new NumberSetting("Back Delay", "Ticks before switching back", 2.0, 1.0, 20.0, 1.0));
    public final BooleanSetting smashAssist = addSetting(new BooleanSetting("Smash Assist", "Attack while falling with a mace", false));
    public final ModeSetting smashMode = addSetting(new ModeSetting("Smash Mode", "When to land the smash", "Direct", "Direct", "Delayed"));
    public final NumberSetting minFall = addSetting(new NumberSetting("Min Fall (blocks)", "Minimum fall distance in blocks", 4.0, 2.0, 30.0, 1.0));
    public final BooleanSetting players = addSetting(new BooleanSetting("Players", "Smash players", true));
    public final BooleanSetting hostiles = addSetting(new BooleanSetting("Hostiles", "Smash hostile mobs", false));
    public final BooleanSetting passives = addSetting(new BooleanSetting("Passives", "Smash passive mobs", false));
    public final BooleanSetting invisible = addSetting(new BooleanSetting("Invisible", "Smash invisible targets", false));
    public final BooleanSetting skipFriends = addSetting(new BooleanSetting("Skip Friends", "Never smash a friend", true));

    private int ticks;
    private int maceSlot = -1;
    private int returnSlot = -1;
    private int backTick;
    private int lastAttackTick = -100;
    private Entity lastTarget;
    private int lastTargetTick = -100;

    public SpectreMace() {
        super("SpectreMace", "Spectre port: swaps to a mace for smash hits while falling, with an optional smash assist.", Category.SPECTRE);
        prefer.visibleIf(autoSwap::isEnabled);
        weaponsOnly.visibleIf(autoSwap::isEnabled);
        swapBack.visibleIf(autoSwap::isEnabled);
        backDelay.visibleIf(() -> autoSwap.isEnabled() && swapBack.isEnabled());
        smashMode.visibleIf(smashAssist::isEnabled);
        minFall.visibleIf(smashAssist::isEnabled);
        players.visibleIf(smashAssist::isEnabled);
        hostiles.visibleIf(smashAssist::isEnabled);
        passives.visibleIf(smashAssist::isEnabled);
        invisible.visibleIf(smashAssist::isEnabled);
        skipFriends.visibleIf(smashAssist::isEnabled);
    }

    @Override
    public void onEnable() {
        ticks = 0;
        maceSlot = -1;
        returnSlot = -1;
        lastAttackTick = -100;
        lastTarget = null;
    }

    @Override
    public void onDisable() {
        endSwap(swapBack.isEnabled());
    }

    /** Called from the attack hook before the packet leaves, like Spectre's beforeAttack. */
    public void beforeAttack(PlayerEntity attacker, Entity target) {
        var player = mc.player;
        if (player == null || attacker != player || !(target instanceof LivingEntity)) return;
        if (!canSmash(player)) return; // outside the vanilla smash window: do not arm

        int slot = swapSlotFor(player);
        if (slot < 0) return;

        returnSlot = player.getInventory().getSelectedSlot();
        maceSlot = slot;
        player.getInventory().setSelectedSlot(slot);
        // The server has to know the slot before it processes the attack, otherwise the smash
        // is resolved with the previous item (the same order HitSwap uses).
        syncSlot(slot);
        lastAttackTick = ticks;
        if (swapBack.isEnabled()) {
            backTick = ticks + Math.max(1, (int) backDelay.getAsDouble());
        } else {
            maceSlot = -1;
            returnSlot = -1;
        }
    }

    private int swapSlotFor(PlayerEntity player) {
        if (!autoSwap.isEnabled()) return -1;
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
            } else if (ticks >= backTick && ticks > lastAttackTick && mc.currentScreen == null) {
                endSwap(true);
            }
        }
    }

    private void trySmash(PlayerEntity player) {
        if (mc.currentScreen != null) return;
        // Smash Assist must hit inside the smash window, not on any fall.
        if (!canSmash(player)) return;
        if (player.fallDistance < minFall.getAsDouble()) return;
        // Attack cooldown: swinging at 0% charge only sends weak hits and kills the timing.
        if (player.getAttackCooldownProgress(0.5f) <= 0.9f) return;
        if (!(mc.crosshairTarget instanceof EntityHitResult hit)) return;
        if (!(hit.getEntity() instanceof LivingEntity target) || !isSmashTarget(player, target)) return;

        // "Delayed" waits until the target is actually below you (the moment the smash lands);
        // "Direct" fires as soon as everything else is ready.
        if (smashMode.is("Delayed") && target.getY() >= player.getY()) return;
        if (player.distanceTo(target) > reach(player)) return;

        // Per-target cooldown so a long fall does not spam attacks on the same entity.
        if (target == lastTarget && ticks - lastTargetTick < TARGET_COOLDOWN) return;

        // Arm the mace FIRST: the old code attacked with whatever was in hand.
        if (!player.getMainHandStack().isOf(Items.MACE)) {
            int slot = swapSlotFor(player);
            if (slot < 0) return;
            returnSlot = player.getInventory().getSelectedSlot();
            maceSlot = slot;
            player.getInventory().setSelectedSlot(slot);
            syncSlot(slot);
            if (swapBack.isEnabled()) {
                backTick = ticks + Math.max(1, (int) backDelay.getAsDouble());
            } else {
                maceSlot = -1;
                returnSlot = -1;
            }
        }

        mc.interactionManager.attackEntity(player, target);
        player.swingHand(Hand.MAIN_HAND);
        lastAttackTick = ticks;
        lastTarget = target;
        lastTargetTick = ticks;
    }

    private boolean isSmashTarget(PlayerEntity player, LivingEntity target) {
        if (!target.isAlive() || target == player) return false;
        if (target.isInvisible() && !invisible.isEnabled()) return false;
        if (target instanceof PlayerEntity other) {
            String name = other.getGameProfile().name();
            if (skipFriends.isEnabled() && name != null
                    && com.anormal.client.module.impl.client.Friends.isFriend(name)) return false;
            return players.isEnabled();
        }
        return target instanceof HostileEntity ? hostiles.isEnabled() : passives.isEnabled();
    }

    /** Vanilla mace smash window: airborne, moving down, past the 1.5 block fall. */
    private static boolean canSmash(PlayerEntity player) {
        return !player.isOnGround()
                && player.getVelocity().y < 0.0
                && player.fallDistance > SMASH_FALL;
    }

    private static double reach(PlayerEntity player) {
        try {
            return Math.max(1.0, player.getEntityInteractionRange() - REACH_MARGIN);
        } catch (Throwable ignored) {
            return 3.0;
        }
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
