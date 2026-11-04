package com.anormal.client.module.impl.kuro;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.kuro.KuroSwapLimit;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;

/** Ölmez Totemni offhand'da saqlaydi — offhand bo'sh qolgan har tickda inventory'dan birini oladi. */
public class KuroAutoTotem extends Module {
    public final NumberSetting delay = new NumberSetting("Delay (ticks)", "Almashtirishning cooldowni", 1.0, 0.0, 10.0, 1.0);
    public final BooleanSetting replace = new BooleanSetting("Replace offhand item", "Offhand totem emas bo'lsa ham almashtiradi", true);

    private int cooldown;

    public KuroAutoTotem() {
        super("AutoTotem", "Offhand'da Ölmez Totemni saqlaydi (kuro port)", Category.KURO);
        addSetting(delay);
        addSetting(replace);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.interactionManager == null || mc.currentScreen != null || mc.player.isSpectator()) return;
        if (cooldown > 0) { cooldown--; return; }

        ItemStack off = mc.player.getOffHandStack();
        if (off.isOf(Items.TOTEM_OF_UNDYING)) return;
        if (!replace.isEnabled() && !off.isEmpty()) return;

        for (int i = 0; i < 36; i++) {
            if (!mc.player.getInventory().getStack(i).isOf(Items.TOTEM_OF_UNDYING)) continue;
            int slot = i < 9 ? 36 + i : i;          // inventory index -> player screen handler slot
            if (!KuroSwapLimit.take()) return;      // bir tickda bitta swap; keyingi tickda yana urinish
            // SWAP button 40: kliklangan slotni offhand bilan almashtiradi
            mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, slot, 40, SlotActionType.SWAP, mc.player);
            cooldown = delay.getValue().intValue();
            return;
        }
    }
}
