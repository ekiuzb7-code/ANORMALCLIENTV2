package com.anormal.client.util.staff;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Persistent user-maintained staff list.
 *
 * <p>Stored as JSON next to the client config, exactly like the other Anormal stores. Every entry is
 * validated before it is accepted, a malformed file never replaces a valid list, and nothing is
 * ever uploaded anywhere.</p>
 */
public final class StaffListManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type LIST_TYPE = new TypeToken<List<StaffEntry>>() {
    }.getType();

    private static final List<StaffEntry> ENTRIES = new ArrayList<>();
    private static boolean loaded;
    private static boolean dirty;

    private StaffListManager() {
    }

    public static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("anormalclient").resolve("staff-list.json");
    }

    public static synchronized void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        try {
            Path path = file();
            if (!Files.exists(path)) return;
            List<StaffEntry> parsed = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), LIST_TYPE);
            if (parsed == null) return;
            List<StaffEntry> good = new ArrayList<>();
            for (StaffEntry entry : parsed) {
                if (valid(entry)) good.add(entry);
            }
            ENTRIES.clear();
            ENTRIES.addAll(good);
        } catch (Throwable ignored) {
            // A broken file must never crash the client or wipe the list held in memory.
        }
    }

    private static boolean valid(StaffEntry entry) {
        if (entry == null) return false;
        boolean hasName = entry.username != null && !entry.username.isBlank();
        boolean hasUuid = entry.uuid != null && !entry.uuid.isBlank();
        if (!hasName && !hasUuid) return false;
        if (entry.evidence == null) entry.evidence = new ArrayList<>();
        if (entry.serverKey == null) entry.serverKey = "";
        if (entry.group == null) entry.group = "";
        if (entry.notes == null) entry.notes = "";
        if (entry.status == null || entry.status.isBlank()) entry.status = StaffStatus.CONFIRMED.name();
        return true;
    }

    public static synchronized List<StaffEntry> all() {
        ensureLoaded();
        return ENTRIES;
    }

    public static synchronized List<StaffEntry> view(String query, String serverKey, boolean serverScoped) {
        ensureLoaded();
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<StaffEntry> out = new ArrayList<>();
        for (StaffEntry entry : ENTRIES) {
            if (serverScoped && !entry.matchesServer(serverKey)) continue;
            if (!needle.isEmpty()) {
                String haystack = (entry.displayName() + " " + entry.uuid + " " + entry.group
                        + " " + entry.notes).toLowerCase(Locale.ROOT);
                if (!haystack.contains(needle)) continue;
            }
            out.add(entry);
        }
        return out;
    }

    public static synchronized void add(StaffEntry entry) {
        ensureLoaded();
        if (!valid(entry)) return;
        ENTRIES.add(entry);
        save();
    }

    public static synchronized void remove(StaffEntry entry) {
        ensureLoaded();
        if (ENTRIES.remove(entry)) save();
    }

    public static synchronized void markDirty() {
        dirty = true;
    }

    /** Batched write, so observing a player each tick does not hammer the disk. */
    public static synchronized void flush() {
        if (dirty) save();
    }

    public static synchronized void save() {
        ensureLoaded();
        try {
            Path path = file();
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(ENTRIES, LIST_TYPE), StandardCharsets.UTF_8);
            dirty = false;
        } catch (Throwable ignored) {
        }
    }

    /** Records a real client-side observation: first/last seen, and the UUID once it is known. */
    public static synchronized void observe(StaffEntry entry, UUID uuid, long now) {
        if (entry == null) return;
        if (uuid != null && !entry.hasUuid()) entry.uuid = uuid.toString();
        if (entry.firstObserved == 0L) entry.firstObserved = now;
        entry.lastObserved = now;
        dirty = true;
    }

    public static synchronized String exportJson() {
        ensureLoaded();
        return GSON.toJson(ENTRIES, LIST_TYPE);
    }

    /**
     * @return number of imported entries, or {@code -1} when the payload was not a valid list
     */
    public static synchronized int importJson(String json) {
        if (json == null || json.isBlank()) return -1;
        try {
            List<StaffEntry> parsed = GSON.fromJson(json, LIST_TYPE);
            if (parsed == null) return -1;
            List<StaffEntry> good = new ArrayList<>();
            for (StaffEntry entry : parsed) {
                if (valid(entry)) good.add(entry);
            }
            if (good.isEmpty()) return -1;
            ENTRIES.clear();
            ENTRIES.addAll(good);
            save();
            return good.size();
        } catch (Throwable ignored) {
            return -1;
        }
    }
}
