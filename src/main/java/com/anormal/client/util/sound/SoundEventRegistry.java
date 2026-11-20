package com.anormal.client.util.sound;

import com.anormal.client.mixin.EntityTrackingSoundInstanceAccessor;
import com.anormal.client.mixin.MovingMinecartSoundInstanceAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.EntityTrackingSoundInstance;
import net.minecraft.client.sound.MovingMinecartSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.entity.Entity;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.sound.SoundCategory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * Thread-safe hand-off between the sound thread and the detector module.
 *
 * <p>{@code SoundManagerMixin} calls {@link #capture(SoundInstance)} for every instance the
 * client actually plays, which is the only place where a sound is guaranteed to have been
 * received and accepted by the client. The queue is bounded, so a spammy farm cannot grow the
 * heap; the module drains it on the client thread and does all world/entity work there.</p>
 */
public final class SoundEventRegistry {

    /** Hard cap so a broken server cannot flood memory before the next tick drains the queue. */
    private static final int MAX_QUEUED = 512;

    public static final class RawSound {
        public final Identifier id;
        public final SoundCategory category;
        public final double x;
        public final double y;
        public final double z;
        public final float volume;
        public final float pitch;
        public final boolean relative;
        public final long time;
        public final Entity entity;

        RawSound(Identifier id, SoundCategory category, double x, double y, double z,
                 float volume, float pitch, boolean relative, long time, Entity entity) {
            this.id = id;
            this.category = category;
            this.x = x;
            this.y = y;
            this.z = z;
            this.volume = volume;
            this.pitch = pitch;
            this.relative = relative;
            this.time = time;
            this.entity = entity;
        }
    }

    private static final Object LOCK = new Object();
    private static final ArrayDeque<RawSound> QUEUE = new ArrayDeque<>();
    /** Signature of the last queued instance, so two capture hooks never report the same play twice. */
    private static String lastSignature = "";
    private static long lastSignatureTime;

    private SoundEventRegistry() {
    }

    public static void capture(SoundInstance sound) {
        if (sound == null) return;
        try {
            Identifier id = sound.getId();
            if (id == null) return;
            long now = System.currentTimeMillis();
            String signature = signature(id, sound.getX(), sound.getY(), sound.getZ());
            RawSound raw = new RawSound(id, sound.getCategory(), sound.getX(), sound.getY(),
                    sound.getZ(), sound.getVolume(), sound.getPitch(), sound.isRelative(),
                    now, entityOf(sound));
            synchronized (LOCK) {
                // SoundManager and SoundSystem both report the same instance; keep one of them.
                if (signature.equals(lastSignature) && now - lastSignatureTime < 60L) return;
                lastSignature = signature;
                lastSignatureTime = now;
                if (QUEUE.size() < MAX_QUEUED) QUEUE.add(raw);
            }
        } catch (Throwable ignored) {
        }
    }

    /** Capture from a received sound packet (client thread). */
    public static void capturePacket(Identifier id, SoundCategory category, double x, double y, double z,
                                     float volume, float pitch, Entity entity) {
        if (id == null) return;
        RawSound raw = new RawSound(id, category, x, y, z, volume, pitch, false,
                System.currentTimeMillis(), entity);
        synchronized (LOCK) {
            if (QUEUE.size() < MAX_QUEUED) QUEUE.add(raw);
        }
    }

    /** Client thread: takes up to {@code max} captured sounds. */
    public static List<RawSound> drain(int max) {
        List<RawSound> out = new ArrayList<>(Math.min(max, 32));
        synchronized (LOCK) {
            while (out.size() < max && !QUEUE.isEmpty()) out.add(QUEUE.poll());
        }
        return out;
    }

    public static void clear() {
        synchronized (LOCK) {
            QUEUE.clear();
        }
    }

    /**
     * Resolves the entity behind a played sound.
     *
     * <p>{@link EntityTrackingSoundInstance} is the normal case (player steps, mob sounds).
     * {@link MovingMinecartSoundInstance} is the rolling minecart loop: it is a
     * {@code MovingSoundInstance}, so it is <em>not</em> entity tracking and reports itself as
     * relative, yet its {@code minecart} field is the real source. Reading it here is what makes a
     * moving minecart visible to the detector at all. Anything else stays an unknown source instead
     * of guessing.</p>
     */
    private static Entity entityOf(SoundInstance sound) {
        try {
            if (sound instanceof EntityTrackingSoundInstance tracking) {
                return ((EntityTrackingSoundInstanceAccessor) tracking).anormal$trackedEntity();
            }
            if (sound instanceof MovingMinecartSoundInstance cartSound) {
                return ((MovingMinecartSoundInstanceAccessor) cartSound).anormal$minecart();
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    /**
     * Fallback for a moving-loop sound that arrives without a readable owner: picks the minecart the
     * sound most likely belongs to. The reported position wins while it is plausible; otherwise the
     * nearest cart around the player is used, so a listener-relative coordinate never invents a
     * position on the other side of the world.
     */
    public static Entity findMovingOwner(Identifier id, Vec3d position) {
        if (id == null || !id.getPath().contains("minecart")) return null;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null || client.player == null) return null;
        try {
            Box area = client.player.getBoundingBox().expand(96.0);
            AbstractMinecartEntity best = null;
            double bestScore = Double.MAX_VALUE;
            for (AbstractMinecartEntity cart : client.world.getEntitiesByClass(
                    AbstractMinecartEntity.class, area, cart -> true)) {
                double toReported = position == null ? Double.MAX_VALUE : cart.squaredDistanceTo(position);
                double toPlayer = cart.squaredDistanceTo(client.player);
                double score = toReported <= 32.0 * 32.0 ? toReported : toPlayer + 1.0E6;
                if (score < bestScore) {
                    bestScore = score;
                    best = cart;
                }
            }
            return best;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String signature(Identifier id, double x, double y, double z) {
        return id + "|" + Math.round(x * 16.0) + "," + Math.round(y * 16.0) + "," + Math.round(z * 16.0);
    }
}
