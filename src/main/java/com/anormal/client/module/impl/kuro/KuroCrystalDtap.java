package com.anormal.client.module.impl.kuro;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.module.impl.client.AntiBot;
import com.anormal.client.module.impl.client.Friends;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.NotificationManager;
import com.anormal.client.util.kuro.KuroBlockPlacer;
import com.anormal.client.util.kuro.KuroRotations;
import com.anormal.client.util.kuro.KuroServerSync;

import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Hand;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

/**
 * Ported from kuro (dev.kuro.client.module.impl.CrystalDtap).
 *
 * <p>Crystal Dtap: a one-key combo on the nearest enemy.
 * launch (melee hit, or a wind charge at their feet) - obsidian under them - two crystals
 * on it - both crystals broken in the SAME tick (the double tap).</p>
 *
 * <p>Silent aim (default on): each click turns the SERVER-side aim onto its target with a
 * look packet; your camera never moves. One interaction step per tick, each placement waits
 * for the server (KuroServerSync). It switches itself off when finished and puts your old
 * slot back.</p>
 */
public class KuroCrystalDtap extends Module {
    private static final double REACH = 4.5;
    private static final int MAX_AGE = 200;
    private static final int SERVER_TIMEOUT = 5;
    private static final int CRYSTAL_WAIT = 8;       // ticks to wait for a placed crystal to show up before trying again
    private static final int MAX_TRIES = 3;

    private final NumberSetting launch = new NumberSetting("Launch", "0 = melee hit, 1 = wind charge, 2 = none", 0.0, 0.0, 2.0, 1.0);
    private final NumberSetting crystals = new NumberSetting("Crystals", "How many crystals to place (1-2)", 2.0, 1.0, 2.0, 1.0);
    private final NumberSetting targetRange = new NumberSetting("Target range", "How far the enemy may be", 5.0, 2.0, 8.0, 0.5);
    private final NumberSetting minDamage = new NumberSetting("Min damage", "The spot must hurt them at least this much", 6.0, 0.0, 36.0, 0.5);
    private final BooleanSetting limitSelf = new BooleanSetting("Limit self damage", "Skip spots that would hurt you too much", false);
    private final NumberSetting maxSelf = new NumberSetting("Max self damage", "Your own damage cap (when Limit self damage is on)", 10.0, 0.0, 36.0, 0.5);
    private final BooleanSetting silentAim = new BooleanSetting("Silent aim", "Aim with look packets, camera stays put", true);
    private final NumberSetting delay = new NumberSetting("Extra delay (ticks)", "Extra wait between steps", 0.0, 0.0, 10.0, 1.0);

    private enum Phase { IDLE, INIT, LAUNCH, LAUNCH_WAIT, PLAN, OBSIDIAN, CRYSTAL, BREAK, RESTORE }
    private Phase phase = Phase.IDLE;

    private static final class Spot {
        BlockPos obs, crystal;
        boolean existing;               // obsidian / bedrock is already there
        BlockHitResult obsHit;          // click that places the obsidian (null when existing)
        int obsTries, cClicks, cClickAge;
    }

    private PlayerEntity target;
    private final List<Spot> spots = new ArrayList<>();
    private int origSlot, age, wait, launchTicks;
    private boolean swapped;

    public KuroCrystalDtap() {
        super("CrystalDtap", "Launch, obsidian, two crystals, both popped at once (kuro port)", Category.KURO);
        addSetting(launch);
        addSetting(crystals);
        addSetting(targetRange);
        addSetting(minDamage);
        addSetting(limitSelf);
        addSetting(maxSelf);
        addSetting(silentAim);
        addSetting(delay);
    }

    @Override
    public void onEnable() {
        phase = Phase.INIT;
        age = 0; wait = 0; launchTicks = 0;
        swapped = false;
        target = null;
        spots.clear();
    }

    @Override
    public void onDisable() {
        if (swapped && mc.player != null) KuroBlockPlacer.selectForce(origSlot);
        KuroRotations.release(this);
        phase = Phase.IDLE;
        target = null;
        spots.clear();
        swapped = false;
    }

    // ---------------- the sequence ----------------

    @Override
    public void onTick() {
        if (phase == Phase.IDLE) return;
        if (mc.player == null || mc.world == null || mc.interactionManager == null) { finish(); return; }
        if (phase == Phase.INIT) { init(); return; }
        if (mc.currentScreen != null) return;
        if (target == null || !target.isAlive() || target.isRemoved()) { abort("The target is gone"); return; }
        if (++age > MAX_AGE) { abort("Gave up (took too long)"); return; }
        if (wait > 0) { wait--; return; }

        switch (phase) {
            case LAUNCH -> launch();
            case LAUNCH_WAIT -> launchWait();
            case PLAN -> plan();
            case OBSIDIAN -> obsidian();
            case CRYSTAL -> crystal();
            case BREAK -> breakCrystals();
            case RESTORE -> restore();
            default -> { }
        }
    }

    private void init() {
        if (mc.currentScreen != null) { fail("Press its key bind in game (a menu is open)"); return; }
        target = bestTarget();
        if (target == null) { fail("No enemy player in range"); return; }
        if (crystalCount() < 1) { fail("No end crystals (hotbar or offhand)"); return; }
        if (launch.getValue().intValue() == 0 && weaponSlot() < 0 && !isWeapon(mc.player.getMainHandStack())) {
            fail("No weapon in your hotbar for the launch"); return;
        }
        if (launch.getValue().intValue() == 1 && KuroBlockPlacer.findSlot(s -> s.isOf(Items.WIND_CHARGE)) < 0) {
            fail("No wind charge in your hotbar"); return;
        }
        origSlot = KuroBlockPlacer.selected();
        wait = delay.getValue().intValue();
        phase = launch.getValue().intValue() == 2 ? Phase.PLAN : Phase.LAUNCH;
    }

    // ---- 1. launch ----

    private void launch() {
        if (launch.getValue().intValue() == 0) meleeLaunch(); else windLaunch();
    }

    private void meleeLaunch() {
        if (!isWeapon(mc.player.getMainHandStack())) {
            int slot = weaponSlot();
            if (slot < 0) { abort("No weapon in your hotbar"); return; }
            if (!KuroServerSync.ready(SERVER_TIMEOUT) || !KuroBlockPlacer.select(slot)) return;
            KuroServerSync.beginStep();
            swapped = true;
            wait = delay.getValue().intValue();
            return;
        }
        if (reachTo(target) > 3.0) { abort("The target is out of melee reach"); return; }
        if (mc.player.getAttackCooldownProgress(0.5f) < 0.9f) return;       // wait for the weapon to recharge
        look(target.getEntityPos().add(0, target.getHeight() * 0.6, 0));
        KuroAttackPackets.hit(target);
        launchTicks = 0;
        wait = Math.max(1, delay.getValue().intValue());
        phase = Phase.PLAN;
    }

    private void windLaunch() {
        if (!hold(s -> s.isOf(Items.WIND_CHARGE), "wind charges")) return;
        if (!KuroServerSync.ready(SERVER_TIMEOUT) || mc.player.isUsingItem()) return;
        look(target.getEntityPos().add(0, 0.1, 0));                               // at their feet: the burst throws them up
        mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
        mc.player.swingHand(Hand.MAIN_HAND);
        KuroServerSync.beginStep();
        launchTicks = 0;
        phase = Phase.LAUNCH_WAIT;
    }

    /** Waits (a few ticks at most) until the wind charge has thrown the target up. */
    private void launchWait() {
        if (target.getVelocity().y > 0.3 || !target.isOnGround() || ++launchTicks > 8) {
            wait = delay.getValue().intValue();
            phase = Phase.PLAN;
        }
    }

    // ---- 2. plan the two crystal spots (from where the target is now) ----

    private void plan() {
        spots.clear();
        Vec3d eye = mc.player.getEyePos();
        Box me = mc.player.getBoundingBox(), them = target.getBoundingBox();
        BlockPos base = target.getBlockPos();
        List<double[]> scored = new ArrayList<>();
        List<Spot> found = new ArrayList<>();
        for (int x = -3; x <= 3; x++) for (int y = -4; y <= 1; y++) for (int z = -3; z <= 3; z++) {
            BlockPos o = base.add(x, y, z);
            BlockState st = mc.world.getBlockState(o);
            boolean existing = st.isOf(Blocks.OBSIDIAN) || st.isOf(Blocks.BEDROCK);
            if (!existing && !st.isReplaceable()) continue;
            BlockPos c = o.up();
            if (!mc.world.getBlockState(c).isAir()) continue;
            Box obsBox = new Box(o);
            if (!existing && (obsBox.intersects(me) || obsBox.intersects(them))) continue;   // a block cannot go inside a player
            if (!mc.world.getOtherEntities(null, new Box(c.getX(), c.getY(), c.getZ(),
                    c.getX() + 1, c.getY() + 2, c.getZ() + 1), e -> true).isEmpty()) continue;
            Vec3d top = Vec3d.ofBottomCenter(c);
            if (eye.squaredDistanceTo(top) > REACH * REACH) continue;
            Vec3d cp = top.add(0, 0.5, 0);
            double dmg = KuroCrystalAura.crystalDamage(cp, target);
            double self = KuroCrystalAura.crystalDamage(cp, mc.player);
            if (dmg < minDamage.getValue()) continue;
            if (limitSelf.isEnabled() && self > maxSelf.getValue()) continue;
            Spot sp = new Spot();
            sp.obs = o; sp.crystal = c; sp.existing = existing;
            if (!existing) {
                sp.obsHit = placementHit(o, true);
                if (sp.obsHit == null) sp.obsHit = placementHit(o, false);
                if (sp.obsHit == null) continue;
            }
            found.add(sp);
            scored.add(new double[] { found.size() - 1, dmg - self * 0.5 + (existing ? 1.0 : 0.0) });
        }
        scored.sort(Comparator.comparingDouble((double[] a) -> a[1]).reversed());
        int obsLeft = obsidianCount();
        for (double[] sc : scored) {
            if (spots.size() >= crystals.getValue().intValue()) break;
            Spot sp = found.get((int) sc[0]);
            if (!sp.existing) { if (obsLeft <= 0) continue; obsLeft--; }
            if (crystalCount() <= spots.size()) break;
            spots.add(sp);
        }
        if (spots.isEmpty()) { abort("No crystal spot hurts the target enough (or no obsidian)"); return; }
        phase = Phase.OBSIDIAN;
    }

    // ---- 3. obsidian ----

    private void obsidian() {
        for (Spot sp : spots) {
            BlockState st = mc.world.getBlockState(sp.obs);
            if (st.isOf(Blocks.OBSIDIAN) || st.isOf(Blocks.BEDROCK)) continue;
            if (sp.obsTries >= MAX_TRIES) { abort("The obsidian could not be placed"); return; }
            if (!st.isReplaceable()) { abort("A block got in the way"); return; }
            if (!hold(s -> s.isOf(Items.OBSIDIAN), "obsidian")) return;
            if (!KuroServerSync.ready(SERVER_TIMEOUT) || mc.player.isUsingItem() || mc.player.isSneaking()) return;
            BlockHitResult hit = sp.obsHit;
            look(hit.getPos());
            if (click(Hand.MAIN_HAND, hit)) sp.obsTries++;
            return;                                                     // one step per tick
        }
        phase = Phase.CRYSTAL;
    }

    // ---- 4. crystals ----

    private void crystal() {
        boolean waiting = false;
        for (Spot sp : spots) {
            if (crystalAt(sp) != null) continue;
            if (sp.cClicks > 0 && age - sp.cClickAge < CRYSTAL_WAIT) { waiting = true; continue; }
            if (sp.cClicks >= MAX_TRIES) continue;                      // gave up on this one
            if (!mc.world.getBlockState(sp.obs).isOf(Blocks.OBSIDIAN)
                    && !mc.world.getBlockState(sp.obs).isOf(Blocks.BEDROCK)) continue;
            Hand hand = mc.player.getOffHandStack().isOf(Items.END_CRYSTAL) ? Hand.OFF_HAND : Hand.MAIN_HAND;
            if (hand == Hand.MAIN_HAND && !hold(s -> s.isOf(Items.END_CRYSTAL), "end crystals")) return;
            if (!KuroServerSync.ready(SERVER_TIMEOUT) || mc.player.isUsingItem() || mc.player.isSneaking()) return;
            Vec3d top = Vec3d.ofCenter(sp.obs).add(0, 0.5, 0);
            BlockHitResult hit = new BlockHitResult(top, Direction.UP, sp.obs, false);
            look(top);
            if (click(hand, hit)) { sp.cClicks++; sp.cClickAge = age; }
            return;                                                     // one step per tick
        }
        if (!waiting) phase = Phase.BREAK;
    }

    // ---- 5. the double tap: every crystal is hit in the same tick ----

    private void breakCrystals() {
        List<EndCrystalEntity> present = new ArrayList<>();
        for (Spot sp : spots) { EndCrystalEntity c = crystalAt(sp); if (c != null) present.add(c); }
        if (present.isEmpty()) { abort("No crystal appeared"); return; }
        if (limitSelf.isEnabled()) {
            for (EndCrystalEntity c : present) {
                if (KuroCrystalAura.crystalDamage(c.getEntityPos().add(0, 0.5, 0), mc.player) > maxSelf.getValue()) {
                    NotificationManager.info("CrystalDtap", "Left the crystals: too much damage to you");
                    phase = Phase.RESTORE;
                    return;
                }
            }
        }
        Vec3d aimPoint = present.get(0).getEntityPos().add(0, 0.5, 0);
        look(aimPoint);
        for (EndCrystalEntity c : present) KuroAttackPackets.hit(c);     // all hits go out together, this tick
        NotificationManager.info("CrystalDtap", present.size() + (present.size() == 1 ? " crystal" : " crystals")
                + " popped on " + target.getName().getString());
        phase = Phase.RESTORE;
    }

    private void restore() {
        if (swapped && KuroBlockPlacer.selected() != origSlot) {
            if (!KuroServerSync.ready(SERVER_TIMEOUT) || !KuroBlockPlacer.select(origSlot)) return;
            KuroServerSync.beginStep();
        }
        swapped = false;
        finish();
    }

    // ---------------- helpers ----------------

    /** Makes sure the main hand holds a matching item: false = not yet (swapped this tick / refused / missing). */
    private boolean hold(Predicate<ItemStack> wanted, String what) {
        if (wanted.test(mc.player.getMainHandStack())) return true;
        int slot = KuroBlockPlacer.findSlot(wanted);
        if (slot < 0) { abort("Ran out of " + what); return false; }
        if (!KuroServerSync.ready(SERVER_TIMEOUT) || !KuroBlockPlacer.select(slot)) return false;
        KuroServerSync.beginStep();
        swapped = true;
        wait = delay.getValue().intValue();
        return false;
    }

    private static boolean isWeapon(ItemStack s) {
        return !s.isEmpty() && (s.isIn(ItemTags.SWORDS) || s.isIn(ItemTags.AXES) || s.isOf(Items.MACE) || s.isIn(ItemTags.SPEARS));
    }

    private int weaponSlot() {
        int s = KuroBlockPlacer.findSlot(i -> !i.isEmpty() && i.isIn(ItemTags.SWORDS));
        if (s < 0) s = KuroBlockPlacer.findSlot(i -> !i.isEmpty() && i.isIn(ItemTags.AXES));
        if (s < 0) s = KuroBlockPlacer.findSlot(i -> i.isOf(Items.MACE));
        if (s < 0) s = KuroBlockPlacer.findSlot(i -> !i.isEmpty() && i.isIn(ItemTags.SPEARS));
        return s;
    }

    private int crystalCount() {
        int n = mc.player.getOffHandStack().isOf(Items.END_CRYSTAL) ? mc.player.getOffHandStack().getCount() : 0;
        for (int i = 0; i < 9; i++) { ItemStack s = mc.player.getInventory().getStack(i); if (s.isOf(Items.END_CRYSTAL)) n += s.getCount(); }
        return n;
    }

    private int obsidianCount() {
        int n = 0;
        for (int i = 0; i < 9; i++) { ItemStack s = mc.player.getInventory().getStack(i); if (s.isOf(Items.OBSIDIAN)) n += s.getCount(); }
        return n;
    }

    /** Nearest enemy player in range: friends and bots are skipped. */
    private PlayerEntity bestTarget() {
        PlayerEntity best = null;
        double bestD = targetRange.getValue();
        for (PlayerEntity p : mc.world.getEntitiesByClass(PlayerEntity.class, mc.player.getBoundingBox().expand(bestD), Entity::isAlive)) {
            if (p == mc.player || p.isSpectator() || Friends.isFriend(p.getName().getString()) || AntiBot.isBot(p)) continue;
            double d = mc.player.distanceTo(p);
            if (d < bestD) { bestD = d; best = p; }
        }
        return best;
    }

    /** Distance from your eyes to the closest point of the entity's hitbox (what melee reach is measured against). */
    private double reachTo(Entity e) {
        Vec3d eye = mc.player.getEyePos();
        Box b = e.getBoundingBox();
        Vec3d near = new Vec3d(MathHelper.clamp(eye.x, b.minX, b.maxX),
                MathHelper.clamp(eye.y, b.minY, b.maxY),
                MathHelper.clamp(eye.z, b.minZ, b.maxZ));
        return eye.distanceTo(near);
    }

    private EndCrystalEntity crystalAt(Spot sp) {
        Box box = new Box(sp.crystal).expand(0.2);
        for (EndCrystalEntity c : mc.world.getEntitiesByClass(EndCrystalEntity.class, box, Entity::isAlive)) return c;
        return null;
    }

    /** A click that places a block at c by pressing on a solid neighbour (never a container). Prefers a visible face. */
    private BlockHitResult placementHit(BlockPos c, boolean visibleOnly) {
        Vec3d eye = mc.player.getEyePos();
        BlockHitResult best = null;
        double bestD = Double.MAX_VALUE;
        for (Direction d : Direction.values()) {
            BlockPos s = c.offset(d);
            BlockState st = mc.world.getBlockState(s);
            if (st.isReplaceable() || st.isOf(Blocks.RESPAWN_ANCHOR) || st.getBlock() instanceof BlockWithEntity) continue;
            Direction face = d.getOpposite();
            Vec3d hit = Vec3d.ofCenter(s).add(face.getOffsetX() * 0.5, face.getOffsetY() * 0.5, face.getOffsetZ() * 0.5);
            double dist = eye.squaredDistanceTo(hit);
            if (dist > REACH * REACH || dist >= bestD) continue;
            if (visibleOnly) {
                BlockHitResult r = mc.world.raycast(new RaycastContext(eye, hit,
                        RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player));
                if (r.getType() != net.minecraft.util.hit.HitResult.Type.MISS && !r.getBlockPos().equals(s)) continue;
            }
            best = new BlockHitResult(hit, face, s, false);
            bestD = dist;
        }
        return best;
    }

    /** Turns the SERVER-side aim onto the point (a look packet, the camera stays put). */
    private void look(Vec3d point) {
        if (!silentAim.isEnabled()) return;
        Vec3d eye = mc.player.getEyePos();
        double dx = point.x - eye.x, dy = point.y - eye.y, dz = point.z - eye.z;
        double h = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float) MathHelper.clamp(-Math.toDegrees(Math.atan2(dy, Math.max(h, 1.0e-4))), -90.0, 90.0);
        if (KuroRotations.set(this, yaw, pitch)) KuroRotations.sendLookPacket(true);
    }

    /** Vanilla use: interactBlock (sends the use packet), swing if it was accepted. */
    private boolean click(Hand hand, BlockHitResult hit) {
        if (!mc.world.getWorldBorder().contains(hit.getBlockPos())) return false;
        ActionResult r = mc.interactionManager.interactBlock(mc.player, hand, hit);
        if (!r.isAccepted()) return false;
        mc.player.swingHand(hand);
        KuroServerSync.beginStep();
        return true;
    }

    /** Nothing was done yet: report and switch off. */
    private void fail(String why) {
        NotificationManager.info("CrystalDtap", why);
        finish();
    }

    /** Stop part-way: put the slot back, then switch off. */
    private void abort(String why) {
        NotificationManager.info("CrystalDtap", why);
        if (swapped) phase = Phase.RESTORE; else finish();
    }

    private void finish() {
        KuroRotations.release(this);
        phase = Phase.IDLE;
        if (isEnabled()) setEnabled(false);
    }
}
