package com.anormal.client.module.impl.kuro;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.util.InputForcer;
import com.anormal.client.util.kuro.KuroFreecam;

import net.minecraft.client.option.KeyBinding;

/**
 * kuro {@code AutoWalk} port (kuro: Category.MOVEMENT).
 *
 * <p>O'zingiz o'rniga oldinga kalitni tutilgan holda ushlab turadi.
 * Ekran ochiq yoki Freecam'da bo'lganda to'xtaydi.</p>
 *
 * <p>Moslash: kuro {@code keyUp.setDown(true)} + {@code KeyMapping.setAll()}
 * ishlatadi; Anormal'da shunga o'xshash pressni {@link InputForcer} saqlaydi
 * ({@code updatePressedStates()} soxta bosishni o'chirmasligi uchun).</p>
 */
public class KuroAutoWalk extends Module {
    private boolean pressedByUs;

    public KuroAutoWalk() { super("Auto Walk", "Holds the forward key for you", Category.KURO); }

    @Override public void onDisable() { release(); }

    private void release() {
        if (pressedByUs) {
            pressedByUs = false;
            InputForcer.forward = false;            // kuro: setDown(false) + setAll()
            try { KeyBinding.updatePressedStates(); } catch (Throwable ignored) {}
        }
    }

    @Override
    public void onPreTick() {
        if (mc.player == null || mc.currentScreen != null || KuroFreecam.active()) { release(); return; }
        InputForcer.forward = true;
        mc.options.forwardKey.setPressed(true);
        pressedByUs = true;
    }
}
