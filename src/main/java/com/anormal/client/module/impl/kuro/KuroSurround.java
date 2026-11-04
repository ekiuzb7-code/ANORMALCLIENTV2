package com.anormal.client.module.impl.kuro;

import com.anormal.client.module.ModuleManager;
import com.anormal.client.module.impl.world.Freecam;
import com.anormal.client.util.kuro.KuroBlockPlacer;
import com.anormal.client.util.kuro.KuroPacing;
import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * Ported from kuro (dev.kuro.client.module.impl.Surround).
 *
 * <p>Walls you in: a block on each of the four sides of your feet (optionally the floor
 * under you and a second ring at head height). Prefers obsidian, otherwise any plain solid
 * hotbar block. Anything that gets broken is put back. Counterpart of KuroAutoBox, which
 * walls in another player.</p>
 */
public class KuroSurround extends Module {
    private static final Direction[] SIDES = { Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST };

    private final NumberSetting perTick = new NumberSetting("Blocks/tick",
            "Blocks placed in one step (used when Finish time is 0)", 2.0, 1.0, 4.0, 1.0);
    private final NumberSetting delay = new NumberSetting("Delay",
            "Ticks between blocks (used when Finish time is 0)", 1.0, 1.0, 10.0, 1.0);
    private final NumberSetting finish = new NumberSetting("Finish time (s)",
            "Seconds the whole action takes; 0 = old instant behaviour (Delay / Blocks per tick)", 2.0, 0.0, 10.0, 0.5);
    private final BooleanSetting head = new BooleanSetting("Head level too",
            "Also ring your block at head height", false);
    private final BooleanSetting floor = new BooleanSetting("Floor under feet",
            "Also fill the block under you", false);
    private final BooleanSetting onGround = new BooleanSetting("Only on ground",
            "Do nothing while you are airborne", true);
    private final BooleanSetting stayPut = new BooleanSetting("Off if I move",
            "Turn off once you walk out of the wall", true);
    private final BooleanSetting obsidianOnly = new BooleanSetting("Obsidian only",
            "Only use obsidian - any other hotbar block is ignored", false);

    private int wait, restore = -1;
    private BlockPos anchor;
    private boolean restoreDue;

    public KuroSurround() {
        super("Surround", "Walls you in with hotbar blocks (kuro port)", Category.KURO);
        addSetting(perTick);
        addSetting(delay);
        addSetting(finish);
        addSetting(head);
        addSetting(floor);
        addSetting(onGround);
        addSetting(stayPut);
        addSetting(obsidianOnly);
    }

    @Override
    public void onEnable() {
        wait = 0;
        restore = -1;
        anchor = mc.player != null ? mc.player.getBlockPos() : null;
    }

    @Override
    public void onDisable() { forceRestore(); anchor = null; }

    private void restoreSlot() {
        if (restore >= 0 && mc.player != null && !KuroBlockPlacer.select(restore)) { restoreDue = true; return; }   // one swap per tick: retried next tick
        restore = -1;
        restoreDue = false;
    }

    private void forceRestore() {
        if (restore >= 0 && mc.player != null) KuroBlockPlacer.selectForce(restore);
        restore = -1;
        restoreDue = false;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.currentScreen != null || freecamActive()) return;
        if (restoreDue) restoreSlot();
        BlockPos feet = mc.player.getBlockPos();
        if (anchor == null) anchor = feet;
        if (!feet.equals(anchor)) {
            if (stayPut.isEnabled()) { setEnabled(false); return; }
            anchor = feet;                                           // follow the player
        }
        if (onGround.isEnabled() && !mc.player.isOnGround()) return;

        int slot = KuroBlockPlacer.findSlot(s -> s.isOf(Items.OBSIDIAN));
        if (slot < 0 && !obsidianOnly.isEnabled()) slot = KuroBlockPlacer.findSlot(KuroBlockPlacer::isSolidBlock);
        if (slot < 0) { restoreSlot(); return; }

        // floor first, then the ring around the feet, then the ring at head height, so every block has support
        List<BlockPos> spots = new ArrayList<>();
        if (floor.isEnabled()) spots.add(feet.down());
        for (Direction d : SIDES) spots.add(feet.offset(d));
        if (head.isEnabled()) for (Direction d : SIDES) spots.add(feet.up().offset(d));

        List<BlockPos> need = new ArrayList<>();
        for (BlockPos p : spots) {
            if (!mc.world.getBlockState(p).isReplaceable()) continue;                       // already filled
            Box b = new Box(p);
            if (!mc.world.getOtherEntities(null, b, e -> e instanceof LivingEntity && !e.isSpectator()).isEmpty()) continue;   // you / a mob is in the way
            need.add(p);
        }
        if (need.isEmpty()) { wait = 0; restoreSlot(); return; }                            // nothing to do: the next job starts a fresh timer
        if (++wait < KuroPacing.gap(finish.getValue(), spots.size(), delay.getValue().intValue())) return;      // paced: one block every finish/N seconds

        int per = KuroPacing.perStep(finish.getValue(), perTick.getValue().intValue());
        int placed = 0;
        for (BlockPos p : need) {
            int cur = KuroBlockPlacer.selected();
            if (!KuroBlockPlacer.select(slot)) return;                  // one swap per tick: try again next tick
            if (restore < 0) restore = cur;
            if (KuroBlockPlacer.place(p) && ++placed >= per) break;
        }
        if (placed > 0) { wait = 0; if (KuroPacing.paced(finish.getValue())) restoreSlot(); } else restoreSlot();
    }

    private static boolean freecamActive() {
        Freecam f = ModuleManager.getModule(Freecam.class);
        return f != null && f.isEnabled() && f.isCameraActive();
    }
}
