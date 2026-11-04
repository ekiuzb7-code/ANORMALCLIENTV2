package com.anormal.client.mixin;

import net.minecraft.client.network.ClientPlayerInteractionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Modulga paket ketma-ketligining aniq bir nuqtasi: "selected slot" paketini yuborish imkonini beradi. */
@Mixin(ClientPlayerInteractionManager.class)
public interface KuroInteractionManagerInvoker {
    /** Ushlab turgan slot serverga oxirgi marta aytilgan bilan farq qilsa, selected-slot paketini yuboradi. */
    @Invoker("syncSelectedSlot")
    void kuro$syncSelectedSlot();
}
