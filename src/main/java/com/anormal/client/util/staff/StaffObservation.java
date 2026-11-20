package com.anormal.client.util.staff;

import net.minecraft.util.math.Vec3d;

/**
 * One evidence-based observation. Position/distance are {@code null}/{@code -1} whenever the client
 * has no reliable value - they are never fabricated.
 */
public final class StaffObservation {

    public final String uuid;
    public final String username;
    public final StaffDetectionType type;
    public final Vec3d position;
    public final double distance;
    public final String evidenceSource;
    public final StaffConfidence confidence;
    public final long timestamp;
    public final boolean identityVerified;
    public final boolean staffListMatched;
    public final String staffEntryName;
    public final String serverKey;

    public StaffObservation(String uuid, String username, StaffDetectionType type, Vec3d position,
                            double distance, StaffConfidence confidence, long timestamp,
                            boolean identityVerified, boolean staffListMatched, String staffEntryName,
                            String serverKey) {
        this.uuid = uuid;
        this.username = username;
        this.type = type;
        this.position = position;
        this.distance = distance;
        this.evidenceSource = type.evidenceSource();
        this.confidence = confidence;
        this.timestamp = timestamp;
        this.identityVerified = identityVerified;
        this.staffListMatched = staffListMatched;
        this.staffEntryName = staffEntryName;
        this.serverKey = serverKey;
    }

    /** Stable identity for deduplication: UUID when known, otherwise the exact username. */
    public String identityKey() {
        if (uuid != null && !uuid.isBlank()) return uuid.toLowerCase(java.util.Locale.ROOT);
        if (username != null && !username.isBlank()) return username.toLowerCase(java.util.Locale.ROOT);
        return "unknown";
    }

    public String displayName() {
        if (username != null && !username.isBlank()) return username;
        if (staffEntryName != null && !staffEntryName.isBlank()) return staffEntryName;
        return "Unknown";
    }

    public String distanceText() {
        if (distance < 0.0) return "Unknown";
        return String.format(java.util.Locale.ROOT, "%.1f blocks", distance);
    }

    public String positionText() {
        if (position == null) return "Unknown";
        return String.format(java.util.Locale.ROOT, "X %.0f / Y %.0f / Z %.0f",
                position.x, position.y, position.z);
    }
}
