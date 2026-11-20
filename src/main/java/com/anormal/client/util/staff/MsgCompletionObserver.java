package com.anormal.client.util.staff;

import com.anormal.client.mixin.ChatScreenAccessor;
import com.anormal.client.module.ModuleManager;
import com.anormal.client.module.impl.render.StaffDetector;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.network.packet.s2c.play.CommandSuggestionsS2CPacket;

import java.util.Locale;

/**
 * Inspects command-completion suggestions the server already returned, and only while the user is
 * typing a /msg-style command themselves. It never sends a completion request, never repeats a
 * query, and a suggestion is treated as weak (LOW confidence) evidence.
 */
public final class MsgCompletionObserver {

    private static final String[] MSG_PREFIXES = {"/msg ", "/tell ", "/w ", "/whisper ", "/m "};
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    private static long lastInspect;

    /**
     * Suggestions arrive on the network thread. Observations are queued here and drained on the
     * client thread, so the module, history and HUD are never touched off-thread.
     */
    private static final java.util.ArrayDeque<StaffObservation> PENDING = new java.util.ArrayDeque<>();

    private MsgCompletionObserver() {
    }

    /** Client thread: hands queued observations to the module. */
    public static void drain(StaffDetector module) {
        if (module == null) return;
        while (true) {
            StaffObservation observation;
            synchronized (PENDING) {
                observation = PENDING.pollFirst();
            }
            if (observation == null) return;
            module.handle(observation);
        }
    }

    public static void inspect(CommandSuggestionsS2CPacket packet) {
        if (packet == null) return;
        try {
            StaffDetector module = ModuleManager.getModule(StaffDetector.class);
            if (module == null || !module.isEnabled()) return;
            if (!module.checkMsgCompletions.isEnabled()) return;
            if (!(mc.currentScreen instanceof ChatScreen chatScreen)) return;

            String typed = ((ChatScreenAccessor) chatScreen).anormal$chatField().getText();
            if (typed == null) return;
            String lower = typed.toLowerCase(Locale.ROOT);
            boolean msgCommand = false;
            for (String prefix : MSG_PREFIXES) {
                if (lower.startsWith(prefix)) {
                    msgCommand = true;
                    break;
                }
            }
            if (!msgCommand) return;

            long now = System.currentTimeMillis();
            if (now - lastInspect < 500L) return;
            lastInspect = now;

            String server = StaffTracker.serverKey();
            Suggestions suggestions = packet.getSuggestions();
            if (suggestions == null) return;
            for (Suggestion suggestion : suggestions.getList()) {
                if (suggestion == null) continue;
                String name = suggestion.getText();
                if (name == null || name.isBlank()) continue;
                StaffMatcher.Match match = StaffMatcher.match(null, name.trim(),
                        module.matchByUuid.isEnabled(),
                        module.matchByUsername.isEnabled(),
                        module.allowUsernameOnly.isEnabled(),
                        module.useServerLists.isEnabled(),
                        server);
                if (match == null) continue;
                if (!module.msgCompletionAlerts.isEnabled()) continue;
                StaffObservation observation = new StaffObservation(null, name.trim(),
                        StaffDetectionType.MSG_COMPLETION, null, -1.0, StaffConfidence.LOW, now,
                        false, true, match.entry.displayName(), server);
                synchronized (PENDING) {
                    if (PENDING.size() < 16) PENDING.addLast(observation);
                }
            }
        } catch (Throwable ignored) {
            // A malformed packet or a screen mid-close must never break the network handler.
        }
    }
}
