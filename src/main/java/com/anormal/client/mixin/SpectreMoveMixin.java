package com.anormal.client.mixin;

import com.anormal.client.module.impl.spectre.SpectreMovementHooks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Spectre movement hook (Spectre {@code PlayerMoveEvent} equivalent).
 *
 * <p>Spectre rewrites the movement vector just before vanilla applies it. Mixin has no
 * portable "modify args" here, so the well-known pattern applies: at HEAD of
 * {@code PlayerEntity.move(MovementType, Vec3d)} we let the hooks rewrite the vector,
 * cancel the original call, and re-call {@code move} with the replacement. The above
 * {@code isReplacing()} guard prevents infinite recursion and only the local player's
 * movement is re-dispatched.</p>
 */
@Mixin(PlayerEntity.class)
public abstract class SpectreMoveMixin {

    /** Re-entry guard around our own replacement move() call. */
    private static boolean anormal$reentering = false;

    @Inject(method = "move(Lnet/minecraft/entity/MovementType;Lnet/minecraft/util/math/Vec3d;)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void anormal$spectreMove(MovementType type, Vec3d movement, CallbackInfo ci) {
        if (movement == null || anormal$reentering) return;
        try {
            Entity self = (Entity) (Object) this;
            net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
            // Only rewrite the local player's own self-movement
            if (mc.player == null || self != mc.player) return;
            Vec3d replacement = SpectreMovementHooks.onMove(self, type, movement);
            if (replacement != movement && replacement != null) {
                ci.cancel();
                anormal$reentering = true;
                try {
                    self.move(type, replacement);
                } finally {
                    anormal$reentering = false;
                }
            }
        } catch (Throwable ignored) {
        }
    }
}
