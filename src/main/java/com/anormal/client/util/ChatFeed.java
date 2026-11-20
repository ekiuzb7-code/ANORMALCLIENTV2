package com.anormal.client.util;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Client-side feed of every chat line the HUD receives.
 *
 * <p>{@code ChatFeedMixin} pushes the plain text of each {@code ChatHud.addMessage(Text)} call
 * here; modules such as CommandCooldowns and AuctionSniper subscribe while enabled. No packet
 * work is involved: this is exactly the text the player reads in chat, so nothing is spoofed
 * and nothing is sent to the server.</p>
 */
public final class ChatFeed {

    public interface Listener {
        void onChatLine(String plain);
    }

    private static final List<Listener> LISTENERS = new CopyOnWriteArrayList<>();

    private ChatFeed() {
    }

    public static void add(Listener listener) {
        if (listener != null && !LISTENERS.contains(listener)) LISTENERS.add(listener);
    }

    public static void remove(Listener listener) {
        if (listener != null) LISTENERS.remove(listener);
    }

    public static void onMessage(String plain) {
        if (plain == null || plain.isEmpty()) return;
        for (Listener listener : LISTENERS) {
            try {
                listener.onChatLine(plain);
            } catch (Throwable ignored) {
            }
        }
    }
}
