package com.anormal.client.module.impl.spectre;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.module.ModuleManager;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ModeSetting;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;

/**
 * Spectre port: "Sprint" — keeps you sprinting.
 *
 * <p>Spectre has a Strict and a Rage mode; both are kept, along with Keep Sprint,
 * Unsprint On Hit and the water behaviour. Vanilla restrictions (food, collisions,
 * underwater, riding) are checked exactly like Spectre's {@code shouldSprint()}.</p>
 */
public final class SpectreSprint extends Module {
    public final ModeSetting mode = addSetting(new ModeSetting("Sprint Mode", "Strict follows vanilla rules, Rage never stops", "Strict", "Strict", "Rage"));
    public final BooleanSetting keepSprint = addSetting(new BooleanSetting("Keep Sprint", "Re-sprint right after a hit", false));
    public final BooleanSetting unsprintOnHit = addSetting(new BooleanSetting("Unsprint On Hit", "Stop sprinting on every attack", false));
    public final BooleanSetting unsprintInWater = addSetting(new BooleanSetting("Unsprint In Water", "Do not force sprint in water", true));
    public final BooleanSetting permaSprint = addSetting(new BooleanSetting("Sprint While Stationary", "Sprint even with no movement input", false));

    public SpectreSprint() {
        super("SpectreSprint", "Spectre port: automatically sprints.", Category.SPECTRE);
    }

    @Override
    public void onTick() {
        var player = mc.player;
        if (player == null || mc.world == null) return;
        if (unsprintInWater.isEnabled() && player.isTouchingWater()) return;
        player.setSprinting(shouldSprint());
    }

    /** Additive dispatch from the attack hook, used by Unsprint On Hit. */
    public static void onAttack() {
        SpectreSprint sprint = ModuleManager.getModule(SpectreSprint.class);
        if (sprint == null || !sprint.isEnabled() || !sprint.unsprintOnHit.isEnabled()) return;
        var player = mc.player;
        if (player == null) return;
        if (mc.getNetworkHandler() != null) {
            mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(player, ClientCommandC2SPacket.Mode.STOP_SPRINTING));
        }
        player.setSprinting(false);
        if (sprint.keepSprint.isEnabled()) player.setSprinting(true);
    }

    public boolean shouldSprint() {
        var player = mc.player;
        if (player == null) return false;
        boolean rage = mode.is("Rage");
        double forward = player.input.getMovementInput().y;
        double threshold = rage ? 1.0E-5F : 0.8;
        boolean moving = Math.abs(forward) > threshold;
        if (rage && permaSprint.isEnabled()) moving = true;
        if (!moving) return false;
        if (rage) return true;
        if (player.isUsingItem()) return false;
        if (!player.getAbilities().allowFlying && player.isTouchingWater()) return false;
        if (player.hasVehicle()) {
            Entity vehicle = player.getVehicle();
            return vehicle != null;
        }
        return player.getHungerManager().getFoodLevel() > 6 && !player.horizontalCollision;
    }
}
