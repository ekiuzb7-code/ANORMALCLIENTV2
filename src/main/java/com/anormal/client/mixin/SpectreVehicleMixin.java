package com.anormal.client.mixin;

import com.anormal.client.module.impl.spectre.SpectreVehicleHooks;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Spectre section: lets the Spectre movement ports rewrite the movement vector of the
 * entity you are riding (Entity Speed, Boat Fly, Boat Noclip).
 *
 * <p>Additive hook: if no Spectre vehicle module is active, {@link SpectreVehicleHooks}
 * returns {@code null} and the original vector is used unchanged.</p>
 */
@Mixin(Entity.class)
public class SpectreVehicleMixin {

    @ModifyVariable(method = "move", at = @At("HEAD"), argsOnly = true, require = 0)
    private Vec3d spectre$movement(Vec3d movement) {
        try {
            Vec3d replacement = SpectreVehicleHooks.modify((Entity) (Object) this, movement);
            return replacement != null ? replacement : movement;
        } catch (Throwable ignored) {
            return movement;
        }
    }
}
