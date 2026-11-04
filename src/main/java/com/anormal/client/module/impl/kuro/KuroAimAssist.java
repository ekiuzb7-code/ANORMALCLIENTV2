package com.anormal.client.module.impl.kuro;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.kuro.KuroAssistAim;
import com.anormal.client.util.kuro.KuroRotationEngine;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;

/**
 * Ported from kuro (dev.kuro.client.module.impl.AimAssist).
 *
 * <p>The aim itself (lead, smoothing, hand wobble, mouse-step quantisation) lives in
 * {@link KuroAssistAim} so that KuroSilentAura and KuroTriggerBot-class modules can use exactly
 * the same aim with a stronger pull. Applied per-frame from the HUD callback so the smoothing is
 * framerate based, like kuro's RenderEvent hook.</p>
 */
public class KuroAimAssist extends Module {
    private final NumberSetting distance = new NumberSetting("Distance",
            "Maximum target distance", 4.5, 1.0, 8.0, 0.1);
    private final NumberSetting maxAngle = new NumberSetting("Max Angle",
            "Overhead cone limit (kept for setting parity)", 90.0, 1.0, 360.0, 1.0);
    private final BooleanSetting aimVertically = new BooleanSetting("Aim Vertically",
            "Also move the pitch", false);
    private final NumberSetting horizontalSpeed = new NumberSetting("Horizontal Speed",
            "Horizontal pull speed", 5.0, 1.0, 40.0, 0.5);
    private final NumberSetting verticalSpeed = new NumberSetting("Vertical Speed",
            "Vertical pull speed", 5.0, 1.0, 40.0, 0.5);
    private final BooleanSetting players = new BooleanSetting("Players",
            "Target other players", true);
    private final BooleanSetting requireMouseDown = new BooleanSetting("Require Mouse Down",
            "Only aim while the attack button is held", true);

    private LivingEntity target;
    private final KuroAssistAim aim = new KuroAssistAim();

    public KuroAimAssist() {
        super("AimAssist", "Softly pulls your aim onto the nearest target (kuro port)", Category.KURO);
        addSetting(distance);
        addSetting(maxAngle);
        addSetting(aimVertically);
        addSetting(horizontalSpeed);
        addSetting(verticalSpeed);
        addSetting(players);
        addSetting(requireMouseDown);
    }

    @Override
    public void onEnable() { aim.reset(); }

    @Override
    public void onDisable() { target = null; aim.reset(); }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.player == null || mc.world == null) return;

        if (requireMouseDown.isEnabled() && !mc.options.attackKey.isPressed()) {
            aim.reset();
            return;
        }

        if (target == null || !target.isAlive() || mc.player.distanceTo(target) > distance.getValue()) {
            target = findTarget();
            if (target == null) {
                aim.reset();
                return;
            }
        }

        float[] d = aim.step(target, mc.player.getYaw(), mc.player.getPitch(),
                horizontalSpeed.getValue(), verticalSpeed.getValue(), aimVertically.isEnabled(), 1f, 1f);
        if (d != null) {
            // applyTrackedMouseDelta takes mouse units: yaw += x * 0.15, pitch -= y * 0.15 (pitch passed negated)
            KuroRotationEngine.PlayerMouseRotationApplier.applyTrackedMouseDelta(d[0] / 0.15f, -d[1] / 0.15f);
        }
    }

    private LivingEntity findTarget() {
        LivingEntity best = null;
        double bestDist = distance.getValue();
        for (Entity e : mc.world.getEntities()) {
            if (!(e instanceof LivingEntity le) || le == mc.player || !le.isAlive()) continue;
            if (le instanceof PlayerEntity && !players.isEnabled()) continue;
            double d = mc.player.distanceTo(le);
            if (d < bestDist) { bestDist = d; best = le; }
        }
        return best;
    }
}
