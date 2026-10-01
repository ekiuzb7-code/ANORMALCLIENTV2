package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;

public class KillAura extends Module {
    public final NumberSetting targetRange = new NumberSetting("Target Range", "Attack range in blocks", 4.0, 1.0, 7.0, 0.5);
    public final NumberSetting cps = new NumberSetting("CPS", "Attacks per second", 10.0, 1.0, 20.0, 1.0);
    public final BooleanSetting playersOnly = new BooleanSetting("Players Only", "Only attack players", true);
    private int tickCounter = 0;

    public KillAura() {
        super("KillAura", "Attacks nearest entity in range at set CPS", Category.UZNY11);
        addSetting(targetRange);
        addSetting(cps);
        addSetting(playersOnly);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null || mc.currentScreen != null) return;
        try {
            LivingEntity best = null;
            double bestDist = Double.MAX_VALUE;
            for (Entity e : mc.world.getEntities()) {
                if (!(e instanceof LivingEntity le) || e == mc.player || !le.isAlive()) continue;
                if (playersOnly.isEnabled() && !(le instanceof PlayerEntity)) continue;
                double d = mc.player.distanceTo(le);
                if (d > targetRange.getValue() || d > bestDist) continue;
                bestDist = d;
                best = le;
            }
            if (best == null) {
                tickCounter = 0;
                return;
            }
            int interval = Math.max(1, 20 / Math.max(1, cps.getValue().intValue()));
            if (tickCounter++ < interval) return;
            tickCounter = 0;
            mc.interactionManager.attackEntity(mc.player, best);
            mc.player.swingHand(Hand.MAIN_HAND);
        } catch (Throwable ignored) {}
    }
}
