package com.anormal.client.module.impl.spectre;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.module.impl.client.Friends;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
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
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Spectre port: "Crystal Aura" — places and pops end crystals around your target.
 *
 * <p>Spectre runs a full damage model (armour, enchantments, explosion exposure). This port keeps
 * the require-hold gate, the break/place/target ranges, the place and break delays, the min and
 * max damage windows, face place, friend skipping, auto-obsidian, jitter, switch-back and the
 * AC profile, with a distance-based damage estimate in place of the full exposure solver.</p>
 */
public final class SpectreCrystalAura extends Module {
    public final BooleanSetting requireHold = addSetting(new BooleanSetting("Require Hold", "Only run while the use key is held", false));
    public final NumberSetting breakDelay = addSetting(new NumberSetting("Break Delay", "Ticks between breaks", 0.0, 0.0, 20.0, 1.0));
    public final NumberSetting placeDelay = addSetting(new NumberSetting("Place Delay", "Ticks between placements", 0.0, 0.0, 20.0, 1.0));
    public final NumberSetting breakRange = addSetting(new NumberSetting("Break Range", "Maximum crystal break range", 3.0, 1.0, 6.0, 0.1));
    public final NumberSetting placeRange = addSetting(new NumberSetting("Place Range", "Maximum placement range", 4.5, 1.0, 6.0, 0.1));
    public final NumberSetting targetRange = addSetting(new NumberSetting("Target Range", "Maximum target range", 8.0, 1.0, 12.0, 1.0));
    public final NumberSetting minDamage = addSetting(new NumberSetting("Min Damage", "Minimum damage to the target", 4.0, 0.0, 36.0, 1.0));
    public final NumberSetting maxSelfDamage = addSetting(new NumberSetting("Max Self Damage", "Maximum damage to you", 8.0, 0.0, 36.0, 1.0));
    public final BooleanSetting facePlace = addSetting(new BooleanSetting("Face Place", "Ignore armour-damage rules for low targets", true));
    public final BooleanSetting skipFriends = addSetting(new BooleanSetting("Skip Friends", "Never target friends", true));
    public final BooleanSetting ignoreTerrain = addSetting(new BooleanSetting("Ignore Terrain", "Ignore blast resistance", true));
    public final ModeSetting damageMode = addSetting(new ModeSetting("Damage Mode", "Normal or safe estimates", "Normal", "Normal", "Safe"));
    public final NumberSetting minPlaceRatio = addSetting(new NumberSetting("Min Place Ratio", "Damage ratio to place", 1.4, 0.0, 5.0, 0.1));
    public final NumberSetting minExplode = addSetting(new NumberSetting("Min Explode", "Minimum explode damage", 2.5, 0.0, 20.0, 0.5));
    public final NumberSetting maxExplode = addSetting(new NumberSetting("Max Explode", "Maximum explode damage", 9.0, 0.0, 36.0, 0.5));
    public final NumberSetting minExplodeRatio = addSetting(new NumberSetting("Min Explode Ratio", "Explode ratio", 1.1, 0.0, 5.0, 0.1));
    public final NumberSetting forcePop = addSetting(new NumberSetting("Force Pop", "Totem pop threshold", 1.0, 0.0, 10.0, 1.0));
    public final NumberSetting antiSelfPop = addSetting(new NumberSetting("Anti Self Pop", "Never pop yourself", 1.0, 0.0, 10.0, 1.0));
    public final NumberSetting antiFriendPop = addSetting(new NumberSetting("Anti Friend Pop", "Never pop a friend", 1.0, 0.0, 10.0, 1.0));
    public final NumberSetting maxFriendPlace = addSetting(new NumberSetting("Max Friend Place", "Friend damage cap", 8.0, 0.0, 36.0, 1.0));
    public final NumberSetting maxFriendExplode = addSetting(new NumberSetting("Max Friend Explode", "Friend explode cap", 12.0, 0.0, 36.0, 1.0));
    public final NumberSetting minFriendRatio = addSetting(new NumberSetting("Min Friend Ratio", "Friend ratio", 2.0, 0.0, 5.0, 1.0));
    public final NumberSetting slowDamage = addSetting(new NumberSetting("Slow Damage", "Slow down below this damage", 3.0, 0.0, 20.0, 1.0));
    public final NumberSetting slowPlaceDelay = addSetting(new NumberSetting("Slow Place Delay", "Delay while slow", 10.0, 0.0, 20.0, 1.0));
    public final BooleanSetting autoObsidian = addSetting(new BooleanSetting("Auto Obsidian", "Place obsidian when needed", true));
    public final NumberSetting obsidianDelay = addSetting(new NumberSetting("Obsidian Delay", "Ticks between obsidian placements", 1.0, 0.0, 20.0, 1.0));
    public final NumberSetting jitter = addSetting(new NumberSetting("Jitter", "Random extra delay", 0.0, 0.0, 4.0, 1.0));
    public final BooleanSetting switchBack = addSetting(new BooleanSetting("Switch Back", "Return to the old slot", true));
    public final ModeSetting acProfile = addSetting(new ModeSetting("AC Profile", "Anticheat pacing", "Grim-Aldenz", "Off", "Grim-Aldenz"));

    private int placeClock;
    private int breakClock;
    private int obsidianClock;
    private int previousSlot = -1;
    private int slowClock;
    private double lastSpotDamage;

    public SpectreCrystalAura() {
        super("SpectreCrystalAura", "Spectre port: places and pops end crystals around your target.", Category.SPECTRE);
    }

    @Override
    public void onEnable() {
        placeClock = 0;
        breakClock = 0;
        obsidianClock = 0;
        slowClock = 0;
        previousSlot = -1;
    }

    @Override
    public void onDisable() {
        restoreSlot();
    }

    @Override
    public void onTick() {
        net.minecraft.client.network.ClientPlayerEntity self = mc.player;
        if (self == null || mc.world == null || mc.interactionManager == null) return;
        if (placeClock > 0) placeClock--;
        if (breakClock > 0) breakClock--;
        if (obsidianClock > 0) obsidianClock--;
        if (slowClock > 0) slowClock--;
        if (requireHold.isEnabled() && !mc.options.useKey.isPressed()) return;

        PlayerEntity target = pickTarget(self);
        if (target == null) return;

        if (breakClock == 0) {
            EndCrystalEntity crystal = bestCrystal(self, target);
            if (crystal != null) {
                if (previousSlot < 0) previousSlot = self.getInventory().getSelectedSlot();
                mc.interactionManager.attackEntity(self, crystal);
                self.swingHand(Hand.MAIN_HAND);
                breakClock = delay(breakDelay.getAsDouble());
                restoreSlot();
                return;
            }
        }

        if (placeClock > 0) return;
        BlockPos spot = bestSpot(self, target);
        if (spot == null) {
            if (autoObsidian.isEnabled() && obsidianClock == 0) placeObsidian(self, target);
            return;
        }
        int crystalSlot = findSlot(Items.END_CRYSTAL);
        if (crystalSlot < 0) return;
        if (previousSlot < 0) previousSlot = self.getInventory().getSelectedSlot();
        self.getInventory().setSelectedSlot(crystalSlot);
        BlockHitResult hit = new BlockHitResult(new Vec3d(spot.getX() + 0.5, spot.getY() + 1.0, spot.getZ() + 0.5),
                Direction.UP, spot, false);
        mc.interactionManager.interactBlock(self, Hand.MAIN_HAND, hit);
        self.swingHand(Hand.MAIN_HAND);
        // Slow Damage: weak placements are paced with the slower delay instead.
        placeClock = lastSpotDamage < slowDamage.getAsDouble()
                ? delay(slowPlaceDelay.getAsDouble())
                : delay(placeDelay.getAsDouble());
        restoreSlot();
    }

    private PlayerEntity pickTarget(PlayerEntity self) {
        double max = targetRange.getAsDouble();
        PlayerEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (PlayerEntity other : mc.world.getPlayers()) {
            if (other == self || !other.isAlive() || other.isSpectator()) continue;
            String name = other.getGameProfile().name();
            if (skipFriends.isEnabled() && name != null && Friends.isFriend(name)) continue;
            double distance = self.distanceTo(other);
            if (distance > max || distance >= bestDistance) continue;
            best = other;
            bestDistance = distance;
        }
        return best;
    }

    private EndCrystalEntity bestCrystal(net.minecraft.client.network.ClientPlayerEntity self, PlayerEntity target) {
        double max = breakRange.getAsDouble();
        EndCrystalEntity best = null;
        double bestScore = -Double.MAX_VALUE;
        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof EndCrystalEntity crystal)) continue;
            double distance = self.distanceTo(crystal);
            if (distance > max) continue;
            Vec3d at = new Vec3d(crystal.getX(), crystal.getY(), crystal.getZ());
            double damage = damageTo(target, at, 6.0F, false);
            double selfDamage = damageTo(self, at, 6.0F, true);
            // Min/Max Explode is the damage window a break has to land in.
            if (damage < minExplode.getAsDouble() || damage > maxExplode.getAsDouble()) continue;
            if (selfDamage > maxSelfDamage.getAsDouble()) continue;
            if (antiSelfPop.getAsDouble() > 0.0 && selfDamage >= self.getHealth() - antiSelfPop.getAsDouble()) continue;
            if (damage / Math.max(0.01, selfDamage) < minExplodeRatio.getAsDouble()) continue;
            double friendDamage = friendDamage(self, target, at);
            if (friendDamage > maxFriendExplode.getAsDouble()) continue;
            if (friendDamage > 0.01 && damage / friendDamage < minFriendRatio.getAsDouble()) continue;
            if (antiFriendPop.getAsDouble() > 0.0 && friendDamage >= antiFriendPop.getAsDouble()) continue;
            double score = damage - selfDamage * 0.6 - distance * 0.1;
            if (score > bestScore) {
                bestScore = score;
                best = crystal;
            }
        }
        return best;
    }

    private BlockPos bestSpot(net.minecraft.client.network.ClientPlayerEntity self, PlayerEntity target) {
        double max = placeRange.getAsDouble();
        BlockPos base = self.getBlockPos();
        BlockPos best = null;
        double bestScore = Double.MIN_VALUE;
        int radius = (int) Math.ceil(max);
        List<BlockPos> candidates = new ArrayList<>();
        for (BlockPos pos : BlockPos.iterate(base.add(-radius, -3, -radius), base.add(radius, 2, radius))) {
            if (mc.world.getBlockState(pos).isAir() && !mc.world.getBlockState(pos.down()).isAir()
                    && self.getEyePos().squaredDistanceTo(Vec3d.ofCenter(pos)) <= max * max) {
                candidates.add(pos.toImmutable());
            }
        }
        for (BlockPos spot : candidates) {
            var below = mc.world.getBlockState(spot.down());
            boolean usable = below.isOf(Blocks.OBSIDIAN) || below.isOf(Blocks.BEDROCK)
                    || (autoObsidian.isEnabled() && below.isSolidBlock(mc.world, spot.down()) && ignoreTerrain.isEnabled());
            if (!usable) continue;
            Vec3d center = Vec3d.ofCenter(spot).add(0, 1.0, 0);
            if (!mc.world.getOtherEntities(null, new Box(spot.up())).isEmpty()) continue;
            double damage = damageTo(target, center, 6.0F, false);
            double selfDamage = damageTo(self, center, 6.0F, true);
            if (selfDamage > maxSelfDamage.getAsDouble()) continue;
            if (antiSelfPop.getAsDouble() > 0.0 && selfDamage >= self.getHealth() - antiSelfPop.getAsDouble()) continue;
            if (damage < requiredDamage(target)) continue;
            if (damage / Math.max(0.01, selfDamage) < minPlaceRatio.getAsDouble()) continue;
            double friendDamage = friendDamage(self, target, center);
            if (friendDamage > maxFriendPlace.getAsDouble()) continue;
            if (friendDamage > 0.01 && damage / friendDamage < minFriendRatio.getAsDouble()) continue;
            if (antiFriendPop.getAsDouble() > 0.0 && friendDamage >= antiFriendPop.getAsDouble()) continue;
            double score = damage - selfDamage * 0.8;
            if (score > bestScore) {
                bestScore = score;
                best = spot;
                lastSpotDamage = damage;
            }
        }
        return best;
    }

    private void placeObsidian(net.minecraft.client.network.ClientPlayerEntity self, PlayerEntity target) {
        int slot = findSlot(Items.OBSIDIAN);
        if (slot < 0) return;
        BlockPos spot = target.getBlockPos().down();
        if (!mc.world.getBlockState(spot).isAir()) return;
        if (previousSlot < 0) previousSlot = self.getInventory().getSelectedSlot();
        self.getInventory().setSelectedSlot(slot);
        BlockPos neighbour = spot.down();
        if (!mc.world.getBlockState(neighbour).isAir()) {
            BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(neighbour), Direction.UP, neighbour, false);
            mc.interactionManager.interactBlock(self, Hand.MAIN_HAND, hit);
            self.swingHand(Hand.MAIN_HAND);
        }
        obsidianClock = delay(obsidianDelay.getAsDouble());
        restoreSlot();
    }

    /**
     * Spectre's exposure solver is replaced by a distance falloff estimate.
     * Same shape: base damage scaled by (1 - distance / diameter), never below zero.
     */
    private static double estimate(PlayerEntity entity, Vec3d blast, float power) {
        double distance = entity.getEyePos().distanceTo(blast) / 2.0;
        if (distance > power) return 0.0;
        double exposure = 1.0 - distance / power;
        double raw = (exposure * exposure + exposure) * 0.5 * 7.0 * power * 2.0 + 1.0;
        double armour = entity.getArmor() * 0.04;
        return raw * Math.max(0.2, 1.0 - armour);
    }

    /** Face Place and Force Pop lower the damage bar for soft or already-popping targets. */
    private double requiredDamage(PlayerEntity target) {
        double required = minDamage.getAsDouble();
        if (forcePop.getAsDouble() > 0.0 && target.getAbsorptionAmount() > 0.0) {
            required = Math.min(required, forcePop.getAsDouble());
        }
        if (facePlace.isEnabled()) {
            double health = target.getHealth() + target.getAbsorptionAmount();
            if (health <= minDamage.getAsDouble() + 2.0 || target.getArmor() <= 5) required = Math.min(required, 1.0);
        }
        return required;
    }

    /** Worst damage a friend would take from this blast (0 when no friend is nearby). */
    private double friendDamage(PlayerEntity self, PlayerEntity target, Vec3d blast) {
        double worst = 0.0;
        double reach = targetRange.getAsDouble() + 6.0;
        for (PlayerEntity other : mc.world.getPlayers()) {
            if (other == self || other == target || !other.isAlive() || other.isSpectator()) continue;
            String name = other.getGameProfile().name();
            if (name == null || !Friends.isFriend(name)) continue;
            if (self.distanceTo(other) > reach) continue;
            worst = Math.max(worst, damageTo(other, blast, 6.0F, false));
        }
        return worst;
    }

    /** Damage Mode "Safe" shades the estimates towards the pessimistic side. */
    private double damageTo(PlayerEntity entity, Vec3d blast, float power, boolean self) {
        double raw = estimate(entity, blast, power);
        if (damageMode.is("Safe")) raw *= self ? 1.15 : 0.9;
        return raw;
    }

    private int delay(double base) {
        int ticks = Math.max(0, (int) Math.round(base));
        if (acProfile.is("Grim-Aldenz") && ticks == 0) ticks = 1;
        int jit = (int) jitter.getAsDouble();
        if (jit > 0) ticks += ThreadLocalRandom.current().nextInt(jit + 1);
        return ticks;
    }

    private int findSlot(net.minecraft.item.Item item) {
        var inventory = mc.player.getInventory();
        for (int i = 0; i < 9; i++) {
            if (inventory.getStack(i).isOf(item)) return i;
        }
        return -1;
    }

    private void restoreSlot() {
        if (switchBack.isEnabled() && previousSlot >= 0 && mc.player != null) {
            mc.player.getInventory().setSelectedSlot(previousSlot);
        }
        previousSlot = -1;
    }
}
