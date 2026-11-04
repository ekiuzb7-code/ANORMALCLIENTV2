package com.anormal.client.module.impl.kuro;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;

import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ShulkerBoxScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.screen.GenericContainerScreenHandler;

import java.util.HashSet;
import java.util.Set;

/** kuro {@code ChestStealer} port: sandiq/barrel/shulker ochiqliganda hammasini shift-click qilib oladi. */
public class KuroChestStealer extends Module {
    private final NumberSetting delay = new NumberSetting("Delay (ticks)", "Ticks between steals", 1.0, 0.0, 10.0, 1.0);
    private final BooleanSetting autoClose = new BooleanSetting("Auto close", false);
    private final Set<Integer> tried = new HashSet<>();
    private int lastSync = -1, cooldown;

    public KuroChestStealer() {
        super("Chest Stealer", "Shift-clicks everything out of an open chest or shulker box", Category.KURO);
        addSetting(delay);
        addSetting(autoClose);
    }

    @Override public void onDisable() { tried.clear(); lastSync = -1; }

    @Override
    public void onTick() {
        if (mc.player == null || mc.interactionManager == null) return;
        // kuro: ChestMenu rowCount*9 yoki ShulkerBox doim 27. Bu mapping'da shulker
        // box ham GenericContainerScreenHandler (3 qator = 27) sifatida kiradi.
        if (!(mc.player.currentScreenHandler instanceof net.minecraft.screen.GenericContainerScreenHandler h)) { tried.clear(); lastSync = -1; return; }

        int size = h.getInventory().size();

        if (h.syncId != lastSync) { tried.clear(); lastSync = h.syncId; cooldown = delay.getValue().intValue(); }
        if (cooldown > 0) { cooldown--; return; }

        for (int i = 0; i < size; i++) {
            if (tried.contains(i)) continue;
            tried.add(i);                                  // each slot is tried once, so a full inventory can't loop forever
            ItemStack item = h.slots.get(i).getStack();
            if (item.isEmpty()) continue;
            mc.interactionManager.clickSlot(h.syncId, i, 0, SlotActionType.QUICK_MOVE, mc.player);
            cooldown = delay.getValue().intValue();
            return;
        }
        if (autoClose.isEnabled()) mc.player.closeHandledScreen();
    }
}
