package com.anormal.client.module.impl.spectre;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.util.math.Vec3d;

/**
 * Spectre port: "Fly" — lets you fly.
 *
 * <p>Spectre has two engines: the vanilla abilities engine and its own velocity engine with
 * an anti-kick packet rewrite. This port keeps both modes, the speed and vertical-match
 * settings and the anti-kick timing, but the anti-kick is the glide variant (a short
 * downward nudge every cycle) because the packet Y accessor does not exist on this version.</p>
 */
public final class SpectreFly extends Module {
    private static final float VANILLA_FLY_SPEED = 0.05F;

    public final ModeSetting mode = addSetting(new ModeSetting("Mode", "Abilities uses the vanilla fly flag, Velocity drives the motion", "Abilities", "Abilities", "Velocity"));
    public final NumberSetting speed = addSetting(new NumberSetting("Speed", "Fly speed", 0.1, 0.0, 10.0, 0.05));
    public final BooleanSetting verticalSpeedMatch = addSetting(new BooleanSetting("Vertical Speed Match", "Double vertical speed scale", false));
    public final BooleanSetting noSneak = addSetting(new BooleanSetting("No Sneak", "Never count as sneaking while flying", false));
    public final ModeSetting antiKickMode = addSetting(new ModeSetting("Anti Kick Mode", "How the periodic drop is applied", "Normal", "Normal", "None"));
    public final NumberSetting delay = addSetting(new NumberSetting("Anti Kick Delay", "Ticks between anti-kick drops", 20.0, 1.0, 200.0, 1.0));
    public final NumberSetting offTime = addSetting(new NumberSetting("Anti Kick Off Time", "Ticks the drop lasts", 1.0, 1.0, 20.0, 1.0));

    private int delayLeft = 20;
    private int offLeft = 1;

    public SpectreFly() {
        super("SpectreFly", "Spectre port: lets you fly.", Category.SPECTRE);
    }

    @Override
    public void onEnable() {
        var player = mc.player;
        if (player == null) {
            setEnabled(false);
            return;
        }
        delayLeft = (int) delay.getAsDouble();
        offLeft = (int) offTime.getAsDouble();
        if (mode.is("Abilities") && !player.isSpectator()) {
            player.getAbilities().flying = true;
            if (!player.getAbilities().creativeMode) player.getAbilities().allowFlying = true;
        }
    }

    @Override
    public void onDisable() {
        var player = mc.player;
        if (player != null && mode.is("Abilities")) abilitiesOff();
    }

    @Override
    public void onTick() {
        var player = mc.player;
        if (player == null || mc.world == null) {
            setEnabled(false);
            return;
        }

        if (mode.is("Abilities")) {
            if (player.isSpectator()) return;
            var abilities = player.getAbilities();
            abilities.setFlySpeed(speed.getValue().floatValue());
            if (antiKickMode.is("Normal") && antiKickDropTick()) {
                // Spectre's Normal anti-kick clears the fly flag for the off-time window.
                abilities.flying = false;
                if (!abilities.creativeMode) abilities.allowFlying = true;
                return;
            }
            abilities.flying = true;
            if (!abilities.creativeMode) abilities.allowFlying = true;
            return;
        }

        if (!player.isSpectator()) player.getAbilities().flying = false;
        Vec3d velocity = player.getVelocity();
        double vertical = speed.getAsDouble() * (verticalSpeedMatch.isEnabled() ? 10.0 : 5.0);
        double y = 0.0;
        if (mc.options.jumpKey.isPressed()) y = vertical;
        else if (mc.options.sneakKey.isPressed()) y = -vertical;
        if (y == 0.0 && antiKickMode.is("Normal") && antiKickDropTick()) y = -0.0313;
        player.setVelocity(velocity.x, y, velocity.z);
        player.fallDistance = 0.0F;
        if (noSneak.isEnabled()) player.setOnGround(false);
    }

    /**
     * Spectre's anti-kick cycle: wait {@code Anti Kick Delay} ticks, then drop for
     * {@code Anti Kick Off Time} ticks, then start over.
     *
     * @return {@code true} on a tick that belongs to the off (drop) window
     */
    private boolean antiKickDropTick() {
        if (delayLeft > 0) delayLeft--;
        if (offLeft <= 0 && delayLeft <= 0) {
            delayLeft = Math.max(1, (int) delay.getAsDouble());
            offLeft = Math.max(1, (int) offTime.getAsDouble());
            return false;
        }
        if (delayLeft <= 0) {
            offLeft--;
            return true;
        }
        return false;
    }

    private void abilitiesOff() {
        var player = mc.player;
        if (player == null || player.isSpectator()) return;
        var abilities = player.getAbilities();
        abilities.flying = false;
        abilities.setFlySpeed(VANILLA_FLY_SPEED);
        if (!abilities.creativeMode) abilities.allowFlying = false;
    }

    public float offGroundSpeed() {
        var player = mc.player;
        if (!isEnabled() || !mode.is("Velocity") || player == null) return -1.0F;
        return speed.getValue().floatValue() * (player.isSprinting() ? 15.0F : 10.0F);
    }

    /** Same as {@link #offGroundSpeed()} for an explicit player (used by the mixin). */
    public static float offGroundSpeedFor(net.minecraft.entity.player.PlayerEntity player) {
        var module = com.anormal.client.module.ModuleManager.getModule(SpectreFly.class);
        if (module == null || player != mc.player) return -1.0F;
        return module.offGroundSpeed();
    }

    public boolean noSneakActive() {
        return isEnabled() && mode.is("Velocity") && noSneak.isEnabled();
    }
}
