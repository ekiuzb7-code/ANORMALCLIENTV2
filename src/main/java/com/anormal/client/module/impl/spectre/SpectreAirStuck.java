package com.anormal.client.module.impl.spectre;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import net.minecraft.network.packet.Packet;

/**
 * Spectre port: "Air Stuck" — freezes you in place, even mid-air.
 *
 * <p>Spectre cancels ServerboundClientTickEndPacket and every
 * ServerboundMovePlayerPacket. The Yarn equivalents are ClientTickEndC2SPacket
 * and PlayerMoveC2SPacket (+ nested variants), dispatched via SpectrePackets.</p>
 */
public final class SpectreAirStuck extends Module implements SpectrePackets.SendHook {

    public SpectreAirStuck() {
        super("SpectreAirStuck", "Spectre port: freezes you in place, even mid-air.", Category.SPECTRE);
    }

    @Override
    public void onEnable() {
        SpectrePackets.addHooks(this);
    }

    @Override
    public void onDisable() {
        SpectrePackets.removeHook(this);
    }

    @Override
    public Packet<?> onSend(Packet<?> packet, SpectrePackets.Holder holder) {
        if (packet instanceof net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket
                || packet instanceof net.minecraft.network.packet.c2s.play.ClientTickEndC2SPacket) {
            holder.setCancelled(true);
        }
        return null;
    }
}
