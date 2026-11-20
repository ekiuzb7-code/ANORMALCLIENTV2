package com.anormal.client.mixin;

import com.anormal.client.module.impl.optimization.EntityLodOptimizer;
import com.anormal.client.module.impl.optimization.FogCull;
import com.anormal.client.module.impl.optimization.SmartCulling;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Central entity-culling hook shared by {@code FogCull}, {@code SmartCulling}
 * and {@code EntityLodOptimizer}.
 *
 * <p>{@code shouldRender} is asked once per entity per frame, before anything is
 * pushed to the render queue - returning {@code false} here is the cheapest
 * possible rejection.</p>
 */
@Mixin(EntityRenderer.class)
public class EntityRendererMixin {

    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true, require = 0)
    private void anormal$shouldRender(Entity entity, Frustum frustum, double x, double y, double z,
                                      CallbackInfoReturnable<Boolean> cir) {
        try {
            if (entity == null) return;
            // Spectre section: No Render can hide falling blocks entirely.
            if (entity instanceof net.minecraft.entity.FallingBlockEntity
                    && com.anormal.client.module.impl.spectre.SpectreNoRender.hideFallingBlocks()) {
                cir.setReturnValue(false);
                return;
            }
            // Farm FPS: dropped items, xp orbs, primed TNT and falling blocks are the bulk of a
            // farm's entity count, so they are rejected before entering the render queue.
            if ((entity instanceof net.minecraft.entity.ItemEntity
                    && com.anormal.client.module.impl.client.FarmFpsOptimizer.shouldCancelItemRender())
                    || (entity instanceof net.minecraft.entity.ExperienceOrbEntity
                    && com.anormal.client.module.impl.client.FarmFpsOptimizer.shouldCancelXpRender())
                    || (entity instanceof net.minecraft.entity.TntEntity
                    && com.anormal.client.module.impl.client.FarmFpsOptimizer.shouldCancelTntRender())
                    || (entity instanceof net.minecraft.entity.FallingBlockEntity
                    && com.anormal.client.module.impl.client.FarmFpsOptimizer.shouldCancelFallingRender())) {
                cir.setReturnValue(false);
                return;
            }
            if (FogCull.shouldCull(entity)
                    || SmartCulling.shouldCull(entity)
                    || EntityLodOptimizer.shouldHide(entity)) {
                cir.setReturnValue(false);
                return;
            }
            // Spectre section: SpectrePerformance turns the view-frustum skip on and off.
            if (!com.anormal.client.module.impl.spectre.SpectrePerformance.frustumCulling()) {
                cir.setReturnValue(true);
            }
        } catch (Throwable ignored) {
        }
    }
}
