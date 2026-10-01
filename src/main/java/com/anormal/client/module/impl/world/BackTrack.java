package com.anormal.client.module.impl.world;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayDeque;
import java.util.Deque;

public class BackTrack extends Module {
    public final NumberSetting latency = new NumberSetting("Latency", "Target position delay in ms", 100.0, 20.0, 500.0, 10.0);
    public final BooleanSetting renderServerPos = new BooleanSetting("Render Server Pos", "Keep delayed server-side position buffer", true);
    public final ColorSetting color = new ColorSetting("Color", "Server position marker color", 0xFFFF5555);

    private static final class TrackPoint {
        final double x, y, z;
        final long time;
        TrackPoint(double x, double y, double z, long time) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.time = time;
        }
    }

    private final Deque<TrackPoint> buffer = new ArrayDeque<>();
    private int targetId = -1;

    public BackTrack() {
        super("BackTrack", "Buffers target positions for latency advantage (packet-free estimate)", Category.WORLD);
        addSetting(latency);
        addSetting(renderServerPos);
        addSetting(color);
    }

    @Override
    public void onEnable() {
        buffer.clear();
        targetId = -1;
    }

    @Override
    public void onDisable() {
        buffer.clear();
        targetId = -1;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;
        LivingEntity target = null;
        if (mc.targetedEntity instanceof LivingEntity living && living.isAlive()) {
            target = living;
        } else {
            double best = 6.0;
            for (var e : mc.world.getEntities()) {
                if (!(e instanceof LivingEntity living) || living == mc.player || !living.isAlive()) continue;
                double d = mc.player.distanceTo(living);
                if (d < best) {
                    best = d;
                    target = living;
                }
            }
        }
        if (target == null) {
            targetId = -1;
            if (!buffer.isEmpty()) buffer.clear();
            return;
        }
        if (target.getId() != targetId) {
            targetId = target.getId();
            buffer.clear();
        }
        buffer.addLast(new TrackPoint(target.getX(), target.getY(), target.getZ(), System.currentTimeMillis()));
        long window = Math.max(20L, latency.getValue().longValue());
        while (buffer.size() > 2) {
            TrackPoint first = buffer.peekFirst();
            if (first == null) break;
            if (System.currentTimeMillis() - first.time <= window) break;
            buffer.removeFirst();
        }
        if (!renderServerPos.isEnabled()) {
            while (buffer.size() > 5) buffer.removeFirst();
        }
    }

    public Vec3d getDelayedPos() {
        TrackPoint first = buffer.peekFirst();
        if (first == null) return null;
        return new Vec3d(first.x, first.y, first.z);
    }
}
