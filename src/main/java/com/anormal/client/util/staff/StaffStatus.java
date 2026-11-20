package com.anormal.client.util.staff;

import java.util.Locale;

/** Staff-list entry status, as required by the Staff Detector spec. */
public enum StaffStatus {
    CONFIRMED("Confirmed"),
    UNCONFIRMED("Unconfirmed"),
    WATCHLIST("Watchlist");

    private final String display;

    StaffStatus(String display) {
        this.display = display;
    }

    public String display() {
        return display;
    }

    public static StaffStatus byName(String value) {
        if (value == null) return CONFIRMED;
        for (StaffStatus status : values()) {
            if (status.name().equalsIgnoreCase(value) || status.display.equalsIgnoreCase(value)) {
                return status;
            }
        }
        return CONFIRMED;
    }

    public StaffStatus next() {
        StaffStatus[] all = values();
        return all[(ordinal() + 1) % all.length];
    }

    public String lower() {
        return display.toLowerCase(Locale.ROOT);
    }
}
