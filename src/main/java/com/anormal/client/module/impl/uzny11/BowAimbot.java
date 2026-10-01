package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

public class BowAimbot extends Module {
    public final NumberSetting strength = new NumberSetting("Strength", "Aim assist strength", 3.0, 0.5, 10.0, 0.5);
    public final NumberSetting range = new NumberSetting("Range", "Target range in blocks", 30.0, 5.0, 60.0, 1.0);

    public BowAimbot() {
        super("BowAimbot", "Aims at nearest entity while drawing a bow", Category.UZNY11);
        addSetting(strength);
        addSetting(range);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;
        try {
            Identifier held = Registries.ITEM.getId(mc.player.getMainHandStack().getItem());
            if (held == null || !held.getPath().contains("bow")) return;
            if (!mc.player.isUsingItem()) return;
            LivingEntity best = null;
            double bestDist = Double.MAX_VALUE;
            for (Entity e : mc.world.getEntities()) {
                if (e instanceof LivingEntity le && e != mc.player && le.isAlive()) {
                    double d = mc.player.distanceTo(le);
                    if (d > range.getValue() || d > bestDist) continue;
                    bestDist = d;
                    best = le;
                }
            }
            if (best == null) return;
            double px = mc.player.getX();
            double py = mc.player.getY() + mc.player.getStandingEyeHeight();
            double pz = mc.player.getZ();
            double tx = best.getX() - px;
            double ty = (best.getY() + best.getHeight() * 0.75) - py;
            double tz = best.getZ() - pz;
            double h = Math.sqrt(tx * tx + tz * tz);
            float yaw = (float) Math.toDegrees(Math.atan2(tz, tx)) - 90.0f;
            float pitch = (float) -Math.toDegrees(Math.atan2(ty, h));
            float yawDiff = MathHelper.wrapDegrees(yaw - mc.player.getYaw());
            float pitchDiff = MathHelper.wrapDegrees(pitch - mc.player.getPitch());
            float stepYaw = strength.getValue().floatValue() * 0.7f;
            float stepPitch = strength.getValue().floatValue() * 0.5f;
            mc.player.setYaw(mc.player.getYaw() + MathHelper.clamp(yawDiff, -stepYaw, stepYaw));
            mc.player.setPitch(mc.player.getPitch() + MathHelper.clamp(pitchDiff, -stepPitch, stepPitch));
        } catch (Throwable ignored) {}
    }
}
