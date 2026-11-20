package com.anormal.client.util.sound;

import java.util.ArrayList;
import java.util.List;

/**
 * Live warning cards: grouping, per-second budget and expiry.
 *
 * <p>A card is grouped with another card of the same category inside the group window, so four
 * footsteps become one "Movement x4" card instead of four. Expired cards are dropped from the
 * list, which keeps the HUD and the world markers in sync without any extra bookkeeping.</p>
 */
public final class SoundWarningManager {

    public static final class Warning {
        public SoundEventData data;
        public int count = 1;
        public int totalScore;
        public long created;
        public long lastSeen;

        Warning(SoundEventData data, long now) {
            this.data = data;
            this.count = 1;
            this.totalScore = data.score;
            this.created = now;
            this.lastSeen = now;
        }
    }

    private final List<Warning> active = new ArrayList<>();
    private final java.util.ArrayDeque<Long> recent = new java.util.ArrayDeque<>();

    public List<Warning> active() {
        return active;
    }

    public void tick(long durationMs) {
        long now = System.currentTimeMillis();
        active.removeIf(warning -> now - warning.lastSeen > durationMs);
    }

    public void clear() {
        active.clear();
        recent.clear();
    }

    /** Sliding one-second budget; keeps a burst from filling the whole screen. */
    public boolean allow(long now, int maxPerSecond) {
        while (!recent.isEmpty() && now - recent.peekFirst() > 1000L) recent.pollFirst();
        if (recent.size() >= Math.max(1, maxPerSecond)) return false;
        recent.addLast(now);
        return true;
    }

    /**
     * The card this event would merge into, or {@code null} when it starts a new card.
     *
     * <p>Refreshing an existing card must not consume the one-second budget, otherwise ambient
     * loops (water, fire, footsteps) eat the whole budget and a block being broken never gets a
     * slot - exactly what happened with the "Everything" preset.</p>
     */
    public Warning mergeTarget(SoundEventData data, boolean grouping, long groupWindowMs) {
        if (!grouping) return null;
        long now = System.currentTimeMillis();
        for (Warning warning : active) {
            if (warning.data.soundClass != data.soundClass) continue;
            if (now - warning.lastSeen > groupWindowMs) continue;
            return warning;
        }
        return null;
    }

    /**
     * Adds or refreshes a card.
     *
     * @return the card, or {@code null} when the visible-card limit rejected it
     */
    public Warning submit(SoundEventData data, int maxVisible, boolean grouping, long groupWindowMs) {
        long now = System.currentTimeMillis();
        if (grouping) {
            for (Warning warning : active) {
                if (warning.data.soundClass != data.soundClass) continue;
                if (now - warning.lastSeen > groupWindowMs) continue;
                warning.count++;
                warning.totalScore += data.score;
                warning.lastSeen = now;
                if (data.distance >= 0.0 && (warning.data.distance < 0.0
                        || data.distance < warning.data.distance)) {
                    warning.data = data;
                }
                return warning;
            }
        }
        Warning warning = new Warning(data, now);
        active.add(0, warning);
        while (active.size() > Math.max(1, maxVisible)) active.remove(active.size() - 1);
        return warning;
    }
}
