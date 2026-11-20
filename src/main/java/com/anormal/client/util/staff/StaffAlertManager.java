package com.anormal.client.util.staff;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Turns accepted observations into HUD alerts.
 *
 * <p>An alert is only created on a state change (first observation of a type for an identity) or
 * after the per-identity cooldown, so a player standing still cannot spam the panel every tick.</p>
 */
public final class StaffAlertManager {

    public static final class Alert {
        public final StaffObservation observation;
        public final long created;

        Alert(StaffObservation observation, long created) {
            this.observation = observation;
            this.created = created;
        }

        /** "Staff member" for a staff-list match, "Unknown identity" otherwise. */
        public String prefix() {
            return observation.staffListMatched ? "Staff member" : "Unknown identity";
        }

        public String detail() {
            return observation.type.detail();
        }

        public int accent() {
            if (observation.type == StaffDetectionType.SPECTATOR) return 0xFFF87171;
            if (!observation.staffListMatched) return 0xFF60A5FA;
            return 0xFFFBBF24;
        }
    }

    private final List<Alert> alerts = new ArrayList<>();
    private final Map<String, Long> lastAlert = new HashMap<>();
    private final Set<String> states = new HashSet<>();

    public boolean submit(StaffObservation observation, long cooldownMs, boolean repeatOnStateChange) {
        if (observation == null) return false;
        String state = observation.identityKey() + "|" + observation.type.name() + "|" + observation.serverKey;
        boolean changed = states.add(state);
        long now = observation.timestamp;
        if (!changed) {
            if (!repeatOnStateChange) return false;
            Long previous = lastAlert.get(state);
            if (previous != null && now - previous < cooldownMs) return false;
        }
        lastAlert.put(state, now);
        alerts.add(0, new Alert(observation, now));
        while (alerts.size() > 12) alerts.remove(alerts.size() - 1);
        return true;
    }

    public void tick(long maxAgeMs) {
        long now = System.currentTimeMillis();
        alerts.removeIf(alert -> now - alert.created > maxAgeMs);
        if (states.size() > 512) states.clear();
        if (lastAlert.size() > 512) lastAlert.clear();
    }

    public List<Alert> active() {
        return alerts;
    }

    public void clear() {
        alerts.clear();
        states.clear();
        lastAlert.clear();
    }
}
