package com.anormal.client.module.impl.spectre;

import com.anormal.client.mixin.LivingEntityAccessor;
import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.NumberSetting;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Spectre port: "No Jump Delay" — removes the delay between jumps.
 *
 * <p>Spectre re-arms the vanilla jump cooldown with its configured value plus an optional
 * random spread (useful against anticheats that expect jitter). The delay field is
 * {@code LivingEntity.jumpingCooldown}, reached through the existing
 * {@link LivingEntityAccessor}.</p>
 */
public final class SpectreNoJumpDelay extends Module {
    public final NumberSetting cooldown = addSetting(new NumberSetting("Cooldown", "Ticks to wait between jumps", 0.0, 0.0, 9.0, 1.0));
    public final NumberSetting variance = addSetting(new NumberSetting("Variance", "Random extra ticks (0 = none)", 0.0, 0.0, 4.0, 1.0));

    public SpectreNoJumpDelay() {
        super("SpectreNoJumpDelay", "Spectre port: removes the delay between jumps.", Category.SPECTRE);
    }

    @Override
    public void onPreTick() {
        if (mc.player == null) return;
        ((LivingEntityAccessor) mc.player).setJumpingCooldown(rearm());
    }

    private int rearm() {
        int ticks = (int) cooldown.getAsDouble();
        int spread = (int) variance.getAsDouble();
        if (spread > 0) ticks += ThreadLocalRandom.current().nextInt(spread + 1);
        return ticks;
    }
}
