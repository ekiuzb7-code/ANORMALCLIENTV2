package com.anormal.client.module.impl.kuro;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.kuro.KuroSwapLimit;

import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.item.ItemStack;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * kuro {@code InvSorter} port.
 *
 * <p>Bitta safarlik inventar tartiblash: yoqilsa asosiy inventarni (hotbar va
 * armor emas) item bo'yicha guruhlaydi, avval eng katta stack, bir vaqtda
 * bitta almashtirib, keyin o'zini o'chiradi. Bind qilingan tugma "sort
 * tugmasi" sifatida ishlaydi.</p>
 */
public class KuroInvSorter extends Module {
    private final NumberSetting delay = new NumberSetting("Delay (ticks)", "Ticks between swaps", 1.0, 1.0, 5.0, 1.0);
    private final Deque<int[]> swaps = new ArrayDeque<>();
    private int wait;

    public KuroInvSorter() {
        super("Inv Sorter", "One-shot inventory sort", Category.KURO);
        addSetting(delay);
    }

    @Override
    public void onEnable() {
        swaps.clear();
        wait = 0;
        if (mc.player == null) { setEnabled(false); return; }

        int[] keys = new int[27];
        for (int k = 0; k < 27; k++) {
            ItemStack s = mc.player.getInventory().getStack(9 + k);
            // kuro System.identityHashCode(item) — item instance'ning identifikatori
            keys[k] = s.isEmpty() ? Integer.MAX_VALUE : System.identityHashCode(s.getItem()) * 128 + (127 - Math.min(127, s.getCount()));
        }
        for (int i = 0; i < 27; i++) {                     // selection sort, recording each swap
            int m = i;
            for (int j = i + 1; j < 27; j++) if (keys[j] < keys[m]) m = j;
            if (m != i) {
                int t = keys[i]; keys[i] = keys[m]; keys[m] = t;
                swaps.add(new int[]{9 + i, 9 + m});         // player screen handler slots 9..35 = main inventory
            }
        }
        if (swaps.isEmpty()) setEnabled(false);
    }

    @Override public void onDisable() { swaps.clear(); }

    @Override
    public void onTick() {
        var p = mc.player;
        if (p == null || mc.interactionManager == null) return;
        if (mc.currentScreen != null && !(mc.currentScreen instanceof InventoryScreen)) return;
        if (!p.currentScreenHandler.getCursorStack().isEmpty()) return;   // something already on the cursor
        if (++wait < delay.getValue().intValue()) return;
        wait = 0;

        int[] sw = swaps.peekLast();
        if (sw == null) { setEnabled(false); return; }
        if (!KuroSwapLimit.take()) return;                  // one swap per tick: try again next tick
        swaps.poll();
        int sync = p.playerScreenHandler.syncId;
        // pick up A, drop on B (swaps them), put what came back down on A
        mc.interactionManager.clickSlot(sync, sw[0], 0, net.minecraft.screen.slot.SlotActionType.PICKUP, p);
        mc.interactionManager.clickSlot(sync, sw[1], 0, net.minecraft.screen.slot.SlotActionType.PICKUP, p);
        mc.interactionManager.clickSlot(sync, sw[0], 0, net.minecraft.screen.slot.SlotActionType.PICKUP, p);
        if (swaps.isEmpty()) setEnabled(false);
    }
}
