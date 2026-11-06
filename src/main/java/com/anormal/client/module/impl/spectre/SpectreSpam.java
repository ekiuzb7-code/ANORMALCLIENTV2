package com.anormal.client.module.impl.spectre;

import com.anormal.client.lucid.StringSetting;
import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Spectre port: "Spam" — spams specified messages in chat.
 */
public final class SpectreSpam extends Module {
    private static final int MAX_MESSAGE_LENGTH = 256;

    public final StringSetting messages = addSetting(new StringSetting(
            "Messages (separate with |)", "Messages to spam in chat", "Spectre was here!", 256));
    public final NumberSetting delay = addSetting(new NumberSetting("Delay", "Ticks between messages", 20, 0, 1200, 1));
    public final BooleanSetting disableOnLeave = addSetting(new BooleanSetting("Disable On Leave", "Turn the module off when you leave the world", true));
    public final BooleanSetting disableOnDisconnect = addSetting(new BooleanSetting("Disable On Disconnect", "Turn the module off when the connection drops", true));
    public final BooleanSetting random = addSetting(new BooleanSetting("Randomise", "Send the messages in random order", false));
    public final BooleanSetting autoSplitMessages = addSetting(new BooleanSetting("Auto Split Messages", "Split a long message across consecutive sends", false));
    public final NumberSetting splitLength = addSetting(new NumberSetting("Split Length", "Characters per split", 256, 1, 256, 1));
    public final NumberSetting autoSplitDelay = addSetting(new NumberSetting("Split Delay", "Ticks between splits", 20, 0, 200, 1));
    public final BooleanSetting bypass = addSetting(new BooleanSetting("Bypass", "Append a random suffix to bypass chat filters", false));
    public final BooleanSetting uppercase = addSetting(new BooleanSetting("Include Uppercase Characters", "Use uppercase letters in the suffix", true));
    public final NumberSetting length = addSetting(new NumberSetting("Length", "Suffix length", 16, 1, 256, 1));

    private final Random rng = new Random();
    private int messageI;
    private int timer;
    private int splitNum;
    private String text;
    private boolean resetPending;
    private boolean wasInGame;

    private String parsedRaw;
    private List<String> parsedMessages = List.of();

    public SpectreSpam() {
        super("SpectreSpam", "Spectre port: spams specified messages in chat.", Category.SPECTRE);
    }

    @Override
    public void onTickAlways() {
        // Spectre onGameLeft / onDisconnectedScreen detection: player joins -> in game; leaves -> reset
        boolean inGame = mc.player != null && mc.world != null;
        if (inGame && !wasInGame) {
            resetPending = true;
        } else if (!inGame && wasInGame) {
            if (isEnabled() && disableOnLeave.isEnabled()) {
                setEnabled(false);
            }
            resetPending = true;
        }
        // Connection dropped while still in a world: Spectre's onDisconnectedScreen path.
        if (inGame && mc.getNetworkHandler() == null && isEnabled() && disableOnDisconnect.isEnabled()) {
            setEnabled(false);
        }
        wasInGame = inGame;
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;
        if (resetPending) {
            resetPending = false;
            timer = (int) delay.getAsDouble();
            messageI = 0;
            splitNum = 0;
        }

        List<String> list = messages();
        if (list.isEmpty()) return;

        if (timer > 0) {
            timer--;
            return;
        }

        if (text == null) {
            int i;
            if (random.isEnabled()) {
                i = rng.nextInt(list.size());
            } else {
                if (messageI >= list.size()) messageI = 0;
                i = messageI++;
            }
            text = list.get(i);
            if (bypass.isEnabled()) {
                String suffix = randomSuffix((int) length.getAsDouble());
                text = text + " " + suffix;
            }
        }

        int chunk = (int) splitLength.getAsDouble();
        var connection = mc.getNetworkHandler();
        if (connection == null || mc.player == null) return;

        if (autoSplitMessages.isEnabled() && text.length() > chunk) {
            int splits = (text.length() + chunk - 1) / chunk;
            if (splitNum >= splits) splitNum = 0;

            int start = splitNum * chunk;
            int end = Math.min(start + chunk, text.length());
            sendPlayerMsg(connection, text.substring(start, end));
            splitNum = (splitNum + 1) % splits;
            timer = (int) autoSplitDelay.getAsDouble();
            if (splitNum == 0) {
                timer = (int) delay.getAsDouble();
                text = null;
            }
        } else {
            if (text.length() > MAX_MESSAGE_LENGTH) {
                text = text.substring(0, MAX_MESSAGE_LENGTH);
            }
            sendPlayerMsg(connection, text);
            timer = (int) delay.getAsDouble();
            text = null;
        }
    }

    private static void sendPlayerMsg(net.minecraft.client.network.ClientPlayNetworkHandler connection, String message) {
        mc.inGameHud.getChatHud().addToMessageHistory(message);
        if (message.startsWith("/")) {
            connection.sendChatCommand(message.substring(1));
        } else {
            connection.sendChatMessage(message);
        }
    }

    private List<String> messages() {
        String raw = messages.get();
        if (!raw.equals(parsedRaw)) {
            parsedRaw = raw;
            parsedMessages = parseMessages(raw);
        }
        return parsedMessages;
    }

    static List<String> parseMessages(String raw) {
        List<String> out = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '\\' && i + 1 < raw.length() && raw.charAt(i + 1) == '|') {
                current.append('|');
                i++;
            } else if (c == '|') {
                addEntry(out, current);
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        addEntry(out, current);
        return List.copyOf(out);
    }

    private static void addEntry(List<String> out, CharSequence entry) {
        String message = entry.toString().trim();
        if (!message.isEmpty()) out.add(message);
    }

    private String randomSuffix(int length) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            char c = (char) ('a' + rng.nextInt(26));
            if (uppercase.isEnabled() && rng.nextBoolean()) c = Character.toUpperCase(c);
            sb.append(c);
        }
        return sb.toString();
    }
}
