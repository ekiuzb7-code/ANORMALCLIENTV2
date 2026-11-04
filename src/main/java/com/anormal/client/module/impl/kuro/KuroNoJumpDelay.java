package com.anormal.client.module.impl.kuro;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.mixin.LivingEntityAccessor;

/**
 * Ported one-to-one from kuro (dev.kuro.client.module.impl.NoJumpDelay).
 *
 * <p>Removes the jump cooldown, so holding space bunny-hops every tick you touch the ground.</p>
 *
 * <p>kuro sets the field through a dedicated mixin accessor; here the private
 * {@code jumpingCooldown} field (Yarn mapping for the jump delay) is cleared by reflection,
 * which works without touching the mixin config.</p>
 */
public class KuroNoJumpDelay extends Module {

    public KuroNoJumpDelay() {
        super("NoJumpDelay", "Removes the jump cooldown (kuro port)", Category.KURO);
    }

    /**
     * Sakrash kechikishi maydoni runtime'da obfuscated — eski reflection ${
     * kodida nom mayxonali o'qilgani uchun maydon topilmaydiki (silently
     * yo'qoladi). Endi mixin Accessor bilan xavfsiz, o'zgarish chegarasiz.
     */
    @Override
    public void onPreTick() {
        if (mc.player == null) return;
        ((LivingEntityAccessor) mc.player).setJumpingCooldown(0);
    }
}
