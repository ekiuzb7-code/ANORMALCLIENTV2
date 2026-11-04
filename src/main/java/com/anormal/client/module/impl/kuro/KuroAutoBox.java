package com.anormal.client.module.impl.kuro;

import com.anormal.client.lucid.WorldRender3D;
import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.module.ModuleManager;
import com.anormal.client.module.impl.world.Freecam;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.mixin.KuroInteractionManagerInvoker;
import com.anormal.client.util.kuro.KuroBlockPlacer;
import com.anormal.client.util.kuro.KuroPacing;
import com.anormal.client.util.kuro.KuroServerSync;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.block.Blocks;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.entity.player.PlayerEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Ported from kuro (dev.kuro.client.module.impl.AutoBox).
 *
 * <p>Auto Box: traps the nearest player.</p>
 *
 * <ol>
 *   <li>COBWEB - a cobweb goes into their feet block (needs a cobweb in the hotbar;
 *       without one this step is skipped).</li>
 *   <li>BOX - once they are stuck in the web, blocks are built around them: a ring at
 *       their feet, a ring at head height and a roof (a side block first, because a roof
 *       block needs something to be placed against). Obsidian is preferred, otherwise any
 *       plain solid hotbar block.</li>
 * </ol>
 *
 * <p>One spot is deliberately left EMPTY ("Opening") so you can still hit them: by default
 * the head-height block on the side that faces you. Still-to-place spots are outlined in
 * the world ("Highlights"); the opening gets its own colour. One interaction step per tick,
 * each waiting for the server's last transaction (KuroServerSync); the slot swap back is its
 * own step once the box is done. Spots that cannot be clicked are skipped for a while.</p>
 */
public class KuroAutoBox extends Module {
    private static final Direction[] SIDES = { Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST };

    private final NumberSetting range = new NumberSetting("Range", "Maximum distance to the target", 5.0, 2.0, 6.0, 0.5);
    private final BooleanSetting webFirst = new BooleanSetting("Cobweb first", "Put a cobweb under them before the box", true);
    private final com.anormal.client.setting.ModeSetting opening = new com.anormal.client.setting.ModeSetting("Opening",
            "Spot left free so you can still hit them", "Head, facing you", "None (full box)", "Head, facing you", "Feet, facing you", "Top");
    private final BooleanSetting cap = new BooleanSetting("Roof", "Cage them in from above too", true);
    private final BooleanSetting frontOnly = new BooleanSetting("Front side only",
            "Only blocks a real click could place from where you stand", false);
    private final com.anormal.client.setting.ModeSetting rotationMode = new com.anormal.client.setting.ModeSetting("Rotation",
            "How the placement click is aimed", "Camera (turns your view)", "Camera (turns your view)", "Silent (look packet)");
    private final NumberSetting aimSpeed = new NumberSetting("Aim speed", "Camera rotation speed", 6.0, 1.0, 10.0, 0.5);
    private final NumberSetting finish = new NumberSetting("Finish time (s)",
            "Seconds the whole action takes; 0 = use Delay", 2.0, 0.0, 10.0, 0.5);
    private final NumberSetting delay = new NumberSetting("Delay", "Ticks between blocks (used when Finish time is 0)", 1.0, 1.0, 10.0, 1.0);
    private final BooleanSetting highlights = new BooleanSetting("Highlights", "Outline the spots still to fill", true);
    private final ColorSetting color = new ColorSetting("Block color", "Colour of the planned blocks", 0xFF66CCEE);
    private final ColorSetting openColor = new ColorSetting("Opening color", "Colour of the opening spot", 0xFF55AA99);
    private final ColorSetting webColor = new ColorSetting("Cobweb color", "Colour of the cobweb spot", 0xFFBBDD44);

    private int wait, restore = -1, webWait, ticks;
    private final Map<Long, Integer> skipUntil = new HashMap<>();   // spots that could not be clicked: not retried for a few ticks
    private UUID lockedTarget;
    private Direction towardMe = Direction.NORTH;        // side of the target that faces you, fixed per target

    // what the world renderer shows (set every tick)
    private final List<BlockPos> shownBlocks = new ArrayList<>();
    private BlockPos shownOpening, shownWeb;
    private boolean registered;

    public KuroAutoBox() {
        super("AutoBox", "Traps the nearest player in a block cage (kuro port)", Category.KURO);
        addSetting(range);
        addSetting(webFirst);
        addSetting(opening);
        addSetting(cap);
        addSetting(frontOnly);
        addSetting(rotationMode);
        addSetting(aimSpeed);
        addSetting(finish);
        addSetting(delay);
        addSetting(highlights);
        addSetting(color);
        addSetting(openColor);
        addSetting(webColor);
    }

    @Override
    public void onEnable() {
        wait = 0; restore = -1; webWait = 0; ticks = 0;
        skipUntil.clear(); lockedTarget = null; clearShown();
        if (registered) return;
        try {
            WorldRenderEvents.END_MAIN.register(this::anormal$dispatchWorld);
            registered = true;
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void onDisable() { forceRestore(); clearShown(); lockedTarget = null; }

    private void anormal$dispatchWorld(WorldRenderContext wrc) {
        if (!isEnabled() || !highlights.isEnabled()) return;
        renderWorld(wrc);
    }

    private void clearShown() { shownBlocks.clear(); shownOpening = null; shownWeb = null; }

    /** Swaps back to the slot held before: its own interaction step (one per tick, after the server confirmed the last one). */
    private void tryRestore() {
        if (restore < 0 || mc.player == null) return;
        if (!KuroServerSync.ready(5)) return;
        if (KuroBlockPlacer.select(restore)) {
            KuroServerSync.beginStep();
            ((KuroInteractionManagerInvoker) mc.interactionManager).kuro$syncSelectedSlot();
            restore = -1;
        }
    }

    private void forceRestore() {
        if (restore >= 0 && mc.player != null) {
            KuroBlockPlacer.selectForce(restore);
            ((KuroInteractionManagerInvoker) mc.interactionManager).kuro$syncSelectedSlot();
        }
        restore = -1;
    }

    private static boolean webbed(BlockPos feet) {
        var level = mc.world;
        return level.getBlockState(feet).isOf(Blocks.COBWEB) || level.getBlockState(feet.up()).isOf(Blocks.COBWEB);
    }

    /** Side of the target that faces the player (dominant axis). */
    private Direction facing(PlayerEntity t) {
        double dx = mc.player.getX() - t.getX(), dz = mc.player.getZ() - t.getZ();
        if (Math.abs(dx) > Math.abs(dz)) return dx > 0 ? Direction.EAST : Direction.WEST;
        return dz > 0 ? Direction.SOUTH : Direction.NORTH;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null || mc.currentScreen != null || freecamActive()) { clearShown(); return; }
        ticks++;
        skipUntil.values().removeIf(until -> until <= ticks);
        if (webWait > 0) webWait--;

        PlayerEntity t = KuroBlockPlacer.nearestPlayer(range.getValue());
        if (t == null) { wait = 0; webWait = 0; lockedTarget = null; clearShown(); tryRestore(); return; }
        if (!t.getUuid().equals(lockedTarget)) { lockedTarget = t.getUuid(); towardMe = facing(t); }

        int blockSlot = KuroBlockPlacer.findSlot(s -> s.isOf(Items.OBSIDIAN));
        if (blockSlot < 0) blockSlot = KuroBlockPlacer.findSlot(KuroBlockPlacer::isSolidBlock);
        int webSlot = webFirst.isEnabled() ? KuroBlockPlacer.findSlot(s -> s.isOf(Items.COBWEB)) : -1;

        BlockPos feet = t.getBlockPos();
        Box targetBox = t.getBoundingBox(), myBox = mc.player.getBoundingBox();

        // ---- the plan: ring at feet, ring at head, then the roof (side block first, then the middle) ----
        int open = opening.getIndex();
        BlockPos openSpot = switch (open) {
            case 1 -> feet.up().offset(towardMe);
            case 2 -> feet.offset(towardMe);
            default -> null;
        };
        List<BlockPos> spots = new ArrayList<>();
        for (int y = 0; y <= 1; y++) for (Direction d : SIDES) spots.add(feet.up(y).offset(d));
        if (openSpot != null) spots.remove(openSpot);
        if (cap.isEnabled() && open != 3) {
            Direction scaffold = towardMe.getOpposite();                      // away from the gap, so its wall block exists
            if (frontOnly.isEnabled()) scaffold = towardMe.rotateYClockwise(); // front only: a side wall you can see, not the far one
            else if (open == 0) scaffold = SIDES[0];
            spots.add(feet.up(2).offset(scaffold));
            spots.add(feet.up(2));
        }
        List<BlockPos> need = new ArrayList<>();
        for (BlockPos p : spots) {
            if (!mc.world.getBlockState(p).isReplaceable()) continue;         // already filled
            Box b = new Box(p);
            if (b.intersects(targetBox) || b.intersects(myBox)) continue;     // would be rejected / trap yourself
            if (frontOnly.isEnabled() && !KuroBlockPlacer.reachable(p)) continue;   // no click from where you stand could place it
            need.add(p);
        }

        boolean stuck = webbed(feet);
        boolean needWeb = webSlot >= 0 && !stuck && mc.world.getBlockState(feet).isReplaceable();

        // ---- what to show in the world ----
        shownBlocks.clear();
        if (!need.isEmpty() && blockSlot >= 0) shownBlocks.addAll(need);
        shownOpening = openSpot != null && mc.world.getBlockState(openSpot).isReplaceable() ? openSpot : null;
        shownWeb = needWeb ? feet : null;

        // ---- act: at most one interaction step this tick ----
        List<BlockPos> candidates = needWeb ? List.of(feet) : need;
        int slot = needWeb ? webSlot : blockSlot;
        if (slot < 0 || candidates.isEmpty()) { wait = 0; tryRestore(); return; }   // nothing left to do: swap back
        if (needWeb && webWait > 0) return;                                        // web placed, waiting for the server to show it
        if (++wait < KuroPacing.gap(finish.getValue(), need.size() + (needWeb ? 1 : 0), delay.getValue().intValue())) return;
        if (!KuroServerSync.ready(5)) return;                                      // previous step not confirmed yet

        for (BlockPos p : candidates) {
            if (skipUntil.containsKey(p.asLong())) continue;
            int before = KuroBlockPlacer.selected();
            KuroBlockPlacer.PlaceResult r;
            if (rotationMode.getIndex() == 0) {
                // camera: turn the real view toward the spot; the click goes out on a LATER tick,
                // once the ray from the sent angles really hits a face next to the spot
                KuroBlockPlacer.Attempt at = KuroBlockPlacer.placeFromView(this, slot, p);
                r = at.result();
                if (r == KuroBlockPlacer.PlaceResult.AIM) {
                    KuroBlockPlacer.turnToward(at.yaw(), at.pitch(), aimSpeed.getValue());
                    return;
                }
            } else {
                r = KuroBlockPlacer.placeAt(this, slot, p);
            }
            if (KuroBlockPlacer.selected() != before && restore < 0) restore = before;
            if (r == KuroBlockPlacer.PlaceResult.DONE) {
                KuroServerSync.beginStep();
                wait = 0;
                if (needWeb) webWait = 8;
                return;
            }
            if (r == KuroBlockPlacer.PlaceResult.LATER) return;                    // a rule said "not now": same spot again next tick
            skipUntil.put(p.asLong(), ticks + 10);                                 // no valid click on this spot: try the next one
        }
    }

    /** Outlines the blocks that are still to be placed, the cobweb spot and the opening that is left free. */
    void renderWorld(WorldRenderContext wrc) {
        Camera camera = wrc.gameRenderer().getCamera();
        if (camera == null || !camera.isReady()) return;
        MatrixStack matrices = wrc.matrices();
        VertexConsumerProvider consumers = wrc.consumers();
        if (matrices == null || consumers == null) return;
        if (shownBlocks.isEmpty() && shownOpening == null && shownWeb == null) return;

        int b = color.getColor() & 0xFFFFFF, o = openColor.getColor() & 0xFFFFFF, w = webColor.getColor() & 0xFFFFFF;
        double in = 0.03;

        WorldRender3D.begin(matrices, camera);
        try {
            for (BlockPos p : shownBlocks)
                WorldRender3D.drawBox(consumers,
                        new Box(p.getX() + in, p.getY() + in, p.getZ() + in,
                                p.getX() + 1 - in, p.getY() + 1 - in, p.getZ() + 1 - in),
                        0xFF000000 | b);
            BlockPos op = shownOpening;
            if (op != null)
                WorldRender3D.drawBox(consumers,
                        new Box(op.getX() + in, op.getY() + in, op.getZ() + in,
                                op.getX() + 1 - in, op.getY() + 1 - in, op.getZ() + 1 - in),
                        0xFF000000 | o);
            BlockPos web = shownWeb;
            if (web != null)
                WorldRender3D.drawBox(consumers,
                        new Box(web.getX() + in, web.getY() + in, web.getZ() + in,
                                web.getX() + 1 - in, web.getY() + 1 - in, web.getZ() + 1 - in),
                        0xFF000000 | w);
        } finally {
            WorldRender3D.end(matrices);
        }
    }

    private static boolean freecamActive() {
        Freecam f = ModuleManager.getModule(Freecam.class);
        return f != null && f.isEnabled() && f.isCameraActive();
    }
}
