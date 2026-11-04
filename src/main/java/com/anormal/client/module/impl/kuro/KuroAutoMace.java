package com.anormal.client.module.impl.kuro;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.kuro.KuroBlockPlacer;

import net.minecraft.entity.Entity;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.Optional;

/**
 * Kuro AutoMace port (dev.kuro.client.module.impl.AutoMace'dan Anormal'da).
 *
 * <p>Mace smash: tushish paytida nishonga mace bilan urish, mace zaryadini (Density/Breach)
 * fall balandligiga qarab tanlash, qalqonni bolta bilan urib tushirish (Stun slam / Drain),
 * nayza dtap va elytra + firework bilan "rocket dive".</p>
 *
 * <p>Har bir qadam o'yinchi qila oladigan real harakat bilan bajariladi: slot swap
 * ({@link KuroBlockPlacer#select}) tickda bitta, hujum tickda bitta
 * ({@link #executeAttack}), aim real player rotation'ni burish orqali. Tick'da
 * bajarilmaydigan qadam keyingi tickni kutadi.</p>
 */
public class KuroAutoMace extends Module {

    public enum ShieldMode { NONE, STUN_SLAM, DRAIN }

    // ---- Asosiy ----
    private final NumberSetting range = new NumberSetting("Range",
            "Urish masofasi (blok)", 3.0, 1.0, 6.0, 0.1);
    private final NumberSetting aimRange = new NumberSetting("Aim range",
            "Nishonni ko'rish/nishonga olish masofasi", 4.5, 1.0, 8.0, 0.1);
    private final NumberSetting minFallDistance = new NumberSetting("Min fall distance",
            "Smash uchun minimal tushish balandligi", 1.5, 1.5, 8.0, 0.1);
    private final NumberSetting densityThreshold = new NumberSetting("Density threshold",
            "Shu balandlikdan chuqurroq tushishda Density mace tanlanadi", 3.0, 1.5, 12.0, 0.1);
    private final BooleanSetting targetPlayers = new BooleanSetting("Target players",
            "O'yinchilarni nishonga olish", true);
    private final BooleanSetting targetMobs = new BooleanSetting("Target mobs",
            "Mob'larni nishonga olish", false);
    private final BooleanSetting autoSwitch = new BooleanSetting("Auto switch",
            "Mace'ni hotbar'dan o'zi olib kelish", true);
    private final ModeSetting shieldMode = new ModeSetting("Shield mode",
            "Qalqonga qarshi usul: None = yo'q, Stun slam = bolta + mace, Drain = faqat mace",
            "Stun slam", "None", "Stun slam", "Drain");
    private final BooleanSetting checkFalling = new BooleanSetting("Check falling",
            "Faqat tushayotganda urish", true);
    private final BooleanSetting fastStunSlam = new BooleanSetting("Fast stun slam",
            "Bolta urishidan keyin mace'ni darhol olish", true);
    private final NumberSetting stunSlamDelay = new NumberSetting("Stun slam delay (ms)",
            "Bolta va mace urishi orasidagi minimal vaqt", 50.0, 0.0, 500.0, 5.0);
    private final BooleanSetting stayOnMace = new BooleanSetting("Stay on mace",
            "Urgandan keyin mace'da qolish", false);
    private final BooleanSetting swapBackOnHit = new BooleanSetting("Swap back on hit",
            "Urgandan keyin eski slotga qaytish", true);
    private final BooleanSetting retrySlam = new BooleanSetting("Retry slam",
            "Smash o'tmasa qayta urinish", true);
    private final NumberSetting retryDelay = new NumberSetting("Retry delay (ms)",
            "Qayta urinish orasidagi vaqt", 250.0, 0.0, 1000.0, 25.0);
    private final BooleanSetting spearDtap = new BooleanSetting("Spear mace dtap",
            "Nayza zaryadidan keyin mace bilan qo'shimcha zarba", false);
    private final BooleanSetting requireCharge = new BooleanSetting("Require full charge",
            "Hujumdan oldin qurolning to'liq zaryadini kutish", false);

    // ---- Rocket dive ----
    private final BooleanSetting autoRocket = new BooleanSetting("Auto rocket dive",
            "Elytra + firework bilan tepadan mace smash", false);
    private final BooleanSetting rocketLoop = new BooleanSetting("Rocket loop",
            "Dive'ni nishon o'lmaguncha takrorlash", false);
    private final NumberSetting rocketTargetRange = new NumberSetting("Rocket target range",
            "Dive uchun nishon qidirish masofasi", 48.0, 8.0, 128.0, 1.0);
    private final NumberSetting rocketAttackRange = new NumberSetting("Rocket attack range",
            "Dive'dagi urish masofasi", 3.0, 2.0, 6.0, 0.1);
    private final NumberSetting rocketSnapFov = new NumberSetting("Rocket snap FOV",
            "Nishon qidirish burchagi (360 = hamma tomonga)", 360.0, 10.0, 360.0, 1.0);
    private final NumberSetting rocketHeightAbove = new NumberSetting("Rocket height above",
            "Nishon ustidan qancha balandlikda uchish", 8.0, 0.0, 30.0, 0.5);
    private final NumberSetting rocketFireworkDelay = new NumberSetting("Firework delay ms",
            "Ikki firework orasidagi vaqt", 650.0, 100.0, 2500.0, 25.0);
    private final NumberSetting rocketDropDelay = new NumberSetting("Drop delay ms",
            "Tepadaga yetgach tushishni boshlashdan oldingi kutish", 120.0, 0.0, 1000.0, 10.0);
    private final NumberSetting rocketAttackDelay = new NumberSetting("Rocket attack delay ms",
            "Dive paytida urishlar orasidagi vaqt", 100.0, 0.0, 500.0, 10.0);
    private final NumberSetting rocketReequipDelay = new NumberSetting("Re-equip delay ms",
            "Urishdan keyin elytra'ni qayta kiyishdan oldingi kutish", 150.0, 0.0, 1000.0, 10.0);
    private final NumberSetting rocketGlideDelay = new NumberSetting("Glide delay ms",
            "Loop'da keyingi dive boshlanishidan oldingi kutish", 100.0, 0.0, 1000.0, 10.0);
    private final NumberSetting rocketAimOffset = new NumberSetting("Rocket aim offset",
            "Nishon oldiga qancha blok oldinroq nishonga olish", 1.5, 0.0, 4.0, 0.1);
    private final BooleanSetting rocketRecover = new BooleanSetting("Rocket recover",
            "Dive'dan keyin elytra'ni qaytarish", true);

    // ---- Holat ----
    private int savedSlot = -1;
    private boolean maceHit = false;
    private long lastMaceHitTime = 0L;
    private long swapBackAt = 0L;
    private static boolean autoAttacking = false;
    private int lastAttackTick = Integer.MIN_VALUE;
    private boolean prevRocket, prevSpear, prevPlayers = true, prevMobs;

    private double fallStartY = -1;
    private boolean isFalling = false;

    private final KuroAutoMaceTargeting targeting = new KuroAutoMaceTargeting(this);
    private final KuroShieldBreakHandler shieldBreakHandler = new KuroShieldBreakHandler(this);
    private final KuroRocketDiveHandler rocketDive = new KuroRocketDiveHandler(this);
    private final KuroSpearDtapHandler spearDtapHandler =
            new KuroSpearDtapHandler(this, shieldBreakHandler, targeting);

    public KuroAutoMace() {
        super("AutoMace", "Mace smash, shield break va rocket dive (kuro port)", Category.KURO);
        addSetting(range);
        addSetting(aimRange);
        addSetting(minFallDistance);
        addSetting(densityThreshold);
        addSetting(targetPlayers);
        addSetting(targetMobs);
        addSetting(autoSwitch);
        addSetting(shieldMode);
        addSetting(checkFalling);
        addSetting(fastStunSlam);
        addSetting(stunSlamDelay);
        addSetting(stayOnMace);
        addSetting(swapBackOnHit);
        addSetting(retrySlam);
        addSetting(retryDelay);
        addSetting(spearDtap);
        addSetting(requireCharge);
        addSetting(autoRocket);
        addSetting(rocketLoop);
        addSetting(rocketTargetRange);
        addSetting(rocketAttackRange);
        addSetting(rocketSnapFov);
        addSetting(rocketHeightAbove);
        addSetting(rocketFireworkDelay);
        addSetting(rocketDropDelay);
        addSetting(rocketAttackDelay);
        addSetting(rocketReequipDelay);
        addSetting(rocketGlideDelay);
        addSetting(rocketAimOffset);
        addSetting(rocketRecover);
    }

    @Override
    public void onEnable() {
        resetFall();
        rocketDive.reset(true);
        savedSlot = -1;
        lastMaceHitTime = 0L;
        swapBackAt = 0L;
        lastAttackTick = Integer.MIN_VALUE;
        prevRocket = autoRocket.isEnabled();
        prevSpear = spearDtap.isEnabled();
        prevPlayers = targetPlayers.isEnabled();
        prevMobs = targetMobs.isEnabled();
        spearDtapHandler.reset();
        shieldBreakHandler.reset();
    }

    @Override
    public void onDisable() {
        if (!stayOnMace.isEnabled() && savedSlot != -1 && mc.player != null) {
            KuroBlockPlacer.selectForce(savedSlot);
        }
        spearDtapHandler.onDisable();
        rocketDive.reset(true);
        resetFall();
        shieldBreakHandler.reset();
        lastMaceHitTime = 0L;
        swapBackAt = 0L;
        savedSlot = -1;
    }

    /** Sozlamalar bir-biriga zid kombinatsiyada qolmasligi uchun har tickda to'g'rilanadi. */
    private void enforceSettings() {
        if (aimRange.getValue() < range.getValue()) aimRange.setValue(range.getValue());
        if (stayOnMace.isEnabled() && swapBackOnHit.isEnabled()) swapBackOnHit.setEnabled(false);
        if (!rocketRecover.isEnabled() && rocketLoop.isEnabled()) rocketLoop.setEnabled(false);
        if (!autoRocket.isEnabled() && rocketLoop.isEnabled()) rocketLoop.setEnabled(false);
        if (autoRocket.isEnabled() && spearDtap.isEnabled()) {
            if (prevRocket && !prevSpear) autoRocket.setEnabled(false);
            else spearDtap.setEnabled(false);
        }
        if (!targetPlayers.isEnabled() && !targetMobs.isEnabled()) {
            if (prevPlayers) targetPlayers.setEnabled(true);
            else if (prevMobs) targetMobs.setEnabled(true);
            else targetPlayers.setEnabled(true);
        }
        prevRocket = autoRocket.isEnabled();
        prevSpear = spearDtap.isEnabled();
        prevPlayers = targetPlayers.isEnabled();
        prevMobs = targetMobs.isEnabled();
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) {
            if (!rocketDive.idle()) rocketDive.reset(false);
            resetFall();
            return;
        }

        enforceSettings();
        updateFall();

        if (swapBackAt != 0L && !stayOnMace.isEnabled() && savedSlot != -1
                && System.currentTimeMillis() >= swapBackAt) {
            if (KuroBlockPlacer.select(savedSlot)) {
                savedSlot = -1;
                swapBackAt = 0L;
            }
        }

        spearDtapHandler.onTickPre();

        if (autoRocket.isEnabled()) {
            rocketDive.tick();
        } else if (!rocketDive.idle()) {
            rocketDive.reset(true);
        }

        if (autoRocket.isEnabled() && !rocketDive.idle()) return;
        if (mc.player.isGliding()) return;
        if (shieldBreakHandler.findAnyMaceSlot() == -1) return;
        if (mc.currentScreen != null) return;

        double fallDist = fallStartY == -1 ? 0 : Math.max(0, fallStartY - mc.player.getY());

        if (!checkFalling.isEnabled() && maceHit && mc.player.getAttackCooldownProgress(0.0f) >= 1.0f) {
            maceHit = false;
            shieldBreakHandler.setSlamState(0);
        }

        if (checkFalling.isEnabled()) {
            if (!isFalling || mc.player.getVelocity().y >= -0.1) return;
            if (fallDist < minFallDistance.getValue()) return;
        }

        Entity target = targeting.findOptimalTarget();
        if (target == null) {
            shieldBreakHandler.abortStaleSlam();
            return;
        }

        aimAt(targeting.calculateAdvancedRotations(mc.player, target), mc.player.distanceTo(target));

        if (distanceToBox(target) > reach()) return;

        if (maceHit && retrySlam.isEnabled() && isFalling && mc.player.getVelocity().y < -0.1
                && fallDist >= minFallDistance.getValue()
                && mc.player.getAttackCooldownProgress(0.0f) >= 0.9F
                && System.currentTimeMillis() - lastMaceHitTime >= (long) retryDelay.getAsDouble()) {
            maceHit = false;
            shieldBreakHandler.setSlamState(0);
        }

        handleMaceAttack(target, fallDist);
    }

    /** Nishonga qarab real burilish: xatoning bir qismi + tick chegarasi, sichqoncha qadamiga moslab. */
    public void aimAt(float[] rawRotations, double distance) {
        if (mc.player == null || mc.currentScreen != null || rawRotations == null) return;

        float yaw = mc.player.getYaw();
        float pitch = mc.player.getPitch();
        float yawError = MathHelper.wrapDegrees(rawRotations[0] - yaw);
        float pitchError = rawRotations[1] - pitch;

        if (distance < 0.8) {
            float k = (float) (distance / 0.8);
            yawError *= k;
            pitchError *= k;
        }

        float yawStep = MathHelper.clamp(yawError * 0.6f, -40.0f, 40.0f);
        float pitchStep = MathHelper.clamp(pitchError * 0.6f, -25.0f, 25.0f);

        float[] snapped = targeting.applyGCDMouseFix(yaw, pitch, yaw + yawStep, pitch + pitchStep);
        float newYaw = snapped[0];
        float newPitch = MathHelper.clamp(snapped[1], -90.0f, 90.0f);
        if (newYaw == yaw && newPitch == pitch) return;

        mc.player.setYaw(newYaw);
        mc.player.setPitch(newPitch);
        mc.player.headYaw = newYaw;
        mc.player.lastYaw += newYaw - yaw;
        mc.player.lastPitch += newPitch - pitch;
    }

    /** Rocket dive tugaganda chaqiriladi. */
    public void releaseAim() {
        // aim to'g'ridan-to'g'ri real rotation'ga yozildi, yozilgan holat qolmadi
    }

    private void updateFall() {
        boolean onGround = mc.player.isOnGround();
        double currentY = mc.player.getY();
        double yVel = mc.player.getVelocity().y;

        if (onGround) {
            if (isFalling) resetFall();
            if (!stayOnMace.isEnabled() && savedSlot != -1) {
                if (KuroBlockPlacer.select(savedSlot)) {
                    savedSlot = -1;
                    swapBackAt = 0L;
                }
            }
            return;
        }

        if (fallStartY == -1 || currentY > fallStartY) fallStartY = currentY;

        if (yVel > 0.05) {
            if (maceHit) {
                maceHit = false;
                shieldBreakHandler.setSlamState(0);
            }
        } else if (yVel < -0.05) {
            if (!isFalling) {
                isFalling = true;
                maceHit = false;
                shieldBreakHandler.setSlamState(0);
            }
        }
    }

    private void resetFall() {
        isFalling = false;
        fallStartY = -1;
        maceHit = false;
        shieldBreakHandler.setSlamState(0);
    }

    private void handleMaceAttack(Entity target, double fallDist) {
        if (shieldBreakHandler.handleShieldBreak(target, fallDist)) return;

        if (!shieldBreakHandler.hasMace() && !maceHit) {
            if (savedSlot == -1) savedSlot = KuroBlockPlacer.selected();
            if (autoSwitch.isEnabled() && !shieldBreakHandler.switchToAppropriateMace(fallDist)) return;
        }

        if (shieldBreakHandler.hasMace() && !maceHit) {
            if (executeAttack(target)) registerMaceHit();
        }
    }

    /** Crosshair (real look) nishon ustida va masofa ichida bo'lsa true. */
    public boolean crosshairOn(Entity target, double reach) {
        if (mc.player == null || target == null) return false;
        Vec3d eye = mc.player.getEyePos();
        Vec3d look = Vec3d.fromPolar(mc.player.getPitch(), mc.player.getYaw());
        Box box = target.getBoundingBox().expand(target.getTargetingMargin());
        if (box.contains(eye)) return true;
        Optional<Vec3d> hit = box.raycast(eye, eye.add(look.multiply(reach)));
        return hit.isPresent();
    }

    /** Hozir o'yinchi kabi urish mumkinmi: ekran yo'q, tickda bitta, masofa va crosshair mos. */
    public boolean canAttack(Entity target, boolean needCharged) {
        if (mc.interactionManager == null || mc.player == null || target == null || !target.isAlive()) return false;
        if (mc.currentScreen != null) return false;
        if (mc.player.age == lastAttackTick) return false;
        if (needCharged && requireCharge.isEnabled() && mc.player.getAttackCooldownProgress(0.5F) < 0.95F) return false;
        double r = reach();
        if (distanceToBox(target) > r) return false;
        return crosshairOn(target, r);
    }

    public boolean executeAttack(Entity target) { return executeAttack(target, true); }

    public boolean executeAttack(Entity target, boolean needCharged) {
        if (!canAttack(target, needCharged)) return false;
        autoAttacking = true;
        try {
            mc.interactionManager.attackEntity(mc.player, target);
            mc.player.swingHand(Hand.MAIN_HAND);
            lastAttackTick = mc.player.age;
            return true;
        } finally {
            autoAttacking = false;
        }
    }

    public static boolean isAutoAttacking() { return autoAttacking; }

    public void registerMaceHit() {
        maceHit = true;
        long now = System.currentTimeMillis();
        lastMaceHitTime = now;
        if (!stayOnMace.isEnabled() && swapBackOnHit.isEnabled()) swapBackAt = now + 50L;
    }

    // ---- Getterlar (handlerlar ishlatadi) ----

    /** Modul har doim urishdan oldin o'zi tekshiradigan maksimal masofa. */
    public double reach() {
        double r = range.getValue();
        if (mc.player != null) r = Math.min(r, mc.player.getEntityInteractionRange());
        return r;
    }

    /** Rocket dive uchun xuddi shu chegara. */
    public double rocketReach() {
        double r = rocketAttackRange.getValue();
        if (mc.player != null) r = Math.min(r, mc.player.getEntityInteractionRange());
        return r;
    }

    private double distanceToBox(Entity e) {
        return com.anormal.client.util.kuro.KuroCombatReach.eyeDistance(e);
    }

    public NumberSetting getRange() { return range; }
    public NumberSetting getAimRange() { return aimRange; }
    public NumberSetting getMinFallDistance() { return minFallDistance; }
    public NumberSetting getDensityThreshold() { return densityThreshold; }
    public BooleanSetting getTargetPlayers() { return targetPlayers; }
    public BooleanSetting getTargetMobs() { return targetMobs; }
    public BooleanSetting getAutoSwitch() { return autoSwitch; }
    public BooleanSetting getCheckFalling() { return checkFalling; }
    public BooleanSetting getFastStunSlam() { return fastStunSlam; }
    public NumberSetting getStunSlamDelay() { return stunSlamDelay; }
    public BooleanSetting getRetrySlam() { return retrySlam; }
    public NumberSetting getRetryDelay() { return retryDelay; }
    public BooleanSetting getSpearDtap() { return spearDtap; }
    public BooleanSetting getRequireCharge() { return requireCharge; }
    public BooleanSetting getStayOnMace() { return stayOnMace; }
    public BooleanSetting getSwapBackOnHit() { return swapBackOnHit; }

    public ShieldMode getShieldMode() {
        if (!autoSwitch.isEnabled()) return ShieldMode.NONE;   // ikkala rejim ham qurol o'zgartirishni talab qiladi
        if (shieldMode.is("Stun slam")) return ShieldMode.STUN_SLAM;
        if (shieldMode.is("Drain")) return ShieldMode.DRAIN;
        return ShieldMode.NONE;
    }

    public double rocketTargetRange() { return rocketTargetRange.getValue(); }
    public double rocketAttackRange() { return rocketReach(); }
    public double rocketSnapFov() { return rocketSnapFov.getValue(); }
    public double rocketHeightAbove() { return rocketHeightAbove.getValue(); }
    public long rocketFireworkDelayMs() { return (long) rocketFireworkDelay.getAsDouble(); }
    public long rocketDropDelayMs() { return (long) rocketDropDelay.getAsDouble(); }
    public long rocketAttackDelayMs() { return (long) rocketAttackDelay.getAsDouble(); }
    public long rocketReequipDelayMs() { return (long) rocketReequipDelay.getAsDouble(); }
    public long rocketGlideDelayMs() { return (long) rocketGlideDelay.getAsDouble(); }
    public double rocketAimOffset() { return rocketAimOffset.getValue(); }
    public boolean rocketLoop() { return rocketLoop.isEnabled(); }
    public boolean rocketRecover() { return rocketRecover.isEnabled(); }
    public BooleanSetting getAutoRocket() { return autoRocket; }

    public boolean targetPlayers() { return targetPlayers.isEnabled(); }
    public boolean targetMobs() { return targetMobs.isEnabled(); }
    public double minFall() { return minFallDistance.getValue(); }

    public int findMaceSlot(double fall) { return shieldBreakHandler.findAnyMaceSlot(); }
    public int getSavedSlot() { return savedSlot; }
    public void setSavedSlot(int slot) { this.savedSlot = slot; }
    public boolean isMaceHit() { return maceHit; }
    public KuroRocketDiveHandler getRocketDiveHandler() { return rocketDive; }
}
