package com.anormal.client.module.impl.spectre;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.ModeSetting;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerInputC2SPacket;
import net.minecraft.util.PlayerInput;

/**
 * Spectre port: "Sneak" — sneaks for you.
 *
 * <p>Spectre offers Vanilla (hold the sneak key) and Packet (sneak without the local key)
 * modes. Vanilla drives the local sneak key, so the pose and collisions stay in sync.
 * Packet mode rewrites the sneaking flag of the outgoing {@code PlayerInputC2SPacket}
 * ({@code PlayerInputC2SPacket} is what carries the shift state since 1.21.2, replacing
 * Spectre's {@code PRESS_SHIFT_KEY} command), so the server sees sneaking while the local
 * key and pose are left untouched.</p>
 */
public final class SpectreSneak extends Module {
    public final ModeSetting mode = addSetting(new ModeSetting("Mode", "How sneaking is applied", "Vanilla", "Packet", "Vanilla"));

    private boolean vanillaHeld;

    private final SpectrePackets.SendHook inputHook = (packet, holder) -> {
        if (!isEnabled() || !mode.is("Packet")) return null;
        if (!(packet instanceof PlayerInputC2SPacket inputPacket)) return null;
        PlayerInput input = inputPacket.input();
        if (input.sneak()) return null;
        return new PlayerInputC2SPacket(new PlayerInput(input.forward(), input.backward(),
                input.left(), input.right(), input.jump(), true, input.sprint()));
    };

    public SpectreSneak() {
        super("SpectreSneak", "Spectre port: sneaks for you.", Category.SPECTRE);
    }

    @Override
    public void onEnable() {
        SpectrePackets.addHooks(inputHook);
    }

    @Override
    public void onTick() {
        var player = mc.player;
        if (player == null || mc.world == null || player.getAbilities().flying) {
            release();
            return;
        }
        if (mode.is("Packet")) {
            // Packet mode never touches the local key; the send hook does the work.
            releaseKey();
        } else {
            if (!vanillaHeld) {
                mc.options.sneakKey.setPressed(true);
                vanillaHeld = true;
            }
        }
    }

    @Override
    public void onDisable() {
        SpectrePackets.removeHook(inputHook);
        releaseKey();
    }

    private void release() {
        releaseKey();
    }

    private void releaseKey() {
        if (vanillaHeld && mc.options != null) {
            mc.options.sneakKey.setPressed(false);
        }
        vanillaHeld = false;
    }
}
