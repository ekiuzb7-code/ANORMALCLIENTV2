package com.anormal.client.util.sound;

import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Duplicate suppression and per-event cooldowns.
 *
 * <p>Minecraft frequently plays several sounds for one action (chest open + container click,
 * footsteps per block). Without this every action would produce a burst of cards. The key is the
 * sound id plus the block-rounded position inside a short window, exactly as the spec suggests.</p>
 */
public final class SoundDeduplicator {

    private static final int MAX_ENTRIES = 1024;

    private final Map<String, Long> lastSeen = new HashMap<>();
    private final Map<String, Long> lastFired = new HashMap<>();

    public static String key(String id, Vec3d position) {
        if (position == null) return id + "|unknown";
        return id + "|" + Math.round(position.x) + "," + Math.round(position.y) + ","
                + Math.round(position.z);
    }

    /** True when the same id at the same rounded position fired inside the window. */
    public boolean duplicate(String key, long now, long windowMs) {
        if (windowMs <= 0L) return false;
        Long previous = lastSeen.get(key);
        if (previous != null && now - previous < windowMs) return true;
        lastSeen.put(key, now);
        pruneIfNeeded();
        return false;
    }

    /** True when this sound id fired inside the per-event cooldown. */
    public boolean cooling(String id, long now, long cooldownMs) {
        if (cooldownMs <= 0L) return false;
        Long previous = lastFired.get(id);
        if (previous != null && now - previous < cooldownMs) return true;
        lastFired.put(id, now);
        return false;
    }

    public void clear() {
        lastSeen.clear();
        lastFired.clear();
    }

    private void pruneIfNeeded() {
        if (lastSeen.size() <= MAX_ENTRIES) return;
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, Long>> it = lastSeen.entrySet().iterator();
        while (it.hasNext()) {
            if (now - it.next().getValue() > 10_000L) it.remove();
        }
        if (lastSeen.size() > MAX_ENTRIES) lastSeen.clear();
    }
}
