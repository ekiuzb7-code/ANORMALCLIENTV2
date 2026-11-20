package com.anormal.client.util.staff;

import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.scoreboard.Team;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the rank a server itself publishes about a player.
 *
 * <p>Many servers put the rank in the tab-list display name or in a scoreboard team prefix
 * ({@code [ADMIN] Steve}, {@code Owner | Alex}). Those are facts the client received; nothing is
 * guessed. The matcher only accepts whole words, so names such as {@code ModestPanda} or
 * {@code Adminium} never count as staff.</p>
 */
public final class StaffRanks {

    private static final Pattern KEYWORDS = Pattern.compile(
            "(?<![a-z])(admin|administrator|owner|moderator|helper|staff|developer|builder|curator"
                    + "|support|manager|operator|spectator|watch|vanish)(?![a-z])",
            Pattern.CASE_INSENSITIVE);

    private StaffRanks() {
    }

    /** @return the matched rank in upper case, or {@code null} when the tab list shows none. */
    public static String rankOf(PlayerListEntry entry) {
        if (entry == null) return null;
        String text = textOf(entry);
        if (text.isBlank()) return null;
        Matcher matcher = KEYWORDS.matcher(text);
        if (!matcher.find()) return null;
        return matcher.group(1).toUpperCase(Locale.ROOT);
    }

    private static String textOf(PlayerListEntry entry) {
        StringBuilder text = new StringBuilder();
        try {
            if (entry.getDisplayName() != null) {
                text.append(entry.getDisplayName().getString()).append(' ');
            }
        } catch (Throwable ignored) {
        }
        try {
            Team team = entry.getScoreboardTeam();
            if (team != null) {
                if (team.getPrefix() != null) text.append(team.getPrefix().getString()).append(' ');
                if (team.getSuffix() != null) text.append(team.getSuffix().getString());
            }
        } catch (Throwable ignored) {
        }
        return text.toString().replace('§', ' ').replaceAll("(?i)\\s+", " ").trim();
    }
}
