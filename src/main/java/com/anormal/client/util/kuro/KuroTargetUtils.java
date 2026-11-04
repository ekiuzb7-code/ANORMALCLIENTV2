package com.anormal.client.util.kuro;

import com.anormal.client.module.impl.client.AntiBot;
import com.anormal.client.module.impl.client.Friends;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Kuro TargetUtils port (echo utils/combat/TargetUtils'dan Anormal mapping'larga).
 *
 * Hujum modullari (Aura, CrystalAura, AnchorAura...) uchun nishon filtri va
 * tanlash yordamchilari: do'stlik, o'liklik, hitbox masofa va sortlash.
 */
public final class KuroTargetUtils {
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    private KuroTargetUtils() {}

    /** Ushbu entity hujum modullari uchun yaroqli nishonmi (do'st, o'lik, o'z-o'zini istisno qilish). */
    public static boolean isValidTarget(Entity entity) {
        if (entity == null || mc.player == null || mc.world == null) return false;
        if (entity == mc.player) return false;
        if (!(entity instanceof LivingEntity living)) return false;
        if (!living.isAlive()) return false;
        if (entity instanceof ArmorStandEntity) return false;
        if (entity instanceof PlayerEntity player && isFriend(player)) return false;
        if (AntiBot.isBot(entity)) return false;
        return true;
    }

    public static boolean isFriend(PlayerEntity player) {
        if (player == null) return false;
        return Friends.isFriend(player.getGameProfile().name());
    }

    /** Hitbox masofa: o'q koordinatidan nishon hitbox'ining eng yaqin nuqtasigacha bo'lgan masofa. */
    public static double getHitboxDistance(Entity target) {
        if (mc.player == null || target == null) return Double.MAX_VALUE;
        return mc.player.getEyePos().distanceTo(closestPointToHitbox(target, mc.player.getEyePos()));
    }

    /** O'q nuqtasiga hitbox'ning eng yaqin nuqtasi (echo'dagi closestPointBox birga bir). */
    public static Vec3d closestPointToHitbox(Entity target, Vec3d from) {
        Box box = target.getBoundingBox();
        double x = MathHelper.clamp(from.x, box.minX, box.maxX);
        double y = MathHelper.clamp(from.y, box.minY, box.maxY);
        double z = MathHelper.clamp(from.z, box.minZ, box.maxZ);
        return new Vec3d(x, y, z);
    }

    /** Berilgan radius ichidagi barcha yaroqli nishonlarni qaytaradi. */
    public static List<LivingEntity> getTargetsInRange(double range) {
        List<LivingEntity> targets = new ArrayList<>();
        if (mc.player == null || mc.world == null || range <= 0) return targets;
        for (Entity entity : getEntitiesWithin(range)) {
            if (entity instanceof LivingEntity living && isValidTarget(entity)) {
                targets.add(living);
            }
        }
        return targets;
    }

    /** Radius ichidagi eng yaqin yaroqli nishon (masofa = hitbox masofa). */
    public static LivingEntity getNearestTarget(double range) {
        LivingEntity nearest = null;
        double nearestDist = Double.MAX_VALUE;
        for (LivingEntity t : getTargetsInRange(range)) {
            double d = getHitboxDistance(t);
            if (d <= range && d < nearestDist) {
                nearestDist = d;
                nearest = t;
            }
        }
        return nearest;
    }

    /** Berilgan qo'shimcha filtri bilan radius ichidagi yaroqli nishonlarni qaytaradi. */
    public static List<LivingEntity> getTargets(double range, Predicate<LivingEntity> extraFilter) {
        List<LivingEntity> result = new ArrayList<>();
        for (LivingEntity t : getTargetsInRange(range)) {
            if (extraFilter == null || extraFilter.test(t)) result.add(t);
        }
        return result;
    }

    /** Nishonlar masofa bo'yicha o'sish tartibida sortlangan ro'yxat. */
    public static List<LivingEntity> getSortedTargets(double range) {
        List<LivingEntity> targets = getTargetsInRange(range);
        targets.sort((a, b) -> Double.compare(getHitboxDistance(a), getHitboxDistance(b)));
        return targets;
    }

    /** Jami HP bo'yicha eng zaif nishon (echo'da sortByHealth birga bir). */
    public static LivingEntity getWeakestTarget(double range) {
        LivingEntity weakest = null;
        float weakestHealth = Float.MAX_VALUE;
        for (LivingEntity t : getTargetsInRange(range)) {
            if (getHitboxDistance(t) > range) continue;
            float health = t.getHealth() + t.getAbsorptionAmount();
            if (health < weakestHealth) {
                weakestHealth = health;
                weakest = t;
            }
        }
        return weakest;
    }

    private static List<Entity> getEntitiesWithin(double range) {
        World w = mc.world;
        if (w == null || mc.player == null) return List.of();
        return w.getOtherEntities(mc.player, mc.player.getBoundingBox().expand(range),
                e -> true);
    }
}
