package com.anormal.client.util.staff;

/** Evidence confidence levels from the Staff Detector spec. */
public enum StaffConfidence {
    HIGH("High", 3),
    MEDIUM("Medium", 2),
    LOW("Low", 1),
    UNCONFIRMED("Unconfirmed", 0);

    private final String display;
    private final int rank;

    StaffConfidence(String display, int rank) {
        this.display = display;
        this.rank = rank;
    }

    public String display() {
        return display;
    }

    public int rank() {
        return rank;
    }

    public boolean atLeast(StaffConfidence other) {
        return this.rank >= other.rank;
    }

    public static StaffConfidence byName(String value) {
        if (value == null) return MEDIUM;
        for (StaffConfidence confidence : values()) {
            if (confidence.name().equalsIgnoreCase(value) || confidence.display.equalsIgnoreCase(value)) {
                return confidence;
            }
        }
        return MEDIUM;
    }
}
