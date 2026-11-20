package com.anormal.client.mixin;

import com.anormal.client.module.impl.spectre.SpectreJesus;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Spectre Jesus: bubble columns.
 *
 * <p>Vanilla tells the entity when it is above or inside a bubble column; Jesus uses that to let
 * the jump key push you up the column while walking on the surface (Spectre's
 * {@code inBubbleColumn} flag). Both Yarn and Mojang method names are injected.</p>
 */
@Mixin(Entity.class)
public class SpectreJesusEntityMixin {

    @Inject(method = "onBubbleColumnSurfaceCollision", at = @At("HEAD"), require = 0)
    private void anormal$jesusAboveBubbleColumn(CallbackInfo ci) {
        markBubbleColumn();
    }

    @Inject(method = "onBubbleColumnCollision", at = @At("HEAD"), require = 0)
    private void anormal$jesusInsideBubbleColumn(CallbackInfo ci) {
        markBubbleColumn();
    }

    @Inject(method = "onAboveBubbleColumn", at = @At("HEAD"), require = 0)
    private void anormal$jesusAboveBubbleColumnMojang(CallbackInfo ci) {
        markBubbleColumn();
    }

    @Inject(method = "onInsideBubbleColumn", at = @At("HEAD"), require = 0)
    private void anormal$jesusInsideBubbleColumnMojang(CallbackInfo ci) {
        markBubbleColumn();
    }

    private void markBubbleColumn() {
        try {
            SpectreJesus jesus = SpectreJesus.active;
            if (jesus == null || !jesus.isEnabled()) return;
            if ((Object) this != MinecraftClient.getInstance().player) return;
            jesus.markInBubbleColumn();
        } catch (Throwable ignored) {
        }
    }
}
