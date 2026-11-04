package com.anormal.client.module.impl.kuro;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.util.kuro.KuroBlockPlacer;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;

import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

/**
 * Ported from kuro (dev.kuro.client.module.impl.AutoMLG).
 *
 * <p>When you fall far enough, looks straight down, switches to a hotbar water bucket and
 * places the water just before you land, then scoops it back up and returns to your old slot
 * and view. Needs a water bucket in the hotbar. Does nothing while flying / gliding / riding,
 * in dimensions where water evaporates, or when you would land in water / on a slime block anyway.</p>
 */
public class KuroAutoMLG extends Module {
    private final NumberSetting minFall = new NumberSetting("Min fall (blocks)",
            "Fall distance that arms the module", 4.0, 3.0, 30.0, 1.0);
    /** Distance from your EYES to the floor at which the water is placed. A bucket only reaches ~4.5 blocks from the eyes. */
    private final NumberSetting place = new NumberSetting("Place distance (eyes)",
            "Eyes-to-floor distance at which the water goes down (bucket reach is ~4.5)", 4.4, 2.0, 4.5, 0.1);
    private final BooleanSetting pickup = new BooleanSetting("Pick water up", "Scoop the water back after landing", true);
    private final BooleanSetting restoreLook = new BooleanSetting("Restore view", "Turn the view back after the landing", true);

    private enum State { IDLE, ARMED, PLACED, PICKUP }
    private State state = State.IDLE;
    private int previous = -1, bucketSlot = -1, timer, pendingRestore = -1;
    private float prevPitch;

    public KuroAutoMLG() {
        super("AutoMLG", "Water bucket clutch on long falls (kuro port)", Category.KURO);
        addSetting(minFall);
        addSetting(place);
        addSetting(pickup);
        addSetting(restoreLook);
    }

    @Override
    public void onEnable() { state = State.IDLE; }

    @Override
    public void onDisable() {
        finish();
        if (pendingRestore >= 0 && mc.player != null) KuroBlockPlacer.selectForce(pendingRestore);
        pendingRestore = -1;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) { state = State.IDLE; return; }
        if (pendingRestore >= 0) { if (KuroBlockPlacer.select(pendingRestore)) pendingRestore = -1; return; }   // one swap per tick
        if (mc.player.isCreative() || mc.player.isSpectator() || mc.player.getAbilities().flying
                || mc.player.isGliding() || mc.player.hasVehicle()
                || mc.world.getRegistryKey() == World.NETHER) {
            if (state != State.IDLE) finish();
            return;
        }

        switch (state) {
            case IDLE -> {
                if (mc.player.isOnGround() || mc.player.isTouchingWater() || mc.player.getVelocity().y > -0.5) return;
                if (mc.player.fallDistance < minFall.getValue()) return;
                int slot = KuroBlockPlacer.findSlot(s -> s.isOf(Items.WATER_BUCKET));
                if (slot < 0) return;                         // no water bucket in the hotbar
                int cur = KuroBlockPlacer.selected();
                if (!KuroBlockPlacer.select(slot)) return;    // a swap already happened this tick: try again next tick
                bucketSlot = slot;
                previous = cur;
                prevPitch = mc.player.getPitch();
                mc.player.setPitch(90f);
                state = State.ARMED;
                armedStep();                                  // do not waste a tick: the floor may already be in reach
            }
            case ARMED -> armedStep();
            case PLACED -> {
                // wait for the landing (or for the water to catch us), then scoop it back up
                if (++timer > 40) { finish(); return; }
                if (mc.player.isOnGround() || mc.player.isTouchingWater()) {
                    timer = 0;
                    state = pickup.isEnabled() ? State.PICKUP : State.IDLE;
                    if (!pickup.isEnabled()) finish();
                }
            }
            case PICKUP -> {
                if (++timer > 10) { finish(); return; }
                if (!mc.player.getMainHandStack().isOf(Items.BUCKET)) {
                    if (mc.player.getMainHandStack().isOf(Items.WATER_BUCKET)) { finish(); return; }   // the placement failed, nothing to scoop
                    return;                                   // the server has not confirmed the empty bucket yet
                }
                mc.player.setPitch(90f);
                mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                mc.player.swingHand(Hand.MAIN_HAND);
                finish();
            }
            default -> { }
        }
    }

    /**
     * Armed: keep the bucket in hand and the view straight down, and place the water the moment
     * the floor is inside the bucket's reach (measured from the EYES).
     */
    private void armedStep() {
        if (mc.player.isOnGround() || mc.player.isTouchingWater()) { finish(); return; }
        if (!mc.player.getMainHandStack().isOf(Items.WATER_BUCKET) && !KuroBlockPlacer.select(bucketSlot)) return;   // swap not allowed this tick: wait
        mc.player.setPitch(90f);
        BlockHitResult hit = floor();
        if (hit == null) return;                              // nothing below within range yet
        if (!mc.world.getFluidState(hit.getBlockPos()).isEmpty()
                || mc.world.getBlockState(hit.getBlockPos()).isOf(Blocks.SLIME_BLOCK)
                || mc.world.getBlockState(hit.getBlockPos()).isOf(Blocks.HAY_BLOCK)) {
            finish();                                         // water / slime / hay already cushions the landing
            return;
        }
        double reach = Math.min(place.getValue(), mc.player.getBlockInteractionRange() - 0.15);
        if (mc.player.getEyeY() - hit.getPos().y > reach) return;   // still too far for the bucket
        mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
        mc.player.swingHand(Hand.MAIN_HAND);
        timer = 0;
        state = State.PLACED;
    }

    /** First solid block (or fluid) straight below the player, within 6 blocks. */
    private BlockHitResult floor() {
        Vec3d from = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        Vec3d to = from.subtract(0, 6, 0);
        BlockHitResult r = mc.world.raycast(new RaycastContext(from, to,
                RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.SOURCE_ONLY, mc.player));
        return r.getType() == HitResult.Type.BLOCK ? r : null;
    }

    private void finish() {
        if (mc.player != null) {
            if (previous >= 0 && !KuroBlockPlacer.select(previous)) pendingRestore = previous;
            if (restoreLook.isEnabled() && state != State.IDLE) mc.player.setPitch(prevPitch);
        }
        previous = -1;
        bucketSlot = -1;
        state = State.IDLE;
    }
}
