package com.anormal.client.module.impl.kuro;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.module.ModuleManager;
import com.anormal.client.module.impl.client.AntiBot;
import com.anormal.client.module.impl.client.Friends;
import com.anormal.client.module.impl.world.Freecam;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.kuro.KuroAssistAim;
import com.anormal.client.util.kuro.KuroCombatControl;
import com.anormal.client.util.kuro.KuroCombatReach;
import com.anormal.client.util.kuro.KuroRotations;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * Kuro SilentAura port (dev.kuro.client.module.impl.SilentAura'dan Anormal'da).
 *
 * Server faqat hujum tickida Deal of silent yaw/pitch ko'radi; kamera esa real
 * qoladi. Grim xavfsizligi uchun reach 2.85 bilan cheklanadi, hujum paketi
 * AttackPackets tartibi bilan harakat paketidan oldin (yoki look paketi
 * yordamida) chiqadi.
 */
public class KuroSilentAura extends Module {
    private final NumberSetting range = new NumberSetting("Range", "Max reach", 3.0, 1.0, 6.0, 0.1);
    private final NumberSetting rotation = new NumberSetting("Rotation", "0 = silent (F5 shows it), 1 = camera turns your view", 0.0, 0.0, 1.0, 1.0);
    private final NumberSetting strength = new NumberSetting("Aim strength", "How fast the aim converges", 6.0, 1.0, 10.0, 0.5);
    private final BooleanSetting vertical = new BooleanSetting("Aim vertically", "Also converge the pitch", true);
    private final NumberSetting humanize = new NumberSetting("Hand wobble %", "Human-like noise in the aim", 20.0, 0.0, 100.0, 5.0);
    private final BooleanSetting showModel = new BooleanSetting("Show in F5", "Show silent angles on the player model (F5)", true);
    private final BooleanSetting blatant = new BooleanSetting("Blatant mode", "Snap straight onto the target + orbit", false);
    private final NumberSetting spinSpeed = new NumberSetting("Spin speed", "Orbit speed in blatant mode", 0.8, 0.2, 1.6, 0.05);
    private final NumberSetting spinDist = new NumberSetting("Spin distance", "Orbit radius", 2.5, 1.5, 3.0, 0.1);
    private final NumberSetting minCharge = new NumberSetting("Min Charge", "Minimum attack cooldown progress", 0.95, 0.0, 1.0, 0.05);
    private final BooleanSetting players = new BooleanSetting("Players", "Attack other players", true);
    private final BooleanSetting mobs = new BooleanSetting("Mobs", "Attack other mobs", false);
    private final BooleanSetting friends = new BooleanSetting("Attack Friends", "Also attack friends", false);

    private final KuroAssistAim aim = new KuroAssistAim();
    private LivingEntity target;
    private boolean spoof;
    private float sYaw, sPitch;
    private float sentYaw, sentPitch;
    private boolean sentKnown;

    public KuroSilentAura() {
        super("Silent Aura", "Silent-aim melee aura (kuro port)", Category.KURO);
        addSetting(range);
        addSetting(rotation);
        addSetting(strength);
        addSetting(vertical);
        addSetting(humanize);
        addSetting(showModel);
        addSetting(blatant);
        addSetting(spinSpeed);
        addSetting(spinDist);
        addSetting(minCharge);
        addSetting(players);
        addSetting(mobs);
        addSetting(friends);
    }

    @Override public void onEnable() { target = null; sentKnown = false; idle(); }
    @Override public void onDisable() { target = null; idle(); }

    private void idle() {
        spoof = false;
        aim.reset();
        KuroRotations.release(this);
    }

    private boolean usable() {
        return mc.player != null && mc.world != null && mc.interactionManager != null && mc.currentScreen == null
                && !freecamActive() && !mc.player.isSpectator() && mc.player.isAlive();
    }

    @Override
    public void onTick() {
        // oxirgi yuborilgan haqiqiyy burchaklar: harakat paketlari doim real kamerani ko'taradi
        if (mc.player != null) {
            sentYaw = mc.player.getYaw();
            sentPitch = mc.player.getPitch();
            sentKnown = true;
        }

        if (!usable() || KuroCombatControl.blocked(this)) { target = null; idle(); return; }

        target = findTarget();
        if (target == null) { idle(); return; }

        boolean silent = rotation.getValue() == 0.0;
        boolean legit = !blatant.isEnabled();
        if (silent && !KuroRotations.free() && !KuroRotations.ownedBy(this)) return;
        if (!sentKnown) { sentYaw = mc.player.getYaw(); sentPitch = mc.player.getPitch(); sentKnown = true; }

        Vec3d eye = mc.player.getEyePos();
        // hitboxning aynan markaziga nishonlash
        Box bb = target.getBoundingBox();
        Vec3d targetCenter = bb.getCenter();
        double dist = Math.sqrt(targetCenter.squaredDistanceTo(eye));

        // latentsiya-hosil Hitboxes bayroqlaridan himoya: reach 2.85 bilan cap
        double reach = Math.min(range.getValue(), Math.min(2.85, KuroCombatReach.MAX));

        if (!silent && legit && canHit(dist, reach) && KuroCombatReach.rayHitsLen(target, sentYaw, sentPitch, reach)) swing(target);

        if (silent) {
            if (!spoof) { sYaw = mc.player.getYaw(); sPitch = mc.player.getPitch(); }
        } else {
            sYaw = mc.player.getYaw(); sPitch = mc.player.getPitch();
        }

        float pull = blatant.isEnabled() ? 10f : (float) strength.getAsDouble();
        float[] d = aim.step(target, sYaw, sPitch, 10.0, 10.0, vertical.isEnabled(), pull, (float) humanize.getAsDouble() / 100f);
        if (d != null) {
            if (silent) {
                sYaw += d[0];
                sPitch = MathHelper.clamp(sPitch + d[1], -90f, 90f);
            } else {
                float y0 = mc.player.getYaw(), p0 = mc.player.getPitch();
                float ny = y0 + d[0], np = MathHelper.clamp(p0 + d[1], -90f, 90f);
                mc.player.setYaw(ny);
                mc.player.setPitch(np);
                sYaw = ny; sPitch = np;
            }
        }

        if (silent) {
            spoof = true;
            if (!KuroRotations.set(this, sYaw, sPitch)) return;
            KuroRotations.modelOwner = showModel.isEnabled() ? this : null;

            if (blatant.isEnabled()) {
                KuroRotations.sendLookPacket();
                if (canHit(dist, reach) && KuroCombatReach.rayHitsLen(target, sYaw, sPitch, reach)) swing(target);
            } else {
                if (canHit(dist, reach) && KuroCombatReach.rayHitsLen(target, sYaw, sPitch, reach)) {
                    float prevYaw = mc.player.getYaw();
                    float prevPitch = mc.player.getPitch();

                    // silent burchaklarga sinxronlash
                    mc.player.setYaw(sYaw);
                    mc.player.setPitch(sPitch);
                    swing(target);

                    // real ko'rinishni qaytarish
                    mc.player.setYaw(prevYaw);
                    mc.player.setPitch(prevPitch);
                }
            }
        } else {
            spoof = false;
            KuroRotations.release(this);
        }

        if (blatant.isEnabled()) orbit(target);

        if (!silent && !legit) {
            if (!canHit(dist, reach) || !KuroCombatReach.rayHitsLen(target, sYaw, sPitch, reach)) return;
            swing(target);
        }
    }

    private boolean canHit(double dist, double reach) {
        if (dist > reach) return false;
        if (mc.player.isUsingItem()) return false;
        if (mc.player.getAttackCooldownProgress(0.5f) < minCharge.getValue()) return false;
        return canSee(target);
    }

    private boolean canSee(Entity other) {
        try {
            return mc.player.canSee(other);
        } catch (Throwable t) {
            return false;
        }
    }

    private void swing(LivingEntity hitTarget) {
        if (hitTarget == null) return;
        mc.interactionManager.attackEntity(mc.player, hitTarget);
        mc.player.swingHand(net.minecraft.util.Hand.MAIN_HAND);
    }

    private void orbit(LivingEntity t) {
        Vec3d me = mc.player.getEntityPos(), c = t.getEntityPos();
        double r = spinDist.getValue(), sp = spinSpeed.getValue();
        double na = Math.atan2(me.z - c.z, me.x - c.x) + sp / r;
        double wx = c.x + Math.cos(na) * r, wz = c.z + Math.sin(na) * r;
        double vx = wx - me.x, vz = wz - me.z, len = Math.hypot(vx, vz), cap = sp * 1.3;
        if (len > cap) { vx *= cap / len; vz *= cap / len; }
        Vec3d mv = mc.player.getVelocity();
        if (blockBelowEmpty(me.x + vx, me.z + vz)) return;
        double vy = mc.player.isOnGround() && mc.player.horizontalCollision ? 0.42 : mv.y;
        mc.player.setVelocity(vx, vy, vz);
    }

    private boolean blockBelowEmpty(double x, double z) {
        try {
            return !mc.world.getBlockState(
                    net.minecraft.util.math.BlockPos.ofFloored(x, mc.player.getEntityPos().y - 0.6, z)
            ).isSolid();
        } catch (Throwable t) {
            return false;
        }
    }

    private LivingEntity findTarget() {
        Vec3d eye = mc.player.getEyePos();
        double reach = Math.min(range.getValue(), KuroCombatReach.MAX);
        double aimRange = reach + 2.0;
        LivingEntity best = null;
        double bestD = aimRange;
        for (Entity e : mc.world.getEntities()) {
            if (!(e instanceof LivingEntity le) || le == mc.player || !le.isAlive() || le.isSpectator()) continue;
            if (le instanceof PlayerEntity p) {
                if (!players.isEnabled()) continue;
                if (!friends.isEnabled() && Friends.isFriend(p.getGameProfile().name())) continue;
                if (AntiBot.isBot(p)) continue;
            } else {
                if (!mobs.isEnabled() || le instanceof ArmorStandEntity) continue;
            }
            double d = Math.sqrt(le.getBoundingBox().getCenter().squaredDistanceTo(eye));
            if (d < bestD) { bestD = d; best = le; }
        }
        return best;
    }

    private boolean freecamActive() {
        Freecam fc = ModuleManager.getModule(Freecam.class);
        return fc != null && fc.isEnabled();
    }
}
