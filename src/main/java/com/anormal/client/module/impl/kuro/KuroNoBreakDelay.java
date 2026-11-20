package com.anormal.client.module.impl.kuro;

import com.anormal.client.mixin.ClientPlayerInteractionManagerAccessor;
import com.anormal.client.module.Category;
import com.anormal.client.module.Module;

/**
 * kuro {@code NoBreakDelay} port: ikki blokning orasidagi 5-tick kechikishni
 * olib tashlaydi.
 *
 * <p>Anormaldagi farqlar: kuro'da yordamchi {@code InteractionManagerInvoker}
 * bilan {@code destroyDelay}GA to‘g‘ridan-to‘g‘ri set qilinadi; Anormalda
 * allaqachon bor {@link ClientPlayerInteractionManagerAccessor} faqat
 * {@code blockBreakingCooldown} va {@code currentBreakingProgress} foydalanish
 * uchun ochiq. Shu sababli shu modul yangi Accessor yozmasdan, Anormal mixin
 * #{ClientPlayerInteractionManagerAccessor}ga bog'lanadi.</p>
 */
public class KuroNoBreakDelay extends Module {
    public KuroNoBreakDelay() {
        super("No Break Delay", "Removes the delay between breaking two blocks (kuro port)", Category.KURO);
    }

    @Override
    public void onTickAlways() {
        // onTickAlways() runs for every module, enabled or not. Without this guard the vanilla
        // post-break cooldown was zeroed in every session even with the module switched off.
        if (!isEnabled()) return;
        if (mc.player == null || mc.interactionManager == null) return;
        ((ClientPlayerInteractionManagerAccessor) mc.interactionManager).lucid$setBlockBreakingCooldown(0);
    }
}
