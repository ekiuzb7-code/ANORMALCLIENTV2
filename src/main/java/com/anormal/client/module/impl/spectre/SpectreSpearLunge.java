package com.anormal.client.module.impl.spectre;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Spectre port: "Spear Swap" — swaps to your Lunge spear, lunges and swaps back.
 *
 * <p>Spectre arms the lunge on its own keybind; this port arms it on the attack key instead
 * (the 1.21.11 spear lunge is an attack), keeping the cooldown, the pending window, the
 * best-spear selection by Lunge level, the optional switch-back and the jitter.</p>
 */
public final class SpectreSpearLunge extends Module {
    private static final int BUFFER_TICKS = 10;

    public final NumberSetting cooldown = addSetting(new NumberSetting("Cooldown", "Ticks between lunges", 12.0, 0.0, 40.0, 1.0));
    public final BooleanSetting switchBack = addSetting(new BooleanSetting("Switch Back", "Return to the previous slot after the lunge", true));
    public final NumberSetting backDelay = addSetting(new NumberSetting("Back Delay", "Ticks before switching back", 3.0, 1.0, 20.0, 1.0));
    public final NumberSetting jitter = addSetting(new NumberSetting("Jitter", "Random extra delay ticks", 1.0, 0.0, 4.0, 1.0));

    private int ticks;
    private int readyTick;
    private boolean pending;
    private int pendingTicks;
    private boolean returning;
    private int spearSlot = -1;
    private int returnSlot = -1;
    private int returnTick;

    public SpectreSpearLunge() {
        super("SpectreSpearLunge", "Spectre port: swaps to your Lunge spear, lunges and swaps back.", Category.SPECTRE);
    }

    @Override
    public void onEnable() {
        reset();
    }

    @Override
    public void onDisable() {
        if (returning) finishReturn(switchBack.isEnabled());
        reset();
    }

    private void reset() {
        pending = false;
        pendingTicks = 0;
        returning = false;
        spearSlot = -1;
        returnSlot = -1;
        readyTick = ticks;
    }

    @Override
    public void onTick() {
        ticks++;
        var player = mc.player;
        if (player == null || mc.world == null || mc.interactionManager == null) {
            if (returning || pending) reset();
            return;
        }

        if (mc.currentScreen == null && ticks >= readyTick && !returning && !pending
                && mc.options.attackKey.isPressed()) {
            pending = true;
            pendingTicks = 0;
        }

        if (returning) followReturn(player);
        else if (pending) tryLunge(player);
    }

    private void followReturn(PlayerEntity player) {
        if (player.getInventory().getSelectedSlot() != spearSlot) {
            finishReturn(false);
            return;
        }
        if ((ticks >= returnTick) && mc.currentScreen == null) finishReturn(true);
    }

    private void finishReturn(boolean restore) {
        var player = mc.player;
        if (restore && player != null && returnSlot >= 0 && player.getInventory().getSelectedSlot() == spearSlot) {
            player.getInventory().setSelectedSlot(returnSlot);
        }
        returning = false;
        spearSlot = -1;
        returnSlot = -1;
    }

    private void tryLunge(PlayerEntity player) {
        if (++pendingTicks > BUFFER_TICKS) {
            pending = false;
            return;
        }
        if (!canLunge(player)) {
            pending = false;
            return;
        }
        int slot = bestSpear(player);
        if (slot < 0) {
            pending = false;
            return;
        }
        ItemStack spear = player.getInventory().getStack(slot);
        if (!spear.contains(DataComponentTypes.PIERCING_WEAPON)) {
            pending = false;
            return;
        }

        pending = false;
        int previous = player.getInventory().getSelectedSlot();
        player.getInventory().setSelectedSlot(slot);
        player.swingHand(Hand.MAIN_HAND);
        readyTick = ticks + (int) cooldown.getAsDouble();

        if (previous != slot && switchBack.isEnabled()) {
            returning = true;
            spearSlot = slot;
            returnSlot = previous;
            returnTick = ticks + Math.max(1, (int) backDelay.getAsDouble() + spread());
        }
    }

    private boolean canLunge(PlayerEntity player) {
        if (!player.isAlive() || player.isUsingItem() || player.isTouchingWater() || player.isGliding()) return false;
        if (player.hasVehicle()) return false;
        return player.getHungerManager().isNotFull() || player.getAbilities().allowFlying;
    }

    private int spread() {
        int amount = (int) jitter.getAsDouble();
        return amount <= 0 ? 0 : ThreadLocalRandom.current().nextInt(amount + 1);
    }

    private static int bestSpear(PlayerEntity player) {
        int selected = player.getInventory().getSelectedSlot();
        int best = -1;
        int bestLevel = 0;
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = player.getInventory().getStack(slot);
            if (!stack.contains(DataComponentTypes.PIERCING_WEAPON)) continue;
            int level = stack.get(DataComponentTypes.PIERCING_WEAPON) == null ? 0 : 1;
            if (level > bestLevel || (level == bestLevel && slot == selected)) {
                best = slot;
                bestLevel = level;
            }
        }
        return best;
    }
}
