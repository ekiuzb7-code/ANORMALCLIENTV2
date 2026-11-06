package com.anormal.client.module.impl.spectre;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;
import net.minecraft.world.GameMode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Spectre port: "Staff List" — lists online staff and flags people who drop off TAB
 * while the client can still see them.
 *
 * <p>Spectre also renders a HUD widget, plays a chime and tracks "region" switches from
 * gamemode packets. This port keeps the classification (online / vanished / spectating),
 * the join-leave notifications and the on-screen list, scanning the tab list every ten
 * ticks and matching names against the same built-in staff list.</p>
 */
public final class SpectreStaffList extends Module {
    private static final int SCAN_INTERVAL_TICKS = 10;
    private static final int MAX_ROWS = 8;

    private static final List<String> STAFF = List.of(
            "FluffyMaster07", "archivePedro", "Munkerlich", "Frenk_Btw", "Napooo_",
            "auzzitech", "CryptoDaveYT", "W1zox_", "ZEEF69", "showered",
            "CaptainMoose35", "BobIsFound", "NoahvdAa", "0GSummer", "LzouzMP5",
            "Pastagamer08", "u_vv", "Owen1212055", "Splaterd", "Fallerfly");

    private static final Set<String> STAFF_KEYS = Set.copyOf(STAFF.stream()
            .map(name -> name.toLowerCase(Locale.ROOT)).toList());

    public enum Presence {
        ONLINE, SPECTATOR, VANISHED
    }

    public final BooleanSetting notify = addSetting(new BooleanSetting("Notify", "Chat message when a staff member joins or leaves", true));
    public final BooleanSetting showHud = addSetting(new BooleanSetting("HUD", "Show the staff list on screen", true));
    public final BooleanSetting regionAlert = addSetting(new BooleanSetting("Region Alert", "Warn when staff drop off TAB or switch to spectator", true));

    private final Map<UUID, Entry> tracked = new LinkedHashMap<>();
    private int ticksToScan;

    private static final class Entry {
        final UUID id;
        String name;
        Presence presence = Presence.ONLINE;
        int offTabScans;
        boolean spectating;

        Entry(UUID id) {
            this.id = id;
        }
    }

    public SpectreStaffList() {
        super("SpectreStaffList", "Spectre port: lists online staff and flags vanish behaviour.", Category.SPECTRE);
    }

    @Override
    public void onEnable() {
        tracked.clear();
        ticksToScan = 0;
    }

    @Override
    public void onDisable() {
        tracked.clear();
    }

    public static boolean isStaffName(String name) {
        return name != null && STAFF_KEYS.contains(name.toLowerCase(Locale.ROOT));
    }

    @Override
    public void onTick() {
        var handler = mc.getNetworkHandler();
        var player = mc.player;
        if (handler == null || player == null || mc.world == null) {
            tracked.clear();
            return;
        }
        if (--ticksToScan > 0) return;
        ticksToScan = SCAN_INTERVAL_TICKS;

        java.util.Set<UUID> listed = new java.util.HashSet<>();
        for (PlayerListEntry info : handler.getListedPlayerListEntries()) {
            if (info.getProfile() != null && info.getProfile().id() != null) {
                listed.add(info.getProfile().id());
            }
        }

        java.util.Set<UUID> seen = new java.util.HashSet<>();
        for (PlayerListEntry info : handler.getPlayerList()) {
            if (info.getProfile() == null) continue;
            String name = info.getProfile().name();
            UUID id = info.getProfile().id();
            if (name == null || id == null || id.equals(player.getUuid())) continue;
            if (!isStaffName(name)) continue;

            seen.add(id);
            Entry entry = tracked.get(id);
            boolean fresh = entry == null;
            if (fresh) {
                entry = new Entry(id);
                tracked.put(id, entry);
            }
            Presence before = entry.presence;
            entry.name = name;
            entry.spectating = info.getGameMode() == GameMode.SPECTATOR;
            entry.presence = entry.spectating ? Presence.SPECTATOR
                    : (listed.contains(id) ? Presence.ONLINE : Presence.VANISHED);
            entry.offTabScans = 0;

            if (fresh) {
                if (notify.isEnabled()) message(name + " joined (" + label(entry.presence) + ")");
            } else if (before != entry.presence && notify.isEnabled()) {
                message(name + " is now " + label(entry.presence));
            }
        }

        for (var it = tracked.values().iterator(); it.hasNext(); ) {
            Entry entry = it.next();
            if (seen.contains(entry.id)) continue;
            if (presentInWorld(entry.id)) {
                entry.offTabScans++;
                if (entry.offTabScans >= 2 && entry.presence != Presence.VANISHED) {
                    entry.presence = Presence.VANISHED;
                    if (regionAlert.isEnabled()) message(entry.name + " vanished (off TAB, still nearby)");
                }
            } else {
                it.remove();
                if (notify.isEnabled()) message(entry.name + " left");
            }
        }
    }

    private boolean presentInWorld(UUID id) {
        for (var entity : mc.world.getPlayers()) {
            if (entity.getUuid().equals(id)) return true;
        }
        return false;
    }

    private void message(String text) {
        if (mc.inGameHud == null) return;
        mc.inGameHud.getChatHud().addMessage(Text.literal("[SpectreStaffList] " + text));
    }

    private static String label(Presence presence) {
        return switch (presence) {
            case SPECTATOR -> "spectating";
            case VANISHED -> "vanished";
            default -> "online";
        };
    }

    private static int colour(Presence presence) {
        return switch (presence) {
            case SPECTATOR -> 0xFFFFC44D;
            case VANISHED -> 0xFFFF6666;
            default -> 0xFF7CE87C;
        };
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (!showHud.isEnabled() || tracked.isEmpty() || mc.player == null) return;
        if (mc.options.hudHidden) return;

        List<Entry> rows = new ArrayList<>(tracked.values());
        rows.sort((a, b) -> {
            int ga = group(a), gb = group(b);
            if (ga != gb) return Integer.compare(ga, gb);
            return a.name.compareToIgnoreCase(b.name);
        });
        int shown = Math.min(rows.size(), MAX_ROWS);
        int y = 8;
        for (int i = 0; i < shown; i++) {
            Entry entry = rows.get(i);
            String line = entry.name + "  " + label(entry.presence);
            context.drawText(mc.textRenderer, line, 8, y, colour(entry.presence), true);
            y += mc.textRenderer.fontHeight + 2;
        }
        if (rows.size() > shown) {
            context.drawText(mc.textRenderer, "+" + (rows.size() - shown) + " more", 8, y, 0xFFAAAAAA, true);
        }
    }

    /** Vanished first, then spectating, then online — the order players care about. */
    private static int group(Entry entry) {
        return switch (entry.presence) {
            case VANISHED -> 0;
            case SPECTATOR -> 1;
            default -> 2;
        };
    }
}
