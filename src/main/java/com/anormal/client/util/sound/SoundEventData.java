package com.anormal.client.util.sound;

import net.minecraft.util.math.Vec3d;

import java.util.Locale;

/**
 * One normalised sound event, as required by the Sound Detector spec.
 *
 * <p>Every field is derived from information the client actually has: the sound id and category
 * come from the {@code SoundInstance}, the position from the instance, and the source only from
 * an entity the instance is tracking. Nothing is guessed - when the client does not know the
 * position or the source, {@link #position} is {@code null} and {@link #source} is
 * {@code "Unknown"}.</p>
 */
public final class SoundEventData {

    public enum Severity {
        INFO, WARNING, CRITICAL
    }

    public final String eventId;
    public final String rawSoundId;
    public final SoundClass soundClass;
    public final String name;
    /** Mutable so a moving source (minecart) can keep its marker on the entity. */
    public Vec3d position;
    public double distance;
    public final String source;
    public final String player;
    public final int score;
    public final long timestamp;
    public final Severity severity;
    public final boolean localPlayer;

    public SoundEventData(String eventId, String rawSoundId, SoundClass soundClass, String name,
                          Vec3d position, double distance, String source, String player, int score,
                          long timestamp, Severity severity, boolean localPlayer) {
        this.eventId = eventId;
        this.rawSoundId = rawSoundId;
        this.soundClass = soundClass;
        this.name = name;
        this.position = position;
        this.distance = distance;
        this.source = source;
        this.player = player;
        this.score = score;
        this.timestamp = timestamp;
        this.severity = severity;
        this.localPlayer = localPlayer;
    }

    public String distanceText() {
        if (distance < 0.0) return "Unknown";
        return String.format(Locale.ROOT, "%.0fm", distance);
    }

    public String positionText() {
        if (position == null) return "Unknown";
        return String.format(Locale.ROOT, "X %.0f / Y %.0f / Z %.0f",
                position.x, position.y, position.z);
    }

    public String sourceText() {
        if (player != null && !player.isEmpty()) return player;
        if (source != null && !source.isEmpty()) return source;
        return "Unknown";
    }
}
