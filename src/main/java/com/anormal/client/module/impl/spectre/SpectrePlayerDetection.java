package com.anormal.client.module.impl.spectre;

import com.anormal.client.lucid.StringSetting;
import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.module.ModuleManager;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.util.NotificationManager;
import com.anormal.client.util.ColorUtils;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Spectre port: "Player Detection" — detects when players are in the world.
 */
public final class SpectrePlayerDetection extends Module {
    private static final Set<String> PERMANENT_WHITELIST = Set.of("freecamera");
    private static final long DISCONNECT_DELAY_MS = 500L;

    public final StringSetting modulesToToggle = addSetting(new StringSetting("Modules To Toggle",
            "Comma-separated module names to toggle off on detection", "", 128));
    public final BooleanSetting enableDisconnect = addSetting(new BooleanSetting("Disconnect", "Disconnect when a player appears", true));
    public final ModeSetting notificationMode = addSetting(new ModeSetting("Notification Mode", "Where the warning shows", "Both", "Chat", "Toast", "Both"));
    public final BooleanSetting toggleOnPlayer = addSetting(new BooleanSetting("Toggle On Detect", "Pause hiding modules on detection", true));
    public final StringSetting userWhitelist = addSetting(new StringSetting("User Whitelist", "Ignored player names, comma-separated", "", 128));
    public final BooleanSetting toggleSelf = addSetting(new BooleanSetting("Toggle This Module", "Panic: toggle this module too", true));

    private final Set<String> detectedPlayers = new LinkedHashSet<>();
    private net.minecraft.client.world.ClientWorld lastLevel;
    private String whitelistRaw;
    private Set<String> whitelist = Set.of();

    public SpectrePlayerDetection() {
        super("SpectrePlayerDetection", "Spectre port: detects when players are in the world.", Category.SPECTRE);
    }

    @Override
    public void onDisable() {
        detectedPlayers.clear();
        lastLevel = null;
    }

    @Override
    public void onTick() {
        var self = mc.player;
        var level = mc.world;
        if (self == null || level == null) {
            detectedPlayers.clear();
            lastLevel = null;
            return;
        }

        if (level != lastLevel) {
            detectedPlayers.clear();
            lastLevel = level;
        }

        String selfName = self.getDisplayName().getString();
        java.util.UUID selfId = self.getUuid();
        Set<String> ignored = whitelist();
        Set<String> current = null;

        for (var player : mc.world.getPlayers()) {
            if (player == self || selfId.equals(player.getUuid())) continue;
            String name = player.getDisplayName().getString();
            if (name == null || name.equals(selfName)) continue;
            String key = name.toLowerCase(Locale.ROOT);
            if (PERMANENT_WHITELIST.contains(key) || ignored.contains(key)) continue;
            if (current == null) current = new LinkedHashSet<>();
            current.add(name);
        }

        if (current == null) {
            detectedPlayers.clear();
        } else if (!current.equals(detectedPlayers)) {
            detectedPlayers.clear();
            detectedPlayers.addAll(current);
            handlePlayerDetection(current);
        }
    }

    private void handlePlayerDetection(Set<String> players) {
        String mode = notificationMode.getValue();
        if (!mode.equals("Toast")) {
            announceDetection(players);
        }
        if (mode.equals("Toast") || mode.equals("Both")) {
            NotificationManager.push("Player Detection!", "Player Detected!", ColorUtils.rgba(255, 60, 60, 255));
        }
        if (toggleOnPlayer.isEnabled()) {
            for (Module module : resolveModulesToToggle()) {
                if (module.isEnabled()) module.toggle();
            }
        }
        if (toggleSelf.isEnabled()) {
            setEnabled(false);
        }
        if (enableDisconnect.isEnabled()) {
            scheduleDisconnect(players);
        }
    }

    private void announceDetection(Set<String> players) {
        if (mc.player == null) return;
        StringBuilder sb = new StringBuilder(players.size() == 1 ? "Player detected: " : "Players detected: ");
        boolean first = true;
        for (String p : players) {
            if (!first) sb.append(", ");
            sb.append(p);
            first = false;
        }
        mc.player.sendMessage(net.minecraft.text.Text.literal(sb.toString()), false);
    }

    private void scheduleDisconnect(Set<String> players) {
        // Spectre schedules on the main thread after half a second
        long delay = DISCONNECT_DELAY_MS;
        String names = String.join(", ", players);
        new Thread(() -> {
            try {
                Thread.sleep(delay);
            } catch (InterruptedException ignored) {
            }
            mc.execute(() -> {
                if (mc.world != null) {
                    try {
                        mc.disconnect(net.minecraft.text.Text.literal(
                                "[Player Detection] " + (names.isEmpty() ? "Player detected" : names)));
                    } catch (Throwable ignored) {
                    }
                }
            });
        }, "SpectrePlayerDetection-Disconnect").start();
    }

    private java.util.List<Module> resolveModulesToToggle() {
        String raw = modulesToToggle.get();
        if (raw.isBlank()) return java.util.List.of();
        java.util.LinkedHashSet<Module> resolved = new java.util.LinkedHashSet<>();
        for (String entry : raw.split(",")) {
            String key = entry.trim();
            if (key.isEmpty()) continue;
            Module module = ModuleManager.getModuleByName(key);
            if (module != null && module != this) resolved.add(module);
        }
        return new java.util.ArrayList<>(resolved);
    }

    private Set<String> whitelist() {
        String raw = userWhitelist.get();
        if (!raw.equals(whitelistRaw)) {
            whitelistRaw = raw;
            Set<String> parsed = new java.util.HashSet<>();
            for (String entry : raw.split(",")) {
                String key = entry.trim().toLowerCase(Locale.ROOT);
                if (!key.isEmpty()) parsed.add(key);
            }
            whitelist = Set.copyOf(parsed);
        }
        return whitelist;
    }
}
