package com.anormal.client.util.staff;

/**
 * One piece of evidence attached to a staff-list entry.
 *
 * <p>Plain fields so it can be written to and read from JSON with Gson without any adapter.</p>
 */
public final class StaffEvidence {

    public String type = "Player list observation";
    public String description = "";
    public long timestamp;
    public String confidence = StaffConfidence.MEDIUM.name();
    public String source = "";
    public String serverKey = "";

    public StaffEvidence() {
    }

    public StaffEvidence(String type, String description, long timestamp,
                         StaffConfidence confidence, String source, String serverKey) {
        this.type = type;
        this.description = description == null ? "" : description;
        this.timestamp = timestamp;
        this.confidence = confidence == null ? StaffConfidence.MEDIUM.name() : confidence.name();
        this.source = source == null ? "" : source;
        this.serverKey = serverKey == null ? "" : serverKey;
    }
}
