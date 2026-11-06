package com.anormal.client.module.impl.spectre;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.vehicle.AbstractBoatEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;

/**
 * Spectre port: "Boat Noclip" — flies the boat through blocks and entities.
 *
 * <p>Spectre flips {@code noPhysics} on the boat and drives it with its own movement
 * vector; the same vector is produced here and applied by {@code SpectreVehicleMixin}.</p>
 */
public final class SpectreBoatNoclip extends Module {
    public final NumberSetting speed = addSetting(new NumberSetting("Speed", "Blocks per second in open air", 10.0, 0.0, 50.0, 0.5));
    public final NumberSetting speedInsideBlocks = addSetting(new NumberSetting("Speed Inside Blocks", "Blocks per second while inside geometry", 5.0, 0.0, 50.0, 0.5));
    public final NumberSetting verticalSpeed = addSetting(new NumberSetting("Vertical Speed", "Up/down speed", 6.0, 0.0, 20.0, 0.5));
    public final NumberSetting fallSpeed = addSetting(new NumberSetting("Fall Speed", "Downward drift when nothing is pressed", 0.0, 0.0, 10.0, 0.1));

    private AbstractBoatEntity noclipBoat;

    public SpectreBoatNoclip() {
        super("SpectreBoatNoclip", "Spectre port: fly your boat through blocks and entities.", Category.SPECTRE);
    }

    @Override
    public void onDisable() {
        releaseBoat();
    }

    private void releaseBoat() {
        if (noclipBoat != null) noclipBoat.noClip = false;
        noclipBoat = null;
    }

    public Vec3d boatMovement(AbstractBoatEntity boat, PlayerEntity player) {
        if (noclipBoat != boat) {
            releaseBoat();
            noclipBoat = boat;
        }
        boat.noClip = true;

        double bps = insideBlock(boat) ? speedInsideBlocks.getAsDouble() : speed.getAsDouble();
        Vec3d horizontal = SpectreVehicleHooks.horizontalVelocity(bps);
        double velY = SpectreVehicleHooks.verticalVelocity(verticalSpeed.getAsDouble(), fallSpeed.getAsDouble());
        return new Vec3d(horizontal.x, velY, horizontal.z);
    }

    private boolean insideBlock(AbstractBoatEntity boat) {
        if (mc.world == null) return false;
        Box boatBox = boat.getBoundingBox();
        Box box = boatBox.contract(0.0, 0.05, 0.0).expand(0.5, 0.0, 0.5);
        int maxX = MathHelper.floor(box.maxX);
        int maxY = MathHelper.floor(box.maxY);
        int maxZ = MathHelper.floor(box.maxZ);
        BlockPos.Mutable pos = new BlockPos.Mutable();

        for (int x = MathHelper.floor(box.minX); x <= maxX; x++) {
            for (int y = MathHelper.floor(box.minY); y <= maxY; y++) {
                for (int z = MathHelper.floor(box.minZ); z <= maxZ; z++) {
                    pos.set(x, y, z);
                    var state = mc.world.getBlockState(pos);
                    if (state.isAir()) continue;
                    VoxelShape shape = state.getCollisionShape(mc.world, pos);
                    if (!shape.isEmpty() && boatBox.intersects(shape.getBoundingBox().offset(pos))) {
                        return true;
                    }
                }
            }
        }

        for (var other : mc.world.getEntitiesByClass(net.minecraft.entity.Entity.class, box, e -> true)) {
            if (other == mc.player || other.getVehicle() == boat || boat.getFirstPassenger() == other) continue;
            if (other.isAlive() && other.getBoundingBox().intersects(box)) return true;
        }
        return false;
    }
}
