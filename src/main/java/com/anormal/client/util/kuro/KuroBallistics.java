package com.anormal.client.util.kuro;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Kuro {@code Ballistics} port: otilgan snaryadlar (o'qlar, ender pearl'lar)
 * uchun klientdagi parvoz simulyatsiyasi. Vanilla tartibi har tickda:
 * joriy tezlik bilan harakatlanadi (yo'ldagi birinchi blok / entity'da
 * to'xtaydi), so'ng tezlik *= drag, so'ng tezlik.y -= gravity.
 * O'qlar: gravity 0.05, drag 0.99. Ender pearl: gravity 0.03, drag 0.99.
 */
public final class KuroBallistics {
    private KuroBallistics() {}

    public static final class Result {
        /** Start point first, then the position after every simulated tick; the last point is the impact point. */
        public final List<Vec3d> pts = new ArrayList<>();
        public Vec3d end;
        /** Face of the block that was hit, or null when the path ended in the air / on an entity. */
        public Direction face;
        /** First living entity the path crosses before any block, or null. */
        public LivingEntity hit;
        public boolean hitBlock;
        public int ticks;
    }

    public static Result simulate(Entity ignore, Vec3d start, Vec3d velocity, double gravity, double drag,
                                  int maxTicks, boolean checkEntities) {
        MinecraftClient mc = MinecraftClient.getInstance();
        Result r = new Result();
        if (mc.world == null) { r.end = start; r.pts.add(start); return r; }
        Vec3d pos = start, vel = velocity;
        r.pts.add(pos);
        for (int t = 1; t <= maxTicks; t++) {
            Vec3d next = pos.add(vel);
            BlockHitResult bhr = mc.world.raycast(new RaycastContext(pos, next,
                    RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, ignore));
            if (bhr.getType() != HitResult.Type.MISS) {
                r.pts.add(bhr.getPos());
                r.face = bhr.getSide();
                r.hitBlock = true;
                r.ticks = t;
                break;
            }
            r.pts.add(next);
            pos = next;
            r.ticks = t;
            vel = vel.multiply(drag, drag, drag).subtract(0, gravity, 0);
            if (pos.y < -100) break;
        }
        if (checkEntities) findEntityHit(ignore, r);
        r.end = r.pts.get(r.pts.size() - 1);
        return r;
    }

    /** Cuts the path at the first living entity it crosses (same 0.3 block margin vanilla uses for projectiles). */
    private static void findEntityHit(Entity ignore, Result r) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (r.pts.size() < 2) return;
        double x1 = Double.MAX_VALUE, y1 = Double.MAX_VALUE, z1 = Double.MAX_VALUE, x2 = -Double.MAX_VALUE, y2 = -Double.MAX_VALUE, z2 = -Double.MAX_VALUE;
        for (Vec3d p : r.pts) {
            x1 = Math.min(x1, p.x); y1 = Math.min(y1, p.y); z1 = Math.min(z1, p.z);
            x2 = Math.max(x2, p.x); y2 = Math.max(y2, p.y); z2 = Math.max(z2, p.z);
        }
        Box all = new Box(x1, y1, z1, x2, y2, z2).expand(2.0);
        List<Entity> near = mc.world.getOtherEntities(ignore, all,
                e -> e instanceof LivingEntity le && le.isAlive() && !le.isSpectator() && e != ignore);
        if (near.isEmpty()) return;
        for (int i = 1; i < r.pts.size(); i++) {
            Vec3d a = r.pts.get(i - 1), b = r.pts.get(i);
            Entity best = null;
            Vec3d bestPoint = null;
            double bestD = Double.MAX_VALUE;
            for (Entity e : near) {
                Box box = e.getBoundingBox().expand(0.3);
                Vec3d hit = null;
                if (box.contains(a)) hit = a;
                else {
                    Optional<Vec3d> c = box.raycast(a, b);
                    if (c.isPresent()) hit = c.get();
                }
                if (hit == null) continue;
                double d = hit.squaredDistanceTo(a);
                if (d < bestD) { bestD = d; best = e; bestPoint = hit; }
            }
            if (best != null) {
                while (r.pts.size() > i) r.pts.remove(r.pts.size() - 1);
                r.pts.add(bestPoint);
                r.hit = (LivingEntity) best;
                r.face = null;
                r.hitBlock = false;
                r.ticks = i;
                return;
            }
        }
    }
}
