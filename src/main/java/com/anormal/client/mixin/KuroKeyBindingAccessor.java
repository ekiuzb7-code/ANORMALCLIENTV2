package com.anormal.client.mixin;

import net.minecraft.client.option.KeyBinding;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * KeyBinding.pressed maydoniga kirish (kuro Freecam'dagi KeyMapping.setDown
 * analogi porti): freecam faol bo'lganda real o'yinchining harakat klavishlarini
 * har tick bo'shatish uchun kerak (Yarn 1.21.11'da setDown yo'q).
 */
@Mixin(KeyBinding.class)
public interface KuroKeyBindingAccessor {

    @Accessor("pressed")
    void setPressed(boolean value);
}
