package com.anormal.client.mixin;

import com.anormal.client.module.impl.kuro.KuroBacktrack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.util.math.Box;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Ported one-to-one from kuro (mixin.ProjectileUtilMixin) — Yarn name of the intercepted
 * method is {@code ProjectileUtil.raycast(Entity, Vec3d, Vec3d, Box, Predicate, double)}.
 *
 * <p>Backtrack (Ghost hitbox mode): the crosshair / attack picking asks every candidate entity
 * for its bounding box inside this method. For tracked players we hand it the box from where
 * they were Delay ms ago instead. Only this one call is redirected (it is the only
 * {@code getBoundingBox} call inside {@code raycast}), so collision, rendering and everything
 * else still use the real box.</p>
 *
 * <p>require = 0: if the method signature differs, this simply does nothing (Ghost hitbox then
 * has no effect) instead of crashing the game.</p>
 */
@Mixin(ProjectileUtil.class)
public abstract class KuroBacktrackGhostBoxMixin {

    @Redirect(method = "raycast(Lnet/minecraft/entity/Entity;Lnet/minecraft/util/math/Vec3d;Lnet/minecraft/util/math/Vec3d;Lnet/minecraft/util/math/Box;Ljava/util/function/Predicate;D)Lnet/minecraft/util/hit/EntityHitResult;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;getBoundingBox()Lnet/minecraft/util/math/Box;"),
            require = 0)
    private static Box anormal$ghostBox(Entity entity) {
        Box ghost = KuroBacktrack.ghostBox(entity);
        return ghost != null ? ghost : entity.getBoundingBox();
    }
}
