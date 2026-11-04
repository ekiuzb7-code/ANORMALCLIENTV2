package com.anormal.client.module.impl.kuro;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.UUID;

/**
 * kuro {@code FakePlayer} port: jang modullarini server yoki ikkinchi akkaunt
 * siz sinash uchun klient ichida yashovchi karton o'yinchi.
 *
 * <p>U faqat SIZNING klientingizda bor (manfiy entity id, hech qayerga
 * jo'natilmaydi). Uni urganda hujum {@code ClientPlayerInteractionManager#attackEntity}
 * mixin'ida ushlanadi (paket yuborilmaydi), damage/knockback/death simulyatsiya
 * qilinadi. O'yinchilarga qaraydigan barcha modullar (aura, nametag, hitbox...)
 * uni oddiy o'yinchi sifatida ko'radi.</p>
 *
 * <p>Damage = hujum-damage attributi (tutgan qurolingiz qo'shiladi) la charge
 * ko'paytirilgani, havodan crit bo'lsa x1.5, so'ng karton qirig'i bilan
 * kamaytirilgan. Sharpness kabi enchant simulyatsiya qilinmaydi.</p>
 *
 * <p>Moslash: kuro ismini {@code Setting.Text}'ga yozish va "Respawn Now"
 * {@code Setting.Button}'ini ko'rsatadi; Anormal sobrasi sozlamalarda bu turlar
 * yo'q, shuning uchun ism o'zgarmas "Dummy" va respawn faqat
 * {@link KuroFakePlayer#respawnNow()} metod orqali chaqiriladi.</p>
 */
public class KuroFakePlayer extends Module {
    private static final int FAKE_ID = -80421;
    private static final String DUMMY_NAME = "Dummy";
    private static KuroFakePlayer instance;

    private final NumberSetting health = new NumberSetting("Health", "Starting health", 20.0, 1.0, 40.0, 1.0);
    private final BooleanSetting copyGear = new BooleanSetting("Copy My Gear", "Copy your armor and held items", true);
    private final ModeSetting motion = new ModeSetting("Motion", "How the dummy moves", "Static", "Static", "Strafe", "Follow");
    private final NumberSetting moveSpeed = new NumberSetting("Move Speed", "speed of strafe/follow", 0.25, 0.05, 0.6, 0.05);
    private final NumberSetting radius = new NumberSetting("Distance", "how far it stays from you", 3.0, 1.5, 6.0, 0.1);
    private final BooleanSetting knockback = new BooleanSetting("Knockback", "if your hits launch it", true);
    private final BooleanSetting respawn = new BooleanSetting("Respawn On Death", "respawn instead of turning off", true);
    private final BooleanSetting damageLog = new BooleanSetting("Damage In Chat", true);

    private OtherClientPlayerEntity fake;
    private ClientWorld fakeWorld;
    private Vec3d anchor = Vec3d.ZERO;
    private Vec3d vel = Vec3d.ZERO;
    private double orbit;
    private int hurtCooldown;
    private int deadTicks = -1;

    public KuroFakePlayer() {
        super("Fake Player", "Client-side dummy player to test combat modules", Category.KURO);
        instance = this;
        addSetting(health);
        addSetting(copyGear);
        addSetting(motion);
        addSetting(moveSpeed);
        addSetting(radius);
        addSetting(knockback);
        addSetting(respawn);
        addSetting(damageLog);
    }

    public static KuroFakePlayer get() { return instance; }

    // ---------------------------------------------------------------- hooks used elsewhere

    /** True for the dummy (AntiBot / LogoutSpots do not treat it as a bot or a logout). */
    public static boolean isFake(net.minecraft.entity.Entity e) {
        return instance != null && instance.fake != null && e == instance.fake;
    }

    /**
     * Called from ClientPlayerInteractionManagerMixin#attackEntity. Returns true when the target is
     * the dummy: the real attack is then cancelled (no packet leaves the client) and the hit is simulated here.
     */
    public static boolean onAttack(net.minecraft.entity.Entity target) {
        if (instance == null || !instance.isEnabled() || instance.fake == null || target != instance.fake) return false;
        instance.hit();
        return true;
    }

    // ---------------------------------------------------------------- lifecycle

    @Override public void onEnable() { if (mc.world == null || mc.player == null) { setEnabled(false); return; } spawn(); }
    @Override public void onDisable() { remove(); }

    /** kuro "Respawn Now" button (Setting.Button doesn't exist here): respawn now if enabled. */
    public void respawnNow() { if (isEnabled() && mc.world != null) spawn(); }

    @Override
    public void onTick() {
        if (mc.world == null || mc.player == null) return;
        if (fake == null || fake.isRemoved() || fakeWorld != mc.world) {   // joined another world: the old entity is gone
            spawn();
            return;
        }

        if (hurtCooldown > 0) hurtCooldown--;

        if (deadTicks >= 0) {                                              // death: let it fall over, then respawn / switch off
            if (++deadTicks >= 25) {
                remove();
                if (respawn.isEnabled()) spawn(); else setEnabled(false);
            }
            return;
        }

        moveFake();
    }

    private void spawn() {
        remove();
        if (mc.world == null || mc.player == null) return;

        fake = new OtherClientPlayerEntity(mc.world, new GameProfile(UUID.randomUUID(), DUMMY_NAME));
        fake.setId(FAKE_ID);
        fakeWorld = mc.world;

        Vec3d look = mc.player.getRotationVec(1.0f);
        Vec3d flat = new Vec3d(look.x, 0, look.z);
        flat = flat.lengthSquared() < 1e-4 ? new Vec3d(0, 0, 1) : flat.normalize();
        Vec3d want = mc.player.getEntityPos().add(flat.multiply(radius.getValue()));
        anchor = safeSpawn(want);
        vel = Vec3d.ZERO;
        orbit = Math.atan2(anchor.z - mc.player.getZ(), anchor.x - mc.player.getX());
        hurtCooldown = 0;
        deadTicks = -1;

        place(anchor.x, anchor.y, anchor.z);
        faceTowards(mc.player.getX(), mc.player.getZ());
        fake.lastX = anchor.x; fake.lastY = anchor.y; fake.lastZ = anchor.z;
        fake.setOnGround(true);

        EntityAttributeInstance max = fake.getAttributeInstance(EntityAttributes.MAX_HEALTH);
        if (max != null) max.setBaseValue(health.getValue());
        fake.setHealth(health.getValue().floatValue());

        if (copyGear.isEnabled()) {
            for (EquipmentSlot s : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
                    EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND}) {
                fake.equipStack(s, mc.player.getEquippedStack(s).copy());
            }
        }

        mc.world.addEntity(fake);
    }

    private void remove() {
        if (fake != null && !fake.isRemoved()) fake.discard();
        fake = null;
        fakeWorld = null;
        deadTicks = -1;
    }

    // ---------------------------------------------------------------- collision

    /** Box (0.6 x 1.8) berilgan nuqtada bu'sh (devor ichiga sig'adigan) joy bo'lsa true. */
    private boolean freeSpot(Vec3d p) {
        Box box = new Box(p.x - 0.3, p.y, p.z - 0.3, p.x + 0.3, p.y + 1.8, p.z + 0.3);
        return mc.world.isSpaceEmpty(fake, box);
    }

    /** Box bir oz siljiganda to'qnashuv yo'q bo'lsa true. */
    private boolean free(double dx, double dy, double dz) {
        if (fake == null) return true;
        return mc.world.isSpaceEmpty(fake, fake.getBoundingBox().offset(dx, dy, dz));
    }

    /**
     * Spawn uchun yaroqli joy topadi: havo (devor ichi emas) va ostida qattiq pol bor.
     * Anchor blok ichiga tushadigan bo'lsa atrofida spiral qilib qidiradi.
     */
    private Vec3d safeSpawn(Vec3d want) {
        if (isValidSpot(want)) return want;
        for (double d = 0.5; d <= radius.getValue() + 2.0; d += 0.5) {
            for (int a = 0; a < 16; a++) {
                double ang = a / 16.0 * Math.PI * 2.0;
                Vec3d p = new Vec3d(want.x + Math.cos(ang) * d, want.y, want.z + Math.sin(ang) * d);
                if (isValidSpot(p)) return p;
                Vec3d up = p.add(0, 1, 0);
                if (isValidSpot(up)) return up;
            }
        }
        return want;   // hech biri yo'q — baribir shu joyda (kollizya harakatda himoya qiladi)
    }

    private boolean isValidSpot(Vec3d p) {
        if (!freeSpot(p)) return false;
        BlockPos ground = BlockPos.ofFloored(p.x, p.y - 0.1, p.z);
        return !mc.world.getBlockState(ground).getCollisionShape(mc.world, ground).isEmpty();
    }

    // ---------------------------------------------------------------- movement (OtherClientPlayerEntity has no physics of its own)

    private void moveFake() {
        if (fake == null) return;
        Vec3d pos = fake.getEntityPos();
        double dx = 0, dz = 0;

        String mode = motion.getValue();
        if ("Strafe".equals(mode)) {                         // circles you at the set distance
            orbit += moveSpeed.getValue() / Math.max(1.0, radius.getValue());
            double tx = mc.player.getX() + Math.cos(orbit) * radius.getValue();
            double tz = mc.player.getZ() + Math.sin(orbit) * radius.getValue();
            dx = (tx - pos.x) * 0.35;
            dz = (tz - pos.z) * 0.35;
        } else if ("Follow".equals(mode)) {                  // walks up to you and stops at the set distance
            double ax = mc.player.getX() - pos.x, az = mc.player.getZ() - pos.z;
            double d = Math.sqrt(ax * ax + az * az);
            double stop = Math.min(radius.getValue(), 2.0);
            if (d > stop) {
                double step = Math.min(moveSpeed.getValue(), d - stop);
                dx = ax / d * step;
                dz = az / d * step;
            }
        }

        // simulated knockback + gravity
        vel = new Vec3d(vel.x * 0.6, (vel.y - 0.08) * 0.98, vel.z * 0.6);
        dx += vel.x;
        dz += vel.z;
        double dy = vel.y;

        // Per-axis collision: devor ichiga surib ketishning oldi olinadi —
        // blok bo'lsa o'sha o'q qadamda to'xtaydi (sleyd), qolgan ikki o'q davom etadi.
        boolean grounded = false;

        if (!free(dx, dy, dz)) {                              // to'liq qadam mos kelmadi — o'qlar bo'ylab yechamiz
            if (dy != 0 && !free(0, dy, 0)) {
                double s = Math.signum(dy) * 0.05, moved = 0;
                while (Math.abs(moved + s) <= Math.abs(dy) && free(0, moved + s, 0)) moved += s;
                dy = moved;
                vel = new Vec3d(vel.x, 0, vel.z);
                if (dy < 0) grounded = true;                  // polda to'xtadi (pastga siljish kutildi)
            }
            if (dx != 0 && !free(dx, 0, 0)) {
                double s = Math.signum(dx) * 0.05, moved = 0;
                while (Math.abs(moved + s) <= Math.abs(dx) && free(moved + s, 0, 0)) moved += s;
                dx = moved;
                vel = new Vec3d(0, vel.y, vel.z);
            }
            if (dz != 0 && !free(0, 0, dz)) {
                double s = Math.signum(dz) * 0.05, moved = 0;
                while (Math.abs(moved + s) <= Math.abs(dz) && free(0, 0, moved + s)) moved += s;
                dz = moved;
                vel = new Vec3d(vel.x, vel.y, 0);
            }
        }

        place(pos.x + dx, pos.y + dy, pos.z + dz);
        fake.setOnGround(grounded);
        faceTowards(mc.player.getX(), mc.player.getZ());
    }

    private void place(double x, double y, double z) {
        fake.setPosition(x, y, z);   // the world tick copies the previous position to lastX/lastY/lastZ, so rendering interpolates
    }

    private void faceTowards(double x, double z) {
        float yaw = (float) Math.toDegrees(Math.atan2(z - fake.getZ(), x - fake.getX())) - 90f;
        fake.setYaw(yaw);
        fake.setPitch(0f);
        fake.headYaw = yaw;
        fake.bodyYaw = yaw;
        if (fake.age == 0) {              // first placement: no smoothing from a stale angle
            fake.lastYaw = yaw; fake.lastPitch = 0f;
        }
    }

    // ---------------------------------------------------------------- damage simulation

    private void hit() {
        if (deadTicks >= 0 || hurtCooldown > 0) { resetAttackCharge(); return; }

        float charge = mc.player.getAttackCooldownProgress(0.5f);
        double dmg = mc.player.getAttributeValue(EntityAttributes.ATTACK_DAMAGE) * (0.2 + charge * charge * 0.8);
        boolean crit = charge > 0.9f && mc.player.fallDistance > 0 && !mc.player.isOnGround()
                && !mc.player.isTouchingWater() && !mc.player.hasVehicle() && !mc.player.isSprinting();
        if (crit) dmg *= 1.5;

        // vanilla armour formula
        double armor = fake.getArmor();
        double tough = fake.getAttributeValue(EntityAttributes.ARMOR_TOUGHNESS);
        double eff = MathHelper.clamp(armor - dmg / (2.0 + tough / 4.0), armor * 0.2, 20.0);
        dmg *= 1.0 - eff / 25.0;

        float before = fake.getHealth();
        fake.setHealth(Math.max(0f, before - (float) dmg));
        ((com.anormal.client.mixin.PlayerEntityAccessor) fake).setDamageTiltYaw(mc.player.getYaw());   // 1.21.11: damage tilt is a protected PlayerEntity field (kuro: animateHurt)
        hurtCooldown = 10;

        if (knockback.isEnabled()) {
            Vec3d away = fake.getEntityPos().subtract(mc.player.getEntityPos());
            away = new Vec3d(away.x, 0, away.z);
            away = away.lengthSquared() < 1e-6 ? mc.player.getRotationVec(1.0f) : away.normalize();
            double kb = mc.player.isSprinting() ? 0.9 : 0.4;
            vel = new Vec3d(away.x * kb, 0.36, away.z * kb);
        }

        resetAttackCharge();   // vanilla attack() resets the charge; we cancelled it

        say(String.format("hit for %.1f%s  (%.1f / %.0f hp)",
                dmg, crit ? " (crit)" : "", fake.getHealth(), health.getValue()));
        if (fake.getHealth() <= 0f) {
            deadTicks = 0;
            say("killed");
        }
    }

    private void say(Object s) {
        if (damageLog.isEnabled() && mc.player != null) {
            mc.player.sendMessage(net.minecraft.text.Text.literal("[Fake Player] " + s), false);
        }
    }

    private static void resetAttackCharge() {
        if (mc.player != null) {
            ((com.anormal.client.mixin.LivingEntityAccessor) mc.player).setLastAttackedTime(0);   // charge = 0: next hit payan recharges
        }
    }
}
