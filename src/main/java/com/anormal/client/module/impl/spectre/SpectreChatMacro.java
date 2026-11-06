package com.anormal.client.module.impl.spectre;

import com.anormal.client.lucid.StringSetting;
import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.KeybindSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.client.gui.screen.ChatScreen;

import java.util.Locale;

/**
 * Spectre port: "Chat Macro" — sends chat messages or commands with a key press.
 */
public final class SpectreChatMacro extends Module {
    public static final int MAX_MACROS = 8;
    private static final String ACTION_SEND = "Send";
    private static final String ACTION_OPEN = "Open in Chat";

    public final ModeSetting slots = addSetting(new ModeSetting("Slots", "How many macro slots", "3", "1", "2", "3", "4", "5", "6", "7", "8"));
    public final ModeSetting pressAction = addSetting(new ModeSetting("On Press", "What happens on the key", ACTION_SEND, ACTION_SEND, ACTION_OPEN));
    public final NumberSetting cooldown = addSetting(new NumberSetting("Cooldown (s)", "Minimum seconds between sends", 1.0, 1.0, 10.0, 0.5));

    public final KeybindSetting[] keys = new KeybindSetting[MAX_MACROS];
    public final StringSetting[] texts = new StringSetting[MAX_MACROS];

    private final boolean[] prevDown = new boolean[MAX_MACROS];
    private long lastSendNanos;

    public SpectreChatMacro() {
        super("SpectreChatMacro", "Spectre port: sends chat messages or commands with a key press.", Category.SPECTRE);
        for (int i = 0; i < MAX_MACROS; i++) {
            int slot = i + 1;
            keys[i] = addSetting(new KeybindSetting("Macro " + slot + " Key", "Keybind for macro " + slot, KeybindSetting.MOUSE_BASE - 1)); // NONE default below
            keys[i].setValue(-1); // unbound by default like Spectre's KeySetting(-1)
            texts[i] = addSetting(new StringSetting("Macro " + slot + " Text", "Text for macro " + slot, "", 128));
        }
    }

    @Override
    public void onTickAlways() {
        int count = slotCount();
        for (int i = 0; i < MAX_MACROS; i++) {
            int code = keys[i].getKey();
            // Edge state is tracked for every slot every tick, so opening a screen while a macro
            // key is held does not fire the macro the moment the screen closes.
            boolean down = code > 0 && keyIsDown(code);
            boolean pressed = down && !prevDown[i];
            prevDown[i] = down;
            if (!pressed || mc.currentScreen != null || mc.player == null) continue;
            if (i < count && !texts[i].get().isBlank()) send(i);
        }
    }

    private void send(int slot) {
        String text = texts[slot].get().trim();
        if (text.isEmpty() || text.equals("/")) return;
        var connection = mc.getNetworkHandler();
        if (connection == null) return;

        if (pressAction.is(ACTION_OPEN)) {
            mc.setScreen(new ChatScreen(text, false));
            return;
        }

        long now = System.nanoTime();
        long cooldownNanos = (long) (cooldown.getAsDouble() * 1.0E9);
        long left = cooldownNanos - (now - lastSendNanos);
        if (left > 0L && lastSendNanos != 0L) {
            double seconds = Math.ceil(left / 1.0E8) / 10.0;
            mc.player.sendMessage(net.minecraft.text.Text.literal(String.format(Locale.ROOT, "On cooldown, %.1fs left", seconds)), true);
            return;
        }
        lastSendNanos = now;
        mc.inGameHud.getChatHud().addToMessageHistory(text);
        if (text.startsWith("/")) {
            connection.sendChatCommand(text.substring(1));
        } else {
            connection.sendChatMessage(text);
        }
    }

    private static boolean keyIsDown(int code) {
        if (code <= 0) return false;
        try {
            if (KeybindSetting.isMouseCode(code)) {
                int button = KeybindSetting.mouseButton(code);
                return org.lwjgl.glfw.GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), button)
                        == org.lwjgl.glfw.GLFW.GLFW_PRESS;
            }
            return net.minecraft.client.util.InputUtil.isKeyPressed(mc.getWindow(), code);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private int slotCount() {
        try {
            return Integer.parseInt(slots.getValue());
        } catch (NumberFormatException e) {
            return 3;
        }
    }
}
