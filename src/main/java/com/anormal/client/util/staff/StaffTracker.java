package com.anormal.client.util.staff;

import com.mojang.authlib.GameProfile;
import com.anormal.client.module.impl.render.StaffDetector;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

import java.util.UUID;

/**
 * Tick-driven detector. It only reads data the client already has: the tab-list entries the server
 * sent, and the player entities present in the client world. Nothing is requested from the server
 * and no hidden entity can be invented.
 *
 * <p>Detection is automatic: a rank shown in the tab list or team prefix, a spectator game mode in
 * the tab list, and invisible player entities are enough. The manual staff list only adds the
 * accounts the user explicitly wants to follow.</p>
 */
public final class StaffTracker {

    private static final MinecraftClient mc = MinecraftClient.getInstance();

    private long lastPurge;
    private long lastFlush;

    public void tick(StaffDetector module) {
        if (mc.player == null || mc.world == null) return;
        long now = System.currentTimeMillis();
        String server = serverKey();

        StaffListManager.ensureLoaded();
        scanPlayerList(module, now, server);
        scanWorld(module, now, server);

        if (now - lastFlush > 5000L) {
            lastFlush = now;
            StaffListManager.flush();
        }
        if (now - lastPurge > 60000L) {
            lastPurge = now;
            StaffListManager.ensureLoaded();
        }
    }

    private void scanPlayerList(StaffDetector module, long now, String server) {
        ClientPlayNetworkHandler handler = mc.getNetworkHandler();
        if (handler == null) return;
        boolean auto = module.autoDetect.isEnabled();
        for (PlayerListEntry entry : handler.getPlayerList()) {
            GameProfile profile = entry.getProfile();
            if (profile == null) continue;
            UUID id = profile.id();
            String name = profile.name();
            String uuid = id == null ? null : id.toString();

            GameMode mode = null;
            try {
                mode = entry.getGameMode();
            } catch (Throwable ignored) {
            }
            boolean spectator = mode == GameMode.SPECTATOR;

            StaffMatcher.Match match = match(module, id, name, server);
            if (match != null) StaffListManager.observe(match.entry, id, now);

            if (spectator && module.spectatorAlerts.isEnabled()) {
                StaffConfidence confidence = match != null ? StaffConfidence.HIGH : StaffConfidence.MEDIUM;
                if (!confidence.atLeast(module.minConfidence())) continue;
                module.handle(new StaffObservation(uuid, name, StaffDetectionType.SPECTATOR, null, -1.0,
                        confidence, now, match != null && match.uuidVerified, true,
                        match != null ? match.entry.displayName() : "spectator", server));
                continue;
            }

            if (match != null) {
                if (!module.staffAlerts.isEnabled()) continue;
                if (!match.confidence.atLeast(module.minConfidence())) continue;
                module.handle(new StaffObservation(uuid, name, StaffDetectionType.PLAYER_LIST, null, -1.0,
                        match.confidence, now, match.uuidVerified, true,
                        match.entry.displayName(), server));
                continue;
            }

            if (!auto || !module.staffAlerts.isEnabled()) continue;
            String rank = StaffRanks.rankOf(entry);
            if (rank == null) continue;
            module.handle(new StaffObservation(uuid, name, StaffDetectionType.AUTO_RANK, null, -1.0,
                    StaffConfidence.MEDIUM, now, false, true, rank, server));
        }
    }

    private void scanWorld(StaffDetector module, long now, String server) {
        double range = module.nearbyRange.getValue();
        for (AbstractClientPlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player) continue;
            GameProfile profile = player.getGameProfile();
            UUID id = profile == null ? null : profile.id();
            String name = profile == null ? null : profile.name();
            boolean invisible = player.isInvisible();
            boolean spectator = player.isSpectator();
            if (!invisible && !spectator) continue;

            double distance = mc.player.distanceTo(player);
            Vec3d position = player.getEntityPos();
            StaffMatcher.Match match = match(module, id, name, server);

            if (match != null) {
                StaffListManager.observe(match.entry, id, now);
                StaffDetectionType type = spectator
                        ? StaffDetectionType.SPECTATOR : StaffDetectionType.INVISIBLE_STAFF;
                boolean allowed = spectator ? module.spectatorAlerts.isEnabled()
                        : (module.invisibleAlerts.isEnabled() && distance <= range);
                if (!allowed) continue;
                if (!match.confidence.atLeast(module.minConfidence())) continue;
                module.handle(new StaffObservation(id == null ? null : id.toString(), name, type,
                        position, distance, match.confidence, now, match.uuidVerified, true,
                        match.entry.displayName(), server));
                continue;
            }

            if (!module.autoDetect.isEnabled()) continue;
            if (spectator && module.spectatorAlerts.isEnabled()) {
                module.handle(new StaffObservation(id == null ? null : id.toString(), name,
                        StaffDetectionType.SPECTATOR, position, distance, StaffConfidence.MEDIUM, now,
                        false, true, "spectator", server));
                continue;
            }
            if (invisible && !spectator && module.invisibleAlerts.isEnabled()
                    && module.showUnknownInvisible.isEnabled() && distance <= range) {
                module.handle(new StaffObservation(id == null ? null : id.toString(), name,
                        StaffDetectionType.INVISIBLE_AUTO, position, distance,
                        StaffConfidence.LOW, now, false, false, null, server));
            }
        }
    }

    private StaffMatcher.Match match(StaffDetector module, UUID id, String name, String server) {
        return StaffMatcher.match(id, name,
                module.matchByUuid.isEnabled(),
                module.matchByUsername.isEnabled(),
                module.allowUsernameOnly.isEnabled(),
                module.useServerLists.isEnabled(),
                server);
    }

    /** Server label used to scope staff entries; single-player and unknown are explicit. */
    public static String serverKey() {
        try {
            ServerInfo info = mc.getCurrentServerEntry();
            if (info == null) return "singleplayer";
            if (info.address == null || info.address.isBlank()) return "unknown";
            return info.address;
        } catch (Throwable ignored) {
            return "unknown";
        }
    }
}
