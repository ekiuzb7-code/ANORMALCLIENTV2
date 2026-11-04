package com.anormal.client.util.kuro;

import com.anormal.client.module.ModuleManager;
import com.anormal.client.module.impl.world.Freecam;

/**
 * Kuro {@code Freecam.active()} ekvivalenti.
 *
 * <p>kuro'ning movement modullari (Bhop, Flight, Entity Speed, No Slow, Safe Walk, Sprint,
 * Elytra Hop) freecam yoqilgan paytda ishlamaydi. Anormal'ning Freecam modulida statik
 * {@code active()} yo'q, shuning uchun o'sha shart shu yerda tekshiriladi.</p>
 */
public final class KuroFreecam {

    private KuroFreecam() {}

    /** True while the Freecam camera is detached from the body. */
    public static boolean active() {
        try {
            Freecam freecam = ModuleManager.getModule(Freecam.class);
            return freecam != null && freecam.isCameraActive();
        } catch (Throwable t) {
            return false;
        }
    }
}
