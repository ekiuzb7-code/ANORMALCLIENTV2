package com.anormal.client.module.impl.kuro;

import com.anormal.client.module.Module;
import com.anormal.client.module.impl.client.AntiBot;
import com.anormal.client.module.impl.client.Friends;
import com.anormal.client.module.impl.kuro.KuroAutoMace;
import com.anormal.client.util.kuro.KuroRotations;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Kuro AutoMaceTargeting port (dev.kuro.client.module.impl.automace.AutoMaceTargeting'dan Anormal'da).
 *
 * <p>KuroAutoMace uchun nishon tanlash va aim nuqtalarini hisoblash yordamchisi:</p>
 * <ul>
 *   <li>{@link #applyGCDMouseFix} sichqoncha burilish qadamiga snam shakllanadi
 *       (real sichqoncha muvafaqqiyati ko'rinishda muhim).</li>
 *   <li>{@link #calculateAdvancedRotations} nishon hitboxining ko'zdan eng yaqin
 *       nuqtasiga (15 tamin ray ochqichida topilgan) burchaklar beradi.</li>
 * </ul>
 */
public class KuroAutoMaceTargeting {

    private static final MinecraftClient mc = MinecraftClient.getInstance();

    private final KuroAutoMace parent;

    public enum Priority { DISTANCE, LOWEST_HP, HIGHEST_HP }
    public enum AimPoint { CENTER, NEAREST, CLOSEST }

    public KuroAutoMaceTargeting(KuroAutoMace parent) {
        this.parent = parent;
    }

    public float[] applyGCDMouseFix(float currentYaw, float currentPitch, float targetYaw, float targetPitch) {
        double sens = mc.options.getMouseSensitivity().getValue() * 0.6 + 0.2;
        float gcd = (float) (sens * sens * sens * 1.2);
        float finalYaw = currentYaw + Math.round((targetYaw - currentYaw) / gcd) * gcd;
        float finalPitch = currentPitch + Math.round((targetPitch - currentPitch) / gcd) * gcd;
        finalPitch = MathHelper.clamp(finalPitch, -90.0F, 90.0F);
        return new float[]{finalYaw, finalPitch};
    }

    public float[] calculateAdvancedRotations(PlayerEntity player, Entity entity) {
        Box boundingBox = entity.getBoundingBox();
        Vec3d eyes = player.getEyePos();
        Vec3d targetPoint = null;

        float currentYaw = KuroRotations.active() ? KuroRotations.yaw() : player.getYaw();
        float currentPitch = KuroRotations.active() ? KuroRotations.pitch() : player.getPitch();

        Vec3d lookVec = Vec3d.fromPolar(currentPitch, currentYaw);

        double maxT = parent.getAimRange().getValue();
        double step = maxT / 15.0;
        double bestDistSq = Double.MAX_VALUE;

        for (int i = 0; i <= 15; i++) {
            double t = i * step;
            Vec3d rayPoint = eyes.add(lookVec.multiply(t));

            double clampedX = MathHelper.clamp(rayPoint.x, boundingBox.minX, boundingBox.maxX);
            double clampedY = MathHelper.clamp(rayPoint.y, boundingBox.minY, boundingBox.maxY);
            double clampedZ = MathHelper.clamp(rayPoint.z, boundingBox.minZ, boundingBox.maxZ);

            Vec3d boxPoint = new Vec3d(clampedX, clampedY, clampedZ);

            double dSq = rayPoint.squaredDistanceTo(boxPoint);
            if (dSq < bestDistSq) {
                bestDistSq = dSq;
                targetPoint = boxPoint;
            }
        }

        if (targetPoint == null || mc.world.raycast(new net.minecraft.world.RaycastContext(eyes,
                targetPoint, net.minecraft.world.RaycastContext.ShapeType.COLLIDER,
                net.minecraft.world.RaycastContext.FluidHandling.NONE, player))
                .getType() == net.minecraft.util.hit.HitResult.Type.BLOCK) {
            targetPoint = new Vec3d(entity.getX(), entity.getY() + entity.getHeight() * 0.75, entity.getZ());
        }

        double diffX = targetPoint.x - eyes.x;
        double diffY = targetPoint.y - eyes.y;
        double diffZ = targetPoint.z - eyes.z;
        double hypotenuse = Math.sqrt(diffX * diffX + diffZ * diffZ);

        float yaw = (float) (Math.atan2(diffZ, diffX) * 180.0 / Math.PI) - 90.0F;
        float pitch = (float) -(Math.atan2(diffY, hypotenuse) * 180.0 / Math.PI);

        return new float[]{yaw, pitch};
    }

    /** Hujum masofa ichidagi to'g'ri nishon. */
    public boolean isValidTarget(Entity entity) { return isValidTarget(entity, parent.reach()); }

    /** Ko'rlar bilan hitbox masofaga qaragan (serverga bir xil reach). */
    public boolean isValidTarget(Entity entity, double maxDist) {
        if (entity == null || entity == mc.player || entity == mc.getCameraEntity()) return false;
        if (!(entity instanceof LivingEntity) || !entity.isAlive()) return false;
        if (boxDistanceSq(entity.getBoundingBox(), eyesPos()) > maxDist * maxDist) return false;

        if (entity instanceof PlayerEntity p) {
            if (!parent.getTargetPlayers().isEnabled()) return false;
            if (p.isCreative() || p.isSpectator()) return false;
            if (AntiBot.isBot(p)) return false;
            if (Friends.isFriend(p.getGameProfile().name())) return false;
            return true;
        } else {
            return parent.getTargetMobs().isEnabled();
        }
    }

    /** Box#squaredDistanceTo 1.21.11'da olib tashlangan: masofa kvadratini o'zimiz hisoblaymiz. */
    private static double boxDistanceSq(Box box, Vec3d p) {
        double dx = Math.max(Math.max(box.minX - p.x, 0.0), p.x - box.maxX);
        double dy = Math.max(Math.max(box.minY - p.y, 0.0), p.y - box.maxY);
        double dz = Math.max(Math.max(box.minZ - p.z, 0.0), p.z - box.maxZ);
        return dx * dx + dy * dy + dz * dz;
    }

    private Vec3d eyesPos() {
        return mc.player != null ? mc.player.getEyePos() : Vec3d.ZERO;
    }

    /** Nishon ko'rinishdagi eng yaqin to'g'ri nishon, Aim Range-ish bo'lguncha. */
    public Entity findOptimalTarget() {
        return findOptimalTarget(Math.max(parent.getAimRange().getValue(), parent.reach()));
    }

    public Entity findOptimalTarget(double maxDist) {
        double bestMetric = Double.MAX_VALUE;
        Entity best = null;

        if (mc.world == null || mc.player == null) return null;

        for (Entity e : mc.world.getEntities()) {
            if (!isValidTarget(e, maxDist)) continue;
            if (!canSee(e)) continue;

            double metricValue = mc.player.squaredDistanceTo(e);

            if (metricValue < bestMetric) {
                bestMetric = metricValue;
                best = e;
            }
        }
        return best;
    }

    private boolean canSee(Entity e) {
        if (mc.player == null || mc.world == null) return false;
        net.minecraft.util.hit.HitResult hr = mc.world.raycast(new net.minecraft.world.RaycastContext(
                mc.player.getEyePos(), e.getEntityPos().add(0, e.getHeight() * 0.75, 0),
                net.minecraft.world.RaycastContext.ShapeType.COLLIDER,
                net.minecraft.world.RaycastContext.FluidHandling.NONE, mc.player));
        return hr.getType() != net.minecraft.util.hit.HitResult.Type.BLOCK;
    }

    public static Entity find(double range, boolean targetPlayers, boolean targetMobs, Priority priority, double fov) {
        if (mc.player == null || mc.world == null) return null;
        Entity best = null;
        double bestMetric = Double.MAX_VALUE;
        Vec3d eye = mc.player.getEyePos();
        Vec3d look = mc.player.getRotationVec(1.0f).normalize();

        for (Entity e : mc.world.getEntities()) {
            if (e == mc.player || !(e instanceof LivingEntity living) || !living.isAlive()) continue;

            double dist = mc.player.distanceTo(e);
            if (dist > range) continue;

            if (e instanceof PlayerEntity p) {
                if (!targetPlayers || p.isSpectator() || p.isCreative() || AntiBot.isBot(p)
                        || Friends.isFriend(p.getGameProfile().name())) continue;
            } else {
                if (!targetMobs) continue;
            }

            if (mc.world.raycast(new net.minecraft.world.RaycastContext(
                    mc.player.getEyePos(), e.getEntityPos().add(0, e.getHeight() * 0.75, 0),
                    net.minecraft.world.RaycastContext.ShapeType.COLLIDER,
                    net.minecraft.world.RaycastContext.FluidHandling.NONE, mc.player))
                    .getType() == net.minecraft.util.hit.HitResult.Type.BLOCK) continue;

            if (fov < 360.0) {
                Vec3d to = e.getBoundingBox().getCenter().subtract(eye);
                if (to.lengthSquared() > 1.0E-6) {
                    double ang = Math.toDegrees(Math.acos(MathHelper.clamp(look.dotProduct(to.normalize()), -1.0, 1.0)));
                    if (ang > fov / 2.0) continue;
                }
            }

            double metric = switch (priority) {
                case LOWEST_HP -> living.getHealth();
                case HIGHEST_HP -> -living.getHealth();
                default -> dist;
            };

            if (metric < bestMetric) {
                bestMetric = metric;
                best = e;
            }
        }
        return best;
    }

    public static float[] angles(Vec3d target) {
        if (mc.player == null) return null;
        Vec3d eyes = mc.player.getEyePos();
        double dx = target.x - eyes.x;
        double dy = target.y - eyes.y;
        double dz = target.z - eyes.z;
        double hyp = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, hyp));
        return new float[]{yaw, pitch};
    }

    public static Vec3d aimPoint(Entity entity, AimPoint point) {
        return new Vec3d(entity.getX(), entity.getY() + entity.getHeight() * 0.75, entity.getZ());
    }
}
