package com.anormal.client.module.impl.kuro;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.module.ModuleManager;
import com.anormal.client.module.impl.client.Notifications;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.NotificationManager;
import com.anormal.client.util.RenderUtils;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

import java.util.Locale;

/**
 * kuro {@code Stopwatch} port.
 *
 * <p>HUD'dagi taymer: stop-watch bo'lsa vaqt to'playdi; countdown bo'lsa
 * belgilangan sekunddan nolga hisoblaydi va nolga yetganda yang'ratish
 * qo'ng'irog'i, bildirishnoma va HUD'da "Time is up" filmagi.</p>
 *
 * <p>Moslash: kuro HUD element (HudElement.onHud + Look.panel) va
 * .timer chat buyrug'i orqali boshqariladi. Anormal suhbat buyruq qatlomiga
 * ega emas, shuningda bind qilingan tugma start/pause qiladi
 * (module {@link #onKeyPressed}), reset esa modulni o'chirib-qo'yish.</p>
 */
public class KuroStopwatch extends Module {
    private static KuroStopwatch instance;
    public static KuroStopwatch get() { return instance; }

    private final NumberSetting x = new NumberSetting("X", "HUD position", 6.0, 0.0, 4000.0, 1.0);
    private final NumberSetting y = new NumberSetting("Y", "HUD position", 100.0, 0.0, 4000.0, 1.0);
    private final ModeSetting mode = new ModeSetting("Mode", "What the timer counts", "Stopwatch", "Stopwatch", "Countdown");
    private final NumberSetting countdown = new NumberSetting("Countdown (seconds)", "Countdown length", 300.0, 5.0, 7200.0, 5.0);
    private final BooleanSetting tenths = new BooleanSetting("Tenths of a second", false);
    private final BooleanSetting chime = new BooleanSetting("Chime at zero", true);

    private long accumMs;             // time counted before the current run
    private long startedMs = -1;      // wall-clock start of the current run, -1 = not running
    private boolean fired;            // the countdown already rang
    private int lastW = 60, lastH = 15;

    // Look.panel palette (kuro ranglari)
    private static final int TEXT = 0xFFECEFF1;
    private static final int MUTED = 0xFF9E9E9E;
    private static final int BG = 0xE0101010;
    private static final int BORDER = 0xFF2A2A2A;
    private static final int GOOD = 0xFF66BB6A;
    private static final int BAD = 0xFFEF5350;
    private static final int ACCENT = 0xFF7C4DFF;

    public KuroStopwatch() {
        super("Stopwatch", "Stopwatch or countdown on the HUD", Category.KURO);
        instance = this;
        addSetting(x);
        addSetting(y);
        addSetting(mode);
        addSetting(countdown);
        addSetting(tenths);
        addSetting(chime);
    }

    /** kuro: bind qilingan tugma start / pause qiladi (.timer start buyrug'ining o'rnini bosadi). */
    @Override
    public boolean onKeyPressed() {
        startPause();
        return true;
    }

    private boolean running() { return startedMs >= 0; }
    private long elapsed() { return accumMs + (running() ? System.currentTimeMillis() - startedMs : 0L); }
    private boolean countingDown() { return mode.is("Countdown"); }

    public void startPause() {
        if (running()) { accumMs = elapsed(); startedMs = -1; }
        else {
            if (countingDown() && elapsed() >= (long) (countdown.getValue() * 1000)) { accumMs = 0; fired = false; }
            startedMs = System.currentTimeMillis();
        }
    }

    public void reset() { accumMs = 0; startedMs = -1; fired = false; }

    // ------------------------------------------------------------------ the countdown ringing

    @Override
    public void onTick() {
        if (!countingDown() || !running() || fired) return;
        if (elapsed() < (long) (countdown.getValue() * 1000)) return;
        fired = true;
        accumMs = (long) (countdown.getValue() * 1000);
        startedMs = -1;
        NotificationManager.info("Timer", "Time is up");
        say("Timer finished.");
        if (chime.isEnabled() && mc.player != null)
            mc.player.playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.2f, 1.0f);
    }

    // ------------------------------------------------------------------ HUD

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        TextRenderer font = mc.textRenderer;
        if (font == null) return;
        long ms = countingDown() ? Math.max(0L, (long) (countdown.getValue() * 1000) - elapsed()) : elapsed();
        String time = format(ms);
        String tag = countingDown() ? (fired ? "Time is up" : running() ? "Countdown" : "Countdown (paused)")
                                    : (running() ? "Stopwatch" : "Stopwatch (paused)");
        int tw = Math.max(font.getWidth(time), font.getWidth(time.replaceAll("[0-9]", "8")));
        int w = Math.max(tw, font.getWidth(tag)) + 18, h = 26;
        lastW = w; lastH = h;
        int x1 = clampX(x.getValue().intValue(), w), y1 = clampY(y.getValue().intValue(), h);
        int accent = fired && (System.currentTimeMillis() / 500) % 2 == 0 ? BAD : running() ? GOOD : ACCENT;

        // kuro Look.panel to'rtburchagi: fon + 1px border + chapdagi accent chiziqcha
        context.fill(x1, y1, x1 + w, y1 + h, BG);
        context.fill(x1, y1, x1 + 3, y1 + h, accent);
        context.fill(x1 + 3, y1, x1 + w, y1 + 1, BORDER);
        context.fill(x1 + 3, y1 + h - 1, x1 + w, y1 + h, BORDER);
        RenderUtils.drawText(context, font, time, x1 + 9, y1 + 4, TEXT, false);
        RenderUtils.drawText(context, font, tag, x1 + 9, y1 + 15, MUTED, false);
    }

    private int clampX(int px, int w) { return Math.max(0, Math.min(px, mc.getWindow().getScaledWidth() - w)); }
    private int clampY(int py, int h) { return Math.max(0, Math.min(py, mc.getWindow().getScaledHeight() - h)); }

    private String format(long ms) {
        long totalSec = ms / 1000, h = totalSec / 3600, m = (totalSec / 60) % 60, s = totalSec % 60;
        String base = h > 0 ? String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s) : String.format(Locale.ROOT, "%02d:%02d", m, s);
        return tenths.isEnabled() ? base + "." + (ms / 100) % 10 : base;
    }

    // ------------------------------------------------------------------ .timer buyrug'i (Anormal'da chat buyruq qatlomi yo'q)

    /** kuro {@code .timer ...} buyrug'i mantig'i; Anormal'da sukunat chat xabari sifatida ko'rsatiladi. */
    public void command(String[] a) {
        String sub = a.length == 0 ? "" : a[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "start", "resume" -> { show(); if (!running()) startPause(); }
            case "pause", "stop" -> { if (running()) startPause(); }
            case "reset", "clear" -> reset();
            case "toggle" -> { show(); startPause(); }
            case "stopwatch" -> { mode.setMode("Stopwatch"); reset(); show(); }
            case "set", "countdown" -> {
                long sec = a.length >= 2 ? parseSeconds(a[1]) : -1;
                if (sec < 5 || sec > 7200) { say("Usage: .timer set <1h30m | 5m | 90s | 2:30>   (5 seconds to 2 hours)"); return; }
                countdown.setValue((double) sec);
                mode.setMode("Countdown");
                reset();
                show();
                say("Countdown set to " + format(sec * 1000) + ". Start it with .timer start");
            }
            default -> say(".timer start | pause | reset | stopwatch | set <1h30m | 5m | 90s | 2:30>");
        }
    }

    private void show() { if (!isEnabled()) setEnabled(true); }

    private void say(String msg) {
        if (mc.player != null) mc.player.sendMessage(Text.literal(msg), false);
    }

    /**
     * "1h30m", "5m", "90s", "2:30" (m:ss) yoki "1:02:03" -> sekund, yoki -1.
     */
    static long parseSeconds(String t) {
        t = t.toLowerCase(Locale.ROOT).trim();
        try {
            if (t.contains(":")) {
                String[] p = t.split(":");
                long total = 0;
                for (String part : p) total = total * 60 + Long.parseLong(part);
                return p.length <= 3 ? total : -1;
            }
            long total = 0, cur = 0;
            boolean any = false;
            for (char c : t.toCharArray()) {
                if (Character.isDigit(c)) { cur = cur * 10 + (c - '0'); any = true; }
                else if (c == 'h') { total += cur * 3600; cur = 0; }
                else if (c == 'm') { total += cur * 60; cur = 0; }
                else if (c == 's') { total += cur; cur = 0; }
                else return -1;
            }
            return any ? total + cur : -1;                 // a bare number = seconds
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
