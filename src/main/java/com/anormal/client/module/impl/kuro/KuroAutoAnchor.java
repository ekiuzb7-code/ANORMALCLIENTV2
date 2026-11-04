package com.anormal.client.module.impl.kuro;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.module.impl.client.AntiBot;
import com.anormal.client.module.impl.client.Friends;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.NotificationManager;
import com.anormal.client.util.kuro.KuroBlockPlacer;
import com.anormal.client.util.kuro.KuroExplosionUtils;
import com.anormal.client.util.kuro.KuroRotations;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.RespawnAnchorBlock;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Kuro AutoAnchor port (dev.kuro.client.module.impl.AutoAnchor'dan Anormal'da).
 *
 * <p>Nishon yaqinida respawn anchor qo'yadi → glowstone bilan zaryadlaydi →
 * portlatadi (place → glowstone → click explode). Mavjud anchor ham qidiradi:
 * charged bo'lsa to'g'ridan portlatadi, uncharged bo'lsa zaryadlaydi.</p>
 */
public class KuroAutoAnchor extends Module {
    private final NumberSetting range = new NumberSetting("Range", "Max distance to the target/anchor", 4.5, 2.0, 6.0, 0.1);
    private final NumberSetting minDamage = new NumberSetting("Min damage", "Minimum damage to the target", 8.0, 0.0, 36.0, 0.5);
    private final NumberSetting maxSelf = new NumberSetting("Max self", "Maximum damage to self", 10.0, 0.0, 36.0, 0.5);
    private final NumberSetting delay = new NumberSetting("Delay (ticks)", "Extra ticks between the steps", 1.0, 0.0, 10.0, 1.0);
    private final BooleanSetting silent = new BooleanSetting("Silent rotate", "Aim server-side only", true);
    private final BooleanSetting autoSwitch = new BooleanSetting("Auto switch", "Switch hotbar slots by itself", true);

    private enum Phase { PLACE, CHARGE, DETONATE }
    private Phase phase = Phase.PLACE;
    private BlockPos anchorPos;
    private int cd;

    public KuroAutoAnchor() {
        super("Auto Anchor", "Places / charges / detonates anchors near targets (kuro port)", Category.COMBAT);
        addSetting(range);
        addSetting(minDamage);
        addSetting(maxSelf);
        addSetting(delay);
        addSetting(silent);
        addSetting(autoSwitch);
    }

    @Override
    public void onDisable() {
        reset();
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null || mc.currentScreen != null) return;
        if (mc.player.isSpectator()) return;
        if (cd > 0) { cd--; return; }

        PlayerEntity target = bestTarget();
        if (target == null) { reset(); return; }

        // Yaqindagi mavjud (charged/uncharged) anchor ustunlik oladi
        BlockPos existing = findAnchorNear(target);
        if (existing != null) {
            anchorPos = existing;
            phase = anchorCharge(existing) > 0 ? Phase.DETONATE : Phase.CHARGE;
        }

        switch (phase) {
            case PLACE -> doPlace(target);
            case CHARGE -> doCharge();
            case DETONATE -> doDetonate(target);
        }
    }

    private void doPlace(PlayerEntity target) {
        BlockPos spot = findPlace(target);
        if (spot == null) return;
        int slot = KuroBlockPlacer.findSlot(s -> s.isOf(Items.RESPAWN_ANCHOR));
        if (slot < 0) return;
        if (autoSwitch.isEnabled() && !KuroBlockPlacer.select(slot)) return;
        aim(Vec3d.ofCenter(spot));
        if (!KuroBlockPlacer.place(spot)) return;
        anchorPos = spot;
        phase = Phase.CHARGE;
        cd = (int) delay.getAsDouble();
    }

    private void doCharge() {
        if (!isAnchor(anchorPos)) { reset(); return; }
        int glow = KuroBlockPlacer.findSlot(s -> s.isOf(Items.GLOWSTONE));
        if (glow < 0) return;
        if (autoSwitch.isEnabled() && !KuroBlockPlacer.select(glow)) return;
        aim(Vec3d.ofCenter(anchorPos));
        click(anchorPos);
        phase = Phase.DETONATE;
        cd = (int) delay.getAsDouble();
    }

    private void doDetonate(PlayerEntity target) {
        if (!isAnchor(anchorPos)) { reset(); return; }
        // Portlashdan oldin qo'lda glowstone turgan bo'lsa boshqa itemga o'tadi
        if (mc.player.getMainHandStack().isOf(Items.GLOWSTONE)) {
            int nonGlow = KuroBlockPlacer.findSlot(s -> !s.isOf(Items.GLOWSTONE) && !s.isEmpty());
            if (nonGlow >= 0) {
                KuroBlockPlacer.select(nonGlow);
                return;
            }
        }
        Vec3d center = Vec3d.ofCenter(anchorPos);
        double dmg = target != null ? KuroExplosionUtils.getAnchorDamageTo(target, anchorPos) : 0.0;
        double self = KuroExplosionUtils.getAnchorDamageTo(mc.player, anchorPos);
        if (dmg < minDamage.getValue() || self > maxSelf.getValue()) { reset(); return; }
        aim(center);
        click(anchorPos);
        reset();
        cd = (int) delay.getAsDouble();
    }

    private void click(BlockPos pos) {
        Vec3d hit = Vec3d.ofCenter(pos);
        BlockHitResult bhr = new BlockHitResult(hit, Direction.UP, pos, false);
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, bhr);
        mc.player.swingHand(Hand.MAIN_HAND);
    }

    private void aim(Vec3d point) {
        if (!silent.isEnabled()) return;
        if (!KuroRotations.free() && !KuroRotations.ownedBy(this)) return;
        Vec3d eye = mc.player.getEyePos();
        double dx = point.x - eye.x, dy = point.y - eye.y, dz = point.z - eye.z;
        double dist = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx))) - 90f;
        float pitch = (float) (-(Math.toDegrees(Math.atan2(dy, Math.max(dist, 1.0E-4)))));
        if (KuroRotations.set(this, yaw, MathHelper.clamp(pitch, -90f, 90f))) {
            KuroRotations.sendLookPacket();
        }
    }

    private void reset() {
        phase = Phase.PLACE;
        anchorPos = null;
        KuroRotations.release(this);
    }

    private PlayerEntity bestTarget() {
        PlayerEntity best = null;
        double bestD = range.getValue() + 6;
        if (mc.world == null) return null;
        for (PlayerEntity p : mc.world.getPlayers()) {
            double d = mc.player.distanceTo(p);
            if (d >= bestD) continue;
            if (p == mc.player || p.isSpectator() || !p.isAlive()) continue;
            if (Friends.isFriend(p.getGameProfile().name()) || AntiBot.isBot(p)) continue;
            bestD = d;
            best = p;
        }
        return best;
    }

    private Vec3d targetCenter(Object unused) { return mc.player.getEntityPos(); }

    private BlockPos findPlace(PlayerEntity target) {
        BlockPos center = target.getBlockPos();
        BlockPos best = null;
        double bestScore = -1;
        int r = (int) Math.ceil(range.getAsDouble());
        for (int x = -r; x <= r; x++)
            for (int y = -1; y <= 2; y++)
                for (int z = -r; z <= r; z++) {
                    BlockPos p = center.add(x, y, z);
                    if (!mc.world.getBlockState(p).isReplaceable()) continue;
                    if (mc.player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(p)) > range.getValue() * range.getValue()) continue;
                    // kliklay oladigan mustahkam yuza kerak
                    boolean support = false;
                    for (Direction d : Direction.values()) {
                        if (!mc.world.getBlockState(p.offset(d)).isReplaceable()) { support = true; break; }
                    }
                    if (!support) continue;
                    if (p.equals(mc.player.getBlockPos())) continue; // o'zingizning ichingizga qo'mang
                    double dmg = anchorGuess(target, Vec3d.ofCenter(p));
                    double self = anchorGuess(mc.player, Vec3d.ofCenter(p));
                    if (dmg < minDamage.getValue() || self > maxSelf.getValue()) continue;
                    double score = dmg - self * 0.5;
                    if (score > bestScore) { bestScore = score; best = p; }
                }
        return best;
    }

    private BlockPos findAnchorNear(PlayerEntity target) {
        BlockPos center = target.getBlockPos();
        int r = (int) Math.ceil(range.getAsDouble());
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (int x = -r; x <= r; x++)
            for (int y = -2; y <= 3; y++)
                for (int z = -r; z <= r; z++) {
                    BlockPos p = center.add(x, y, z);
                    if (!mc.world.getBlockState(p).isOf(Blocks.RESPAWN_ANCHOR)) continue;
                    double d = mc.player.getEyePos().distanceTo(Vec3d.ofCenter(p));
                    if (d <= range.getValue() && d < bestD) { bestD = d; best = p; }
                }
        return best;
    }

    private double anchorGuess(PlayerEntity e, Vec3d pos) {
        // taxminiy zarar (target noma'lum bo'lsa ham ishlatiladi): eng yaqin nuqta masofasi
        double dist = e.getBoundingBox().getCenter().distanceTo(pos) / 10.0;
        if (dist > 1.0) return 0;
        double exposure = 1.0 - dist;
        double impact = exposure * exposure;
        return (impact * impact + impact) * 6.0 * 10.0 / 2.0 + 1.0;
    }

    /** Anchor portlashining yuqori chegarasi (exposure 100% deb): vanilla expanded-blast formulasi. */
    public static double anchorDamage(Vec3d pos, PlayerEntity e) {
        if (e == null) return 0;
        double dist = e.getBoundingBox().getCenter().distanceTo(pos) / 10.0;
        if (dist > 1.0) return 0;
        double exposure = 1.0 - dist;
        double impact = exposure * exposure;
        return (impact * impact + impact) * 6.0 * 10.0 / 2.0 + 1.0;
    }

    static boolean isAnchor(BlockPos pos) {
        return pos != null && mc.world != null && mc.world.getBlockState(pos).isOf(Blocks.RESPAWN_ANCHOR);
    }

    static int anchorCharge(BlockPos pos) {
        if (!isAnchor(pos)) return -1;
        BlockState st = mc.world.getBlockState(pos);
        return st.getOrEmpty(RespawnAnchorBlock.CHARGES).orElse(0);
    }
}
