package com.anormal.client.gui;

import com.anormal.client.module.impl.legit.MusicPlayer;
import com.anormal.client.music.MusicEngine;
import com.anormal.client.music.MusicLibrary;
import com.anormal.client.music.MusicTrack;
import com.anormal.client.util.RenderUtils;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.input.MouseInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.awt.FileDialog;
import java.awt.Frame;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * Playlist manager: add files or whole folders, pick a track, drop one, and set the playback modes.
 *
 * <p>Adding songs works three ways because the native dialog is not available in every JVM: the
 * file picker, a typed/pasted path, or a folder scan (which pulls in every supported audio file it
 * finds next to each other).</p>
 */
public class MusicPlayerScreen extends Screen {

    private static final int PANEL_W = 430;
    private static final int PANEL_H = 344;

    private static final int BG = 0xF0161619;
    private static final int BORDER = 0xFF3A3A42;
    private static final int BUTTON = 0xFF2C2C34;
    private static final int BUTTON_HOVER = 0xFF3A3A46;
    private static final int ACCENT = 0xFF5AA9FF;
    private static final int TEXT = 0xFFE8E8EC;
    private static final int TEXT_DIM = 0xFF8A8A96;

    private final MusicPlayer module;

    /** Keeps the last typed path across screen openings. */
    private static String lastPath = "";

    private TextField pathField;
    private String status = "";
    private int statusColor = TEXT_DIM;
    private int scroll;

    private int px, py;
    private int filesX, filesY, filesW, addH;
    private int folderX, folderW;
    private int clearX, clearW;
    private int prevX, prevW, playX, playW, nextX, nextW, transportY, transportH;
    private int scaleX, scaleY, scaleW, scaleH;
    private int shuffleX, shuffleW, loopX, loopW;
    private int volX, volY, volW, volH;
    private int listX, listY, listW, listH;
    private int closeX, closeY, closeW;
    private int progX, progY, progW, progH;

    /** True while the cursor is held on a slider - the drag handler keeps updating it. */
    private boolean draggingScale, draggingVol;

    public MusicPlayerScreen(MusicPlayer module) {
        super(Text.literal("Music Player"));
        this.module = module;
    }

    @Override
    protected void init() {
        px = (width - PANEL_W) / 2;
        py = (height - PANEL_H) / 2;

        addH = 18;
        filesX = px + 16;
        filesY = py + 50;
        filesW = 96;
        folderX = filesX + filesW + 6;
        folderW = 96;
        clearX = folderX + folderW + 6;
        clearW = 64;

        shuffleX = px + 16;
        shuffleW = 70;
        loopX = shuffleX + shuffleW + 6;
        loopW = 70;

        volX = px + 220;
        volY = py + PANEL_H - 26;
        volW = 120;
        volH = 6;

        listX = px + 10;

        // Transport gets its own row with room for the word on the button; the previous 46px
        // button could not fit "Pause" and the text ran into the scale slider.
        transportY = py + 100;
        transportH = 20;
        prevX = px + 16;
        prevW = 52;
        playX = prevX + prevW + 6;
        playW = 72;
        nextX = playX + playW + 6;
        nextW = 52;

        // Scale slider on a dedicated row underneath, so nothing overlaps.
        scaleX = px + 74;
        scaleY = py + 134;
        scaleW = PANEL_W - 16 - 74 - 16;
        scaleH = 6;

        listY = py + 150;
        listW = PANEL_W - 20;
        listH = PANEL_H - 150 - 58;

        closeW = 64;
        closeX = px + PANEL_W - 16 - closeW;
        closeY = py + PANEL_H - 30;

        // Seek bar under the now-playing line.
        progX = px + 16;
        progY = py + 41;
        progW = PANEL_W - 32;
        progH = 4;

        pathField = new TextField(lastPath, "paste a file or folder path, then Enter", 512);
        // Without focus the field never receives key events, which is why typing into it did
        // nothing at all.
        pathField.setFocused(true);
    }

    // ------------------------------------------------------------------
    //  Render
    // ------------------------------------------------------------------

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        MusicLibrary lib = MusicLibrary.get();

        ctx.fill(0, 0, width, height, 0x99000000);
        // Same frosted panel as the HUD card, so the screen and the card read as one design.
        com.anormal.client.util.RenderUtils.drawGlassPanel(ctx, px, py, px + PANEL_W, py + PANEL_H,
                0x8C10141C, 0x3CFFFFFF,
                (com.anormal.client.theme.ThemeManager.getAccentColor() & 0xFFFFFF) | 0x2E000000);
        for (int i = 0; i < 14; i++) {
            int alpha = 18 - i;
            if (alpha <= 0) break;
            ctx.fill(px + 1, py + 1 + i, px + PANEL_W - 1, py + 2 + i, (alpha << 24) | 0xFFFFFF);
        }
        // Header: accent glyph, title and an accent hairline under it.
        ctx.drawText(textRenderer, "\u266a", px + 16, py + 7, ACCENT, false);
        ctx.drawText(textRenderer, "Music Player", px + 26, py + 7, ACCENT, false);
        ctx.fill(px + 1, py + 22, px + PANEL_W - 1, py + 23, 0x40FFFFFF);
        ctx.fill(px + 16, py + 22, px + 28 + textRenderer.getWidth("Music Player"), py + 23, ACCENT);

        MusicTrack current = lib.current();
        String now = current == null ? "Nothing playing" : current.title() + "  -  " + current.artist();
        ctx.drawText(textRenderer, trim(now, PANEL_W - 132), px + 16, py + 29, TEXT, false);

        // Seek bar: rounded track, accent fill with a knob, times on the right.
        long durMs = MusicEngine.get().durationMs();
        long posMs = MusicEngine.get().positionMs();
        float progRatio = durMs > 0 ? Math.min(1f, posMs / (float) durMs) : 0f;
        RenderUtils.fillRounded(ctx, progX, progY, progW, progH, 2, 0xFF23232B);
        int progFill = (int) (progW * progRatio);
        if (progFill > 0) {
            RenderUtils.fillRounded(ctx, progX, progY, Math.max(2, progFill), progH,
                    Math.min(2, progFill / 2), ACCENT);
            ctx.fill(progX, progY + progH, progX + progFill, progY + progH + 2, 0x335AA9FF);
        }
        int progKnob = progX + progFill;
        ctx.fill(progKnob - 1, progY - 2, progKnob + 1, progY + progH + 2, ACCENT);
        if (durMs > 0) {
            String times = fmt(posMs) + " / " + fmt(durMs);
            ctx.drawText(textRenderer, times,
                    px + PANEL_W - 16 - textRenderer.getWidth(times), py + 29, TEXT_DIM, false);
        }

        drawButton(ctx, filesX, filesY, filesW, addH, "Add Files", false,
                inRect(mouseX, mouseY, filesX, filesY, filesW, addH));
        drawButton(ctx, folderX, filesY, folderW, addH, "Add Folder", false,
                inRect(mouseX, mouseY, folderX, filesY, folderW, addH));
        drawButton(ctx, clearX, filesY, clearW, addH, "Clear", false,
                inRect(mouseX, mouseY, clearX, filesY, clearW, addH));

        // Dark field behind the text widget (the widget's own white box clashed with the panel).
        ctx.fill(px + 16, py + 74, px + PANEL_W - 16, py + 92, 0xFF1B1B21);
        ctx.fill(px + 16, py + 91, px + PANEL_W - 16, py + 92, 0xFF3A3A42);
        pathField.render(ctx, textRenderer, px + 16, py + 74, PANEL_W - 32, 18);

        // Transport row.
        boolean isPlaying = MusicEngine.get().isPlaying();
        boolean isPaused = MusicEngine.get().isPaused();
        drawButton(ctx, prevX, transportY, prevW, transportH, "Prev", false,
                inRect(mouseX, mouseY, prevX, transportY, prevW, transportH));
        drawButton(ctx, playX, transportY, playW, transportH,
                isPlaying ? "Pause" : (isPaused ? "Resume" : "Play"), isPlaying,
                inRect(mouseX, mouseY, playX, transportY, playW, transportH));
        drawButton(ctx, nextX, transportY, nextW, transportH, "Next", false,
                inRect(mouseX, mouseY, nextX, transportY, nextW, transportH));

        // Scale row, on its own line so the label never collides with the buttons.
        float cardScale = module.scale.getValue().floatValue();
        double ratio = (cardScale - module.scale.getMin()) / (module.scale.getMax() - module.scale.getMin());
        drawSlider(ctx, scaleX, scaleY, scaleW, scaleH, ratio);
        String scaleText = String.format(Locale.ROOT, "%.2f", cardScale);
        ctx.drawText(textRenderer, "SCALE", px + 16, scaleY - 2, TEXT_DIM, false);
        ctx.drawText(textRenderer, scaleText, px + PANEL_W - 16 - textRenderer.getWidth(scaleText),
                scaleY - 2, TEXT_DIM, false);

        // Track list.
        List<MusicTrack> tracks = lib.tracks();
        int rowH = 16;
        int visible = listH / rowH;
        int maxScroll = Math.max(0, (tracks.size() - visible) * rowH);
        scroll = Math.max(0, Math.min(maxScroll, scroll));

        for (int i = 0; i < visible; i++) {
            int index = i + scroll / rowH;
            if (index >= tracks.size()) break;

            int rowY = listY + i * rowH;
            boolean hovered = mouseX >= listX && mouseX <= listX + listW
                    && mouseY >= rowY && mouseY <= rowY + rowH;
            boolean playing = index == lib.index();

            if (playing) {
                RenderUtils.fillRounded(ctx, listX, rowY, listW, rowH - 1, 3, 0x335AA9FF);
                ctx.fill(listX + 1, rowY + 2, listX + 3, rowY + rowH - 3, ACCENT);
            } else if (hovered) {
                RenderUtils.fillRounded(ctx, listX, rowY, listW, rowH - 1, 3, 0x22FFFFFF);
            } else if (i % 2 == 0) {
                ctx.fill(listX, rowY, listX + listW, rowY + rowH - 1, 0x0CFFFFFF);
            }

            MusicTrack track = tracks.get(index);
            String num = (index + 1) + ".";
            ctx.drawText(textRenderer, num, listX + 6, rowY + 4, TEXT_DIM, false);
            int labelX = listX + 8 + textRenderer.getWidth(num);
            String label = track.title() + "  -  " + track.artist();
            ctx.drawText(textRenderer, trim(label, listX + listW - 30 - labelX), labelX, rowY + 4,
                    playing ? ACCENT : TEXT, false);

            // Animated equalizer on the row that is playing right now.
            if (playing) {
                long t = System.currentTimeMillis();
                for (int b = 0; b < 3; b++) {
                    double h = 0.3 + 0.7 * (0.5 + 0.5 * Math.sin(t / 150.0 + b * 1.9));
                    int bh = Math.max(1, (int) Math.round(8 * h));
                    int bx = listX + listW - 16 + b * 4;
                    ctx.fill(bx, rowY + 12 - bh, bx + 2, rowY + 12, ACCENT);
                }
            }
        }

        if (tracks.isEmpty()) {
            ctx.drawText(textRenderer, "No tracks yet - use Add Files or Add Folder",
                    listX + 4, listY + 4, TEXT_DIM, false);
        }

        // Modes.
        drawButton(ctx, shuffleX, py + PANEL_H - 32, shuffleW, 16,
                "Shuffle: " + (lib.shuffle() ? "ON" : "OFF"), lib.shuffle(),
                inRect(mouseX, mouseY, shuffleX, py + PANEL_H - 32, shuffleW, 16));
        String loopText = switch (lib.loop()) {
            case OFF -> "Loop: OFF";
            case ALL -> "Loop: ALL";
            case ONE -> "Loop: ONE";
        };
        drawButton(ctx, loopX, py + PANEL_H - 32, loopW, 16, loopText,
                lib.loop() != MusicLibrary.Loop.OFF,
                inRect(mouseX, mouseY, loopX, py + PANEL_H - 32, loopW, 16));

        // Volume.
        float v = module.volume.getValue().floatValue();
        drawSlider(ctx, volX, volY, volW, volH, v);
        ctx.drawText(textRenderer, "Vol " + (int) (v * 100) + "%", volX, volY - 10, TEXT_DIM, false);

        if (!status.isEmpty()) {
            // Sits left of the Close button on the same row - it used to overlap it.
            ctx.drawText(textRenderer, trim(status, PANEL_W - 32), px + 16, py + PANEL_H - 48,
                    statusColor, false);
        }

        drawButton(ctx, closeX, closeY, closeW, 18, "Close", false,
                inRect(mouseX, mouseY, closeX, closeY, closeW, 18));

        super.render(ctx, mouseX, mouseY, delta);
    }

    private void drawButton(DrawContext ctx, int x, int y, int w, int h, String label,
                            boolean active, boolean hovered) {
        int bg = active ? ACCENT : (hovered ? BUTTON_HOVER : BUTTON);
        RenderUtils.fillRounded(ctx, x, y, w, h, 4, bg);
        if (active) {
            ctx.fill(x + 4, y + 1, x + w - 4, y + 2, 0x66FFFFFF);
        } else {
            ctx.fill(x + 4, y, x + w - 4, y + 1, hovered ? 0x3AFFFFFF : BORDER);
            ctx.fill(x + 4, y + h - 1, x + w - 4, y + h, 0xFF1A1A20);
        }
        ctx.drawText(textRenderer, label, x + (w - textRenderer.getWidth(label)) / 2,
                y + (h - 8) / 2, active ? 0xFF0E0E12 : (hovered ? 0xFFFFFFFF : TEXT), false);
    }

    /** Rounded slider with an accent fill, a glow and a knob; {@code ratio} is 0..1. */
    private void drawSlider(DrawContext ctx, int x, int y, int w, int h, double ratio) {
        ratio = Math.max(0, Math.min(1, ratio));
        RenderUtils.fillRounded(ctx, x, y, w, h, Math.min(3, h / 2), 0xFF23232B);
        int fill = (int) (w * ratio);
        if (fill > 0) {
            RenderUtils.fillRounded(ctx, x, y, Math.max(2, fill), h, Math.min(3, h / 2), ACCENT);
            ctx.fill(x, y + h, x + fill, y + h + 2, 0x335AA9FF);
        }
        int knob = x + fill;
        ctx.fill(knob - 1, y - 2, knob + 1, y + h + 2, ACCENT);
        ctx.fill(knob, y - 1, knob + 1, y + h + 1, 0xFFFFFFFF);
    }

    private static String fmt(long ms) {
        long total = Math.max(0, ms) / 1000;
        return String.format(Locale.ROOT, "%d:%02d", total / 60, total % 60);
    }

    private static boolean inRect(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private String trim(String value, int maxWidth) {
        if (textRenderer.getWidth(value) <= maxWidth) return value;
        String out = value;
        while (out.length() > 3 && textRenderer.getWidth(out + "...") > maxWidth) {
            out = out.substring(0, out.length() - 1);
        }
        return out + "...";
    }

    // ------------------------------------------------------------------
    //  Input
    // ------------------------------------------------------------------

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        return handleClick(click.x(), click.y(), click.button(), click);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return handleClick(mouseX, mouseY, button,
                new Click(mouseX, mouseY, new MouseInput(button, 0)));
    }

    // ------------------------------------------------------------------
    //  Slider dragging
    // ------------------------------------------------------------------

    /**
     * Dragging used to be handled only on press, so the sliders jumped once and then stopped
     * following the cursor. Both signatures are implemented because the client dispatches the
     * event-based one while some input paths still call the legacy one.
     */
    public boolean mouseDragged(Click click, double offsetX, double offsetY) {
        return handleDrag(click.x(), click.y());
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        return handleDrag(mouseX, mouseY);
    }

    private boolean handleDrag(double rawX, double rawY) {
        double mouseX = rawX > width ? rawX / client.getWindow().getScaleFactor() : rawX;
        if (draggingScale) {
            applyScale(mouseX);
            return true;
        }
        if (draggingVol) {
            applyVolume(mouseX);
            return true;
        }
        return false;
    }

    public boolean mouseReleased(Click click) {
        draggingScale = false;
        draggingVol = false;
        return false;
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggingScale = false;
        draggingVol = false;
        return false;
    }

    private void applyScale(double mouseX) {
        double r = Math.max(0, Math.min(1, (mouseX - scaleX) / (double) scaleW));
        module.scale.setValue(module.scale.getMin()
                + r * (module.scale.getMax() - module.scale.getMin()));
    }

    private void applyVolume(double mouseX) {
        double ratio = Math.max(0, Math.min(1, (mouseX - volX) / (double) volW));
        module.volume.setValue(ratio);
        MusicEngine.get().setVolume((float) ratio);
    }

    private boolean handleClick(double rawX, double rawY, int button, Click click) {
        double mouseX = rawX > width ? rawX / client.getWindow().getScaleFactor() : rawX;
        double mouseY = rawY > height ? rawY / client.getWindow().getScaleFactor() : rawY;
        int mx = (int) mouseX;
        int my = (int) mouseY;

        MusicLibrary lib = MusicLibrary.get();

        if (inRect(mx, my, filesX, filesY, filesW, addH)) {
            pickFiles();
            return true;
        }
        if (inRect(mx, my, folderX, filesY, folderW, addH)) {
            pickFolder();
            return true;
        }
        if (inRect(mx, my, clearX, filesY, clearW, addH)) {
            lib.clear();
            setStatus("Queue cleared", TEXT_DIM);
            return true;
        }
        if (inRect(mx, my, prevX, transportY, prevW, transportH)) {
            lib.previous();
            return true;
        }
        if (inRect(mx, my, playX, transportY, playW, transportH)) {
            if (MusicEngine.get().isPlaying() || MusicEngine.get().isPaused()) {
                MusicEngine.get().togglePause();
            } else {
                lib.playCurrentOrFirst();
            }
            return true;
        }
        if (inRect(mx, my, nextX, transportY, nextW, transportH)) {
            lib.next(true);
            return true;
        }
        if (inRect(mx, my, progX, progY - 4, progW, progH + 8)) {
            long dur = MusicEngine.get().durationMs();
            if (dur > 0) {
                double r = Math.max(0, Math.min(1, (mouseX - progX) / (double) progW));
                MusicEngine.get().seek((long) (dur * r));
            }
            return true;
        }
        if (inRect(mx, my, scaleX, scaleY - 6, scaleW, scaleH + 12)) {
            draggingScale = true;
            applyScale(mouseX);
            return true;
        }
        if (inRect(mx, my, shuffleX, py + PANEL_H - 32, shuffleW, 16)) {
            lib.toggleShuffle();
            return true;
        }
        if (inRect(mx, my, loopX, py + PANEL_H - 32, loopW, 16)) {
            lib.cycleLoop();
            return true;
        }
        if (inRect(mx, my, volX, volY - 6, volW, volH + 12)) {
            draggingVol = true;
            applyVolume(mouseX);
            return true;
        }
        if (inRect(mx, my, closeX, closeY, closeW, 18)) {
            close();
            return true;
        }

        // Track rows: left plays, right removes.
        if (mouseX >= listX && mouseX <= listX + listW && mouseY >= listY && mouseY <= listY + listH) {
            int rowH = 16;
            int index = scroll / rowH + (int) ((mouseY - listY) / rowH);
            if (index >= 0 && index < lib.tracks().size()) {
                if (button == 1) {
                    String removed = lib.tracks().get(index).title();
                    lib.remove(index);
                    setStatus("Removed: " + removed, TEXT_DIM);
                } else {
                    lib.playIndex(index);
                }
                return true;
            }
        }

        if (pathField != null && pathField.click(mouseX, mouseY, px + 16, py + 74, PANEL_W - 32, 18)) {
            return true;
        }
        // Any click that is not on a control keeps the text field usable.
        if (pathField != null) pathField.setFocused(true);
        return super.mouseClicked(click, false);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        List<MusicTrack> tracks = MusicLibrary.get().tracks();
        int visible = listH / 16;
        int maxScroll = Math.max(0, (tracks.size() - visible) * 16);
        scroll = Math.max(0, Math.min(maxScroll, scroll - (int) (vertical * 16)));
        return true;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        if (input.key() == GLFW.GLFW_KEY_ENTER || input.key() == GLFW.GLFW_KEY_KP_ENTER) {
            addFromPath();
            return true;
        }
        if (pathField != null && pathField.keyPressed(input)) return true;
        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
        if (pathField != null && pathField.charTyped(input)) return true;
        return super.charTyped(input);
    }

    // ------------------------------------------------------------------
    //  Adding tracks
    // ------------------------------------------------------------------

    private void addFromPath() {
        if (pathField == null) return;
        String value = pathField.get().trim().replace("\"", "");
        if (value.isEmpty()) {
            setStatus("Paste a path first", 0xFFFF6B6B);
            return;
        }
        try {
            Path path = Path.of(value);
            if (java.nio.file.Files.isDirectory(path)) {
                int added = MusicLibrary.get().addFolder(path);
                setStatus("Added " + added + " file(s)", 0xFF6BD96B);
            } else if (MusicTrack.isSupported(path)) {
                MusicLibrary.get().add(path);
                setStatus("Added " + path.getFileName(), 0xFF6BD96B);
            } else {
                setStatus("Unsupported file type", 0xFFFF6B6B);
            }
        } catch (Throwable t) {
            setStatus("Could not read that path", 0xFFFF6B6B);
        }
    }

    /** Native multi-file picker; falls back to the path field when AWT is unavailable. */
    private void pickFiles() {
        runDialog(FileDialog.LOAD, false, files -> {
            int added = 0;
            for (String f : files) {
                Path p = Path.of(f);
                if (MusicTrack.isSupported(p)) {
                    MusicLibrary.get().add(p);
                    added++;
                }
            }
            setStatus("Added " + added + " file(s)", 0xFF6BD96B);
        });
    }

    private void pickFolder() {
        runDialog(FileDialog.LOAD, true, files -> {
            if (files.length == 0) return;
            Path dir = Path.of(files[0]);
            if (!java.nio.file.Files.isDirectory(dir)) {
                dir = dir.getParent();
            }
            int added = MusicLibrary.get().addFolder(dir);
            setStatus("Added " + added + " file(s)", 0xFF6BD96B);
        });
    }

    private interface DialogCallback {
        void accept(String[] files);
    }

    private void runDialog(int mode, boolean directoryOnly, DialogCallback callback) {
        Thread thread = new Thread(() -> {
            try {
                java.awt.EventQueue.invokeAndWait(() -> {
                    try {
                        Frame owner = new Frame();
                        owner.setAlwaysOnTop(true);
                        owner.setUndecorated(true);
                        owner.setSize(1, 1);
                        owner.setLocationRelativeTo(null);
                        owner.setVisible(true);

                        FileDialog dialog = new FileDialog(owner, "Select audio", mode);
                        if (mode == FileDialog.LOAD && !directoryOnly) {
                            dialog.setFilenameFilter((dir, name) -> {
                                String lower = name.toLowerCase(Locale.ROOT);
                                return lower.endsWith(".mp3") || lower.endsWith(".wav")
                                        || lower.endsWith(".aiff") || lower.endsWith(".au");
                            });
                            dialog.setMultipleMode(true);
                        }
                        dialog.setVisible(true);

                        String dir = dialog.getDirectory();
                        java.io.File[] picked = dialog.getFiles();
                        dialog.dispose();
                        owner.dispose();

                        if (dir == null || picked == null || picked.length == 0) {
                            setStatus("Cancelled", TEXT_DIM);
                            return;
                        }
                        String[] paths = new String[picked.length];
                        for (int i = 0; i < picked.length; i++) {
                            paths[i] = picked[i].getAbsolutePath();
                        }
                        callback.accept(paths);
                    } catch (Throwable t) {
                        setStatus("File dialog unavailable - paste a path", 0xFFFF6B6B);
                    }
                });
            } catch (Throwable t) {
                setStatus("File dialog unavailable - paste a path", 0xFFFF6B6B);
            }
        }, "anormal-music-picker");
        thread.setDaemon(true);
        thread.start();
    }

    private void setStatus(String message, int color) {
        this.status = message;
        this.statusColor = color;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
