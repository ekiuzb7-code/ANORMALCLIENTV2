package com.anormal.client.module.impl.kuro;

import com.anormal.client.lucid.WorldRender3D;
import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.module.ModuleManager;
import com.anormal.client.module.impl.client.AntiBot;
import com.anormal.client.module.impl.client.Friends;
import com.anormal.client.module.impl.world.Freecam;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.kuro.KuroCombatControl;
import com.anormal.client.util.kuro.KuroCombatReach;
import com.anormal.client.util.kuro.KuroRotations;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Kuro AttackAura port (dev.kuro.client.module.impl.AttackAura'dan Anormal'da).
 *
 * <p>Ko'rish ichidagi eng yaqin valid nishonga yumshoq (silent) aim qiladi va
 * qurollangan paytda uradi. "Attack range" ichida hujum qilinadi,
 * "Pre-aim range" esa nishon qamrab olinadigan radiusni kengaytiradi.</p>
 *
 * <p>Rotation rejimlari:
 * <ul>
 *   <li>1 Default — yumshoq silent aim (GCD, onTarget'da sekinlashish).</li>
 *   <li>2 Snap — hujum tayyor bo'lgan tickda to'g'ridan-to'g'ri burchaklar.</li>
 *   <li>3 HvH — instant lock (hez hollow mode uchun umumiy posit).</li>
 *   <li>4 Camera — real ko'rinishni sekin buradi (silent emas).</li>
 * </ul></p>
 *
 * <p>Hujumlar {@link KuroAttackPackets} (paket tartibi) va {@link KuroAutoWeapon}
 * (siloh tanlash / shield axe / mace) orqali amalga oshadi.</p>
 */
public class KuroAttackAura extends Module {
    private final NumberSetting attackRange = new NumberSetting("Attack range", "Melee reach for hits", 3.0, 2.5, 3.0, 0.1);
    private final NumberSetting preRange = new NumberSetting("Pre-aim range", "Extra aim-detection distance", 1.0, 0.0, 3.0, 0.1);
    private final BooleanSetting players = new BooleanSetting("Players", "Attack other players", true);
    private final BooleanSetting naked = new BooleanSetting("Naked players", "Also attack players without armor", true);
    private final BooleanSetting mobs = new BooleanSetting("Mobs", "Attack mobs", false);
    private final BooleanSetting friends = new BooleanSetting("Friends", "Also attack friends", false);
    private final NumberSetting rotation = new NumberSetting("Rotation (1-4)", "1 silent smooth, 2 snap, 3 HvH, 4 camera", 1.0, 1.0, 4.0, 1.0);
    private final NumberSetting cameraSpeed = new NumberSetting("Camera speed (4)", "Real camera turn speed", 12.0, 2.0, 60.0, 1.0);
    private final NumberSetting minCharge = new NumberSetting("Min charge", "Minimum attack cooldown progress", 0.95, 0.1, 1.0, 0.05);
    private final BooleanSetting throughWalls = new BooleanSetting("Hit through blocks", "Also hit targets behind walls", false);
    private final BooleanSetting pauseEating = new BooleanSetting("Pause while using item", "Aim at nothing while using/eating", true);
    private final BooleanSetting esp = new BooleanSetting("Target box", "Draw the target's hitbox in the world", true);
    private final ColorSetting espColor = new ColorSetting("Target colour", "Target box colour", 0xFFE14A4A);

    private static KuroAttackAura instance;

    private LivingEntity target;
    private float angYaw, angPitch;           // silent burchaklar
    private double speedYaw = 34.0, speedPitch = 12.0;
    private boolean espRegistered;

    public KuroAttackAura() {
        super("Attack Aura", "Smooth silent aim on the melee target (kuro port)", Category.KURO);
        instance = this;
        addSetting(attackRange);
        addSetting(preRange);
        addSetting(players);
        addSetting(naked);
        addSetting(mobs);
        addSetting(friends);
        addSetting(rotation);
        addSetting(cameraSpeed);
        addSetting(minCharge);
        addSetting(throughWalls);
        addSetting(pauseEating);
        addSetting(esp);
        addSetting(espColor);
    }

    public static KuroAttackAura get() { return instance; }
    public static LivingEntity currentTarget() { return instance != null && instance.isEnabled() ? instance.target : null; }

    @Override
    public void onEnable() {
        registerEsp();
    }

    @Override
    public void onDisable() {
        if (target != null) {
            target = null;
        }
        KuroRotations.release(this);
    }

    private void registerEsp() {
        if (espRegistered) return;
        try {
            WorldRenderEvents.END_MAIN.register(this::anormal$dispatchWorld);
            espRegistered = true;
        } catch (Throwable ignored) {
        }
    }

    private void anormal$dispatchWorld(WorldRenderContext wrc) {
        if (!isEnabled() || !esp.isEnabled()) return;
        renderWorld(wrc);
    }

    private boolean usable() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return false;
        if (mc.currentScreen != null || freecamActive() || mc.player.isSpectator()) return false;
        if (KuroCombatControl.blocked(this)) return false;
        return !(pauseEating.isEnabled() && mc.player.isUsingItem());
    }

    private double detectRange() { return attackRange.getValue() + preRange.getValue(); }

    @Override
    public void onTick() {
        if (!usable()) {
            if (target != null) target = null;
            KuroRotations.release(this);
            return;
        }

        LivingEntity prev = target;
        if (target == null || !valid(target)) target = pickTarget();
        if (prev != target) { angYaw = mc.player.getYaw(); angPitch = mc.player.getPitch(); }
        if (target == null) {
            KuroRotations.release(this);
            return;
        }

        int mode = (int) rotation.getAsDouble();
        float[] want = anglesTo(aimPoint());

        switch (mode) {
            case 2 -> {                                   // Snap: faqat hujum tayyor bo'lganda burchak o'rnat
                if (ready()) KuroRotations.set(this, want[0], want[1]);
                else KuroRotations.release(this);
            }
            case 3 -> KuroRotations.set(this, want[0], want[1]); // HvH: instant lock
            case 4 -> {                                   // Camera: real ko'rinishni buradi, silent emas
                KuroRotations.release(this);
                cameraStep(want[0], want[1]);
            }
            default -> {                                  // Default: yumshoq silent aim
                steer(want[0], want[1], rayOnTarget());
                KuroRotations.set(this, angYaw, angPitch);
            }
        }

        // ----- hujum (tick oxiri; paket Keyingi harakat paketidan oldin chiqadi) -----
        if (target == null || !target.isAlive()) return;

        KuroAutoWeapon.onTarget(target);
        boolean weaponHandled = KuroAutoWeapon.bypass(target);
        if (!weaponHandled && KuroAutoWeapon.progress() < minCharge.getValue()) return;
        if (!inAttackRange(target)) return;
        if (!throughWalls.isEnabled() && !lineOfSight(target)) return;
        if (!aimed(target)) return;
        if (!KuroAutoWeapon.readyToAttack()) return;      // bu tickda slot almashtirildi: keyingi tick

        final double reach = attackRange.getValue();
        KuroAttackPackets.sequence(target,
                e -> e instanceof LivingEntity le && le.isAlive() && KuroCombatReach.eyeDistance(le) <= reach + 0.5,
                e -> KuroAttackPackets.sendNow(e));
    }

    private boolean ready() {
        return target != null && (KuroAutoWeapon.bypass(target) || KuroAutoWeapon.progress() >= minCharge.getValue());
    }

    private boolean inAttackRange(LivingEntity e) { return KuroCombatReach.eyeDistance(e) <= attackRange.getValue(); }

    private boolean aimed(LivingEntity e) {
        boolean silent = KuroRotations.active() && KuroRotations.ownedBy(this);
        float y = silent ? KuroRotations.yaw() : mc.player.getYaw();
        float p = silent ? KuroRotations.pitch() : mc.player.getPitch();
        return KuroCombatReach.rayHitsLen(e, y, p, attackRange.getValue());
    }

    private boolean rayOnTarget() {
        return target != null && KuroCombatReach.rayHitsLen(target, angYaw, angPitch, attackRange.getValue());
    }

    private boolean lineOfSight(Entity other) {
        try {
            return mc.player.canSee(other);
        } catch (Throwable t) {
            // eski versiyalar: hasLineOfSight
            try {
                return mc.player.canSee(other);
            } catch (Throwable t2) {
                return false;
            }
        }
    }

    /** Kamera (4-rejim): maqsad burchaklariga real ko'rinishni kameraSpeed/20 darajada buradi. */
    private void cameraStep(float wantYaw, float wantPitch) {
        float p0 = mc.player.getPitch(), y0 = mc.player.getYaw();
        double errYaw = MathHelper.wrapDegrees(wantYaw - y0);
        double errPitch = wantPitch - p0;
        double step = cameraSpeed.getValue() / 20.0;   // har tickda buraladigan daraja
        float ny = (float) (y0 + Math.max(-step, Math.min(step, errYaw)));
        float np = MathHelper.clamp((float) (p0 + Math.max(-step, Math.min(step, errPitch))), -90.0f, 90.0f);
        mc.player.setYaw(ny);
        mc.player.setPitch(np);
    }

    /** Yumshoq yaqinlashish (speedlar): uzoqda tez, targetga tegib ketganda sekin durang. */
    private void steer(float wantYaw, float wantPitch, boolean onTarget) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        double ty = onTarget ? 3 + r.nextDouble() : 20 + r.nextDouble() * 25;
        double tp = onTarget ? 1 + r.nextDouble() : 7 + r.nextDouble() * 5;
        speedYaw += (ty - speedYaw) * 0.3;
        speedPitch += (tp - speedPitch) * 0.3;

        double errYaw = MathHelper.wrapDegrees(wantYaw - angYaw);
        double errPitch = wantPitch - angPitch;
        double stepYaw = Math.max(-speedYaw, Math.min(speedYaw, errYaw));
        double stepPitch = Math.max(-speedPitch, Math.min(speedPitch, errPitch));

        double gcd = gcd();
        if (gcd > 1e-6) {
            stepYaw = Math.round(stepYaw / gcd) * gcd;
            stepPitch = Math.round(stepPitch / gcd) * gcd;
        }
        angYaw += (float) stepYaw;
        angPitch = MathHelper.clamp((float) (angPitch + stepPitch), -90.0f, 90.0f);
    }

    /** Sichqoncha sezgirligidan hosil bo'ladigan GCD (Gradual Coefficient of Differentiation). */
    private static double gcd() {
        try {
            double sens = mc.options.getMouseSensitivity().getValue();
            double f = sens * 0.6 + 0.2;
            return f * f * f * 1.2 * 0.15;
        } catch (Exception e) {
            return 0.0;
        }
    }

    // ----- nishon tanlash ---------------------------------------------------------------------------

    private LivingEntity pickTarget() {
        LivingEntity best = null;
        double bestAngle = Double.MAX_VALUE;
        Vec3d eye = mc.player.getEyePos();
        Vec3d look = Vec3d.fromPolar(mc.player.getPitch(), mc.player.getYaw());
        for (Entity e : mc.world.getEntities()) {
            if (!(e instanceof LivingEntity le) || !valid(le)) continue;
            Vec3d to = le.getEntityPos().add(0, le.getHeight() * 0.5, 0).subtract(eye);
            double len = to.length();
            if (len < 1e-6) continue;
            double angle = Math.acos(MathHelper.clamp(look.dotProduct(to.normalize()), -1.0, 1.0));
            if (angle < bestAngle) { bestAngle = angle; best = le; }
        }
        return best;
    }

    private boolean valid(LivingEntity e) {
        if (e == mc.player || !e.isAlive() || e.isRemoved() || e instanceof ArmorStandEntity) return false;
        if (e.isInvulnerable()) return false;
        if (KuroCombatReach.eyeDistance(e) > detectRange()) return false;
        if (!throughWalls.isEnabled() && !lineOfSight(e)) return false;
        if (e instanceof PlayerEntity p) {
            if (!players.isEnabled() || p.isSpectator() || p.isCreative()) return false;
            if (!naked.isEnabled() && p.getArmor() <= 0) return false;
            if (!friends.isEnabled() && Friends.isFriend(p.getName().getString())) return false;
            return !AntiBot.isBot(p);
        }
        return mobs.isEnabled() && e instanceof MobEntity;
    }

    // ----- nishon nuqtasi ------------------------------------------------------------------------------

    /** Ko'zga eng yaqin hitbox nuqtasi, tananing o'rtasi tomon ozgina tortiladi va salqin sayyorlanadi. */
    private Vec3d aimPoint() {
        if (target == null) return null;
        Box b = target.getBoundingBox();
        Vec3d eye = mc.player.getEyePos();
        double w = b.getLengthX(), h = b.getLengthY();
        double cx = MathHelper.clamp(eye.x, b.minX + w * 0.2, b.maxX - w * 0.2);
        double cz = MathHelper.clamp(eye.z, b.minZ + w * 0.2, b.maxZ - w * 0.2);
        double cy = MathHelper.clamp(eye.y, b.minY + h * 0.25, b.maxY - h * 0.15);
        double midX = (b.minX + b.maxX) / 2, midY = b.minY + h * 0.65, midZ = (b.minZ + b.maxZ) / 2;
        double t = System.currentTimeMillis();
        return new Vec3d(
                midX + (cx - midX) * 0.5 + 0.08 * Math.sin(t / 250.0),
                midY + (cy - midY) * 0.5 + h * 0.04 * Math.cos(t / 850.0),
                midZ + (cz - midZ) * 0.5 + 0.08 * Math.cos(t / 310.0));
    }

    private float[] anglesTo(Vec3d to) {
        Vec3d eye = mc.player.getEyePos();
        double dx = to.x - eye.x, dy = to.y - eye.y, dz = to.z - eye.z;
        double h = Math.sqrt(dx * dx + dz * dz);
        float y = h > 0.01 ? (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0) : angYaw;
        float p = (float) MathHelper.clamp(-Math.toDegrees(Math.atan2(dy, Math.max(h, 1e-4))), -90.0, 90.0);
        return new float[]{y, p};
    }

    // ----- target ESP -----------------------------------------------------------------------------------

    private void renderWorld(WorldRenderContext wrc) {
        if (mc.world == null || mc.player == null || target == null || !target.isAlive()) return;
        Camera camera = wrc.gameRenderer().getCamera();
        if (camera == null || !camera.isReady()) return;
        MatrixStack matrices = wrc.matrices();
        VertexConsumerProvider consumers = wrc.consumers();
        if (matrices == null || consumers == null) return;

        Freecam freecam = ModuleManager.getModule(Freecam.class);
        if (freecam != null && freecam.isEnabled()) return;

        Box bb = target.getBoundingBox();
        Vec3d lp = target.getLerpedPos(mc.getRenderTickCounter().getTickProgress(false));
        bb = bb.offset(lp.x - target.getEntityPos().x, lp.y - target.getEntityPos().y, lp.z - target.getEntityPos().z);
        boolean hurt = target.hurtTime > 0;
        int rgb = hurt ? 0xE14A4A : (espColor.getColor() & 0xFFFFFF);

        WorldRender3D.begin(matrices, camera);
        try {
            WorldRender3D.drawBox(consumers, bb, 0xFF000000 | rgb);
        } finally {
            WorldRender3D.end(matrices);
        }
    }

    private boolean freecamActive() {
        Freecam fc = ModuleManager.getModule(Freecam.class);
        return fc != null && fc.isEnabled();
    }
}
