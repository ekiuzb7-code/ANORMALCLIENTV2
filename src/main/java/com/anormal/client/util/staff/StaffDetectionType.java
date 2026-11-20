package com.anormal.client.util.staff;

/** What produced an observation. Each type has its own evidence label. */
public enum StaffDetectionType {
    PLAYER_LIST("Player list observation"),
    AUTO_RANK("Tab-list rank evidence"),
    INVISIBLE_AUTO("Client-visible invisible entity state"),
    SPECTATOR("Client-visible spectator state"),
    INVISIBLE_STAFF("Client-visible invisible entity state"),
    INVISIBLE_UNKNOWN("Client-visible invisible entity state"),
    MSG_COMPLETION("/msg completion suggestion");

    private final String evidenceSource;

    StaffDetectionType(String evidenceSource) {
        this.evidenceSource = evidenceSource;
    }

    public String evidenceSource() {
        return evidenceSource;
    }

    /** Card detail text, kept close to the reference HUD wording. */
    public String detail() {
        switch (this) {
            case SPECTATOR:
                return "Spectator evidence";
            case INVISIBLE_STAFF:
                return "Invisible nearby";
            case INVISIBLE_UNKNOWN:
                return "Identity unknown";
            case MSG_COMPLETION:
                return "Name suggestion";
            case AUTO_RANK:
                return "Rank visible in tab list";
            case INVISIBLE_AUTO:
                return "Invisible nearby";
            case PLAYER_LIST:
            default:
                return "Player info present";
        }
    }
}
