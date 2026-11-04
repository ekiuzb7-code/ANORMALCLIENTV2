package com.anormal.client.module.impl.kuro;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.kuro.KuroSwapLimit;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.SlotActionType;

/** Hotbardagi stack tugaganda (oxirgi blok qo'yila qolgan, asbob buzilgan...) o'sha itemni inventory'dan o'sha slotga qaytaradi. */
public class KuroAutoReplenish extends Module {
    public final NumberSetting delay = new NumberSetting("Delay (ticks)", "Qayta to'ldirish orasidagi tick soni", 1.0, 1.0, 10.0, 1.0);

    private final Item[] last = new Item[9];
    private int wait;

    public KuroAutoReplenish() {
        super("AutoReplenish", "Hotbar slotlarni avtomatik qayta to'ldiradi (kuro port)", Category.KURO);
        addSetting(delay);
    }

    @Override
    public void onEnable() {
        java.util.Arrays.fill(last, null);
        wait = 0;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.interactionManager == null) return;

        boolean screenOpen = mc.currentScreen != null;
        for (int i = 0; i < 9; i++) {
            ItemStack st = mc.player.getInventory().getStack(i);
            if (screenOpen || !st.isEmpty()) last[i] = st.isEmpty() ? null : st.getItem();   // faqat bo'shashgan slot qayta to'ldiriladi
        }
        if (screenOpen && !(mc.currentScreen instanceof InventoryScreen)) return;
        if (screenOpen) return;   // qo'lda item tashish: hech qachon qarshilik qilmaydi
        if (++wait < delay.getValue().intValue()) return;

        for (int i = 0; i < 9; i++) {
            if (last[i] == null || !mc.player.getInventory().getStack(i).isEmpty()) continue;
            for (int inv = 9; inv < 36; inv++) {
                ItemStack c = mc.player.getInventory().getStack(inv);
                if (c.isEmpty() || c.getItem() != last[i]) continue;
                if (!KuroSwapLimit.take()) return;    // bir tickda bitta swap: keyingi tickda qayta urinish
                // SWAP button i: o'sha inventory slotni hotbar slot i bilan almashtiradi
                mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, inv, i, SlotActionType.SWAP, mc.player);
                last[i] = null;
                wait = 0;
                return;
            }
            last[i] = null;   // qayta to'ldirish uchun hech narsa qolmagan
        }
    }
}
