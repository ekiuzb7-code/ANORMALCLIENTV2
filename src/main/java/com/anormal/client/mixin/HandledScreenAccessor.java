package com.anormal.client.mixin;

import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Kursorni orqa qo'yganki slot — runtime'da obfuscated (field_2787),
 * reflection bilan yavfsiz olinmagan; shuning uchun Accessor'ga o'tqizilmadi.
 */
@Mixin(HandledScreen.class)
public interface HandledScreenAccessor {
    @Accessor("focusedSlot")
    Slot getFocusedSlot();
}
