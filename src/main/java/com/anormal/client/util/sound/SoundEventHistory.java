package com.anormal.client.util.sound;

import net.minecraft.util.math.Vec3d;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Bounded, pausable history of detected events for the history panel. */
public final class SoundEventHistory {

    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss",
            Locale.ROOT);

    public static final class Entry {
        public final String time;
        public final String name;
        public final SoundClass soundClass;
        public final String distance;
        public final Vec3d position;
        public final int score;

        Entry(String name, SoundClass soundClass, String distance, Vec3d position, int score) {
            this.time = LocalTime.now().format(CLOCK);
            this.name = name;
            this.soundClass = soundClass;
            this.distance = distance;
            this.position = position;
            this.score = score;
        }
    }

    private final ArrayDeque<Entry> entries = new ArrayDeque<>();

    public void add(SoundEventData data, int maxSize, boolean paused) {
        if (paused) return;
        entries.addFirst(new Entry(data.name, data.soundClass, data.distanceText(), data.position,
                data.score));
        while (entries.size() > Math.max(1, maxSize)) entries.removeLast();
    }

    public List<Entry> view(int maxSize, String filter, SoundClass category) {
        List<Entry> out = new ArrayList<>();
        String needle = filter == null ? "" : filter.trim().toLowerCase(Locale.ROOT);
        for (Entry entry : entries) {
            if (out.size() >= maxSize) break;
            if (category != null && entry.soundClass != category) continue;
            if (!needle.isEmpty() && !entry.name.toLowerCase(Locale.ROOT).contains(needle)) continue;
            out.add(entry);
        }
        return out;
    }

    public int size() {
        return entries.size();
    }

    public void clear() {
        entries.clear();
    }
}
