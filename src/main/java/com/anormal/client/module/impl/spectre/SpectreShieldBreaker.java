package com.anormal.client.module.impl.spectre;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;

/**
 * Spectre port: "Shield Breaker" — swaps to an axe and hits when the targeted player is
 * blocking with a shield.
 *
 * <p>Spectre's three-phase state machine (idle / armed / returning) is kept, including the
 * hit delay, the jitter, the optional switch-back and the weapon-only gate.</p>
 */
public final class SpectreShieldBreaker extends Module {
    private enum Phase { IDLE, ARMED, RETURNING }

    private static final int LOST_TARGET_TICKS = 4;
    private static final int REARM_TICKS = 10;

    public final NumberSetting hitDelay = addSetting(new NumberSetting("Hit Delay", "Ticks between the swap and the hit", 1.0, 1.0, 6.0, 1.0));
    public final BooleanSetting switchBack = addSetting(new BooleanSetting("Switch Back", "Return to the previous slot after the hit", true));
    public final NumberSetting backDelay = addSetting(new NumberSetting("Back Delay", "Ticks before switching back", 3.0, 1.0, 20.0, 1.0));
    public final NumberSetting jitter = addSetting(new NumberSetting("Jitter", "Random extra delay ticks", 1.0, 0.0, 3.0, 1.0));
    public final BooleanSetting weaponsOnly = addSetting(new BooleanSetting("From Weapons Only", "Only arm while holding a melee weapon", true));
    public final BooleanSetting holdAttack = addSetting(new BooleanSetting("Hold Attack", "Only act while the attack key is held", false));

    private Phase phase = Phase.IDLE;
    private int ticks;
    private int axeSlot = -1;
    private int returnSlot = -1;
    private int dueTick;
    private int lostTicks;
    private int rearmTick;

    public SpectreShieldBreaker() {
        super("SpectreShieldBreaker", "Spectre port: swaps to an axe and hits when the target is blocking with a shield.", Category.SPECTRE);
    }

    @Override
    public void onEnable() {
        ticks = 0;
        rearmTick = 0;
        phase = Phase.IDLE;
        axeSlot = -1;
        returnSlot = -1;
    }

    @Override
    public void onDisable() {
        if (phase != Phase.IDLE) finish(switchBack.isEnabled());
    }

    @Override
    public void onTick() {
        ticks++;
        var player = mc.player;
        if (player == null || mc.world == null || mc.interactionManager == null) {
            if (phase != Phase.IDLE) finish(false);
            return;
        }
        switch (phase) {
            case IDLE -> tryArm(player);
            case ARMED -> tryHit(player);
            case RETURNING -> {
                if (ticks >= dueTick && mc.currentScreen == null) finish(true);
            }
        }
    }

    private void tryArm(PlayerEntity player) {
        if (ticks < rearmTick || player.isUsingItem()) return;
        if (holdAttack.isEnabled() && !mc.options.attackKey.isPressed()) return;

        ItemStack held = player.getMainHandStack();
        if (disablesShields(held)) return;
        if (weaponsOnly.isEnabled() && !isMeleeWeapon(held)) return;

        PlayerEntity target = blockingTarget();
        if (target == null) return;

        int slot = bestAxe(player);
        if (slot < 0) return;

        returnSlot = player.getInventory().getSelectedSlot();
        axeSlot = slot;
        player.getInventory().setSelectedSlot(slot);
        dueTick = ticks + Math.max(1, (int) hitDelay.getAsDouble() + spread());
        lostTicks = 0;
        phase = Phase.ARMED;
    }

    private void tryHit(PlayerEntity player) {
        if (player.getInventory().getSelectedSlot() != axeSlot) {
            finish(false);
            return;
        }
        if (ticks < dueTick) return;

        PlayerEntity target = blockingTarget();
        if (target == null) {
            if (++lostTicks > LOST_TARGET_TICKS) startReturn(0);
            return;
        }
        mc.interactionManager.attackEntity(player, target);
        player.swingHand(Hand.MAIN_HAND);
        startReturn(backDelayTicks());
    }

    private int backDelayTicks() {
        return (int) backDelay.getAsDouble() + spread();
    }

    private int spread() {
        int amount = (int) jitter.getAsDouble();
        return amount <= 0 ? 0 : java.util.concurrent.ThreadLocalRandom.current().nextInt(amount + 1);
    }

    private void startReturn(int delayTicks) {
        if (!switchBack.isEnabled()) {
            finish(false);
            return;
        }
        phase = Phase.RETURNING;
        dueTick = ticks + Math.max(1, delayTicks);
    }

    private void finish(boolean restore) {
        var player = mc.player;
        if (restore && player != null && returnSlot >= 0 && player.getInventory().getSelectedSlot() == axeSlot) {
            player.getInventory().setSelectedSlot(returnSlot);
        }
        phase = Phase.IDLE;
        axeSlot = -1;
        returnSlot = -1;
        rearmTick = ticks + REARM_TICKS;
    }

    private PlayerEntity blockingTarget() {
        if (!(mc.crosshairTarget instanceof EntityHitResult hit)) return null;
        if (!(hit.getEntity() instanceof PlayerEntity target) || !target.isAlive()) return null;
        ((LivingEntity) target).getClass();
        return target.isBlocking() ? target : null;
    }

    private static boolean isMeleeWeapon(ItemStack stack) {
        return stack.isIn(ItemTags.SWORDS) || stack.isIn(ItemTags.AXES) || stack.isOf(Items.MACE);
    }

    private static boolean disablesShields(ItemStack stack) {
        return stack.isIn(ItemTags.AXES) || stack.isOf(Items.MACE);
    }

    /** Highest-tier axe in the hotbar, since axes are what disable shields. */
    private static int bestAxe(PlayerEntity player) {
        int best = -1;
        int bestScore = -1;
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = player.getInventory().getStack(slot);
            if (!stack.isIn(ItemTags.AXES)) continue;
            int score = axeScore(stack);
            if (score > bestScore) {
                bestScore = score;
                best = slot;
            }
        }
        return best;
    }

    private static int axeScore(ItemStack stack) {
        if (stack.isOf(Items.NETHERITE_AXE)) return 5;
        if (stack.isOf(Items.DIAMOND_AXE)) return 4;
        if (stack.isOf(Items.IRON_AXE)) return 3;
        if (stack.isOf(Items.STONE_AXE)) return 2;
        if (stack.isOf(Items.GOLDEN_AXE)) return 1;
        return 1;
    }
}
