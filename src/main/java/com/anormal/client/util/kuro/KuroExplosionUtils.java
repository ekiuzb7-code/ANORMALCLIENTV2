package com.anormal.client.util.kuro;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/**
 * Kuro ExplosionUtils port (echo utils/combat/ExplosionUtils'dan Anormal mapping'larga).
 *
 * Portlashni ko'rish foizi (seen %) va respawn anchor portlash zararini
 * hisoblash yordamchilari — echo'dagi AnchorAura / AnchorBomb modullarining
 * zarar prognoz markazi.
 */
public final class KuroExplosionUtils {
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    private KuroExplosionUtils() {}

    // ---------------------------------------------------------------- seen %

    public static float getSeenPercent(Vec3d explosionCenter, Box targetBox, Entity raycastContext) {
        return getSeenPercent(explosionCenter, targetBox, raycastContext, null);
    }

    private static float getSeenPercent(Vec3d explosionCenter, Box targetBox, Entity raycastContext, BlockPos ignoredBlockPos) {
        if (explosionCenter == null || targetBox == null || raycastContext == null || mc.world == null) {
            return 0.0F;
        }

        double xStep = 1.0 / (targetBox.getLengthX() * 2.0 + 1.0);
        double yStep = 1.0 / (targetBox.getLengthY() * 2.0 + 1.0);
        double zStep = 1.0 / (targetBox.getLengthZ() * 2.0 + 1.0);
        if (xStep < 0.0 || yStep < 0.0 || zStep < 0.0) {
            return 0.0F;
        }

        double xOffset = (1.0 - Math.floor(1.0 / xStep) * xStep) / 2.0;
        double zOffset = (1.0 - Math.floor(1.0 / zStep) * zStep) / 2.0;

        int clearRays = 0;
        int totalRays = 0;

        for (double x = 0.0; x <= 1.0; x += xStep) {
            for (double y = 0.0; y <= 1.0; y += yStep) {
                for (double z = 0.0; z <= 1.0; z += zStep) {
                    Vec3d sample = new Vec3d(
                        MathHelper.lerp(x, targetBox.minX, targetBox.maxX) + xOffset,
                        MathHelper.lerp(y, targetBox.minY, targetBox.maxY),
                        MathHelper.lerp(z, targetBox.minZ, targetBox.maxZ) + zOffset
                    );

                    if (isRayClear(sample, explosionCenter, raycastContext, ignoredBlockPos)) {
                        clearRays++;
                    }
                    totalRays++;
                }
            }
        }

        return totalRays == 0 ? 0.0F : (float) clearRays / totalRays;
    }

    private static boolean isRayClear(Vec3d sample, Vec3d explosionCenter, Entity raycastContext, BlockPos ignoredBlockPos) {
        HitResult hit = mc.world.raycast(new RaycastContext(
            sample,
            explosionCenter,
            RaycastContext.ShapeType.COLLIDER,
            RaycastContext.FluidHandling.NONE,
            raycastContext
        ));
        if (hit.getType() == HitResult.Type.MISS) return true;
        return ignoredBlockPos != null
                && hit instanceof BlockHitResult blockHit
                && blockHit.getBlockPos().equals(ignoredBlockPos);
    }

    /**
     * {@code anchorPos}dagi to'ldirilgan respawn anchor hozir portlasa {@code victim}ga
     * yetadigan zararning taxminini qaytaradi. Vanilla
     * {@code ExplosionDamageCalculator#getEntityDamageAmount}ga mos (radius 5.0,
     * vanilla `(e^2 + e)/2 * 7 * 2r + 1` formulasi), so'ng vanilla zirh
     * yutishini {@code CombatRules}ning qurolsiz tarmog'i orqali qo'llaydi
     * (portlashlar qurol item o'zgartirmaydi). Blast Protection kiritilmagan —
     * u data-driven va ServerLevel qo'l kerak — shuning uchun bu
     * himoya-enchant'siz yuqori chegarasi. Bu chegara bilan thresholdni sozlang.
     * Vanilla portlashni hisoblashdan oldin anchor blokini olib tashlaydi,
     * shuning uchun exposure nurlari {@code anchorPos}ni e'tiborsiz qoldiradi.
     */
    public static float getAnchorDamageTo(LivingEntity victim, BlockPos anchorPos) {
        if (victim == null || anchorPos == null || mc.world == null) return 0.0f;
        return getExplosionDamageTo(victim, Vec3d.ofCenter(anchorPos), 5.0f, anchorPos);
    }

    public static float getExplosionDamageTo(LivingEntity victim, Vec3d explosionCenter, float radius) {
        return getExplosionDamageTo(victim, explosionCenter, radius, null);
    }

    private static float getExplosionDamageTo(LivingEntity victim, Vec3d explosionCenter, float radius, BlockPos ignoredBlockPos) {
        if (victim == null || explosionCenter == null || mc.world == null) return 0.0f;

        float diameter = radius * 2.0f;
        double dist = Math.sqrt(victim.squaredDistanceTo(explosionCenter)) / diameter;
        if (dist > 1.0) return 0.0f;

        float exposure = getSeenPercent(explosionCenter, victim.getBoundingBox(), victim, ignoredBlockPos);
        double e = (1.0 - dist) * exposure;
        float rawDamage = (float) ((e * e + e) / 2.0 * 7.0 * diameter + 1.0);

        float armor = (float) victim.getArmor();
        float toughness = (float) victim.getAttributeValue(EntityAttributes.ARMOR_TOUGHNESS);
        float divider = 2.0f + toughness / 4.0f;
        float effectiveArmor = MathHelper.clamp(armor - rawDamage / divider, armor * 0.2f, 20.0f);
        return rawDamage * (1.0f - effectiveArmor / 25.0f);
    }
}
