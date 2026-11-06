package com.anormal.client.module.impl.spectre;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.util.NotificationManager;
import com.anormal.client.util.ColorUtils;

/**
 * Spectre port: "Weather Notifier" — notifies you when it starts raining.
 */
public final class SpectreWeatherNotifier extends Module {
    private static final int STABLE_TICKS = 20;

    public final BooleanSetting sound = addSetting(new BooleanSetting("Sound", "Play a sound with the notification", true));

    private net.minecraft.client.world.ClientWorld level;
    private boolean raining;
    private boolean soundPlayed;
    private int changedTicks;

    public SpectreWeatherNotifier() {
        super("SpectreWeatherNotifier", "Spectre port: notifies you when it starts raining.", Category.SPECTRE);
    }

    @Override
    public void onDisable() {
        level = null;
    }

    @Override
    public void onTick() {
        var current = mc.world;
        if (current != level) {
            level = current;
            raining = false;
            changedTicks = 0;
        }
        if (current == null || mc.player == null) return;

        if (current.isRaining() == raining) {
            changedTicks = 0;
        } else if (++changedTicks >= STABLE_TICKS) {
            raining = !raining;
            changedTicks = 0;
            soundPlayed = false;
            if (raining) {
                NotificationManager.push("Weather Changed!", "It started raining.", ColorUtils.rgba(70, 170, 255, 255));
                if (sound.isEnabled() && !soundPlayed) {
                    soundPlayed = true;
                    try {
                        mc.player.playSound(net.minecraft.sound.SoundEvents.ENTITY_PLAYER_LEVELUP, 0.7f, 1.1f);
                    } catch (Throwable ignored) {
                    }
                }
            }
        }
    }
}
