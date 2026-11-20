package com.anormal.client.module.impl.player;

import com.anormal.client.lucid.StringSetting;
import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.MultiSelectSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.theme.ThemeManager;
import com.anormal.client.util.ChatFeed;
import com.anormal.client.util.HudStyle;
import com.anormal.client.util.NotificationManager;
import com.anormal.client.util.RenderUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Command cooldown tracker for SMP servers.
 *
 * <p>Servers answer a command they are not ready for with a line such as
 * "You must wait 30 seconds before using /home again" or "RTP cooldown: 1m 12s".
 * This module reads those lines from {@link ChatFeed} (the text the player already sees - no
 * packets, nothing sent), works out which command the line belongs to and starts a countdown.
 * When the countdown finishes it tells the player once (notification, chat line and an optional
 * click), so nobody has to remember whether /home is back up.</p>
 */
public class CommandCooldowns extends Module {

    private static final Pattern CLOCK = Pattern.compile("(\\d{1,2}):(\\d{2})(?::(\\d{2}))?");
    private static final Pattern UNIT = Pattern.compile(
            "(\\d+(?:\\.\\d+)?)\\s*(seconds?|secs?|sec|s|minutes?|mins?|min|m|hours?|hrs?|hr|h)(?![a-z])");
    private static final Pattern HINT = Pattern.compile(
            "(cooldown|wait|again in|try again|slow down|too fast|not ready|please wait|must wait"
                    + "|you can use|comes back|available in|remaining|resting|hold on)");

    private static final class Timer {
        final String name;
        final long totalMs;
        long endMs;
        boolean warned;
        boolean announced;

        Timer(String name, long totalMs) {
            this.name = name;
            this.totalMs = Math.max(1L, totalMs);
            this.endMs = System.currentTimeMillis() + this.totalMs;
        }

        long remaining() {
            return Math.max(0L, endMs - System.currentTimeMillis());
        }

        float fraction() {
            return Math.max(0.0f, Math.min(1.0f, remaining() / (float) totalMs));
        }
    }

    public final NumberSetting posX = new NumberSetting("Pos X", "X Position on screen", 6.0, 0.0, 1920.0, 1.0);
    public final NumberSetting posY = new NumberSetting("Pos Y", "Y Position on screen", 250.0, 0.0, 1080.0, 1.0);
    public final NumberSetting maxRows = new NumberSetting("Max Rows", "How many running timers are listed", 4.0, 1.0, 10.0, 1.0);
    public final NumberSetting warnSeconds = new NumberSetting("Warn Seconds", "Beep this many seconds before a command is ready (0 = off)", 3.0, 0.0, 30.0, 1.0);
    public final BooleanSetting hideFinished = new BooleanSetting("Hide Finished", "Hide a timer a few seconds after it reached zero", true);
    public final BooleanSetting sound = new BooleanSetting("Sound", "Click when a command becomes ready", true);
    public final BooleanSetting chatWarn = new BooleanSetting("Chat Warning", "Print a local chat line when a command becomes ready", true);
    public final BooleanSetting showTotal = new BooleanSetting("Show Total", "Draw the bar against the original cooldown length", true);
    public final MultiSelectSetting track = new MultiSelectSetting("Tracked Commands",
            "Which commands are recognised in cooldown messages",
            new String[]{"Home", "Rtp", "Tpa", "Kit", "Warp", "Shop"},
            "Home", "Rtp", "Tpa", "Kit", "Warp", "Shop", "Back", "Spawn", "Sell", "Pay", "Ah", "Sethome");
    public final StringSetting extraWords = new StringSetting("Extra Words",
            "Comma separated extra command words to recognise (e.g. mine, fish, daily)", "", 128);

    private final Map<String, Timer> timers = new LinkedHashMap<>();
    private final ChatFeed.Listener listener = this::onChatLine;
    private boolean listening;

    public CommandCooldowns() {
        super("CommandCooldowns", "Tracks /home, /rtp, /kit and other command cooldowns from chat and shows them on the HUD", Category.PLAYER);
        addSetting(posX);
        addSetting(posY);
        addSetting(maxRows);
        addSetting(warnSeconds);
        addSetting(hideFinished);
        addSetting(sound);
        addSetting(chatWarn);
        addSetting(showTotal);
        addSetting(track);
        addSetting(extraWords);
    }

    @Override
    public void onEnable() {
        if (!listening) {
            ChatFeed.add(listener);
            listening = true;
        }
    }

    @Override
    public void onDisable() {
        if (listening) {
            ChatFeed.remove(listener);
            listening = false;
        }
    }

    // ------------------------------------------------------------------
    //  Chat parsing
    // ------------------------------------------------------------------

    private void onChatLine(String plain) {
        if (plain == null || plain.isEmpty()) return;
        String lower = plain.toLowerCase(Locale.ROOT);
        if (!HINT.matcher(lower).find()) return;

        long seconds = parseDuration(lower);
        if (seconds <= 0L || seconds > 6L * 60L * 60L) return;

        String command = matchCommand(lower);
        synchronized (timers) {
            timers.put(command, new Timer(command, seconds * 1000L));
        }
    }

    private String matchCommand(String lower) {
        List<String> words = new ArrayList<>();
        List<String> selected = track.getValue();
        if (selected != null) {
            for (String option : selected) {
                if (option == null) continue;
                String word = option.toLowerCase(Locale.ROOT);
                if (word.equals("sethome")) word = "sethome";
                words.add(word);
            }
        }
        String extra = extraWords.getValue();
        if (extra != null && !extra.isBlank()) {
            for (String part : extra.split(",")) {
                String word = part.trim().toLowerCase(Locale.ROOT);
                if (!word.isEmpty()) words.add(word);
            }
        }
        for (String word : words) {
            if (lower.contains(word)) {
                return word.toUpperCase(Locale.ROOT);
            }
        }
        return "COMMAND";
    }

    /** Reads "1m 12s", "45 seconds", "01:12" or "1:02:03" into seconds. */
    private long parseDuration(String lower) {
        double total = 0.0;
        boolean any = false;
        Matcher unit = UNIT.matcher(lower);
        while (unit.find()) {
            double value;
            try {
                value = Double.parseDouble(unit.group(1));
            } catch (Throwable t) {
                continue;
            }
            String u = unit.group(2);
            if (u.startsWith("h")) total += value * 3600.0;
            else if (u.startsWith("m")) total += value * 60.0;
            else total += value;
            any = true;
        }
        if (any) return Math.round(total);

        Matcher clock = CLOCK.matcher(lower);
        if (clock.find()) {
            try {
                if (clock.group(3) != null) {
                    return Long.parseLong(clock.group(1)) * 3600L
                            + Long.parseLong(clock.group(2)) * 60L
                            + Long.parseLong(clock.group(3));
                }
                return Long.parseLong(clock.group(1)) * 60L + Long.parseLong(clock.group(2));
            } catch (Throwable ignored) {
            }
        }
        return -1L;
    }

    // ------------------------------------------------------------------
    //  Ready callbacks
    // ------------------------------------------------------------------

    @Override
    public void onTick() {
        long now = System.currentTimeMillis();
        List<Timer> finished = new ArrayList<>();
        synchronized (timers) {
            for (Timer timer : new ArrayList<>(timers.values())) {
                long remaining = timer.endMs - now;
                long warnMs = (long) (warnSeconds.getValue() * 1000.0);
                if (!timer.warned && warnMs > 0L && remaining > 0L && remaining <= warnMs) {
                    timer.warned = true;
                    beep(1.2f);
                }
                if (remaining <= 0L && !timer.announced) {
                    timer.announced = true;
                    finished.add(timer);
                }
            }
            if (hideFinished.isEnabled()) {
                timers.entrySet().removeIf(entry -> entry.getValue().announced
                        && now - entry.getValue().endMs > 4000L);
            }
        }
        for (Timer timer : finished) {
            announce(timer);
        }
    }

    private void announce(Timer timer) {
        String label = displayName(timer.name);
        try {
            NotificationManager.info(label + " ready", "Cooldown finished");
        } catch (Throwable ignored) {
        }
        if (chatWarn.isEnabled() && mc.inGameHud != null) {
            try {
                mc.inGameHud.getChatHud().addMessage(Text.literal("[Anormal] " + label + " is ready"));
            } catch (Throwable ignored) {
            }
        }
        beep(1.6f);
    }

    private void beep(float pitch) {
        if (!sound.isEnabled() || mc.player == null) return;
        try {
            mc.player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), 0.55f, pitch);
        } catch (Throwable ignored) {
        }
    }

    private String displayName(String raw) {
        if (raw == null || raw.isEmpty()) return "Command";
        String lower = raw.toLowerCase(Locale.ROOT);
        if (lower.length() == 1) return lower.toUpperCase(Locale.ROOT);
        return "/" + lower;
    }

    /** Compact mm:ss (or h:mm:ss) text for a remaining time in milliseconds. */
    private String format(long ms) {
        long totalSeconds = (ms + 999L) / 1000L;
        long h = totalSeconds / 3600L;
        long m = (totalSeconds % 3600L) / 60L;
        long s = totalSeconds % 60L;
        if (h > 0L) return String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s);
        return String.format(Locale.ROOT, "%d:%02d", m, s);
    }

    // ------------------------------------------------------------------
    //  HUD
    // ------------------------------------------------------------------

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.player == null || mc.textRenderer == null) return;
        List<Timer> list;
        synchronized (timers) {
            list = new ArrayList<>(timers.values());
        }
        list.removeIf(timer -> timer.remaining() <= 0L && hideFinished.isEnabled());
        list.sort(Comparator.comparingLong(Timer::remaining));
        int rows = Math.min((int) maxRows.getValue().intValue(), list.size());
        if (rows <= 0) return;

        int w = 178;
        int rowH = 20;
        int h = 24 + rows * rowH;
        int[] at = HudStyle.place(posX.getValue().intValue(), posY.getValue().intValue(), w, h);
        int x = at[0], y = at[1];
        int accent = ThemeManager.getAccentColor();

        HudStyle.panel(context, x, y, w, h, accent);
        HudStyle.title(context, mc.textRenderer, "Cooldowns", x + 10, y + 7);

        for (int i = 0; i < rows; i++) {
            Timer timer = list.get(i);
            int rowY = y + 22 + i * rowH;
            long remaining = timer.remaining();
            boolean ready = remaining <= 0L;
            String value = ready ? "READY" : format(remaining);
            int color = ready ? 0xFF4ADE80 : (remaining <= 5000L ? 0xFFFBBF24 : 0xFFC7CBD6);
            RenderUtils.drawText(context, mc.textRenderer, displayName(timer.name), x + 10, rowY, 0xFFE8E8EC, false);
            int vw = mc.textRenderer.getWidth(value);
            RenderUtils.drawText(context, mc.textRenderer, value, x + w - 10 - vw, rowY, color, true);
            float frac = showTotal.isEnabled() ? timer.fraction() : Math.min(1.0f, remaining / 30000.0f);
            HudStyle.bar(context, x + 10, rowY + 11, w - 20, 3, ready ? 1.0f : frac, color);
        }
    }
}
