package com.anormal.client.module.impl.spectre;

import net.minecraft.network.packet.Packet;

import java.util.ArrayList;
import java.util.List;

/**
 * Central outbound/outgoing packet dispatch point for the Spectre section.
 *
 * <p>NetworkMixin calls {@link #onSend} at the HEAD of
 * {@code ClientCommonNetworkHandler.sendPacket}. Every Spectre packet module
 * registers itself here on enable and removes itself on disable, mirroring
 * Spectre's {@code PacketSendEvent} listeners.</p>
 */
public final class SpectrePackets {
    public static final class Holder {
        public boolean cancelled;

        public void setCancelled(boolean cancelled) {
            this.cancelled = cancelled;
        }
    }

    /**
     * A module-side packet send hook.
     *
     * @return a replacement packet to send instead ({@code null} = send unchanged).
     *         Call {@link Holder#setCancelled(boolean)} to stop the packet completely.
     */
    public interface SendHook {
        Packet<?> onSend(Packet<?> packet, Holder holder);
    }

    private static final List<SendHook> hooks = new ArrayList<>();
    private static final List<SendHook> view = new ArrayList<>();

    private SpectrePackets() {}

    public static void addHooks(SendHook... array) {
        synchronized (hooks) {
            for (SendHook hook : array) if (!hooks.contains(hook)) hooks.add(hook);
            rebuildView();
        }
    }

    public static void removeHook(SendHook hook) {
        synchronized (hooks) {
            hooks.remove(hook);
            rebuildView();
        }
    }

    private static void rebuildView() {
        view.clear();
        view.addAll(hooks);
    }

    /**
     * Called from NetworkMixin on EVERY outgoing packet.
     *
     * @return the replacement packet to send, or {@code null}:
     *         cuccunchanged / cancelled semantics via the holder.
     */
    public static Packet<?> onSend(Packet<?> packet, boolean[] cancelledOut) {
        try {
            for (SendHook hook : view) {
                Holder holder = new Holder();
                Packet<?> replacement = hook.onSend(packet, holder);
                if (holder.cancelled) {
                    cancelledOut[0] = true;
                    return null;
                }
                if (replacement != null) return replacement;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }
}
