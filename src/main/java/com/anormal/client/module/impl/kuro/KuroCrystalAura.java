package com.anormal.client.module.impl.kuro;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.module.ModuleManager;
import com.anormal.client.module.impl.client.AntiBot;
import com.anormal.client.module.impl.client.Friends;
import com.anormal.client.module.impl.world.Freecam;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.kuro.KuroBlockPlacer;
import com.anormal.client.util.kuro.KuroRotations;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Ported from kuro (dev.kuro.client.module.impl.CrystalAura).
 *
 * <p>Crystal place + break aura: damages the target most, keeps the self damage below
 * "Max self damage". Every tick: break the crystal that hurts the target more than it
 * hurts you, otherwise place a new one on obsidian / bedrock near the target.</p>
 */
public class KuroCrystalAura extends Module {
    private final NumberSetting placeRange = new NumberSetting("Place range", "Max distance to a placeable spot", 4.5, 2.0, 6.0, 0.1);
    private final NumberSetting breakRange = new NumberSetting("Break range", "Max distance to a breakable crystal", 4.5, 2.0, 6.0, 0.1);
    private final NumberSetting minDamage = new NumberSetting("Min damage", "The spot must hurt the target at least this much", 6.0, 0.0, 36.0, 0.5);
    private final NumberSetting maxSelf = new NumberSetting("Max self damage", "Never take more than this yourself", 8.0, 0.0, 36.0, 0.5);
    private final NumberSetting placeDelay = new NumberSetting("Place delay", "Ticks between placements", 0.0, 0.0, 10.0, 1.0);
    private final NumberSetting breakDelay = new NumberSetting("Break delay", "Ticks between breaks", 0.0, 0.0, 10.0, 1.0);
    private final BooleanSetting silent = new BooleanSetting("Silent rotate", "Aim with look packets, camera stays put", true);
    private final BooleanSetting autoSwitch = new BooleanSetting("Auto switch", "Swap to the crystal slot for the place", true);
    private final BooleanSetting targetPlayers = new BooleanSetting("Players", "Only attack players", true);
    private final BooleanSetting predict = new BooleanSetting("Predict motion", "Aim 1.5 ticks ahead of their motion", true);

    private int placeCd, breakCd;

    public KuroCrystalAura() {
        super("Crystal Aura", "Place + break crystals near the target (kuro port)", Category.KURO);
        addSetting(placeRange);
        addSetting(breakRange);
        addSetting(minDamage);
        addSetting(maxSelf);
        addSetting(placeDelay);
        addSetting(breakDelay);
        addSetting(silent);
        addSetting(autoSwitch);
        addSetting(targetPlayers);
        addSetting(predict);
    }

    @Override
    public void onDisable() { KuroRotations.release(this); }

    private boolean usable() {
        Freecam f = ModuleManager.getModule(Freecam.class);
        boolean freecamActive = f != null && f.isEnabled();
        return mc.player != null && mc.world != null && mc.interactionManager != null
                && mc.currentScreen == null && !mc.player.isSpectator() && !freecamActive;
    }

    @Override
    public void onTick() {
        if (!usable()) return;
        if (placeCd > 0) placeCd--;
        if (breakCd > 0) breakCd--;

        // 1) Break existing crystals that hurt the target more than us
        if (breakCd <= 0) {
            EndCrystalEntity best = null;
            double bestScore = -1;
            double br = breakRange.getValue();
            Box area = mc.player.getBoundingBox().expand(br + 1.5);
            LivingEntity target = bestTarget();
            for (EndCrystalEntity c : mc.world.getEntitiesByClass(EndCrystalEntity.class, area, Entity::isAlive)) {
                if (mc.player.distanceTo(c) > br) continue;
                double dmg = target == null ? 0 : crystalDamage(c.getEntityPos(), target);
                double self = crystalDamage(c.getEntityPos(), mc.player);
                if (self > maxSelf.getValue()) continue;
                if (target != null && dmg < minDamage.getValue()) continue;
                double score = dmg - self * 0.5;
                if (score > bestScore) { bestScore = score; best = c; }
            }
            if (best != null) {
                aimAt(best.getEntityPos().add(0, 0.5, 0));
                KuroAttackPackets.hit(best);
                breakCd = breakDelay.getValue().intValue();
                return;
            }
        }

        // 2) Place if we have a good spot near a target
        if (placeCd > 0) return;
        LivingEntity target = bestTarget();
        if (target == null) { KuroRotations.release(this); return; }

        BlockPos spot = findPlace(target);
        if (spot == null) { KuroRotations.release(this); return; }

        int crystalSlot = KuroBlockPlacer.findSlot(s -> s.isOf(Items.END_CRYSTAL));
        if (crystalSlot < 0 && !mc.player.getOffHandStack().isOf(Items.END_CRYSTAL)) return;
        if (autoSwitch.isEnabled() && crystalSlot >= 0 && !mc.player.getMainHandStack().isOf(Items.END_CRYSTAL)
                && !mc.player.getOffHandStack().isOf(Items.END_CRYSTAL)) {
            if (!KuroBlockPlacer.select(crystalSlot)) return;
        }

        Vec3d hit = Vec3d.ofCenter(spot).add(0, 0.5, 0);
        aimAt(hit);
        Hand hand = mc.player.getOffHandStack().isOf(Items.END_CRYSTAL) ? Hand.OFF_HAND : Hand.MAIN_HAND;
        mc.interactionManager.interactBlock(mc.player, hand, new BlockHitResult(hit, Direction.UP, spot, false));
        mc.player.swingHand(hand);
        placeCd = placeDelay.getValue().intValue();
    }

    private void aimAt(Vec3d point) {
        if (!silent.isEnabled() || mc.player == null) return;
        Vec3d eye = mc.player.getEyePos();
        double dx = point.x - eye.x, dy = point.y - eye.y, dz = point.z - eye.z;
        double dist = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.max(dist, 1.0e-4)));
        if (KuroRotations.set(this, yaw, pitch)) KuroRotations.sendLookPacket();
    }

    private LivingEntity bestTarget() {
        LivingEntity best = null;
        double bestD = placeRange.getValue() + 8;
        Box area = mc.player.getBoundingBox().expand(bestD);
        for (PlayerEntity p : mc.world.getEntitiesByClass(PlayerEntity.class, area, Entity::isAlive)) {
            if (p == mc.player || p.isSpectator()) continue;
            if (!targetPlayers.isEnabled()) continue;
            if (Friends.isFriend(p.getName().getString()) || AntiBot.isBot(p)) continue;
            double d = mc.player.distanceTo(p);
            if (d < bestD) { bestD = d; best = p; }
        }
        return best;
    }

    private BlockPos findPlace(LivingEntity target) {
        Vec3d tpos = target.getEntityPos();
        if (predict.isEnabled()) tpos = tpos.add(target.getVelocity().multiply(1.5));
        BlockPos center = BlockPos.ofFloored(tpos);
        BlockPos best = null;
        double bestScore = -1;
        int r = (int) Math.ceil(placeRange.getAsDouble());
        for (int x = -r; x <= r; x++) for (int y = -2; y <= 3; y++) for (int z = -r; z <= r; z++) {
            BlockPos base = center.add(x, y, z);
            if (!canPlaceOn(base)) continue;
            BlockPos air = base.up();
            if (!mc.world.getBlockState(air).isReplaceable()) continue;
            if (!mc.world.getOtherEntities(null, new Box(air).expand(0.1), e -> true).isEmpty()) continue;
            Vec3d crystal = Vec3d.ofBottomCenter(air).add(0, 0.5, 0);
            if (mc.player.getEyePos().distanceTo(crystal) > placeRange.getValue()) continue;
            double dmg = crystalDamage(crystal, target);
            double self = crystalDamage(crystal, mc.player);
            if (dmg < minDamage.getValue() || self > maxSelf.getValue()) continue;
            double score = dmg - self * 0.6 - mc.player.squaredDistanceTo(crystal) * 0.02;
            if (score > bestScore) { bestScore = score; best = base; }
        }
        return best;
    }

    private boolean canPlaceOn(BlockPos pos) {
        BlockState s = mc.world.getBlockState(pos);
        return s.isOf(Blocks.OBSIDIAN) || s.isOf(Blocks.BEDROCK);
    }

    /** Rough crystal damage estimate (same formula as kuro, not the full explosion calculator). */
    static double crystalDamage(Vec3d crystal, LivingEntity e) {
        double dist = e.getEntityPos().add(0, e.getHeight() * 0.5, 0).distanceTo(crystal) / 12.0;
        if (dist > 1.0) return 0;
        double exposure = 1.0 - dist;
        double impact = exposure * exposure;
        return (impact * impact + impact) * 7.0 * 12.0 / 2.0 + 1.0;
    }
}
