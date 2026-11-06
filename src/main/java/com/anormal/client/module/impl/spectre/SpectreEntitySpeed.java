package com.anormal.client.module.impl.spectre;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.entity.Entity;

/**
 * Spectre port: "Entity Speed" — makes a ridden entity travel faster horizontally.
 */
public final class SpectreEntitySpeed extends Module {
    public final NumberSetting horizontalSpeed = addSetting(new NumberSetting("Horizontal Speed", "Blocks per second", 10.0, 0.0, 50.0, 0.5));
    public final BooleanSetting onlyOnGround = addSetting(new BooleanSetting("Only On Ground", "Only apply while the vehicle touches the ground", false));
    public final BooleanSetting inWater = addSetting(new BooleanSetting("In Water", "Also apply in water", true));

    public SpectreEntitySpeed() {
        super("SpectreEntitySpeed", "Spectre port: makes you go faster horizontally when riding entities.", Category.SPECTRE);
    }

    public boolean appliesTo(Entity vehicle) {
        return (!onlyOnGround.isEnabled() || vehicle.isOnGround()) && (inWater.isEnabled() || !vehicle.isTouchingWater());
    }

    public double horizontalSpeed() {
        return horizontalSpeed.getAsDouble();
    }
}
