package com.anormal.client.module.impl.kuro;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.NumberSetting;

import net.minecraft.util.math.Vec3d;

/**
 * Ported one-to-one from kuro (dev.kuro.client.module.impl.Velocity).
 *
 * <p>Reduces knockback: between the end of one tick and the start of the next only the network
 * can change your velocity, so any jump in it is knockback (or an explosion); this scales that
 * jump by the chosen percentages.</p>
 *
 * <p>kuro runs this pre-tick on Mojmap via {@code getDeltaMovement()}; here the same comparison
 * runs on END_CLIENT_TICK (Anormal's module tick), where the packet-injected knockback is
 * already visible in the velocity, and it is scaled back to
 * {@code lastEnd + delta * percentage}.</p>
 */
public class KuroVelocity extends Module {
    private final NumberSetting horizontal = new NumberSetting("Horizontal %",
            "How much of the horizontal knockback to keep", 0.0, 0.0, 100.0, 5.0);
    private final NumberSetting vertical = new NumberSetting("Vertical %",
            "How much of the vertical knockback to keep", 0.0, 0.0, 100.0, 5.0);

    private Vec3d lastEnd = Vec3d.ZERO;

    public KuroVelocity() {
        super("Velocity", "Reduces knockback (kuro port)", Category.KURO);
        addSetting(horizontal);
        addSetting(vertical);
    }

    @Override
    public void onEnable() {
        lastEnd = mc.player != null ? mc.player.getVelocity() : Vec3d.ZERO;
    }

    @Override
    public void onDisable() {
        if (mc.player != null) lastEnd = mc.player.getVelocity();
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;
        Vec3d v = mc.player.getVelocity();
        Vec3d d = v.subtract(lastEnd);
        if (Math.hypot(d.x, d.z) < 0.08 && Math.abs(d.y) < 0.08) return; // no knockback
        double h = horizontal.getValue() / 100.0;
        double vert = vertical.getValue() / 100.0;
        mc.player.setVelocity(lastEnd.x + d.x * h, lastEnd.y + d.y * vert, lastEnd.z + d.z * h);
        lastEnd = mc.player.getVelocity();
    }
}
