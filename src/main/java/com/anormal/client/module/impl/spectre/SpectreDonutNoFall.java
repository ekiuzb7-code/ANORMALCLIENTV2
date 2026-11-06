package com.anormal.client.module.impl.spectre;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;

/**
 * Spectre port: "Donut NoFall" — take no fall damage on servers that reject the classic
 * flying-on-ground spoof, by replacing your own move packets after a >3 block fall.
 */
public final class SpectreDonutNoFall extends Module implements SpectrePackets.SendHook {
    private static final double TRIGGER_FALL_DISTANCE = 3.0;
    private static final int GROUND_PACKETS_TO_RESET = 4;

    private boolean noFall;
    private double lastFallDistance;
    private int groundTicks;
    private boolean sendingReplacement;

    public SpectreDonutNoFall() {
        super("SpectreDonutNoFall", "Spectre port: take no fall damage by replacing your own move packets after a >3 block fall.", Category.SPECTRE);
    }

    @Override
    public void onEnable() {
        noFall = false;
        lastFallDistance = 0.0;
        groundTicks = 0;
        sendingReplacement = false;
        // Spectre registers its packet listener on enable; without this the hook never runs.
        SpectrePackets.addHooks(this);
    }

    @Override
    public void onTick() {
        var player = mc.player;
        if (player == null) return;
        // With the replacement regime active the client must not process this tick as landed
        // (Spectre: player.setOnGround(false) keeps the fall-tracking state frozen).
        if (noFall) {
            player.fallDistance = lastFallDistance;
        }
        if (lastFallDistance > TRIGGER_FALL_DISTANCE) {
            noFall = true;
        }
        lastFallDistance = player.fallDistance;
    }

    @Override
    public void onDisable() {
        noFall = false;
        groundTicks = 0;
        SpectrePackets.removeHook(this);
    }

    @Override
    public Packet<?> onSend(Packet<?> packet, SpectrePackets.Holder holder) {
        if (sendingReplacement || !(packet instanceof PlayerMoveC2SPacket move)) return null;
        if (mc.player == null || mc.getNetworkHandler() == null) return null;
        // Outside a >3 block fall the packet is left completely alone.
        if (!noFall && groundTicks == 0) return null;

        holder.setCancelled(true); // Spectre cancels the vanilla packet and sends its own replacement

        if (noFall && move.isOnGround()) {
            groundTicks++;
        }

        net.minecraft.client.network.ClientPlayerEntity player = mc.player;
        if (groundTicks % 2 == 1) {
            // The 1e-8 vertical nudge so the server processes a "just left the ground" state
            player.setPosition(player.getX(), player.getY() + 1.0E-8, player.getZ());
        }

        boolean onGround = !noFall && player.isOnGround();
        sendingReplacement = true;
        PlayerMoveC2SPacket.Full replacement;
        try {
            replacement = new PlayerMoveC2SPacket.Full(
                    player.getX(), player.getY(), player.getZ(),
                    player.getYaw(), player.getPitch(), onGround, false);
        } finally {
            sendingReplacement = false;
        }

        if (groundTicks == GROUND_PACKETS_TO_RESET) {
            noFall = false;
            groundTicks = 0;
        }
        return replacement; // NetworkMixin cancels the original and sends this instead
    }
}
