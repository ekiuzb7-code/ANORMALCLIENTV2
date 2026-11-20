package com.anormal.client.module.impl.legit;

import com.anormal.client.i18n.I18n;
import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.music.AlbumArt;
import com.anormal.client.music.MusicEngine;
import com.anormal.client.music.MusicLibrary;
import com.anormal.client.music.MusicTrack;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.ColorUtils;
import com.anormal.client.util.RenderUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * A now-playing card on the HUD, with working transport controls.
 *
 * <p>The card is laid out in a fixed base coordinate system and every position goes through
 * {@link #sx(int)} / {@link #sy(int)} / {@link #sw(int)}, so the {@code Scale} setting scales
 * everything together and hit testing can convert the cursor back into base units.</p>
 *
 * <p>Rows are strictly stacked - header, title, artist, seek bar, time row, transport row and
 * volume row all own their own vertical band, which is what keeps the transport buttons from
 * crossing the volume slider and its label.</p>
 *
 * <p>Clicks are intercepted in the mouse mixin and cancelled, otherwise pressing play would also
 * swing at the block behind the card.</p>
 */
public class MusicPlayer extends Module {

    public final NumberSetting posX = new NumberSetting("Pos X", "Card X position", 12.0, 0.0, 1920.0, 1.0);
    public final NumberSetting posY = new NumberSetting("Pos Y", "Card Y position", 210.0, 0.0, 1080.0, 1.0);
    public final NumberSetting scale = new NumberSetting("Scale", "Card scale", 1.0, 0.6, 2.0, 0.05);
    public final NumberSetting volume = new NumberSetting("Volume", "Playback volume", 0.8, 0.0, 1.0, 0.05);

    public final BooleanSetting showArt = new BooleanSetting("Show Album Art", "Draw the cover image", true);
    public final BooleanSetting showTimes = new BooleanSetting("Show Times", "Draw elapsed / remaining time", true);
    public final BooleanSetting showProgress = new BooleanSetting("Show Progress", "Draw the seek bar", true);
    public final BooleanSetting showVolume = new BooleanSetting("Show Volume", "Draw the volume slider", true);
    public final BooleanSetting background = new BooleanSetting("Background", "Dark rounded card", true);
    public final BooleanSetting glassStyle = new BooleanSetting("Glass Style",
            "Frosted glass panel with a glowing border (matches the Glassmorphism theme)", true);

    public final ColorSetting accent = new ColorSetting("Accent", "Progress and button colour",
            ColorUtils.rgba(90, 169, 255, 255));

    // ---- Layout (base units) --------------------------------------------------
    private static final int BASE_W = 268;
    private static final int BASE_H = 116;
    private static final int ART = 64;
    private static final int CONTENT_X = 80;
    private static final int CONTENT_R = BASE_W - 10;

    // Row baselines. Every control owns one band; nothing is allowed to overlap.
    private static final int HEADER_Y = 9;
    private static final int TITLE_Y = 21;
    private static final int ARTIST_Y = 33;
    private static final int SEEK_Y = 46;
    private static final int TIME_Y = 52;
    private static final int TRANSPORT_Y = 68;
    private static final int VOLUME_Y = 102;

    private static final int BTN_SIZE = 20;
    private static final int BTN_GAP = 6;

    private static final int ART_X = 7;
    private static final int ART_Y = (BASE_H - ART) / 2;

    private static MusicPlayer instance;

    // Hit boxes in base coordinates, rebuilt every frame.
    private int prevX, playX, nextX, btnY, btnSize;
    private int progX, progY, progW, progH;
    private int volX, volY, volW, volH;
    private int shuffleX, shuffleY, shuffleW, loopX, loopY, loopW;

    public MusicPlayer() {
        super("MusicPlayer", "Plays local audio files with a now-playing card on the HUD", Category.LEGIT);
        addSetting(posX);
        addSetting(posY);
        addSetting(scale);
        addSetting(volume);
        addSetting(showArt);
        addSetting(showTimes);
        addSetting(showProgress);
        addSetting(showVolume);
        addSetting(background);
        addSetting(glassStyle);
        addSetting(accent);
        instance = this;

        MusicEngine.get().setEndListener(() -> MusicLibrary.get().onTrackEnd());
    }

    public static MusicPlayer getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        MusicEngine.get().setVolume(volume.getValue().floatValue());
    }

    @Override
    public void onDisable() {
        // Off means off: leaving the sound running would hide the only way to stop it.
        MusicEngine.get().stop();
    }

    @Override
    public void onTickAlways() {
        MusicEngine.get().setVolume(volume.getValue().floatValue());
    }

    // ------------------------------------------------------------------
    //  HUD
    // ------------------------------------------------------------------

    @Override
    public void onRender2D(DrawContext ctx, float tickDelta) {
        if (mc.player == null) return;

        MusicLibrary lib = MusicLibrary.get();
        MusicTrack track = lib.current();

        gx = posX.getValue().intValue();
        gy = posY.getValue().intValue();
        gs = scale.getValue().floatValue();

        int accentColor = accent.getColor();
        long now = System.currentTimeMillis();
        boolean playing = MusicEngine.get().isPlaying();
        int accentSoft = (accentColor & 0x00FFFFFF) | 0x66000000;

        // ---- Hit boxes / layout, in base units -------------------------------
        btnSize = BTN_SIZE;
        btnY = TRANSPORT_Y;
        int mid = CONTENT_X + (CONTENT_R - CONTENT_X) / 2;
        playX = mid - btnSize / 2;
        prevX = playX - btnSize - BTN_GAP;
        nextX = playX + btnSize + BTN_GAP;

        progX = CONTENT_X;
        progY = SEEK_Y;
        progW = CONTENT_R - CONTENT_X;
        progH = 4;

        volX = CONTENT_X + 14;
        volY = VOLUME_Y;
        volW = CONTENT_R - volX - 24; // leave room for the percentage on the right
        volH = 4;

        boolean shuf = lib.shuffle();
        String loopLabel = switch (lib.loop()) {
            case OFF -> "LOOP";
            case ALL -> "ALL";
            case ONE -> "1";
        };
        loopW = mc.textRenderer.getWidth(loopLabel);
        loopX = CONTENT_R - loopW;
        loopY = HEADER_Y;
        shuffleW = mc.textRenderer.getWidth("SHUF");
        shuffleX = loopX - shuffleW - 14;
        shuffleY = HEADER_Y;

        if (background.isEnabled()) {
            int r = sw(7);
            // Breathing accent glow behind the card: the card visibly "breathes" while music plays.
            int pulse = playing ? 0x16 + (int) (0x1A * (0.5 + 0.5 * Math.sin(now / 280.0))) : 0x10;
            RenderUtils.fillRoundedRaw(ctx, sx(-2), sy(-2), sw(BASE_W + 4), sw(BASE_H + 4),
                    r + sw(2), (accentColor & 0x00FFFFFF) | (pulse << 24));

            if (glassStyle.isEnabled()) {
                // Rounded frosted glass: dark frame, see-through body, accent hairline on top.
                RenderUtils.fillRounded(ctx, sx(0), sy(0), sw(BASE_W), sw(BASE_H), r, 0xFF2E3442);
                RenderUtils.fillRounded(ctx, sx(1), sy(1), sw(BASE_W - 2), sw(BASE_H - 2),
                        Math.max(0, r - sw(1)), 0xE010141C);

                // Top sheen and bottom vignette fake the light falloff of glass.
                for (int i = 0; i < 12; i++) {
                    int alpha = 16 - i;
                    if (alpha <= 0) break;
                    ctx.fill(sx(3), sy(2 + i), sx(BASE_W - 3), sy(3 + i), (alpha << 24) | 0xFFFFFF);
                }
                for (int i = 0; i < 8; i++) {
                    int alpha = 10 - i;
                    if (alpha <= 0) break;
                    ctx.fill(sx(3), sy(BASE_H - 2 - i), sx(BASE_W - 3), sy(BASE_H - 1 - i), alpha << 24);
                }
                ctx.fill(sx(r), sy(1), sx(BASE_W - r), sy(2), accentSoft);
            } else {
                RenderUtils.fillRounded(ctx, sx(0), sy(0), sw(BASE_W), sw(BASE_H), r, 0xFF3A3A42);
                RenderUtils.fillRounded(ctx, sx(1), sy(1), sw(BASE_W - 2), sw(BASE_H - 2),
                        Math.max(0, r - sw(1)), 0xD0141418);
                ctx.fill(sx(r), sy(1), sx(BASE_W - r), sy(2), 0xFF5A5A66);
            }
        }

        if (track == null) {
            textS(ctx, Text.literal(I18n.tr("No tracks")), CONTENT_X, 26, 0xFFFFFFFF, true);
            textS(ctx, Text.literal(I18n.tr("Right-click the module to add songs")), CONTENT_X, 42, 0xFF8A8A96, false);
            return;
        }

        // ---- Album art (vertically centred against the control stack) --------
        if (showArt.isEnabled()) {
            RenderUtils.fillRounded(ctx, sx(ART_X - 1), sy(ART_Y - 1), sw(ART + 2), sw(ART + 2), sw(6), 0xFF05070B);
            Identifier art = AlbumArt.get(track);
            int ax = sx(ART_X), ay = sy(ART_Y), as = sw(ART);
            if (art != null) {
                ctx.drawTexture(RenderPipelines.GUI_TEXTURED, art, ax, ay, 0f, 0f, as, as, ART, ART);
            } else {
                ctx.fill(ax, ay, ax + as, ay + as, 0xFF1F1F25);
                textS(ctx, Text.literal("\u266a"), ART_X + ART / 2 - 3, ART_Y + ART / 2 - 4, accentColor, false);
            }
            if (playing) {
                ctx.fill(sx(ART_X), sy(ART_Y), sx(ART_X + ART), sy(ART_Y + 1), accentSoft);
                ctx.fill(sx(ART_X), sy(ART_Y + ART - 1), sx(ART_X + ART), sy(ART_Y + ART), accentSoft);
                ctx.fill(sx(ART_X), sy(ART_Y), sx(ART_X + 1), sy(ART_Y + ART), accentSoft);
                ctx.fill(sx(ART_X + ART - 1), sy(ART_Y), sx(ART_X + ART), sy(ART_Y + ART), accentSoft);

                // Equalizer bars along the bottom of the cover.
                ctx.fill(ax, ay + as - sw(12), ax + as, ay + as, 0x99000000);
                int bars = 5;
                int bw = Math.max(1, sw(2));
                for (int i = 0; i < bars; i++) {
                    double h = 0.3 + 0.7 * (0.5 + 0.5 * Math.sin(now / 150.0 + i * 1.7));
                    int bh = Math.max(1, (int) Math.round(sw(10) * h));
                    int bx = ax + sw(4) + (int) (i * (as - sw(8)) / (double) bars);
                    ctx.fill(bx, ay + as - sw(3) - bh, bx + bw, ay + as - sw(3), accentColor);
                }
            } else {
                ctx.fill(ax, ay, ax + as, ay + 1, 0x40FFFFFF);
            }
        }

        // ---- Header row: state, shuffle, loop --------------------------------
        if (playing) {
            int dotA = 0xAA + (int) (0x55 * (0.5 + 0.5 * Math.sin(now / 200.0)));
            ctx.fill(sx(CONTENT_X), sy(HEADER_Y + 1), sx(CONTENT_X + 3), sy(HEADER_Y + 4),
                    (dotA << 24) | (accentColor & 0xFFFFFF));
        }
        textS(ctx, Text.literal(I18n.tr("now playing")), CONTENT_X + (playing ? 7 : 0), HEADER_Y,
                playing ? accentColor : 0xFF8A8A96, false);

        textS(ctx, Text.literal("SHUF"), shuffleX, shuffleY, shuf ? accentColor : 0xFF5A5A66, false);
        if (shuf) {
            ctx.fill(sx(shuffleX), sy(shuffleY + 9), sx(shuffleX + mc.textRenderer.getWidth("SHUF")), sy(shuffleY + 10), accentSoft);
        }
        boolean loopOn = lib.loop() != MusicLibrary.Loop.OFF;
        textS(ctx, Text.literal(loopLabel), loopX, loopY, loopOn ? accentColor : 0xFF5A5A66, false);
        if (loopOn) {
            ctx.fill(sx(loopX), sy(loopY + 9), sx(loopX + loopW), sy(loopY + 10), accentSoft);
        }

        // ---- Title / artist --------------------------------------------------
        int textArea = CONTENT_R - CONTENT_X;
        marquee(ctx, Text.literal(track.title()).styled(st -> st.withBold(true)),
                CONTENT_X, TITLE_Y, textArea, 0xFFFFFFFF);

        // Artist is clipped, not scrolled - two moving lines would be unreadable.
        ctx.enableScissor(sx(CONTENT_X), sy(ARTIST_Y - 2), sx(CONTENT_R), sy(ARTIST_Y + 10));
        textS(ctx, Text.literal(track.artist()), CONTENT_X, ARTIST_Y, 0xFFB0B0BA, false);
        ctx.disableScissor();

        long dur = MusicEngine.get().durationMs();
        long pos = MusicEngine.get().positionMs();
        float progress = dur > 0 ? Math.min(1f, pos / (float) dur) : 0f;

        // ---- Seek bar --------------------------------------------------------
        if (showProgress.isEnabled()) {
            RenderUtils.fillRounded(ctx, sx(progX), sy(progY), sw(progW), sw(progH), sw(2), 0xFF23232B);
            int filled = (int) (progW * progress);
            if (filled > 0) {
                RenderUtils.fillRounded(ctx, sx(progX), sy(progY), sw(filled), sw(progH),
                        Math.min(sw(2), Math.max(0, sw(filled) / 2)), accentColor);
                // Lit top edge and a soft glow underneath.
                ctx.fill(sx(progX + 1), sy(progY), sx(progX + filled - 1), sy(progY + 1), 0x40FFFFFF);
                ctx.fill(sx(progX), sy(progY + progH), sx(progX + filled), sy(progY + progH + 2),
                        (accentColor & 0x00FFFFFF) | 0x33000000);
            }
            int knobX = progX + filled;
            ctx.fill(sx(knobX - 1), sy(progY - 2), sx(knobX + 1), sy(progY + progH + 2), accentColor);
            ctx.fill(sx(knobX), sy(progY - 1), sx(knobX + 1), sy(progY + progH + 1), 0xFFFFFFFF);
        }

        // ---- Time row (its own band, directly under the bar) ------------------
        if (showTimes.isEnabled()) {
            textS(ctx, Text.literal(fmt(pos)), CONTENT_X, TIME_Y, 0xFF9AA0AC, false);
            String right = "-" + fmt(Math.max(0, dur - pos));
            textS(ctx, Text.literal(right), CONTENT_R - mc.textRenderer.getWidth(right), TIME_Y, 0xFF9AA0AC, false);
        }

        // ---- Transport row ---------------------------------------------------
        boolean active = MusicEngine.get().isPlaying() || MusicEngine.get().isPaused();
        pill(ctx, prevX, false, accentColor);
        pill(ctx, playX, active, accentColor);
        pill(ctx, nextX, false, accentColor);
        int iconColor = active ? 0xFF10141C : accentColor;

        drawPrev(ctx, prevX, btnY, accentColor);
        if (playing) drawPause(ctx, playX, btnY, iconColor);
        else drawPlay(ctx, playX, btnY, iconColor);
        drawNext(ctx, nextX, btnY, accentColor);

        // ---- Volume row ------------------------------------------------------
        if (showVolume.isEnabled()) {
            // Hairline separator so the volume row reads as its own control group.
            ctx.fill(sx(CONTENT_X), sy(VOLUME_Y - 9), sx(CONTENT_R), sy(VOLUME_Y - 8), 0x16FFFFFF);

            float v = volume.getValue().floatValue();
            drawSpeaker(ctx, CONTENT_X, VOLUME_Y - 3, 10,
                    v <= 0.001f ? 0xFF55555F : 0xFF9AA0AC);

            RenderUtils.fillRounded(ctx, sx(volX), sy(volY), sw(volW), sw(volH), sw(2), 0xFF23232B);
            int fv = (int) (volW * v);
            if (fv > 0) {
                RenderUtils.fillRounded(ctx, sx(volX), sy(volY), sw(fv), sw(volH),
                        Math.min(sw(2), Math.max(0, sw(fv) / 2)), accentColor);
                ctx.fill(sx(volX), sy(volY + volH), sx(volX + fv), sy(volY + volH + 2),
                        (accentColor & 0x00FFFFFF) | 0x33000000);
            }
            int volKnob = volX + fv;
            ctx.fill(sx(volKnob - 1), sy(volY - 2), sx(volKnob + 1), sy(volY + volH + 2), accentColor);
            ctx.fill(sx(volKnob), sy(volY - 1), sx(volKnob + 1), sy(volY + volH + 1), 0xFFFFFFFF);

            String pct = (int) (v * 100) + "%";
            textS(ctx, Text.literal(pct), CONTENT_R - mc.textRenderer.getWidth(pct), VOLUME_Y - 4,
                    v <= 0.001f ? 0xFF55555F : 0xFF9AA0AC, false);
        }
    }

    // ------------------------------------------------------------------
    //  Scaling helpers - base units in, screen pixels out
    // ------------------------------------------------------------------

    private int gx, gy;
    private float gs = 1.0f;

    private int sx(int base) {
        return gx + Math.round(base * gs);
    }

    private int sy(int base) {
        return gy + Math.round(base * gs);
    }

    private int sw(int base) {
        return Math.max(1, Math.round(base * gs));
    }

    /**
     * Draws text clipped to {@code maxWidth}; scrolls it back and forth when it does not fit.
     *
     * <p>Ping-pong rather than a loop, so the start of the name is always readable within a couple
     * of seconds instead of waiting for a full pass.</p>
     */
    private void marquee(DrawContext ctx, Text text, int baseX, int baseY, int maxWidth, int color) {
        int textWidth = mc.textRenderer.getWidth(text);
        int offset = 0;

        if (textWidth > maxWidth) {
            long now = System.currentTimeMillis();
            int span = textWidth - maxWidth + 16;
            long cycle = 6000L;
            double phase = (now % cycle) / (double) cycle;
            double triangle = phase < 0.5 ? phase * 2.0 : (1.0 - phase) * 2.0;
            offset = (int) Math.round(span * triangle);
        }

        ctx.enableScissor(sx(baseX), sy(baseY - 1), sx(baseX + maxWidth), sy(baseY + 10));
        textS(ctx, text, baseX - offset, baseY, color, true);
        ctx.disableScissor();
    }

    /** Draws text at a base position, scaled with the card (text is not measured, so the matrix
     *  here cannot disturb the HUD editor's bounds). */
    private void textS(DrawContext ctx, Text text, int baseX, int baseY, int color, boolean shadow) {
        if (gs == 1.0f) {
            ctx.drawText(mc.textRenderer, text, sx(baseX), sy(baseY), color, shadow);
            return;
        }
        var m = ctx.getMatrices();
        m.pushMatrix();
        m.translate(sx(baseX), sy(baseY));
        m.scale(gs, gs);
        ctx.drawText(mc.textRenderer, text, 0, 0, color, shadow);
        m.popMatrix();
    }

    /** Round transport button; {@code lit} fills it with the accent colour. */
    private void pill(DrawContext ctx, int bx, boolean lit, int accentColor) {
        int bg = lit ? accentColor : 0x2AFFFFFF;
        RenderUtils.fillRounded(ctx, sx(bx), sy(btnY), sw(btnSize), sw(btnSize), sw(btnSize / 2), bg);
        if (!lit) {
            // Faint top highlight so the unlit buttons still read as raised glass.
            ctx.fill(sx(bx + 3), sy(btnY), sx(bx + btnSize - 3), sy(btnY + 1), 0x1AFFFFFF);
        }
    }

    /** Filled triangle glyph used by play / previous / next. */
    private void glyphTriangle(DrawContext ctx, int bx, int by, int size, int color, boolean right) {
        int inset = Math.max(2, Math.round(size / 5f));
        int h = size - inset * 2;
        int w = Math.max(2, Math.round(h * 0.82f));
        int startX = bx + (size - w) / 2;
        for (int i = 0; i < w; i++) {
            double t = i / (double) Math.max(1, w - 1);
            int half = (int) Math.round((h / 2.0) * t);
            int column = right ? startX + i : startX + (w - 1 - i);
            int top = by + inset + h / 2 - half;
            int bottom = by + inset + h / 2 + half + 1;
            ctx.fill(sx(column), sy(top), sx(column + 1), sy(bottom), color);
        }
    }

    private void drawPlay(DrawContext ctx, int bx, int by, int color) {
        glyphTriangle(ctx, bx, by, btnSize, color, true);
    }

    private void drawPause(DrawContext ctx, int bx, int by, int color) {
        int barW = Math.max(2, Math.round(btnSize / 6f));
        int gap = Math.max(2, Math.round(btnSize / 6f));
        int top = by + Math.max(3, btnSize / 5);
        int bottom = by + btnSize - Math.max(3, btnSize / 5);
        int left = bx + btnSize / 2 - (gap + barW) / 2;
        ctx.fill(sx(left), sy(top), sx(left + barW), sy(bottom), color);
        ctx.fill(sx(left + barW + gap), sy(top), sx(left + barW + gap + barW), sy(bottom), color);
    }

    private void drawPrev(DrawContext ctx, int bx, int by, int color) {
        int inner = btnSize - 6;
        int inset = (btnSize - inner) / 2 + 1;
        glyphTriangle(ctx, bx + inset, by + 3, inner, color, false);
        ctx.fill(sx(bx + inset - 2), sy(by + 4), sx(bx + inset), sy(by + btnSize - 4), color);
    }

    private void drawNext(DrawContext ctx, int bx, int by, int color) {
        int inner = btnSize - 6;
        int inset = (btnSize - inner) / 2 + 1;
        glyphTriangle(ctx, bx + inset, by + 3, inner, color, true);
        ctx.fill(sx(bx + btnSize - inset), sy(by + 4), sx(bx + btnSize - inset + 2), sy(by + btnSize - 4), color);
    }

    /** Small speaker glyph in front of the volume slider. */
    private void drawSpeaker(DrawContext ctx, int bx, int by, int size, int color) {
        int body = Math.max(2, Math.round(size * 0.28f));
        int midY = by + size / 2;
        ctx.fill(sx(bx), sy(midY - size / 4), sx(bx + body), sy(midY + size / 4), color);
        int coneH = size / 2;
        for (int i = 0; i <= coneH; i++) {
            int top = midY - i;
            int bottom = midY + i + 1;
            ctx.fill(sx(bx + body + i), sy(Math.max(by, top)), sx(bx + body + i + 1),
                    sy(Math.min(by + size, bottom)), color);
        }
        // Sound wave arc on the right, skipped when muted.
        if (color != 0xFF55555F) {
            int arcX = bx + body + coneH + 2;
            ctx.fill(sx(arcX), sy(midY - 2), sx(arcX + 1), sy(midY - 1), color);
            ctx.fill(sx(arcX), sy(midY + 1), sx(arcX + 1), sy(midY + 2), color);
            ctx.fill(sx(arcX + 1), sy(midY - 2), sx(arcX + 2), sy(midY - 1), color);
            ctx.fill(sx(arcX + 1), sy(midY + 1), sx(arcX + 2), sy(midY + 2), color);
        }
    }

    private static String fmt(long ms) {
        long total = Math.max(0, ms) / 1000;
        return String.format("%d:%02d", total / 60, total % 60);
    }

    // ------------------------------------------------------------------
    //  Input
    // ------------------------------------------------------------------

    /**
     * Handles a click on the card.
     *
     * @return true when the click was consumed (the caller cancels the vanilla click)
     */
    public boolean handleClick(double mouseX, double mouseY, int button) {
        if (mc.player == null || !isEnabled()) return false;

        int x = posX.getValue().intValue();
        int y = posY.getValue().intValue();
        float s = scale.getValue().floatValue();

        // Convert the cursor into base units - the card is drawn scaled, so the raw pixel hit
        // boxes would drift further apart the larger the scale gets.
        double bx = (mouseX - x) / s;
        double by = (mouseY - y) / s;

        if (bx < 0 || bx > BASE_W || by < 0 || by > BASE_H) return false;

        MusicLibrary lib = MusicLibrary.get();

        if (hit(bx, by, prevX, btnY, btnSize, btnSize)) {
            lib.previous();
            return true;
        }
        if (hit(bx, by, playX, btnY, btnSize, btnSize)) {
            if (MusicEngine.get().isPlaying() || MusicEngine.get().isPaused()) {
                MusicEngine.get().togglePause();
            } else {
                lib.playCurrentOrFirst();
            }
            return true;
        }
        if (hit(bx, by, nextX, btnY, btnSize, btnSize)) {
            lib.next(true);
            return true;
        }
        if (showProgress.isEnabled() && hit(bx, by, progX, progY - 4, progW, progH + 8)) {
            long dur = MusicEngine.get().durationMs();
            if (dur > 0) {
                double ratio = Math.max(0, Math.min(1, (bx - progX) / (double) progW));
                MusicEngine.get().seek((long) (dur * ratio));
            }
            return true;
        }
        if (showVolume.isEnabled() && hit(bx, by, volX - 2, volY - 5, volW + 4, volH + 10)) {
            double ratio = Math.max(0, Math.min(1, (bx - volX) / (double) volW));
            volume.setValue(ratio);
            MusicEngine.get().setVolume((float) ratio);
            return true;
        }
        if (hit(bx, by, shuffleX - 2, shuffleY - 2, shuffleW + 4, 11)) {
            lib.toggleShuffle();
            return true;
        }
        if (hit(bx, by, loopX - 2, loopY - 2, loopW + 4, 11)) {
            lib.cycleLoop();
            return true;
        }

        // Clicking the card body does nothing, but still swallows the click so the player does
        // not swing at whatever is behind the HUD.
        return true;
    }

    private static boolean hit(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }
}
