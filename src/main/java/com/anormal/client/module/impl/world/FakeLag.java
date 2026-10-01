package com.anormal.client.module.impl.world;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;

public class FakeLag extends Module {
    public final ModeSetting mode = new ModeSetting("Mode", "How packet delay is simulated", "Latency", "Latency", "Dynamic", "Repel");
    public final NumberSetting delay = new NumberSetting("Delay", "Base delay in milliseconds", 100.0, 20.0, 500.0, 10.0);
    public final NumberSetting transmissionOffset = new NumberSetting("Transmission Offset", "Extra hold for Repel mode", 40.0, 0.0, 200.0, 10.0);

    private int phase = 0;
    private boolean holding = true;

    public FakeLag() {
        super("FakeLag", "Simulates lag by delaying position updates (packet-free estimate)", Category.WORLD);
        addSetting(mode);
        addSetting(delay);
        addSetting(transmissionOffset);
    }

    @Override
    public void onEnable() {
        phase = 0;
        holding = true;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;
        int holdTicks = holdTicks();
        int releaseTicks = Math.max(2, holdTicks / 2);
        if (++phase >= (holding ? holdTicks : releaseTicks)) {
            phase = 0;
            holding = !holding;
        }
        if (holding) {
            try {
                mc.player.setVelocity(new Vec3d(0.0, mc.player.getVelocity().y * 0.2, 0.0));
                mc.player.fallDistance = 0.0f;
            } catch (Throwable ignored) {}
        }
    }

    private int holdTicks() {
        int base = Math.max(1, (int) (delay.getValue() / 50.0));
        try {
            if (mode.is("Latency")) return base;
            LivingEntity near = nearestEnemy(8.0);
            if (mode.is("Dynamic")) {
                // Closer enemy = longer hold (combat advantage simulation)
                if (near == null) return Math.max(1, base / 2);
                double d = mc.player.distanceTo(near);
                double factor = Math.max(0.4, Math.min(1.6, 1.6 - d / 8.0));
                return Math.max(1, (int) (base * factor));
            }
            // Repel: hold only while enemy close, plus offset
            if (near == null || mc.player.distanceTo(near) > 6.0) return 1;
            return base + Math.max(0, (int) (transmissionOffset.getValue() / 50.0));
        } catch (Throwable ignored) {
            return base;
        }
    }

    private LivingEntity nearestEnemy(double range) {
        LivingEntity best = null;
        double bestDist = range;
        try {
            for (Entity e : mc.world.getEntities()) {
                if (e instanceof LivingEntity living && e != mc.player && living.isAlive()) {
                    double d = mc.player.distanceTo(living);
                    if (d < bestDist) {
                        bestDist = d;
                        best = living;
                    }
                }
            }
        } catch (Throwable ignored) {}
        return best;
    }
}
