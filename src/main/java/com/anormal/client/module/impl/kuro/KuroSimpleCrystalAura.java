package com.anormal.client.module.impl.kuro;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.module.impl.client.AntiBot;
import com.anormal.client.module.impl.client.Friends;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.kuro.KuroBlockPlacer;
import com.anormal.client.util.kuro.KuroRotations;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
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
 * Ported from kuro (dev.kuro.client.module.impl.SimpleCrystalAura).
 *
 * <p>Simple Crystal Aura: no damage calculation. Breaks the nearest end crystal in range
 * (ms timers), places a crystal on the nearest obsidian / bedrock with two free blocks above
 * it (optionally only near an enemy) and swaps to the crystal hotbar slot for the place and
 * back afterwards. It does not weigh self damage, so stay out of range of your own crystals.</p>
 */
public class KuroSimpleCrystalAura extends Module {
    private final NumberSetting range = new NumberSetting("Range", "Search and break distance", 5.0, 1.0, 6.0, 0.5);
    private final NumberSetting placeDelay = new NumberSetting("Place delay (ms)", "Ms between placements", 200.0, 50.0, 1000.0, 50.0);
    private final NumberSetting breakDelay = new NumberSetting("Break delay (ms)", "Ms between breaks", 100.0, 50.0, 1000.0, 50.0);
    private final BooleanSetting rotate = new BooleanSetting("Rotate to block", "Silent aim at each click", true);
    private final BooleanSetting onlyNearEnemy = new BooleanSetting("Only near enemy", "Place only within Enemy range of a player", true);
    private final NumberSetting enemyRange = new NumberSetting("Enemy range", "How close an enemy must be", 6.0, 1.0, 15.0, 0.5);
    private final BooleanSetting obsidianOnly = new BooleanSetting("Obsidian only", "Never place on bedrock", false);

    private long lastPlace, lastBreak;
    private int prevSlot = -1;

    public KuroSimpleCrystalAura() {
        super("Simple Crystal Aura", "Nearest crystal in, nearest obsidian out (kuro port)", Category.KURO);
        addSetting(range);
        addSetting(placeDelay);
        addSetting(breakDelay);
        addSetting(rotate);
        addSetting(onlyNearEnemy);
        addSetting(enemyRange);
        addSetting(obsidianOnly);
    }

    @Override
    public void onDisable() {
        if (prevSlot != -1 && mc.player != null) KuroBlockPlacer.selectForce(prevSlot);
        prevSlot = -1;
        KuroRotations.release(this);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null || mc.currentScreen != null) return;
        if (mc.player.isSpectator()) return;

        if (breakNearest()) return;                         // one action per tick keeps packet order tidy

        BlockPos spot = findSpot();
        if (spot == null) { restoreSlot(); KuroRotations.release(this); return; }

        boolean offhand = mc.player.getOffHandStack().isOf(Items.END_CRYSTAL);
        int slot = KuroBlockPlacer.findSlot(s -> s.isOf(Items.END_CRYSTAL));
        if (!offhand && slot < 0) { restoreSlot(); KuroRotations.release(this); return; }

        Vec3d hit = new Vec3d(spot.getX() + 0.5, spot.getY() + 1.0, spot.getZ() + 0.5);
        if (rotate.isEnabled()) aimAt(hit);

        long now = System.currentTimeMillis();
        if (now - lastPlace < placeDelay.getValue().longValue()) return;

        if (!offhand && KuroBlockPlacer.selected() != slot) {
            if (prevSlot == -1) prevSlot = KuroBlockPlacer.selected();
            if (!KuroBlockPlacer.select(slot)) return;      // a swap was already made this tick: place next tick
        }
        Hand hand = offhand ? Hand.OFF_HAND : Hand.MAIN_HAND;
        mc.interactionManager.interactBlock(mc.player, hand, new BlockHitResult(hit, Direction.UP, spot, false));
        mc.player.swingHand(hand);
        lastPlace = now;
    }

    /** Hits the closest crystal in range when the break timer allows. Returns true when a hit was queued. */
    private boolean breakNearest() {
        long now = System.currentTimeMillis();
        if (now - lastBreak < breakDelay.getValue().longValue()) return false;

        EndCrystalEntity nearest = null;
        double best = Double.MAX_VALUE;
        for (Entity e : mc.world.getEntities()) {
            if (!(e instanceof EndCrystalEntity c) || !c.isAlive()) continue;
            double d = mc.player.distanceTo(c);
            if (d <= range.getValue() && d < best) { best = d; nearest = c; }
        }
        if (nearest == null) return false;

        if (rotate.isEnabled()) aimAt(nearest.getEntityPos().add(0, 0.5, 0));
        KuroAttackPackets.hit(nearest);
        lastBreak = now;
        return true;
    }

    private BlockPos findSpot() {
        int r = (int) Math.floor(range.getAsDouble());
        BlockPos origin = mc.player.getBlockPos();
        Vec3d eye = mc.player.getEyePos();
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;

        for (int x = -r; x <= r; x++) for (int y = -r; y <= r; y++) for (int z = -r; z <= r; z++) {
            BlockPos pos = origin.add(x, y, z);
            BlockState s = mc.world.getBlockState(pos);
            boolean base = s.isOf(Blocks.OBSIDIAN) || (!obsidianOnly.isEnabled() && s.isOf(Blocks.BEDROCK));
            if (!base) continue;
            if (!mc.world.getBlockState(pos.up()).isAir() || !mc.world.getBlockState(pos.up(2)).isAir()) continue;
            double d = eye.distanceTo(Vec3d.ofCenter(pos));
            if (d > range.getValue() || d >= bestD) continue;
            if (onlyNearEnemy.isEnabled() && !enemyNear(pos)) continue;
            if (!mc.world.getOtherEntities(null, new Box(pos.getX(), pos.getY() + 1, pos.getZ(),
                    pos.getX() + 1, pos.getY() + 3, pos.getZ() + 1), e -> true).isEmpty()) continue;
            bestD = d;
            best = pos;
        }
        return best;
    }

    private boolean enemyNear(BlockPos pos) {
        Vec3d c = Vec3d.ofCenter(pos);
        for (PlayerEntity p : mc.world.getPlayers()) {
            if (p == mc.player || p.isSpectator() || !p.isAlive()) continue;
            if (Friends.isFriend(p.getName().getString()) || AntiBot.isBot(p)) continue;
            if (p.getEntityPos().distanceTo(c) <= enemyRange.getValue()) return true;
        }
        return false;
    }

    private void restoreSlot() {
        if (prevSlot == -1) return;
        if (KuroBlockPlacer.select(prevSlot)) prevSlot = -1;
    }

    /** Silent aim: the server gets the angles in a look packet right before the click, your camera stays put. */
    private void aimAt(Vec3d point) {
        Vec3d eye = mc.player.getEyePos();
        double dx = point.x - eye.x, dy = point.y - eye.y, dz = point.z - eye.z;
        double h = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = MathHelper.clamp((float) -Math.toDegrees(Math.atan2(dy, Math.max(h, 1e-4))), -90f, 90f);
        if (KuroRotations.set(this, yaw, pitch)) KuroRotations.sendLookPacket();
    }
}
