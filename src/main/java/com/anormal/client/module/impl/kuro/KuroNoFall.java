package com.anormal.client.module.impl.kuro;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.NumberSetting;

import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;

/**
 * Ported one-to-one from kuro (dev.kuro.client.module.impl.NoFall).
 *
 * <p>Cancels fall damage: once you have fallen far enough, the server is told every tick
 * that you are on the ground, which resets its fall counter.</p>
 *
 * <p>kuro builds the "status only" packet by reflection and passes {@code onGround = true} with
 * {@code horizontalCollision = false}; on Yarn 1.21.11 the constructor shape is stable, so the packet
 * is built directly with the same two values.</p>
 */
public class KuroNoFall extends Module {
    private final NumberSetting minFall = new NumberSetting("Min fall distance",
            "Ignore falls below this distance", 2.5, 1.0, 8.0, 0.5);

    public KuroNoFall() {
        super("NoFall", "Cancels fall damage (kuro port)", Category.KURO);
        addSetting(minFall);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.getNetworkHandler() == null) return;
        if (mc.player.isCreative() || mc.player.isSpectator()
                || mc.player.getAbilities().flying || mc.player.isGliding()) return;
        if (mc.player.isTouchingWater() || mc.player.isInLava()
                || mc.player.hasVehicle() || mc.player.isClimbing()) return;
        if (mc.player.fallDistance > minFall.getValue() && mc.player.getVelocity().y < 0) {
            try {
                mc.getNetworkHandler().sendPacket(
                        new PlayerMoveC2SPacket.OnGroundOnly(true, false));
            } catch (Throwable ignored) {
            }
        }
    }
}
