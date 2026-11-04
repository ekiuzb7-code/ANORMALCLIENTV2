package com.anormal.client.module.impl.world;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.ColorUtils;
import com.anormal.client.util.ProjectionUtil;
import com.anormal.client.util.RenderUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntityPositionS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * BackTrack — Ported directly from Vape v4.21 (gg.vape.module.blatant.Backtrack).
 *
 * <p>Delays incoming movement packets of the target enemy, allowing you to hit
 * their delayed server position hitbox. Renders the server-side position with smooth interpolation.</p>
 */
public class BackTrack extends Module {
    public final NumberSetting minLatency = new NumberSetting("Min Latency", "Minimum backtrack delay in ms", 50.0, 0.0, 500.0, 10.0);
    public final NumberSetting maxLatency = new NumberSetting("Max Latency", "Maximum backtrack delay in ms", 150.0, 10.0, 500.0, 10.0);
    public final BooleanSetting renderServerPos = new BooleanSetting("Render Server Pos", "Render backtracked enemy hitbox", true);
    public final ColorSetting color = new ColorSetting("Color", "Backtracked position color", ColorUtils.rgba(5, 134, 105, 120));

    private final Map<Packet<?>, Long> queuedPackets = new LinkedHashMap<>();

    private int activeTargetId = -1;
    private LivingEntity targetEntity = null;
    private long lastAttackTime = 0L;

    // Smooth render position interpolation matching Vape v4.21
    private double currentRenderX, currentRenderY, currentRenderZ;
    private double previousRenderX, previousRenderY, previousRenderZ;
    private double targetRenderX, targetRenderY, targetRenderZ;
    private int interpolationSteps = 0;
    private boolean isPacketFlushing = false;

    public BackTrack() {
        super("BackTrack", "Vape v4.21 Backtrack: delays enemy movement packets", Category.WORLD);
        addSetting(minLatency);
        addSetting(maxLatency);
        addSetting(renderServerPos);
        addSetting(color);
    }

    @Override
    public void onEnable() {
        flushQueuedPackets();
        activeTargetId = -1;
        targetEntity = null;
        interpolationSteps = 0;
    }

    @Override
    public void onDisable() {
        flushQueuedPackets();
        activeTargetId = -1;
        targetEntity = null;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) {
            flushQueuedPackets();
            activeTargetId = -1;
            targetEntity = null;
            return;
        }

        // Interpolate render position (matches Vape v4.21 EventPreTick)
        this.previousRenderX = this.currentRenderX;
        this.previousRenderY = this.currentRenderY;
        this.previousRenderZ = this.currentRenderZ;

        if (this.activeTargetId != -1 && this.interpolationSteps > 0) {
            this.currentRenderX += (this.targetRenderX - this.currentRenderX) / this.interpolationSteps;
            this.currentRenderY += (this.targetRenderY - this.currentRenderY) / this.interpolationSteps;
            this.currentRenderZ += (this.targetRenderZ - this.currentRenderZ) / this.interpolationSteps;
            this.interpolationSteps--;
        }

        // Release target if invalid or out of reach
        if (targetEntity != null) {
            if (!targetEntity.isAlive() || mc.player.distanceTo(targetEntity) > 6.5
                    || System.currentTimeMillis() - lastAttackTime > 2500L) {
                flushQueuedPackets();
                activeTargetId = -1;
                targetEntity = null;
            }
        }

        // Dispatch expired packets
        dispatchExpired(System.currentTimeMillis());
    }

    public void onAttack(LivingEntity target) {
        if (target != null && target != mc.player && target.isAlive()) {
            this.targetEntity = target;
            this.activeTargetId = target.getId();
            this.lastAttackTime = System.currentTimeMillis();
            this.resetRenderPosition(target.getX(), target.getY(), target.getZ());
        }
    }

    public boolean shouldQueueIncomingPacket(Packet<?> packet) {
        if (isPacketFlushing || mc.player == null || mc.world == null) return false;
        if (activeTargetId == -1 || targetEntity == null) return false;

        // Player velocity / knockback packet flushes backtrack (matches Vape v4.21)
        if (packet instanceof EntityVelocityUpdateS2CPacket vel) {
            if (vel.getEntityId() == mc.player.getId()) {
                flushQueuedPackets();
                return false;
            }
        }

        int entityId = -1;
        Vec3d newPos = null;

        if (packet instanceof EntityPositionS2CPacket posPacket) {
            entityId = posPacket.entityId();
            newPos = posPacket.change().position();
        } else if (packet instanceof EntityS2CPacket entPacket) {
            Entity ent = entPacket.getEntity(mc.world);
            if (ent != null) {
                entityId = ent.getId();
            }
        }

        if (entityId == activeTargetId) {
            if (newPos != null) {
                this.targetRenderX = newPos.x;
                this.targetRenderY = newPos.y;
                this.targetRenderZ = newPos.z;
                this.interpolationSteps = 3;
            }

            long min = minLatency.getValue().longValue();
            long max = Math.max(min, maxLatency.getValue().longValue());
            long latency = min + (max > min ? ThreadLocalRandom.current().nextLong(max - min) : 0);
            long releaseAt = System.currentTimeMillis() + latency;

            queuedPackets.put(packet, releaseAt);
            return true;
        }

        return false;
    }

    public void queueIncoming(Packet<?> packet) {
        // Already recorded in shouldQueueIncomingPacket
    }

    private void dispatchExpired(long now) {
        if (isPacketFlushing || queuedPackets.isEmpty() || mc.getNetworkHandler() == null) return;
        isPacketFlushing = true;
        try {
            Iterator<Map.Entry<Packet<?>, Long>> it = queuedPackets.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<Packet<?>, Long> entry = it.next();
                if (now < entry.getValue()) break;

                try {
                    @SuppressWarnings("unchecked")
                    Packet<net.minecraft.network.listener.ClientPlayPacketListener> p =
                            (Packet<net.minecraft.network.listener.ClientPlayPacketListener>) entry.getKey();
                    p.apply(mc.getNetworkHandler());
                } catch (Throwable ignored) {}
                it.remove();
            }
        } finally {
            isPacketFlushing = false;
        }
    }

    public void flushQueuedPackets() {
        if (queuedPackets.isEmpty() || mc.getNetworkHandler() == null) {
            queuedPackets.clear();
            return;
        }
        isPacketFlushing = true;
        try {
            for (Packet<?> packet : queuedPackets.keySet()) {
                try {
                    @SuppressWarnings("unchecked")
                    Packet<net.minecraft.network.listener.ClientPlayPacketListener> p =
                            (Packet<net.minecraft.network.listener.ClientPlayPacketListener>) packet;
                    p.apply(mc.getNetworkHandler());
                } catch (Throwable ignored) {}
            }
            queuedPackets.clear();
        } finally {
            isPacketFlushing = false;
        }
    }

    private void resetRenderPosition(double x, double y, double z) {
        this.currentRenderX = this.previousRenderX = this.targetRenderX = x;
        this.currentRenderY = this.previousRenderY = this.targetRenderY = y;
        this.currentRenderZ = this.previousRenderZ = this.targetRenderZ = z;
        this.interpolationSteps = 0;
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (!renderServerPos.isEnabled() || activeTargetId == -1 || targetEntity == null || !targetEntity.isAlive()) return;

        double renderX = previousRenderX + (currentRenderX - previousRenderX) * tickDelta;
        double renderY = previousRenderY + (currentRenderY - previousRenderY) * tickDelta;
        double renderZ = previousRenderZ + (currentRenderZ - previousRenderZ) * tickDelta;

        float width = targetEntity.getWidth();
        float height = targetEntity.getHeight();
        Box box = new Box(renderX - width / 2.0, renderY, renderZ - width / 2.0,
                renderX + width / 2.0, renderY + height, renderZ + width / 2.0);

        // Project and draw outline
        int col = color.getValue();
        Vec3d[] corners = {
                new Vec3d(box.minX, box.minY, box.minZ),
                new Vec3d(box.maxX, box.minY, box.minZ),
                new Vec3d(box.maxX, box.minY, box.maxZ),
                new Vec3d(box.minX, box.minY, box.maxZ),
                new Vec3d(box.minX, box.maxY, box.minZ),
                new Vec3d(box.maxX, box.maxY, box.minZ),
                new Vec3d(box.maxX, box.maxY, box.maxZ),
                new Vec3d(box.minX, box.maxY, box.maxZ)
        };

        int[][] sc = new int[8][];
        for (int i = 0; i < 8; i++) {
            sc[i] = ProjectionUtil.project(corners[i], tickDelta);
        }

        int[][] edges = {{0, 1}, {1, 2}, {2, 3}, {3, 0}, {4, 5}, {5, 6}, {6, 7}, {7, 4}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};
        for (int[] edge : edges) {
            int[] a = sc[edge[0]], b = sc[edge[1]];
            if (a != null && b != null) {
                RenderUtils.drawLine(context, a[0], a[1], b[0], b[1], col, 1.5f);
            }
        }
    }
}
