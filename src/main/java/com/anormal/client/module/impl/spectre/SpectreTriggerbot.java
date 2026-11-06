package com.anormal.client.module.impl.spectre;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;

/**
 * Spectre port: "Triggerbot" — hits the entity under the crosshair once the swing is
 * charged, with a reaction delay.
 *
 * <p>Reaction + jitter, the Off/Smart/Only crit modes, the weapon filter, the shielding
 * skip and the two-tick gap all follow Spectre. The hit itself goes through the vanilla
 * interaction manager, exactly like a manual click.</p>
 */
public final class SpectreTriggerbot extends Module {
    private static final int MIN_GAP_TICKS = 2;
    private static final int SMART_MAX_WAIT_TICKS = 12;

    public final NumberSetting reaction = addSetting(new NumberSetting("Reaction", "Ticks to wait after acquiring a target", 2.0, 0.0, 10.0, 1.0));
    public final NumberSetting jitter = addSetting(new NumberSetting("Jitter", "Random extra reaction ticks", 1.0, 0.0, 4.0, 1.0));
    public final ModeSetting crits = addSetting(new ModeSetting("Crits", "Off, Smart (wait for crits) or Only (crits required)", "Smart", "Off", "Smart", "Only"));
    public final BooleanSetting holdAttack = addSetting(new BooleanSetting("Hold Attack", "Only hit while the attack key is held", false));
    public final BooleanSetting skipShielding = addSetting(new BooleanSetting("Skip Shielding", "Do not hit targets that are blocking", true));
    public final BooleanSetting players = addSetting(new BooleanSetting("Players", "Target players", true));
    public final BooleanSetting hostiles = addSetting(new BooleanSetting("Hostiles", "Target hostile mobs", false));
    public final BooleanSetting passives = addSetting(new BooleanSetting("Passives", "Target passive mobs", false));
    public final BooleanSetting invisible = addSetting(new BooleanSetting("Invisible", "Target invisible entities", false));
    public final BooleanSetting anyItem = addSetting(new BooleanSetting("Any Item", "Ignore the weapon filter", false));
    public final BooleanSetting swords = addSetting(new BooleanSetting("Swords", "Allow swords", true));
    public final BooleanSetting axes = addSetting(new BooleanSetting("Axes", "Allow axes", true));
    public final BooleanSetting mace = addSetting(new BooleanSetting("Mace", "Allow maces", true));

    private int ticks;
    private int trackedId = -1;
    private int reactionDoneTick;
    private int chargedTick = -1;
    private int lastHitTick = -2;
    private int airborneTick = -1;

    public SpectreTriggerbot() {
        super("SpectreTriggerbot", "Spectre port: hits the entity under the crosshair once the swing is charged.", Category.SPECTRE);
    }

    @Override
    public void onEnable() {
        reset();
    }

    @Override
    public void onDisable() {
        reset();
    }

    private void reset() {
        ticks = 0;
        trackedId = -1;
        reactionDoneTick = 0;
        chargedTick = -1;
        lastHitTick = -2;
        airborneTick = -1;
    }

    @Override
    public void onTick() {
        ticks++;
        var player = mc.player;
        if (player == null || mc.world == null || mc.interactionManager == null
                || mc.currentScreen != null || !player.isAlive()) {
            trackedId = -1;
            return;
        }

        if (player.getAttackCooldownProgress(0.5F) < 1.0F) {
            chargedTick = -1;
        } else if (chargedTick < 0) {
            chargedTick = ticks;
        }
        airborneTick = player.isOnGround() ? -1 : (airborneTick < 0 ? ticks : airborneTick);

        Entity target = crosshairTarget();
        if (target == null || !isTarget(player, target)) {
            trackedId = -1;
            return;
        }
        if (target.getId() != trackedId) {
            trackedId = target.getId();
            reactionDoneTick = ticks + (int) reaction.getAsDouble() + spread();
        }
        if (ticks < reactionDoneTick || chargedTick < 0) return;
        if (ticks - lastHitTick < MIN_GAP_TICKS) return;
        if (holdAttack.isEnabled() && !mc.options.attackKey.isPressed()) return;

        ItemStack held = player.getMainHandStack();
        if (!weaponAllowed(held)) return;
        if (skipShielding.isEnabled() && target instanceof LivingEntity living
                && living.isBlocking() && !disablesShields(held)) return;
        if (!critsAllow(player, target)) return;

        mc.interactionManager.attackEntity(player, target);
        player.swingHand(Hand.MAIN_HAND);
        lastHitTick = ticks;
        chargedTick = -1;
    }

    private Entity crosshairTarget() {
        return mc.crosshairTarget instanceof EntityHitResult hit ? hit.getEntity() : null;
    }

    private int spread() {
        int amount = (int) jitter.getAsDouble();
        return amount <= 0 ? 0 : java.util.concurrent.ThreadLocalRandom.current().nextInt(amount + 1);
    }

    private boolean isTarget(PlayerEntity self, Entity entity) {
        if (entity == self || !entity.isAlive()) return false;
        if (entity.isInvisible() && !invisible.isEnabled()) return false;
        if (!(entity instanceof PlayerEntity)) {
            return entity instanceof HostileEntity ? hostiles.isEnabled() : passives.isEnabled();
        }
        return players.isEnabled();
    }

    private boolean weaponAllowed(ItemStack held) {
        if (anyItem.isEnabled()) return true;
        return (swords.isEnabled() && held.isIn(ItemTags.SWORDS))
                || (axes.isEnabled() && held.isIn(ItemTags.AXES))
                || (mace.isEnabled() && held.isOf(Items.MACE));
    }

    private boolean disablesShields(ItemStack held) {
        return held.isIn(ItemTags.AXES) || held.isOf(Items.MACE);
    }

    private boolean critsAllow(PlayerEntity player, Entity target) {
        boolean crit = wouldCrit(player, target);
        return switch (crits.getValue()) {
            case "Only" -> crit;
            case "Smart" -> crit || airborneTick < 0 || ticks - airborneTick >= SMART_MAX_WAIT_TICKS;
            default -> true;
        };
    }

    private static boolean wouldCrit(PlayerEntity player, Entity target) {
        return player.getAttackCooldownProgress(0.5F) > 0.9F
                && player.fallDistance > 0.0F
                && !player.isOnGround()
                && !player.isClimbing()
                && !player.isTouchingWater()
                && !player.hasVehicle()
                && !player.isSprinting()
                && target instanceof LivingEntity;
    }
}
