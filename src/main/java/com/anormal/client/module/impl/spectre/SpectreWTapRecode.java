package com.anormal.client.module.impl.spectre;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.module.ModuleManager;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;

/**
 * Spectre port: "W Tap Recode" — timed movement taps after a successful hit.
 *
 * <p>The tap is armed from the attack hook ({@code ClientPlayerInteractionManagerMixin}),
 * holds for the configured duration and releases, restoring whatever keys were held
 * before it started. All four Spectre tap styles are kept.</p>
 */
public final class SpectreWTapRecode extends Module {
    public final ModeSetting tapMode = addSetting(new ModeSetting("Mode", "Tap style", "W Tap", "W Tap", "S Tap", "Sneak Tap", "Sprint Tap"));
    public final NumberSetting duration = addSetting(new NumberSetting("Duration", "How long the tap holds (ms)", 180.0, 25.0, 1000.0, 1.0));
    public final NumberSetting delay = addSetting(new NumberSetting("Delay", "Minimum time between taps (ms)", 500.0, 0.0, 2500.0, 1.0));
    public final BooleanSetting onlyOnGround = addSetting(new BooleanSetting("Only Ground", "Only tap while on the ground", true));

    public SpectreWTapRecode() {
        super("SpectreWTapRecode", "Spectre port: timed movement taps after a successful hit.", Category.SPECTRE);
    }

    private long stopTime;
    private long nextTapTime;
    private boolean active;
    private boolean captured;
    private boolean oldForward;
    private boolean oldBack;
    private boolean oldSneak;
    private boolean oldSprint;

    public static void onAttack() {
        SpectreWTapRecode module = ModuleManager.getModule(SpectreWTapRecode.class);
        if (module != null && module.isEnabled()) module.arm();
    }

    public boolean isTapping() {
        return isEnabled() && active;
    }

    @Override
    public void onEnable() {
        active = false;
        captured = false;
        nextTapTime = 0L;
    }

    @Override
    public void onDisable() {
        restoreKeys();
        if (mc.player != null) mc.player.setSprinting(false);
    }

    private void arm() {
        var player = mc.player;
        if (player == null) return;
        long now = System.currentTimeMillis();
        if (active || now < nextTapTime) return;
        if (onlyOnGround.isEnabled() && !player.isOnGround()) return;

        stopTime = now + Math.max(0L, (long) duration.getAsDouble());
        nextTapTime = now + Math.max(0L, (long) delay.getAsDouble());
        active = true;
        captured = false;
    }

    @Override
    public void onTick() {
        if (!active) return;
        if (mc.player == null) {
            active = false;
            return;
        }
        if (System.currentTimeMillis() <= stopTime) {
            applyTap();
        } else {
            restoreKeys();
            active = false;
        }
    }

    private void applyTap() {
        captureKeys();
        switch (tapMode.getValue()) {
            case "W Tap" -> mc.options.forwardKey.setPressed(false);
            case "S Tap" -> mc.options.backKey.setPressed(true);
            case "Sneak Tap" -> mc.options.sneakKey.setPressed(true);
            case "Sprint Tap" -> mc.player.setSprinting(false);
            default -> { }
        }
    }

    private void captureKeys() {
        if (captured) return;
        oldForward = mc.options.forwardKey.isPressed();
        oldBack = mc.options.backKey.isPressed();
        oldSneak = mc.options.sneakKey.isPressed();
        oldSprint = mc.options.sprintKey.isPressed();
        captured = true;
    }

    private void restoreKeys() {
        if (!captured) return;
        mc.options.forwardKey.setPressed(oldForward);
        mc.options.backKey.setPressed(oldBack);
        mc.options.sneakKey.setPressed(oldSneak);
        mc.options.sprintKey.setPressed(oldSprint);
        captured = false;
    }
}
