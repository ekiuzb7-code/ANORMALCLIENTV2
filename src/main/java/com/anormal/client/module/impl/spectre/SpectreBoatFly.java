package com.anormal.client.module.impl.spectre;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.NumberSetting;

/**
 * Spectre port: "Boat Fly" — turns the ridden vehicle into a plane.
 *
 * <p>No physics override is needed for flying as such: the horizontal speed and the
 * vertical input come from the same vector that {@code SpectreVehicleMixin} feeds into
 * the entity's movement, exactly like Spectre's {@code EntityControlVehicleHook}.</p>
 */
public final class SpectreBoatFly extends Module {
    public final NumberSetting speed = addSetting(new NumberSetting("Speed", "Blocks per second", 10.0, 0.0, 50.0, 0.5));
    public final NumberSetting verticalSpeed = addSetting(new NumberSetting("Vertical Speed", "Up/down speed", 6.0, 0.0, 20.0, 0.5));
    public final NumberSetting fallSpeed = addSetting(new NumberSetting("Fall Speed", "Downward drift when nothing is pressed", 0.0, 0.0, 10.0, 0.1));

    public SpectreBoatFly() {
        super("SpectreBoatFly", "Spectre port: transforms your boat (or any ridden vehicle) into a plane.", Category.SPECTRE);
    }

    public double horizontalSpeed() {
        return speed.getAsDouble();
    }

    public double verticalVelocity() {
        return SpectreVehicleHooks.verticalVelocity(verticalSpeed.getAsDouble(), fallSpeed.getAsDouble());
    }
}
