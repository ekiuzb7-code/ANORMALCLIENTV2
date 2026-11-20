package com.anormal.client.util.staff;

import java.util.UUID;

/**
 * Matches a client-visible identity against the staff list.
 *
 * <p>UUID matching always wins. An exact username match without a verified UUID is reported as
 * MEDIUM confidence and is only possible while username-only matching is allowed; similar names are
 * never merged and words such as "admin" or "mod" are never treated as evidence.</p>
 */
public final class StaffMatcher {

    public static final class Match {
        public final StaffEntry entry;
        public final StaffConfidence confidence;
        public final boolean uuidVerified;

        Match(StaffEntry entry, StaffConfidence confidence, boolean uuidVerified) {
            this.entry = entry;
            this.confidence = confidence;
            this.uuidVerified = uuidVerified;
        }
    }

    private StaffMatcher() {
    }

    public static Match match(UUID uuid, String username, boolean byUuid, boolean byUsername,
                              boolean allowUsernameOnly, boolean serverScoped, String serverKey) {
        StaffListManager.ensureLoaded();
        if (byUuid && uuid != null) {
            for (StaffEntry entry : StaffListManager.all()) {
                if (serverScoped && !entry.matchesServer(serverKey)) continue;
                String stored = entry.uuid;
                if (stored == null || stored.isBlank()) continue;
                try {
                    if (UUID.fromString(stored.trim()).equals(uuid)) {
                        return new Match(entry, StaffConfidence.HIGH, true);
                    }
                } catch (IllegalArgumentException ignored) {
                    // Malformed stored UUID: ignore it, never guess.
                }
            }
        }

        if (!byUsername || username == null || username.isBlank()) return null;
        for (StaffEntry entry : StaffListManager.all()) {
            if (serverScoped && !entry.matchesServer(serverKey)) continue;
            if (entry.username == null || entry.username.isBlank()) continue;
            if (!entry.username.equalsIgnoreCase(username)) continue;

            boolean verified = uuid != null && entry.hasUuid()
                    && entry.uuid.equalsIgnoreCase(uuid.toString());
            if (!verified && !allowUsernameOnly) return null;
            return new Match(entry, verified ? StaffConfidence.HIGH : StaffConfidence.MEDIUM, verified);
        }
        return null;
    }
}
