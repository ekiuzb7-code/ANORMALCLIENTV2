package com.anormal.client.module.impl.spectre;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.vehicle.AbstractBoatEntity;
import net.minecraft.util.math.Vec3d;

/**
 * Spectre port: "Entity Control" — takes over the vehicle you are riding.
 *
 * <p>Spectre's module also spoofs the saddle so unsaddled horses accept input; that needs
 * an interaction/controlling-passenger hook which is not ported yet. What is ported is
 * the part that shapes movement: the per-type filters, the yaw lock (the vehicle follows
 * your look instead of its own steering) and the max-jump assist.</p>
 */
public final class SpectreEntityControl extends Module {
    public final BooleanSetting boats = addSetting(new BooleanSetting("Boats", "Control boats", true));
    public final BooleanSetting horses = addSetting(new BooleanSetting("Horses", "Control horses and donkeys", true));
    public final BooleanSetting camels = addSetting(new BooleanSetting("Camels", "Control camels", true));
    public final BooleanSetting pigs = addSetting(new BooleanSetting("Pigs", "Control pigs", true));
    public final BooleanSetting striders = addSetting(new BooleanSetting("Striders", "Control striders", true));
    public final BooleanSetting lockYaw = addSetting(new BooleanSetting("Lock Yaw", "The vehicle follows your yaw", true));
    public final BooleanSetting maxJump = addSetting(new BooleanSetting("Max Jump", "Assist jumps while riding", true));

    public SpectreEntityControl() {
        super("SpectreEntityControl", "Spectre port: lets you control rideable entities without a saddle.", Category.SPECTRE);
    }

    @Override
    public void onTick() {
        var player = mc.player;
        if (player == null || mc.world == null) return;
        Entity vehicle = player.getVehicle();
        if (vehicle == null || !isTarget(vehicle)) return;

        if (lockYaw.isEnabled()) {
            vehicle.setYaw(player.getYaw());
        }
        if (maxJump.isEnabled() && vehicle.isOnGround() && mc.options.jumpKey.isPressed()) {
            Vec3d velocity = vehicle.getVelocity();
            vehicle.setVelocity(velocity.x, 0.42, velocity.z);
        }
    }

    private boolean isTarget(Entity entity) {
        if (entity instanceof AbstractBoatEntity) return boats.isEnabled();
        EntityType<?> type = entity.getType();
        if (type == EntityType.HORSE || type == EntityType.DONKEY || type == EntityType.MULE
                || type == EntityType.SKELETON_HORSE || type == EntityType.ZOMBIE_HORSE) {
            return horses.isEnabled();
        }
        if (type == EntityType.CAMEL) return camels.isEnabled();
        if (type == EntityType.PIG) return pigs.isEnabled();
        if (type == EntityType.STRIDER) return striders.isEnabled();
        return false;
    }

    /** Used by the shared vehicle helper to keep the yaw in sync while flying. */
    public boolean locksYaw(Entity vehicle) {
        return isEnabled() && lockYaw.isEnabled() && isTarget(vehicle);
    }
}
