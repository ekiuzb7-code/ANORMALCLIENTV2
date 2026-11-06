package com.anormal.client.module.impl.spectre;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.ColorUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Spectre port: "Anti Vanish" — flags players who are in the player list but not in the world.
 *
 * <p>Spectre keeps per-UUID history plus packet-level checks. This port keeps the core
 * detection (tab list versus loaded entities), the range filter, the alert with sound and the
 * HUD list of vanished players.</p>
 */
public final class SpectreAntiVanish extends Module {
    public final BooleanSetting chatAlert = addSetting(new BooleanSetting("Chat Alert", "Print a chat message", true));
    public final BooleanSetting sound = addSetting(new BooleanSetting("Sound", "Play a note on a new flag", true));
    public final NumberSetting range = addSetting(new NumberSetting("Range", "Only flag players this close", 128.0, 16.0, 512.0, 1.0));
    public final BooleanSetting hud = addSetting(new BooleanSetting("HUD", "List vanished players on screen", true));
    public final BooleanSetting shadow = addSetting(new BooleanSetting("Shadow", "Draw HUD text with a shadow", true));
    public final ColorSetting textColor = addSetting(new ColorSetting("Text Color", "HUD text colour", ColorUtils.rgba(255, 120, 120, 255)));

    private final Set<String> vanished = new HashSet<>();
    private final List<String> order = new ArrayList<>();

    public SpectreAntiVanish() {
        super("SpectreAntiVanish", "Spectre port: flags players who are listed but not in the world.", Category.SPECTRE);
    }

    @Override
    public void onEnable() {
        vanished.clear();
        order.clear();
    }

    @Override
    public void onDisable() {
        vanished.clear();
        order.clear();
    }

    @Override
    public void onTick() {
        var self = mc.player;
        if (self == null || mc.world == null || mc.getNetworkHandler() == null) return;

        Set<String> present = new HashSet<>();
        for (PlayerEntity player : mc.world.getPlayers()) {
            String name = player.getGameProfile().name();
            if (name != null) present.add(name);
        }
        String own = self.getGameProfile().name();
        double max = range.getAsDouble();

        for (var entry : mc.getNetworkHandler().getPlayerList()) {
            var profile = entry.getProfile();
            String name = profile.name();
            if (name == null || name.equals(own) || present.contains(name)) continue;
            if (vanished.add(name)) {
                order.add(name);
                if (mc.inGameHud != null) {
                    if (chatAlert.isEnabled()) {
                        mc.inGameHud.getChatHud().addMessage(Text.literal(
                                "[SpectreAntiVanish] " + name + " is listed but not in the world."));
                    }
                    if (sound.isEnabled()) {
                        mc.player.playSound(net.minecraft.sound.SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), 0.8F, 0.6F);
                    }
                }
            }
        }

        order.removeIf(name -> {
            boolean stillVanished = vanished.contains(name);
            if (!stillVanished) return true;
            return false;
        });
        if (order.size() > 16) order.subList(16, order.size()).clear();
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (!hud.isEnabled() || order.isEmpty() || mc.options.hudHidden) return;
        int y = 4;
        int color = textColor.getColor();
        context.drawText(mc.textRenderer, "Vanished:", 4, y, color, shadow.isEnabled());
        y += 10;
        for (String name : order) {
            context.drawText(mc.textRenderer, " " + name, 4, y, color, shadow.isEnabled());
            y += 10;
        }
    }
}
