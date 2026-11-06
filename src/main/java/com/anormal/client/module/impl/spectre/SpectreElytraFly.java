package com.anormal.client.module.impl.spectre;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.item.Items;
import net.minecraft.util.math.MathHelper;

/**
 * Spectre port: "Elytra Fly" — gives you more control over your elytra.
 *
 * <p>Spectre ships four flight engines (Vanilla, Packet, Pitch40, Bounce). This port keeps
 * the steering core — horizontal speed, vertical speed, acceleration, auto hover, water stop,
 * sprint and the pitch lock — driven from the tick loop. The packet engines need the network
 * rewrites that are not ported here, so Mode only switches the vertical behaviour.</p>
 */
public final class SpectreElytraFly extends Module {
    public final ModeSetting flightMode = addSetting(new ModeSetting("Mode", "Vanilla steers normally, Boost adds a vertical hold", "Vanilla", "Vanilla", "Boost"));
    public final BooleanSetting autoTakeOff = addSetting(new BooleanSetting("Auto Take Off", "Jump into a glide from the ground", false));
    public final NumberSetting horizontalSpeed = addSetting(new NumberSetting("Horizontal Speed", "Glide speed multiplier", 1.0, 0.0, 10.0, 0.1));
    public final NumberSetting verticalSpeed = addSetting(new NumberSetting("Vertical Speed", "Vertical speed multiplier", 1.0, 0.0, 10.0, 0.1));
    public final BooleanSetting acceleration = addSetting(new BooleanSetting("Acceleration", "Ramp the speed up over time", false));
    public final NumberSetting accelerationStep = addSetting(new NumberSetting("Acceleration Step", "Speed gained per tick", 1.0, 0.1, 5.0, 0.1));
    public final BooleanSetting stopInWater = addSetting(new BooleanSetting("Stop In Water", "Stop flying in water", true));
    public final BooleanSetting autoHover = addSetting(new BooleanSetting("Auto Hover", "Hold altitude when looking level", false));
    public final BooleanSetting lockPitch = addSetting(new BooleanSetting("Pitch Lock", "Clamp the pitch while gliding", true));
    public final NumberSetting pitch = addSetting(new NumberSetting("Pitch", "Locked pitch", 85.0, 0.0, 90.0, 1.0));
    public final BooleanSetting sprint = addSetting(new BooleanSetting("Sprint Constantly", "Always sprint while gliding", true));

    private float ramp = 1.0F;

    public SpectreElytraFly() {
        super("SpectreElytraFly", "Spectre port: gives you more control over your elytra.", Category.SPECTRE);
    }

    @Override
    public void onEnable() {
        ramp = 1.0F;
    }

    @Override
    public void onTick() {
        var player = mc.player;
        if (player == null || mc.world == null) return;

        if (autoTakeOff.isEnabled() && player.isOnGround() && !player.isGliding()
                && player.getEquippedStack(net.minecraft.entity.EquipmentSlot.CHEST).isOf(Items.ELYTRA)) {
            // Never leave the jump key held: it would keep re-triggering vanilla jumps.
            player.jump();
            return;
        }

        if (!player.isGliding()) {
            ramp = 1.0F;
            return;
        }
        if (stopInWater.isEnabled() && player.isTouchingWater()) {
            ramp = 1.0F;
            return;
        }
        if (sprint.isEnabled() && !player.isSprinting() && player.forwardSpeed > 0) player.setSprinting(true);

        if (acceleration.isEnabled()) {
            ramp = Math.min(10.0F, ramp + accelerationStep.getValue().floatValue() * 0.05F);
        } else {
            ramp = 1.0F;
        }

        var velocity = player.getVelocity();
        double horizontal = horizontalSpeed.getAsDouble() * ramp;
        double vertical = verticalSpeed.getAsDouble() * ramp;

        double vx = velocity.x * horizontal;
        double vz = velocity.z * horizontal;
        double vy = velocity.y;
        if (flightMode.is("Boost")) {
            if (Math.abs(player.getPitch()) < 20.0F && autoHover.isEnabled()) {
                vy = vy * 0.2 + 0.02 * vertical;
            } else {
                vy = vy * 0.98 + (player.getPitch() < 0 ? 0.008 : -0.008) * vertical;
            }
        } else if (autoHover.isEnabled() && Math.abs(player.getPitch()) < 5.0F) {
            vy *= 0.4;
        }

        player.setVelocity(vx, vy, vz);
        player.fallDistance = 0.0F;

        if (lockPitch.isEnabled()) {
            float limit = pitch.getValue().floatValue();
            player.setPitch(MathHelper.clamp(player.getPitch(), -limit, limit));
        }
    }
}
