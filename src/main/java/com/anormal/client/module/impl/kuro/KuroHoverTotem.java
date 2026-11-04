package com.anormal.client.module.impl.kuro;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.kuro.KuroSwapLimit;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.item.Items;

/**
 * Kuro HoverTotem birga bir port: survival inventoryda totem ustiga bir muddat
 * kursorni turiztib berganingizda uni offhand'ga ko'chiradi. Respect
 * HoverTotem soddalashtirilgan variantiga asoslangan port.
 */
public class KuroHoverTotem extends Module {
    public final NumberSetting delay = new NumberSetting("Hover ticks", "Hover uchadigan tiklar soni", 2.0, 0.0, 10.0, 1.0);
    public final BooleanSetting onlyEmpty = new BooleanSetting("Only empty offhand", "Offhand bo'sh bo'lsa ishlaydi", true);

    private int hoverTicks;
    private int lastSlot = -1;

    public KuroHoverTotem() {
        super("HoverTotem", "Hover qilingan totemni offhandga ko'chiradi (kuro port)", Category.KURO);
        addSetting(delay);
        addSetting(onlyEmpty);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.interactionManager == null) return;
        if (!(mc.currentScreen instanceof InventoryScreen)) { hoverTicks = 0; lastSlot = -1; return; }
        ItemStack off = mc.player.getOffHandStack();
        if (onlyEmpty.isEnabled() && !off.isEmpty() && !off.isOf(Items.TOTEM_OF_UNDYING)) return;

        // kursorni oraqga qo'ydigan slot — runtime'da obfuscated, o'zbekcha reflection
        // olimda XATOLIK xavfi bor. Endi mixin Accessor bilan olinmaydi.
        int hovered = -1;
        Slot slot = ((com.anormal.client.mixin.HandledScreenAccessor) mc.currentScreen).getFocusedSlot();
        if (slot != null) {
            if (slot.getStack().isOf(Items.TOTEM_OF_UNDYING) && slot.id != 45) {
                hovered = slot.id;   // player screen handler slot-id (clickSlot uchun)
            }
        }

        if (hovered < 0) { hoverTicks = 0; lastSlot = -1; return; }
        if (hovered != lastSlot) { lastSlot = hovered; hoverTicks = 0; }
        hoverTicks++;
        if (hoverTicks < delay.getValue().intValue()) return;

        // 40 = offhand bilan SWAP
        if (!KuroSwapLimit.take()) return; // 1 tickda 1 swap
        mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, hovered, 40, SlotActionType.SWAP, mc.player);
        hoverTicks = 0;
    }
}
