package com.anormal.client.module.impl.kuro;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.module.ModuleManager;
import com.anormal.client.module.impl.world.Freecam;
import com.anormal.client.util.kuro.KuroBlockPlacer;
import com.anormal.client.util.kuro.KuroPacing;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;

/** Ported from kuro (dev.kuro.client.module.impl.AutoCobweb). Places a cobweb on the nearest player's feet (optionally head). */
public class KuroAutoCobweb extends Module {
    private final NumberSetting range = new NumberSetting("Range", "Maximum distance to the target", 4.5, 2.0, 6.0, 0.5);
    private final BooleanSetting head = new BooleanSetting("Head too", "Also web the block above their feet", false);
    private final NumberSetting finish = new NumberSetting("Finish time (s)",
            "Seconds the whole action takes; 0 = old instant behaviour (Delay per web)", 2.0, 0.0, 10.0, 0.5);
    private final NumberSetting delay = new NumberSetting("Delay", "Ticks between webs (used when Finish time is 0)", 2.0, 1.0, 10.0, 1.0);

    private int wait, restore = -1;
    private boolean restoreDue;

    public KuroAutoCobweb() {
        super("AutoCobweb", "Webs the nearest player's feet (kuro port)", Category.KURO);
        addSetting(range);
        addSetting(head);
        addSetting(finish);
        addSetting(delay);
    }

    @Override
    public void onEnable() { wait = 0; restore = -1; }

    @Override
    public void onDisable() { forceRestore(); }

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

        PlayerEntity t = KuroBlockPlacer.nearestPlayer(range.getValue());
        int slot = KuroBlockPlacer.findSlot(s -> s.isOf(Items.COBWEB));
        if (t == null || slot < 0) { wait = 0; restoreSlot(); return; }

        BlockPos feet = t.getBlockPos();
        BlockPos[] spots = head.isEnabled() ? new BlockPos[]{feet, feet.up()} : new BlockPos[]{feet};
        boolean any = false;
        for (BlockPos p : spots) if (mc.world.getBlockState(p).isReplaceable()) any = true;
        if (!any) { wait = 0; restoreSlot(); return; }
        if (++wait < KuroPacing.gap(finish.getValue(), spots.length, delay.getValue().intValue())) return;   // paced: the web lands about "Finish time" after the target is in range

        for (BlockPos p : spots) {
            if (!mc.world.getBlockState(p).isReplaceable()) continue;
            int cur = KuroBlockPlacer.selected();
            if (!KuroBlockPlacer.select(slot)) return;                  // one swap per tick: try again next tick
            if (restore < 0) restore = cur;
            if (KuroBlockPlacer.place(p)) { wait = 0; if (KuroPacing.paced(finish.getValue())) restoreSlot(); return; }
        }
        restoreSlot();
    }

    private static boolean freecamActive() {
        Freecam f = ModuleManager.getModule(Freecam.class);
        return f != null && f.isEnabled() && f.isCameraActive();
    }
}
