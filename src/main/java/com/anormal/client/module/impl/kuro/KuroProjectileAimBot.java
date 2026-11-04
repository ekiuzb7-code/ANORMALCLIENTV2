package com.anormal.client.module.impl.kuro;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.module.impl.client.AntiBot;
import com.anormal.client.module.impl.client.Friends;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.kuro.KuroTargetUtils;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BowItem;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.Items;
import net.minecraft.item.TridentItem;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Kuro Projectile Aim port (dev.kuro.client.module.impl.ProjectileAimBot'dan Anormal'da).
 *
 * <p>ru.white klientidagi "Aim Bot" porti: kamon / krossbow / trident uchun aim.</p>
 *
 * <ul>
 *   <li>Valid nishon — crosshair'ga eng yaqin -- tanlaydi.</li>
 *   <li>Target qayeriga projectile tushishini bashorat qiladi (iterativ parvoz vaqti
 *       taxmin: target silliq tezligi bilan) — "solving the launch angle with gravity
 *       0.05 and drag 0.99 by bisection", o'z harakatingizni kompensatsiya qilib.</li>
 *   <li>Har render frame'ida real ko'rishni yechimga buradi, horizontal / vertikal
 *       silliqlik va sichqoncha sezgirlik GCD bilan — oddiy sichqoncha muvaffaqiyati
 *       sifatida ko'rinadi.</li>
 * </ul>
 */
public class KuroProjectileAimBot extends Module {
    private final BooleanSetting onlyWhenUsing = new BooleanSetting("Only while charging", "Aim only while you are pulling the bow", true);
    private final BooleanSetting players = new BooleanSetting("Players", "Aim at players", true);
    private final BooleanSetting mobs = new BooleanSetting("Mobs", "Aim at hostile mobs", false);
    private final BooleanSetting friends = new BooleanSetting("Friends", "Aim at friends too", false);
    private final NumberSetting range = new NumberSetting("Range", "Target search radius (blocks)", 30.0, 5.0, 80.0, 1.0);
    private final NumberSetting smoothH = new NumberSetting("Horizontal smoothing", "Yaw step share of remaining distance (0..1)", 0.15, 0.01, 1.0, 0.01);
    private final NumberSetting smoothV = new NumberSetting("Vertical smoothing", "Pitch step share of remaining distance (0..1)", 0.15, 0.01, 1.0, 0.01);
    private final NumberSetting predictMult = new NumberSetting("Predict strength", "How far along the flight time the target is extrapolated", 1.0, 0.0, 2.0, 0.05);

    private static final double MOUSE_UNIT = 0.15;

    private final Map<UUID, Vec3d[]> posHistory = new HashMap<>();
    private LivingEntity target;

    public KuroProjectileAimBot() {
        super("Projectile Aim", "Steer your view so a bow/crossbow/trident projectile lands on the target (kuro port)", Category.KURO);
        addSetting(onlyWhenUsing);
        addSetting(players);
        addSetting(mobs);
        addSetting(friends);
        addSetting(range);
        addSetting(smoothH);
        addSetting(smoothV);
        addSetting(predictMult);
    }

    @Override
    public void onDisable() {
        target = null;
        posHistory.clear();
    }

    // ----- per tick: velocity history + target pick -----------------------------------------------

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) { target = null; posHistory.clear(); return; }

        Set<UUID> seen = new HashSet<>();
        for (Entity e : mc.world.getEntities()) {
            if (!(e instanceof LivingEntity) || e == mc.player) continue;
            UUID id = e.getUuid();
            Vec3d pos = e.getEntityPos();
            Vec3d[] h = posHistory.computeIfAbsent(id, k -> new Vec3d[]{pos, pos, pos});
            h[2] = h[1];
            h[1] = h[0];
            h[0] = pos;
            seen.add(id);
        }
        posHistory.keySet().retainAll(seen);

        if (onlyWhenUsing.isEnabled() && projectileSpeed() <= 0f) {
            target = null;
        } else if (target == null || !valid(target)) {
            target = selectTarget();
        }
    }

    // ----- per frame: steer the camera ---------------------------------------------------------

    @Override
    public void onRender2D(net.minecraft.client.gui.DrawContext g, float tickDelta) {
        if (mc.player == null || mc.world == null || mc.currentScreen != null || target == null) return;
        if (!valid(target)) { target = null; return; }

        float speed = projectileSpeed();
        if (speed <= 0f) return;

        Vec3d eye = mc.player.getEyePos();
        Vec3d base = target.getEntityPos().add(0, target.getHeight() * 0.2, 0);
        Vec3d vel = smoothedVelocity(target);
        Vec3d predicted = predictPosition(eye, base, vel, speed);
        predicted = compensatePlayerVelocity(eye, predicted, speed);

        Aim aim = calcAim(eye, predicted, speed);
        if (aim == null) return;

        float newYaw = (float) Math.toDegrees(Math.atan2(-(predicted.x - eye.x), predicted.z - eye.z));
        double yawStep = MathHelper.wrapDegrees(newYaw - mc.player.getYaw()) * smoothH.getValue();
        double pitchStep = (aim.pitch() - mc.player.getPitch()) * smoothV.getValue();

        double gcd = gcd();
        if (gcd > 1e-6) {
            yawStep = Math.round(yawStep / gcd) * gcd;
            pitchStep = Math.round(pitchStep / gcd) * gcd;
        }
        if (Math.abs(yawStep) < 1e-4 && Math.abs(pitchStep) < 1e-4) return;
        mc.player.changeLookDirection((float) (yawStep / MOUSE_UNIT), (float) (pitchStep / MOUSE_UNIT));
    }

    private double gcd() {
        try {
            double f = mc.options.getMouseSensitivity().getValue() * 0.6 + 0.2;
            return f * f * f * 1.2 * 0.15;
        } catch (Exception e) {
            return 0.0;
        }
    }

    // ----- prediction -------------------------------------------------------------------------

    private Vec3d predictPosition(Vec3d from, Vec3d targetBase, Vec3d vel, float speed) {
        Vec3d predicted = targetBase;
        double mult = predictMult.getValue();
        for (int i = 0; i < 10; i++) {
            Aim aim = calcAim(from, predicted, speed);
            if (aim == null) break;
            Vec3d next = targetBase.add(vel.multiply(aim.ticks() * mult));
            if (next.squaredDistanceTo(predicted) < 0.001) break;
            predicted = next;
        }
        return predicted;
    }

    /** Pitch (Minecraft adabiyotida, manfiy = yuqoriga) boshqa vertikal vazifalar (tick) uchun uchish vaqti bilan to yoki null agar yetib borsa. */
    private Aim calcAim(Vec3d from, Vec3d to, float speed) {
        double dx = to.x - from.x, dy = to.y - from.y, dz = to.z - from.z;
        double d = Math.sqrt(dx * dx + dz * dz);
        double ux = d > 0.001 ? dx / d : 0.0;
        double uz = d > 0.001 ? dz / d : 1.0;
        if (d < 0.3) return new Aim((float) (dy > 0.0 ? -89.0f : 89.0f), 5);

        double lo = -1.5607963267948965, hi = 1.5607963267948965;
        if (Double.isNaN(simYAtDist(from.y, ux, uz, speed, Math.toRadians(75.0), d))) return null;

        for (int i = 0; i < 40; i++) {
            double mid = (lo + hi) * 0.5;
            double simY = simYAtDist(from.y, ux, uz, speed, mid, d);
            if (Double.isNaN(simY) || simY < to.y) lo = mid; else hi = mid;
        }

        double elev = (lo + hi) * 0.5;
        double finalY = simYAtDist(from.y, ux, uz, speed, elev, d);
        if (Double.isNaN(finalY) || Math.abs(finalY - to.y) > 2.0) return null;

        float pitch = MathHelper.clamp((float) -Math.toDegrees(elev), -90f, 90f);
        return new Aim(pitch, simFlightTicks(ux, uz, speed, elev, d));
    }

    /** Shu masofadan o'tga yetib borganda balandligi; u yerda ulgurmasa NaN. */
    private double simYAtDist(double fromY, double ux, double uz, float speed, double elev, double targetD) {
        double cos = Math.cos(elev), sin = Math.sin(elev);
        double vx = ux * cos * speed, vy = sin * speed, vz = uz * cos * speed;
        double px = 0, py = fromY, pz = 0, prevHD = 0;

        for (int t = 0; t < 120; t++) {
            double npx = px + vx, npy = py + vy, npz = pz + vz;
            vy -= 0.05;
            vx *= 0.99; vy *= 0.99; vz *= 0.99;
            double hd = Math.sqrt(npx * npx + npz * npz);
            if (hd >= targetD) {
                double frac = prevHD < hd ? (targetD - prevHD) / (hd - prevHD) : 1.0;
                return py + (npy - py) * frac;
            }

            px = npx; py = npy; pz = npz;
            prevHD = hd;
            if (vy < -3.0) break;
        }
        return Double.NaN;
    }

    private int simFlightTicks(double ux, double uz, float speed, double elev, double targetD) {
        double cos = Math.cos(elev), sin = Math.sin(elev);
        double vx = ux * cos * speed, vy = sin * speed, vz = uz * cos * speed;
        double px = 0, pz = 0;
        for (int t = 1; t <= 120; t++) {
            px += vx; pz += vz;
            vy -= 0.05;
            vx *= 0.99; vy *= 0.99; vz *= 0.99;
            if (Math.sqrt(px * px + pz * pz) >= targetD) return t;
        }
        return 40;
    }

    /** O'q sizning harakatingizni oladi: uchish vaqtidagi drift'ga qarshi qarang. */
    private Vec3d compensatePlayerVelocity(Vec3d from, Vec3d to, float speed) {
        Aim rough = calcAim(from, to, speed);
        if (rough == null) return to;
        int t = rough.ticks();
        double dragSum = t > 0 ? (1.0 - Math.pow(0.99, t)) / 0.01 : 0.0;
        Vec3d pv = mc.player.getVelocity();
        return to.subtract(pv.x * dragSum, mc.player.isOnGround() ? 0.0 : pv.y * dragSum, pv.z * dragSum);
    }

    private Vec3d smoothedVelocity(LivingEntity e) {
        Vec3d[] h = posHistory.get(e.getUuid());
        return h == null ? e.getVelocity() : h[0].subtract(h[2]).multiply(0.5);
    }

    // ----- held item --------------------------------------------------------------------------

    /** Tok, involves/ejecta projectile tezligi (0 = aim qilinmagan). */
    private float projectileSpeed() {
        if (mc.player == null) return 0f;
        if (mc.player.isUsingItem()) {
            net.minecraft.item.ItemStack stack = mc.player.getActiveItem();
            if (stack.getItem() instanceof BowItem) {
                float charge = mc.player.getItemUseTime() / 20.0f;
                charge = (charge * charge + charge * 2.0f) / 3.0f;
                charge = Math.min(charge, 1.0f);
                return Math.max(charge, 0.2f) * 3.0f;
            }
            if (stack.getItem() instanceof CrossbowItem) return 3.15f;
            if (stack.getItem() instanceof TridentItem) return 2.5f;
            return 0f;
        }
        if (CrossbowItem.isCharged(mc.player.getMainHandStack()) || CrossbowItem.isCharged(mc.player.getOffHandStack())) return 3.15f;
        return 0f;
    }

    // ----- targets --------------------------------------------------------------------------

    private LivingEntity selectTarget() {
        LivingEntity best = null;
        double bestFov = Double.MAX_VALUE;
        Vec3d eye = mc.player.getEyePos();
        Vec3d look = mc.player.getRotationVec(1.0f).normalize();
        for (Entity e : mc.world.getEntities()) {
            if (!(e instanceof LivingEntity le) || !valid(le)) continue;
            Vec3d c = le.getEntityPos().add(0, le.getHeight() * 0.2, 0);
            double fov = Math.acos(MathHelper.clamp(look.dotProduct(c.subtract(eye).normalize()), -1.0, 1.0));
            if (fov < bestFov) { bestFov = fov; best = le; }
        }
        return best;
    }

    private boolean valid(LivingEntity e) {
        if (e == mc.player || !e.isAlive() || e.isInvulnerable() || e instanceof ArmorStandEntity) return false;
        if (e.squaredDistanceTo(mc.player) > range.getValue() * range.getValue()) return false;
        if (e instanceof PlayerEntity p) {
            if (!players.isEnabled() || p.isSpectator() || p.isCreative()) return false;
            if (AntiBot.isBot(p)) return false;
            return friends.isEnabled() || !Friends.isFriend(p.getGameProfile().name());
        }
        return mobs.isEnabled() && e instanceof MobEntity;
    }

    private record Aim(float pitch, int ticks) {}
}
