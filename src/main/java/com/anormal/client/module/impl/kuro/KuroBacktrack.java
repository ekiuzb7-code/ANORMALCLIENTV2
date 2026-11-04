package com.anormal.client.module.impl.kuro;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.module.impl.client.Friends;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;

import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntityPositionS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityPositionSyncS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityS2CPacket;
import net.minecraft.util.math.Box;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Ported one-to-one from kuro (dev.kuro.client.module.impl.Backtrack).
 *
 * <p>Backtrack. Holds back the server's position updates for nearby players for a short time
 * (Delay), so the players you see and aim at are where they were a moment ago. The held updates
 * are handed to the game in their original order once the delay has passed, the player moves out
 * of Range, or the module is switched off, so nobody is ever lost or left in the wrong place.</p>
 *
 * <p>Only incoming movement of OTHER players is delayed (move, teleport and position-sync
 * packets). Nothing about your own packets changes. Hooks live in
 * {@code KuroBacktrackPacketMixin} (ClientPlayNetworkHandler).</p>
 *
 * <p>Mode "Ghost hitbox" (default): nothing is held back, so players move on screen exactly as
 * normal, but the box your crosshair aims at stays where that player was Delay ms ago. This is
 * purely client side: it changes what YOU can click on. The server still decides whether a hit
 * counts. The aim hook is {@code KuroBacktrackGhostBoxMixin} (ProjectileUtil.raycast); the
 * position history is recorded here every tick.</p>
 */
public class KuroBacktrack extends Module {
    private static KuroBacktrack instance;

    private final ModeSetting mode = new ModeSetting("Mode",
            "Ghost hitbox = keep the crosshair box Delay ms in the past. Delay packets = hold the server's position updates.",
            "Ghost hitbox", "Ghost hitbox", "Delay packets");
    private final NumberSetting delay = new NumberSetting("Delay (ms)",
            "How far back the players you aim at lag", 100.0, 20.0, 500.0, 10.0);
    private final NumberSetting range = new NumberSetting("Range",
            "Only nearby players are delayed", 6.0, 3.0, 12.0, 0.5);
    private final BooleanSetting skipFriends = new BooleanSetting("Skip friends",
            "Do not delay or ghost friends", true);

    private record Held(ClientPlayNetworkHandler listener, Packet<?> packet, int id, long at) {}
    private record Snap(long t, double x, double y, double z) {}

    /** Ghost mode: where each nearby player was over the last Delay + 300 ms, oldest first. */
    private static final Map<Integer, ArrayDeque<Snap>> HISTORY = new HashMap<>();

    private static final ArrayDeque<Held> QUEUE = new ArrayDeque<>();
    private static boolean releasing;   // true while WE hand a held packet back, so the mixin lets it through

    public KuroBacktrack() {
        super("Backtrack (kuro)", "Delays or ghost-lags enemy positions (kuro port)", Category.KURO);
        instance = this;
        addSetting(mode);
        addSetting(delay);
        addSetting(range);
        addSetting(skipFriends);
    }

    /** Cheap check for the packet mixin (runs for every movement packet): mode is Delay packets and we are on the game thread. */
    public static boolean active() {
        KuroBacktrack b = instance;
        return b != null && b.isEnabled() && b.mode.is("Delay packets")
                && !releasing && mc.isOnThread();
    }

    private boolean ghostMode() { return mode.is("Ghost hitbox"); }

    /**
     * Ghost mode: the box of this player where it was Delay ms ago (the box the crosshair uses),
     * or null when the entity should use its real box (module off, other mode, not a tracked
     * player, no history yet).
     */
    public static Box ghostBox(Entity e) {
        KuroBacktrack b = instance;
        if (b == null || !b.isEnabled() || !b.ghostMode() || !(e instanceof PlayerEntity)
                || e == mc.player) return null;
        ArrayDeque<Snap> q = HISTORY.get(e.getId());
        if (q == null || q.isEmpty()) return null;
        long cutoff = System.currentTimeMillis() - (long) b.delay.getAsDouble();
        Snap pick = null;
        for (Snap s : q) {                       // newest snapshot that is at least Delay old
            if (s.t() <= cutoff) pick = s; else break;
        }
        if (pick == null) pick = q.peekFirst();  // not enough history yet: the oldest we have
        return e.getBoundingBox().offset(pick.x() - e.getX(), pick.y() - e.getY(), pick.z() - e.getZ());
    }

    /** Once per client tick: remember where every tracked player is right now. */
    private void recordHistory() {
        if (mc.world == null || mc.player == null || !ghostMode()) { HISTORY.clear(); return; }
        long now = System.currentTimeMillis(), keep = (long) delay.getAsDouble() + 300L;
        double inR = range.getValue() * range.getValue(), outR = Math.pow(range.getValue() + 2.0, 2);
        HISTORY.keySet().removeIf(id -> {        // left range / died / left the world
            Entity e = mc.world.getEntityById(id);
            return !(e instanceof PlayerEntity p) || !p.isAlive() || mc.player.squaredDistanceTo(p) > outR;
        });
        for (PlayerEntity p : mc.world.getPlayers()) {
            if (p == mc.player || !p.isAlive() || p.isSpectator()) continue;
            if (skipFriends.isEnabled() && Friends.isFriend(p.getName().getString())) {
                HISTORY.remove(p.getId()); continue;
            }
            if (mc.player.squaredDistanceTo(p) > inR) continue;
            ArrayDeque<Snap> q = HISTORY.computeIfAbsent(p.getId(), k -> new ArrayDeque<>());
            Snap last = q.peekLast();
            if (last != null && Math.abs(last.x() - p.getX()) + Math.abs(last.y() - p.getY())
                    + Math.abs(last.z() - p.getZ()) > 8.0) q.clear();   // teleport: no ghost across it
            q.addLast(new Snap(now, p.getX(), p.getY(), p.getZ()));
            while (q.size() > 1 && now - q.peekFirst().t() > keep) q.pollFirst();
        }
    }

    /**
     * Called for each incoming entity-position packet on the game thread. Returns true = the
     * packet was taken and the caller must cancel it (it comes back through release()).
     */
    public static boolean intercept(ClientPlayNetworkHandler listener, Packet<?> packet, int id) {
        if (!active() || mc.world == null || mc.player == null) return false;
        if (!holdingFor(id) && !relevant(id)) return false;   // keep order: once an entity has a held packet, all its later ones queue too
        QUEUE.add(new Held(listener, packet, id, System.currentTimeMillis()));
        return true;
    }

    private static boolean holdingFor(int id) {
        for (Held h : QUEUE) if (h.id == id) return true;
        return false;
    }

    /** A player other than you, within Range (and not a protected friend). */
    private static boolean relevant(int id) {
        if (mc.world == null || mc.player == null) return false;
        Entity e = mc.world.getEntityById(id);
        if (!(e instanceof PlayerEntity p) || p == mc.player || !p.isAlive()) return false;
        if (instance.skipFriends.isEnabled() && Friends.isFriend(p.getName().getString())) return false;
        double r = instance.range.getValue();
        return mc.player.squaredDistanceTo(p) <= r * r;
    }

    @Override
    public void onEnable() {
        QUEUE.clear();
        HISTORY.clear();
    }

    @Override
    public void onDisable() {
        flush();
        HISTORY.clear();
    }

    @Override
    public void onTickAlways() {
        boolean enabled = isEnabled();
        if (enabled) releaseAged();
        if (enabled) recordHistory(); else HISTORY.clear();
    }

    /** Hands every held packet whose delay has passed (or whose player left range) back to the game, in order. */
    private static void releaseAged() {
        if (mc.world == null || mc.player == null) { QUEUE.clear(); return; }
        long now = System.currentTimeMillis(), maxAge = (long) instance.delay.getAsDouble();
        Iterator<Held> it = QUEUE.iterator();
        // The queue is in arrival order, so for any one entity its packets are released oldest first.
        while (it.hasNext()) {
            Held h = it.next();
            // out of range (+2 blocks so it does not flicker at the edge): its updates go through at once
            Entity e = mc.world.getEntityById(h.id());
            boolean gone = !(e instanceof PlayerEntity)
                    || mc.player.squaredDistanceTo(e) > Math.pow(instance.range.getValue() + 2.0, 2);
            if (gone || now - h.at() >= maxAge) { it.remove(); release(h); }
        }
    }

    /** Hands every held packet back to the game, in order. */
    public static void flush() {
        Held h;
        while ((h = QUEUE.poll()) != null) release(h);
    }

    private static void release(Held h) {
        releasing = true;
        try {
            if (h.packet() instanceof EntityS2CPacket m) h.listener().onEntity(m);
            else if (h.packet() instanceof EntityPositionS2CPacket t) h.listener().onEntityPosition(t);
            else if (h.packet() instanceof EntityPositionSyncS2CPacket s) h.listener().onEntityPositionSync(s);
        } catch (RuntimeException ignored) {
            // the entity can be gone by now; nothing to apply
        } finally {
            releasing = false;
        }
    }
}
