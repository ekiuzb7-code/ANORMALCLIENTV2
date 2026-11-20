package com.anormal.client.module.impl.world;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.registry.Registries;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

public class BlockIn extends Module {
    public final NumberSetting aimSpeed = new NumberSetting("Aim Speed", "View rotation speed while building", 12.0, 1.0, 30.0, 0.5);
    public final NumberSetting placeDelay = new NumberSetting("Place Delay", "Ticks between placements", 2.0, 0.0, 20.0, 1.0);
    public final BooleanSetting silentAim = new BooleanSetting("Silent Aim", "Build without moving client camera", false);
    public final BooleanSetting sneak = new BooleanSetting("Sneak", "Sneak while placing blocks", true);
    public final BooleanSetting keepSneak = new BooleanSetting("Keep Sneak", "Stay sneaking after building", false);
    public final BooleanSetting bedFinder = new BooleanSetting("Bed Finder", "Leave opening toward nearby bed", true);
    public final ModeSetting blockPriority = new ModeSetting("Block Priority", "Lowest cost saves valuables, Hardest uses tough blocks", "Lowest cost", "Lowest cost", "Hardest");
    public final BooleanSetting returnToLastSlot = new BooleanSetting("Return to Last Slot", "Restore hotbar slot after building", true);
    public final BooleanSetting useBlacklist = new BooleanSetting("Use Blacklist", "Skip utility blocks while building", true);
    private final List<BlockPos> queue = new ArrayList<>();
    private int delay = 0, originalSlot = -1, retries = 0;

    public BlockIn() {
        super("BlockIn", "Builds protective walls around you on demand", Category.WORLD);
        addSetting(aimSpeed); addSetting(placeDelay); addSetting(silentAim);
        addSetting(sneak); addSetting(keepSneak); addSetting(bedFinder);
        addSetting(blockPriority); addSetting(returnToLastSlot); addSetting(useBlacklist);
    }

    @Override
    public void onEnable() {
        queue.clear(); delay = 0; originalSlot = -1; retries = 0;
        if (mc.player == null || mc.world == null) return;
        originalSlot = mc.player.getInventory().getSelectedSlot();
        BlockPos base = mc.player.getBlockPos();
        Direction bedSide = bedFinder.isEnabled() ? findBedSide(base) : null;
        for (int h = 0; h <= 2; h++) {
            for (Direction d : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
                if (d == bedSide && h >= 1) continue;
                BlockPos p = base.offset(d).up(h);
                if (mc.world.isAir(p)) queue.add(p);
            }
        }
        if (queue.isEmpty()) setEnabled(false);
    }

    @Override
    public void onDisable() {
        if (sneak.isEnabled() && !keepSneak.isEnabled() && mc.options != null) mc.options.sneakKey.setPressed(false);
        if (returnToLastSlot.isEnabled() && mc.player != null && originalSlot >= 0 && originalSlot < 9) {
            try {
                mc.player.getInventory().setSelectedSlot(originalSlot);
            } catch (Throwable ignored) {}
        }
        queue.clear();
        delay = 0;
        retries = 0;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) {
            setEnabled(false);
            return;
        }
        if (sneak.isEnabled()) mc.options.sneakKey.setPressed(true);
        if (queue.isEmpty()) { setEnabled(false); return; }
        if (delay > 0) { delay--; return; }
        BlockPos target = queue.get(0);
        if (!mc.world.isAir(target)) {
            queue.remove(0);
            retries = 0;
            return;
        }
        // Pick a solid neighbour to build the new block against - plants, snow layers and fluids make the server reject the click.
        BlockPos anchor = null;
        Direction side = null;
        for (Direction d : Direction.values()) {
            BlockPos n = target.offset(d);
            if (!isAnchor(n)) continue;
            anchor = n;
            side = d.getOpposite();
            break;
        }
        if (anchor == null) {
            // Nothing to build against yet (the block below is still queued): wait, and give up on this one eventually.
            if (++retries > 40) { queue.remove(0); retries = 0; }
            return;
        }
        int slot = findBlockSlot();
        if (slot == -1) {
            setEnabled(false);
            return;
        }
        if (mc.player.getInventory().getSelectedSlot() != slot) {
            // The server only accepts a placement once it has seen the new hotbar slot, so place on the next tick.
            mc.player.getInventory().setSelectedSlot(slot);
            delay = 1;
            return;
        }
        Vec3d hit = new Vec3d(anchor.getX() + 0.5, anchor.getY() + 0.5, anchor.getZ() + 0.5);
        if (!silentAim.isEnabled()) aimAt(hit, aimSpeed.getValue());
        boolean placed;
        try {
            BlockHitResult bhr = new BlockHitResult(hit, side, anchor, false);
            placed = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, bhr).isAccepted();
        } catch (Throwable ignored) {
            placed = false;
        }
        if (placed) {
            mc.player.swingHand(Hand.MAIN_HAND);
            queue.remove(0);
            retries = 0;
            delay = Math.max(0, placeDelay.getValue().intValue());
        } else {
            // Retry a few times instead of silently skipping the hole.
            if (++retries > 20) { queue.remove(0); retries = 0; }
            delay = 1;
        }
    }

    /** A block face that can actually be clicked: not air, not replaceable, and solid enough to place against. */
    private boolean isAnchor(BlockPos p) {
        try {
            var state = mc.world.getBlockState(p);
            if (state.isAir() || state.isReplaceable()) return false;
            return state.isSolidBlock(mc.world, p);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private Direction findBedSide(BlockPos base) {
        for (int dx = -8; dx <= 8; dx++)
            for (int dy = -2; dy <= 2; dy++)
                for (int dz = -8; dz <= 8; dz++) {
                    try {
                        Identifier id = Registries.BLOCK.getId(mc.world.getBlockState(base.add(dx, dy, dz)).getBlock());
                        if (id != null && id.getPath().contains("_bed")) {
                            if (Math.abs(dx) >= Math.abs(dz)) return dx > 0 ? Direction.EAST : Direction.WEST;
                            return dz > 0 ? Direction.SOUTH : Direction.NORTH;
                        }
                    } catch (Throwable ignored) {}
                }
        return null;
    }

    /** Turns the view toward the block being built against, on both axes, at the configured speed. */
    private void aimAt(Vec3d target, double speed) {
        Vec3d eye = mc.player.getEyePos();
        double dx = target.x - eye.x;
        double dy = target.y - eye.y;
        double dz = target.z - eye.z;
        double flat = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) Math.toDegrees(-Math.atan2(dy, flat));
        mc.player.setYaw(approach(mc.player.getYaw(), yaw, speed));
        mc.player.setPitch(approach(mc.player.getPitch(), pitch, speed));
    }

    private static float approach(float current, float wanted, double speed) {
        float delta = wanted - current;
        while (delta > 180.0f) delta -= 360.0f;
        while (delta < -180.0f) delta += 360.0f;
        return current + (float) Math.max(-speed, Math.min(speed, delta));
    }

    private int findBlockSlot() {
        int fallback = -1;
        for (int i = 0; i < 9; i++) {
            try {
                if (!(mc.player.getInventory().getStack(i).getItem() instanceof net.minecraft.item.BlockItem)) continue;
                Identifier id = Registries.ITEM.getId(mc.player.getInventory().getStack(i).getItem());
                if (id == null) continue;
                if (useBlacklist.isEnabled() && isBlacklisted(id.getPath())) continue;
                if (blockPriority.is("Hardest") && isHard(id.getPath())) return i;
                if (fallback == -1) fallback = i;
            } catch (Throwable ignored) {}
        }
        return fallback;
    }

    private boolean isBlacklisted(String p) {
        return p.contains("tnt") || p.contains("glass") || p.contains("chest") || p.contains("crafting") || p.contains("enchant") || p.contains("furnace") || p.contains("hopper") || p.contains("beacon");
    }

    private boolean isHard(String p) {
        return p.contains("obsidian") || p.contains("netherite") || p.contains("anvil");
    }
}
