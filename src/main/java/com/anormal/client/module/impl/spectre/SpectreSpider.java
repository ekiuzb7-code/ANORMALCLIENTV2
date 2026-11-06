package com.anormal.client.module.impl.spectre;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.util.math.Vec3d;

/**
 * Spectre port: "Spider" — climbs walls like a spider.
 *
 * <p>While the player is pressed against a wall and not already rising faster than the
 * climb threshold, the vertical velocity is set to the configured climb speed.</p>
 */
public final class SpectreSpider extends Module {
    private static final double CLIMB_VELOCITY_THRESHOLD = 0.2;

    public final NumberSetting climbSpeed = addSetting(new NumberSetting("Climb Speed", "Upward speed on a wall", 0.2, 0.0, 10.0, 0.1));

    public SpectreSpider() {
        super("SpectreSpider", "Spectre port: allows you to climb walls like a spider.", Category.SPECTRE);
    }

    @Override
    public void onTick() {
        var player = mc.player;
        if (player == null || mc.world == null) return;
        if (!player.horizontalCollision) return;
        Vec3d velocity = player.getVelocity();
        if (velocity.y >= CLIMB_VELOCITY_THRESHOLD) return;
        player.setVelocity(velocity.x, climbSpeed.getAsDouble(), velocity.z);
    }
}
