package com.anormal.client.module.impl.spectre;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Spectre port: "Auto Clicker" — clicks the left and/or right mouse button at a randomised,
 * human CPS.
 *
 * <p>Spectre runs its own click engine per button (Click / Hold / Off). This port keeps the
 * modes, the CPS range, the randomisation and "Only While Held", and drives the clicks
 * through the local attack/use keys so the vanilla click path and animations stay intact.</p>
 */
public final class SpectreAutoClicker extends Module {
    private static final double TICK_MS = 50.0;

    public final ModeSetting left = addSetting(new ModeSetting("Left", "Left button mode", "Click", "Click", "Hold", "Off"));
    public final NumberSetting leftMin = addSetting(new NumberSetting("Left Min CPS", "Minimum left clicks per second", 9.0, 1.0, 20.0, 1.0));
    public final NumberSetting leftMax = addSetting(new NumberSetting("Left Max CPS", "Maximum left clicks per second", 13.0, 1.0, 20.0, 1.0));
    public final ModeSetting right = addSetting(new ModeSetting("Right", "Right button mode", "Off", "Click", "Hold", "Off"));
    public final NumberSetting rightMin = addSetting(new NumberSetting("Right Min CPS", "Minimum right clicks per second", 8.0, 1.0, 20.0, 1.0));
    public final NumberSetting rightMax = addSetting(new NumberSetting("Right Max CPS", "Maximum right clicks per second", 12.0, 1.0, 20.0, 1.0));
    public final NumberSetting randomization = addSetting(new NumberSetting("Randomization", "Timing jitter in percent", 15.0, 0.0, 40.0, 1.0));
    public final BooleanSetting onlyWhileHeld = addSetting(new BooleanSetting("Only While Held", "Only click while the button is held", false));

    private long leftNext;
    private long rightNext;
    private boolean leftPressed;
    private boolean rightPressed;

    public SpectreAutoClicker() {
        super("SpectreAutoClicker", "Spectre port: clicks the mouse buttons at a randomised, human CPS.", Category.SPECTRE);
    }

    @Override
    public void onEnable() {
        leftNext = 0L;
        rightNext = 0L;
        leftPressed = false;
        rightPressed = false;
    }

    @Override
    public void onDisable() {
        release();
    }

    @Override
    public void onTick() {
        var player = mc.player;
        if (player == null || mc.world == null || mc.currentScreen != null
                || !player.isAlive() || mc.interactionManager == null) {
            release();
            return;
        }

        long now = System.currentTimeMillis();
        tickButton(left, leftMin, leftMax, now, true);
        tickButton(right, rightMin, rightMax, now, false);
    }

    private void tickButton(ModeSetting mode, NumberSetting min, NumberSetting max, long now, boolean isLeft) {
        if (mode.is("Off")) {
            if (isLeft && leftPressed) releaseLeft();
            if (!isLeft && rightPressed) releaseRight();
            return;
        }
        if (onlyWhileHeld.isEnabled()) {
            boolean held = isLeft ? mc.options.attackKey.isPressed() : mc.options.useKey.isPressed();
            if (!held) return;
        }

        long next = isLeft ? leftNext : rightNext;
        if (now >= next) {
            long interval = intervalMillis(min, max);
            if (isLeft) leftNext = now + interval;
            else rightNext = now + interval;
            click(isLeft);
        }
    }

    private long intervalMillis(NumberSetting min, NumberSetting max) {
        double minCps = Math.max(1.0, min.getAsDouble());
        double maxCps = Math.max(minCps, max.getAsDouble());
        double cps = minCps + ThreadLocalRandom.current().nextDouble() * (maxCps - minCps);
        long base = Math.max(1L, Math.round(1000.0 / cps));
        double jitter = randomization.getAsDouble() / 100.0;
        if (jitter > 0.0) {
            double factor = 1.0 + (ThreadLocalRandom.current().nextDouble() * 2.0 - 1.0) * jitter;
            base = Math.max(1L, Math.round(base * factor));
        }
        return Math.min(base, (long) TICK_MS * 4L);
    }

    private void click(boolean isLeft) {
        if (isLeft) {
            mc.options.attackKey.setPressed(true);
            leftPressed = true;
        } else {
            mc.options.useKey.setPressed(true);
            rightPressed = true;
        }
        // released on the next tick so vanilla registers exactly one click
        releaseNextTick(isLeft);
    }

    private void releaseNextTick(boolean isLeft) {
        Thread release = new Thread(() -> {
            try {
                Thread.sleep(20L);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            mc.execute(() -> {
                if (isLeft) releaseLeft();
                else releaseRight();
            });
        }, "spectre-autoclicker-release");
        release.setDaemon(true);
        release.start();
    }

    private void releaseLeft() {
        mc.options.attackKey.setPressed(false);
        leftPressed = false;
    }

    private void releaseRight() {
        mc.options.useKey.setPressed(false);
        rightPressed = false;
    }

    private void release() {
        releaseLeft();
        releaseRight();
    }
}
