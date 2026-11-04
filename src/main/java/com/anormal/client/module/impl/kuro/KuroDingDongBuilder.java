package com.anormal.client.module.impl.kuro;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.kuro.KuroBlockPlacer;
import com.anormal.client.util.kuro.KuroFreecam;
import com.anormal.client.util.kuro.KuroPacing;

import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;

import java.util.ArrayList;
import java.util.List;

/**
 * kuro {@code DingDongBuilder} port.
 *
 * <p>Hotbar'dan oddiy ("solid") bloklar bilan qo'zg'almas, blok-blokbela
 * strukturani quradi. Va uni qo'ymoqchilgan yer ko'rsatib, modulni yoqingor
 * qiling: strukturani qarayotgan blokda anchor bo'ladi va siz Butter
 * yoningizda bhuc', qurish paytida reach ichida qoladi. Hammasi joyiga qo'yilganda o'chadi.</p>
 */
public class KuroDingDongBuilder extends Module {
    private final NumberSetting perTick = new NumberSetting("Blocks/tick", "Max blocks per placement step", 2.0, 1.0, 4.0, 1.0);
    private final NumberSetting finish = new NumberSetting("Finish time (s)", "Seconds the whole action takes (0 = instant)", 2.0, 0.0, 10.0, 0.5);
    private final NumberSetting delay = new NumberSetting("Delay", "Legacy delay when Finish time is 0", 1.0, 1.0, 10.0, 1.0);

    private final List<BlockPos> todo = new ArrayList<>();
    private int wait, restore = -1;
    private boolean restoreDue;

    public KuroDingDongBuilder() {
        super("Ding Dong Builder", "Builds a small fixed structure where you look", Category.KURO);
        addSetting(perTick);
        addSetting(finish);
        addSetting(delay);
    }

    /** Blueprint in local coordinates: x = right, y = up, z = forward. Built bottom layer first. */
    private static List<int[]> blueprint() {
        List<int[]> b = new ArrayList<>();
        for (int x = -1; x <= 1; x++) b.add(new int[]{x, 0, 0});   // 3 flat in a straight line
        for (int y = 1; y <= 3; y++) b.add(new int[]{0, y, 0});    // 3 stacked on top of the middle block
        return b;
    }

    @Override
    public void onEnable() {
        todo.clear();
        wait = 0;
        restore = -1;
        restoreDue = false;
        if (mc.player == null || !(mc.crosshairTarget instanceof BlockHitResult hit)
                || hit.getType() != HitResult.Type.BLOCK) {
            setEnabled(false);   // nothing to anchor to: look at a block first
            return;
        }
        BlockPos anchor = hit.getBlockPos().offset(hit.getSide());
        Direction f = mc.player.getHorizontalFacing();
        int fx = f.getOffsetX(), fz = f.getOffsetZ();
        int rx = -fz, rz = fx;                       // "right" of the facing direction
        for (int[] p : blueprint()) {
            todo.add(anchor.add(rx * p[0] + fx * p[2], p[1], rz * p[0] + fz * p[2]));
        }
    }

    @Override
    public void onDisable() {
        forceRestore();
        todo.clear();
    }

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
        if (mc.player == null || mc.world == null || mc.currentScreen != null || KuroFreecam.active()) return;
        if (restoreDue) restoreSlot();

        int slot = KuroBlockPlacer.findSlot(KuroBlockPlacer::isSolidBlock);
        if (slot < 0) return;                                           // need solid blocks in the hotbar

        Box me = mc.player.getBoundingBox();
        boolean remaining = false;
        List<BlockPos> need = new ArrayList<>();
        for (BlockPos p : todo) {
            if (!mc.world.getBlockState(p).isReplaceable()) continue;   // already built
            remaining = true;
            if (new Box(p).intersects(me)) continue;                    // standing in the way
            need.add(p);
        }
        if (!remaining) { setEnabled(false); return; }                  // finished
        if (need.isEmpty()) { wait = 0; return; }                       // only blocks you are standing in are left
        if (++wait < KuroPacing.gap(finish.getValue(), todo.size(), delay.getValue().intValue())) return;   // paced: the whole build takes about "Finish time"

        int per = KuroPacing.perStep(finish.getValue(), perTick.getValue().intValue());
        int placed = 0;
        for (BlockPos p : need) {
            int cur = KuroBlockPlacer.selected();
            if (!KuroBlockPlacer.select(slot)) return;                  // one swap per tick: try again next tick
            if (restore < 0) restore = cur;
            if (KuroBlockPlacer.place(p) && ++placed >= per) break;
        }
        if (placed > 0) {
            wait = 0;
            if (KuroPacing.paced(finish.getValue()) && restore >= 0) restoreSlot();
        }
    }
}
