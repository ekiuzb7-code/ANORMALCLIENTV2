package com.anormal.client.util.staff;

import java.util.ArrayList;
import java.util.List;

/**
 * One staff-list entry.
 *
 * <p>The UUID is optional until the client actually observes the account; it is never invented.
 * A blank {@code serverKey} means the entry is global.</p>
 */
public final class StaffEntry {

    public String username = "";
    public String uuid = "";
    public String group = "";
    public String status = StaffStatus.CONFIRMED.name();
    public String notes = "";
    public List<StaffEvidence> evidence = new ArrayList<>();
    public long firstObserved;
    public long lastObserved;
    public String serverKey = "";
    public boolean uuidVerified;

    public StaffEntry() {
    }

    public StaffEntry(String username) {
        this.username = username == null ? "" : username;
    }

    public StaffStatus statusEnum() {
        return StaffStatus.byName(status);
    }

    public boolean hasUuid() {
        return uuid != null && !uuid.isBlank();
    }

    public boolean isGlobal() {
        return serverKey == null || serverKey.isBlank();
    }

    /** Global entries apply everywhere; scoped entries only on their own server. */
    public boolean matchesServer(String key) {
        if (isGlobal()) return true;
        return key != null && serverKey.equalsIgnoreCase(key);
    }

    public String displayName() {
        if (username != null && !username.isBlank()) return username;
        if (hasUuid()) return uuid;
        return "Unknown";
    }

    public void addEvidence(StaffEvidence item) {
        if (item == null) return;
        if (evidence == null) evidence = new ArrayList<>();
        evidence.add(item);
        while (evidence.size() > 50) evidence.remove(0);
    }
}
