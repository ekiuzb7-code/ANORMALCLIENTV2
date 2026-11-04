package com.anormal.client.module.impl.kuro;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;

import net.minecraft.util.math.Vec3d;

/**
 * Ported from kuro (dev.kuro.client.module.impl.KeepSprint).
 *
 * <p>After an attack, vanilla multiplies horizontal velocity by 0.6 and may stop sprint.
 * This restores a configurable fraction of pre-hit motion and optionally keeps sprinting.
 * 0.6 = vanilla slowdown, 1.0 = no slowdown (full keep momentum).</p>
 *
 * <p>Attacks are detected without PlayersMixin/Fabric attack event: on each client tick we
 * check the literal attack press with a living target, exactly like the vanilla attack link -
 * maintaining the vanilla attack cooldown, while a swing is already triggered. Each new attack
 * (swing edge with a target in the crosshair) restores the last stable (pre-hit) velocity
 * captured on the previous tick end.</p>
 */
public class KuroKeepSprint extends Module {
    private static KuroKeepSprint instance;

    private final NumberSetting slowDownNormal = new NumberSetting("Slow down normal",
            "Motion multiplier after a normal hit (0.6 = vanilla)", 1.0, 0.6, 1.0, 0.01);
    private final NumberSetting slowDownVelocity = new NumberSetting("Slow down on velocity",
            "Motion multiplier while taking knockback", 1.0, 0.6, 1.0, 0.01);
    private final BooleanSetting keepSprintNormal = new BooleanSetting("Keep sprint normal",
            "Keep sprinting after a normal hit", true);
    private final BooleanSetting keepSprintVelocity = new BooleanSetting("Keep sprint on velocity",
            "Keep sprinting while taking knockback", true);
    private final BooleanSetting onlyInAir = new BooleanSetting("Only in air",
            "Do nothing while standing on the ground", false);

    private double preX, preZ;
    private boolean wasSprinting;
    /** Swing edge with a target entity: was swinging last tick, started swinging this tick. */
    private boolean wasSwinging;
    private boolean hadTarget;

    public KuroKeepSprint() {
        super("KeepSprint", "Restores motion after an attack (kuro port)", Category.KURO);
        instance = this;
        addSetting(slowDownNormal);
        addSetting(slowDownVelocity);
        addSetting(keepSprintNormal);
        addSetting(keepSprintVelocity);
        addSetting(onlyInAir);
    }

    @Override
    public void onEnable() {
        snapshot();
    }

    /** Restores a stable pre-hit velocity snapshot. */
    private void snapshot() {
        if (mc.player == null) return;
        Vec3d v = mc.player.getVelocity();
        preX = v.x;
        preZ = v.z;
        wasSprinting = mc.player.isSprinting();
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;

        boolean attacking = mc.options.attackKey.isPressed() && mc.targetedEntity != null;
        boolean swinging = mc.player.handSwinging;
        boolean swingEdge = swinging && !wasSwinging && attacking;

        if (swingEdge) {
            if (onlyInAir.isEnabled() && mc.player.isOnGround()) {
                snapshot();
                wasSwinging = swinging;
                hadTarget = attacking;
                return;
            }

            boolean takingKb = mc.player.hurtTime > 0;
            double factor = takingKb ? slowDownVelocity.getValue() : slowDownNormal.getValue();
            boolean sprint = takingKb ? keepSprintVelocity.isEnabled() : keepSprintNormal.isEnabled();

            // Vanilla already applied 0.6. We want final = pre * factor.
            Vec3d v = mc.player.getVelocity();
            double scale = factor / 0.6;
            // Prefer restoring from captured pre-hit motion for stability
            double nx = preX * factor;
            double nz = preZ * factor;
            // If velocity was almost zero, fall back to scaling current
            if (Math.abs(preX) < 1e-6 && Math.abs(preZ) < 1e-6) {
                nx = v.x * scale;
                nz = v.z * scale;
            }
            mc.player.setVelocity(nx, v.y, nz);

            if (sprint && wasSprinting) {
                mc.player.setSprinting(true);
            }
        }

        // The stable (pre-hit) snapshot is always from the tick without an active swing.
        if (!swingEdge) snapshot();

        wasSwinging = swinging;
        hadTarget = attacking;
    }

    public static boolean active() {
        return instance != null && instance.isEnabled();
    }
}
