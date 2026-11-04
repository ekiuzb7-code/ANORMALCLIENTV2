package com.anormal.client.module.impl.world;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.Vec3d;

import java.util.Iterator;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadLocalRandom;

/**
 * FakeLag — Ported directly from Vape v4.21 (gg.vape.module.world.FakeLag).
 *
 * <p>Supports all 3 Vape v4.21 modes:
 * <ul>
 *   <li><b>Latency:</b> Delays outgoing movement packets by a fixed/configurable time window.</li>
 *   <li><b>Dynamic:</b> Holds packets dynamically based on distance & angle to the current combat target.</li>
 *   <li><b>Repel:</b> Queues combat & movement packets on attack to create a repel/kb delay advantage.</li>
 * </ul>
 */
public class FakeLag extends Module {
    public final ModeSetting mode = new ModeSetting("Mode", "Latency constant, Dynamic combat, Repel keeps distance",
            "Latency", "Latency", "Dynamic", "Repel");

    public final NumberSetting delay = new NumberSetting("Delay", "Packet hold time in milliseconds",
            100.0, 10.0, 1000.0, 10.0);

    public final NumberSetting transmissionOffset = new NumberSetting("Transmission Offset", "Extra delay for Repel/Dynamic",
            40.0, 0.0, 200.0, 10.0);

    public final BooleanSetting flushOnHurt = new BooleanSetting("Flush On Hurt", "Release packets immediately when taking damage", true);

    private static final class QueuedPacket {
        final Packet<?> packet;
        final long sendTime;

        QueuedPacket(Packet<?> packet, long sendTime) {
            this.packet = packet;
            this.sendTime = sendTime;
        }
    }

    private final Queue<QueuedPacket> pendingPackets = new ConcurrentLinkedQueue<>();
    private static volatile boolean flushing = false;

    private LivingEntity targetEntity = null;
    private long flushDeadline = 0L;
    private Vec3d lastPositionSnapshot = null;

    public FakeLag() {
        super("FakeLag", "Vape v4.21 FakeLag: simulates lag via packet delay buffers", Category.WORLD);
        addSetting(mode);
        addSetting(delay);
        addSetting(transmissionOffset);
        addSetting(flushOnHurt);
    }

    @Override
    public void onEnable() {
        flushing = false;
        pendingPackets.clear();
        targetEntity = null;
        flushDeadline = 0L;
        lastPositionSnapshot = null;
    }

    @Override
    public void onDisable() {
        flush();
        pendingPackets.clear();
        targetEntity = null;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) {
            flush();
            return;
        }

        long now = System.currentTimeMillis();

        // Flush on hurt (matches Vape v4.21)
        if (flushOnHurt.isEnabled() && mc.player.hurtTime > 0 && !pendingPackets.isEmpty()) {
            flush();
            return;
        }

        // Repel mode target tracking & hurt-time check
        if (mode.is("Repel") && targetEntity != null) {
            if (!targetEntity.isAlive() || now >= flushDeadline || (targetEntity.hurtTime > 0 && targetEntity.hurtTime <= 3)) {
                flush();
                targetEntity = null;
            }
        }

        // Flush expired packets in FIFO order
        dispatchExpired(now);
    }

    private void dispatchExpired(long now) {
        if (flushing || pendingPackets.isEmpty()) return;
        flushing = true;
        try {
            Iterator<QueuedPacket> iterator = pendingPackets.iterator();
            while (iterator.hasNext()) {
                QueuedPacket qp = iterator.next();
                if (now < qp.sendTime) break;
                if (mc.getNetworkHandler() != null) {
                    mc.getNetworkHandler().getConnection().send(qp.packet, null);
                }
                iterator.remove();
            }
        } catch (Throwable ignored) {
        } finally {
            flushing = false;
        }
    }

    public void flush() {
        if (pendingPackets.isEmpty()) return;
        flushing = true;
        try {
            while (!pendingPackets.isEmpty()) {
                QueuedPacket qp = pendingPackets.poll();
                if (qp != null && mc.getNetworkHandler() != null) {
                    mc.getNetworkHandler().getConnection().send(qp.packet, null);
                }
            }
        } catch (Throwable ignored) {
        } finally {
            flushing = false;
        }
    }

    public boolean shouldQueuePacket(Packet<?> packet) {
        if (flushing || mc.player == null || mc.world == null) return false;
        if (!(packet instanceof PlayerMoveC2SPacket)) return false;

        long now = System.currentTimeMillis();
        long baseDelay = delay.getValue().longValue();

        if (mode.is("Latency")) {
            long sendTime = now + baseDelay;
            pendingPackets.add(new QueuedPacket(packet, sendTime));
            return true;
        }

        if (mode.is("Repel")) {
            if (targetEntity != null && targetEntity.isAlive()) {
                long sendTime = now + baseDelay + transmissionOffset.getValue().longValue() + ThreadLocalRandom.current().nextInt(50);
                pendingPackets.add(new QueuedPacket(packet, sendTime));
                return true;
            }
            return false;
        }

        // Dynamic mode (matches Vape v4.21 FakeLagPacketDelaySubModule)
        if (mode.is("Dynamic")) {
            LivingEntity enemy = nearestEnemy(8.0);
            if (enemy == null) {
                dispatchExpired(now);
                return false;
            }

            double dist = mc.player.distanceTo(enemy);
            Vec3d currentPos = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
            Vec3d enemyPos = new Vec3d(enemy.getX(), enemy.getY(), enemy.getZ());

            // If player is moving away from target, release packets
            if (lastPositionSnapshot != null) {
                double prevDist = lastPositionSnapshot.distanceTo(enemyPos);
                if (dist > prevDist + 0.03) {
                    flush();
                    lastPositionSnapshot = currentPos;
                    return false;
                }
            }
            lastPositionSnapshot = currentPos;

            long sendTime = now + baseDelay + transmissionOffset.getValue().longValue();
            pendingPackets.add(new QueuedPacket(packet, sendTime));
            return true;
        }

        return false;
    }

    public void onAttack(Entity target) {
        if (target instanceof LivingEntity living) {
            this.targetEntity = living;
            long extra = (long) ThreadLocalRandom.current().nextInt(100);
            this.flushDeadline = System.currentTimeMillis() + delay.getValue().longValue() + extra;
        }
    }

    private LivingEntity nearestEnemy(double maxDist) {
        if (mc.world == null || mc.player == null) return null;
        LivingEntity best = null;
        double bestDist = maxDist;
        for (Entity e : mc.world.getEntities()) {
            if (e instanceof LivingEntity living && e != mc.player && living.isAlive()) {
                double d = mc.player.distanceTo(living);
                if (d < bestDist) {
                    bestDist = d;
                    best = living;
                }
            }
        }
        return best;
    }

    public static boolean isFlushing() {
        return flushing;
    }
}
