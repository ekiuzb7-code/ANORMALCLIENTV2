package com.anormal.client.mixin;

import com.anormal.client.module.impl.spectre.SpectreTridentBoost;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Spectre section trident hooks.
 *
 * <p>Trident Boost: vanilla {@code TridentItem.onStoppedUsing} launches the riptide dash
 * as a fixed velocity along the look vector. Returning {@code true} from it also
 * consumes the durability tick. We let vanilla compute the launch vector and then
 * multiply the player's velocity by the configured boost. The Out Of Water setting
 * gates the water check against vanilla's requirement.</p>
 */
@Mixin(net.minecraft.item.TridentItem.class)
public class SpectreTridentMixin {

    @Inject(method = "onStoppedUsing", at = @At("TAIL"), require = 0)
    private void anormal$spectreTridentBoost(ItemStack stack, World world,
                                             net.minecraft.entity.LivingEntity user, int remainingUseTicks,
                                             CallbackInfoReturnable<Boolean> cir) {
        try {
            SpectreTridentBoost.boostRiptideLaunch(world, user);
        } catch (Throwable ignored) {
        }
    }

    @Inject(method = "use", at = @At("HEAD"), cancellable = true, require = 0)
    private void anormal$spectreRiptideStart(World world, net.minecraft.entity.player.PlayerEntity user,
                                             net.minecraft.util.Hand hand,
                                             CallbackInfoReturnable<net.minecraft.util.ActionResult> cir) {
        try {
            // Out Of Water gate (Spectre allowOutOfWater): vanilla refuses to start a
            // riptide dash while dry; Spectre bypasses that with its own start
            if (SpectreTridentBoost.forceStart(world, user, hand)) {
                cir.setReturnValue(net.minecraft.util.ActionResult.SUCCESS);
            }
        } catch (Throwable ignored) {
        }
    }
}
