package com.anormal.client.module.impl.spectre;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.module.impl.client.Friends;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;

/**
 * Spectre port: "Kill Aura" — attacks nearby targets without moving your camera.
 *
 * <p>Spectre runs a server-rotation path with crit pacing. This port keeps the vehicle reach,
 * the players / hostiles / friend filters, the charge gate and the crit hold (drop sprint while
 * falling so the hit crits), attacking through the interaction manager like the rest of the
 * client does.</p>
 */
public final class SpectreKillAura extends Module {
    private static final float FOOT_REACH = 3.0F;
    private static final int CRIT_HOLD_TICKS = 8;

    public final NumberSetting vehicleRange = addSetting(new NumberSetting("Vehicle Range", "Reach while riding", 6.0, 3.0, 8.0, 0.1));
    public final BooleanSetting players = addSetting(new BooleanSetting("Players", "Attack players", true));
    public final BooleanSetting hostiles = addSetting(new BooleanSetting("Hostiles", "Attack hostile mobs", false));
    public final BooleanSetting skipFriends = addSetting(new BooleanSetting("Skip Friends", "Never attack friends", true));
    public final BooleanSetting waitCharge = addSetting(new BooleanSetting("Wait Charge", "Wait for the attack cooldown", true));

    private int critWait;

    public SpectreKillAura() {
        super("SpectreKillAura", "Spectre port: attacks nearby targets without moving your camera.", Category.SPECTRE);
    }

    @Override
    public void onEnable() {
        critWait = 0;
    }

    @Override
    public void onDisable() {
        critWait = 0;
    }

    @Override
    public void onTick() {
        ClientPlayerEntity player = mc.player;
        if (player == null || mc.world == null || mc.interactionManager == null) return;
        if (player.isDead() || player.isUsingItem()) return;

        boolean riding = player.getVehicle() != null;
        float reach = riding ? vehicleRange.getValue().floatValue() : FOOT_REACH;
        LivingEntity target = nearest(player, reach);
        if (target == null) {
            critWait = 0;
            return;
        }
        if (!riding && !player.canSee(target)) return;
        if (waitCharge.isEnabled() && player.getAttackCooldownProgress(0.5F) < 0.9F) {
            critWait = 0;
            return;
        }
        if (!riding && holdForCrit(player) && critWait++ < CRIT_HOLD_TICKS) return;

        if (!riding && falling(player) && player.isSprinting()) {
            player.setSprinting(false);
        }
        mc.interactionManager.attackEntity(player, target);
        player.swingHand(Hand.MAIN_HAND);
        critWait = 0;
    }

    private LivingEntity nearest(PlayerEntity self, float reach) {
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (var entity : mc.world.getEntities()) {
            if (!(entity instanceof LivingEntity living) || living.isDead() || living == self) continue;
            if (!wanted(living)) continue;
            double distance = self.getEyePos().distanceTo(living.getBoundingBox().getCenter());
            if (distance > reach || distance >= bestDistance) continue;
            best = living;
            bestDistance = distance;
        }
        return best;
    }

    private boolean wanted(LivingEntity living) {
        if (living.isSpectator() || !living.isAlive()) return false;
        if (living instanceof PlayerEntity player) {
            if (!players.isEnabled()) return false;
            String name = player.getGameProfile().name();
            return !(skipFriends.isEnabled() && name != null && Friends.isFriend(name));
        }
        return hostiles.isEnabled() && living instanceof HostileEntity;
    }

    private static boolean holdForCrit(PlayerEntity player) {
        if (player.getAbilities().flying || player.isGliding() || player.isTouchingWater()
                || player.isClimbing() || player.hasVehicle()) {
            return false;
        }
        return (!player.isOnGround() && player.fallDistance <= 0.0F) || (player.isOnGround() && mcJumpHeld());
    }

    private static boolean mcJumpHeld() {
        var client = net.minecraft.client.MinecraftClient.getInstance();
        return client.options != null && client.options.jumpKey.isPressed();
    }

    private static boolean falling(PlayerEntity player) {
        return player.fallDistance > 0.0F && !player.isOnGround() && !player.isClimbing()
                && !player.isTouchingWater() && !player.hasVehicle()
                && !player.getAbilities().flying && !player.isGliding();
    }
}
