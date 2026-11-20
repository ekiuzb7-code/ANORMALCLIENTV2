package com.anormal.client.util.staff;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Bounded, filterable evidence log. Stores only detection metadata, never chat contents. */
public final class StaffHistory {

    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ROOT);

    public static final class Record {
        public final String time;
        public final String name;
        public final String uuid;
        public final String type;
        public final String evidence;
        public final String distance;
        public final String position;
        public final String confidence;
        public final String matchStatus;
        public final String server;

        Record(StaffObservation observation) {
            this.time = LocalTime.now().format(CLOCK);
            this.name = observation.displayName();
            this.uuid = observation.uuid == null ? "" : observation.uuid;
            this.type = observation.type.name();
            this.evidence = observation.evidenceSource;
            this.distance = observation.distanceText();
            this.position = observation.positionText();
            this.confidence = observation.confidence.display();
            this.matchStatus = observation.staffListMatched ? "Staff list match" : "Not matched";
            this.server = observation.serverKey;
        }
    }

    private final ArrayDeque<Record> records = new ArrayDeque<>();

    public void add(StaffObservation observation, int maxSize, boolean enabled) {
        if (!enabled || observation == null) return;
        records.addFirst(new Record(observation));
        while (records.size() > Math.max(10, maxSize)) records.removeLast();
    }

    public List<Record> view(String query, String type, int max) {
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<Record> out = new ArrayList<>();
        for (Record record : records) {
            if (out.size() >= max) break;
            if (type != null && !type.equals("All") && !record.type.equalsIgnoreCase(type)) continue;
            if (!needle.isEmpty()) {
                String haystack = (record.name + " " + record.uuid + " " + record.type + " "
                        + record.evidence).toLowerCase(Locale.ROOT);
                if (!haystack.contains(needle)) continue;
            }
            out.add(record);
        }
        return out;
    }

    public String toCsv() {
        StringBuilder sb = new StringBuilder("time,name,uuid,type,evidence,distance,position,confidence,match,server\n");
        for (Record record : records) {
            sb.append(csv(record.time)).append(',').append(csv(record.name)).append(',')
                    .append(csv(record.uuid)).append(',').append(csv(record.type)).append(',')
                    .append(csv(record.evidence)).append(',').append(csv(record.distance)).append(',')
                    .append(csv(record.position)).append(',').append(csv(record.confidence)).append(',')
                    .append(csv(record.matchStatus)).append(',').append(csv(record.server)).append('\n');
        }
        return sb.toString();
    }

    private static String csv(String value) {
        String text = value == null ? "" : value;
        if (text.contains(",") || text.contains("\"") || text.contains("\n")) {
            return '"' + text.replace("\"", "\"\"") + '"';
        }
        return text;
    }

    public int size() {
        return records.size();
    }

    public void clear() {
        records.clear();
    }
}
