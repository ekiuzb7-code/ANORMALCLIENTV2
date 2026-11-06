package com.anormal.client.module.impl.spectre;

import com.anormal.client.mixin.HandledScreenAccessor;
import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;

/**
 * Spectre port: "Hover Totem" — swaps a hovered inventory totem into the offhand or the
 * selected hotbar slot.
 *
 * <p>Spectre reads the hovered slot through an accessor on the container screen and sends a
 * SWAP click; this port uses the project's existing {@code HandledScreenAccessor} and the
 * same two-step choice: offhand first, then the configured hotbar slot.</p>
 */
public final class SpectreHoverTotem extends Module {
    public final BooleanSetting hotbarRefill = addSetting(new BooleanSetting("Hotbar Refill", "Also move a hovered totem into a hotbar slot", false));
    public final NumberSetting hotbarSlot = addSetting(new NumberSetting("Hotbar Slot", "Hotbar slot used by refill", 1.0, 1.0, 9.0, 1.0));

    public SpectreHoverTotem() {
        super("SpectreHoverTotem", "Spectre port: swaps a hovered inventory totem into the offhand or the selected hotbar slot.", Category.SPECTRE);
    }

    @Override
    public void onTick() {
        var player = mc.player;
        if (player == null || mc.interactionManager == null) return;
        if (!(mc.currentScreen instanceof HandledScreen<?> screen)) return;

        Slot hovered = ((HandledScreenAccessor) screen).getFocusedSlot();
        if (hovered == null || !hovered.getStack().isOf(Items.TOTEM_OF_UNDYING)) return;

        int swapButton;
        if (!player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) {
            swapButton = 40;
        } else {
            if (!hotbarRefill.isEnabled()) return;
            swapButton = (int) hotbarSlot.getAsDouble() - 1;
            if (player.getInventory().getStack(swapButton).isOf(Items.TOTEM_OF_UNDYING)) return;
        }

        mc.interactionManager.clickSlot(screen.getScreenHandler().syncId, hovered.id, swapButton,
                SlotActionType.SWAP, player);
    }
}
