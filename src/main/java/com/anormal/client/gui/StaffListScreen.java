package com.anormal.client.gui;

import com.anormal.client.module.impl.render.StaffDetector;
import com.anormal.client.util.ColorUtils;
import com.anormal.client.util.RenderUtils;
import com.anormal.client.util.staff.StaffEntry;
import com.anormal.client.util.staff.StaffEvidence;
import com.anormal.client.util.staff.StaffHistory;
import com.anormal.client.util.staff.StaffListManager;
import com.anormal.client.util.staff.StaffStatus;
import com.anormal.client.util.staff.StaffTracker;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Staff list / evidence screen.
 *
 * <p>Styled like the reference module control panel: rounded charcoal sheet, amber accent, pill
 * tabs, card rows with a status dot and a footer status line. Functionally it adds, edits, removes
 * and searches entries, cycles their status, imports/exports the list as JSON and browses the
 * evidence history. Import/export go through the clipboard so the user stays in control.</p>
 */
public class StaffListScreen extends Screen {

    private static final int PANEL_W = 486;
    private static final int PANEL_H = 336;

    private static final int SCRIM = 0x99000000;
    private static final int PANEL = 0xF218181C;
    private static final int CARD = 0xFF202026;
    private static final int CARD_HOVER = 0xFF28282F;
    private static final int CARD_ACTIVE = 0xFF2B2418;
    private static final int FIELD = 0xFF1B1B21;
    private static final int BORDER = 0xFF2E2E36;
    private static final int ACCENT = 0xFFFFC24B;
    private static final int ACCENT_DIM = 0x55FFC24B;
    private static final int TEXT = 0xFFEDEFF5;
    private static final int TEXT_DIM = 0xFF8A8A96;
    private static final int TEXT_FAINT = 0xFF5F6270;
    private static final int GREEN = 0xFF6BD96B;
    private static final int RED = 0xFFF87171;

    private final StaffDetector module;

    private final TextField search = new TextField("", "Search staff...", 48);
    private final TextField username = new TextField("", "Username", 32);
    private final TextField uuidField = new TextField("", "UUID (optional)", 40);
    private final TextField group = new TextField("", "Group / rank", 24);
    private final TextField notes = new TextField("", "Notes", 96);

    private StaffEntry selected;
    private boolean historyMode;
    private boolean removeArmed;
    private String status = "";
    private int statusColor = TEXT_DIM;
    private int scroll;

    private int px, py;
    private int listX, listY, listW, listH;
    private int editorX, editorY, editorW;
    private int closeX, closeY, closeW;
    private int tabStaffX, tabHistX, tabY, tabW, tabH;
    private int searchX, searchY, searchW;
    private int addX, addY, addW, saveX, saveW, removeX, removeW;
    private int importX, importW, exportX, exportW;
    private int clearX, clearW, csvX, csvW;
    private int statusBtnX, statusBtnY, statusBtnW;
    private int[] rowYs = new int[0];
    private List<StaffEntry> shown = new ArrayList<>();

    public StaffListScreen(StaffDetector module) {
        super(Text.literal("Staff List / Evidence"));
        this.module = module;
    }

    @Override
    protected void init() {
        px = (width - PANEL_W) / 2;
        py = (height - PANEL_H) / 2;

        tabY = py + 48;
        tabH = 18;
        tabW = 70;
        tabHistX = px + PANEL_W - 14 - tabW;
        tabStaffX = tabHistX - 6 - tabW;

        searchX = px + 14;
        searchY = py + 48;
        searchW = 206;

        listX = px + 14;
        listY = py + 78;
        listW = 258;
        listH = 172;

        editorX = listX + listW + 12;
        editorY = listY;
        editorW = PANEL_W - (editorX - px) - 14;

        addY = py + 274;
        addW = 56;
        saveW = 62;
        removeW = 78;
        addX = listX;
        saveX = addX + addW + 6;
        removeX = saveX + saveW + 6;

        importW = 68;
        importX = listX;
        exportW = 68;
        exportX = importX + importW + 6;

        clearW = 58;
        clearX = listX;
        csvW = 90;
        csvX = clearX + clearW + 6;

        closeW = 16;
        closeX = px + PANEL_W - 16 - closeW;
        closeY = py + 12;

        statusBtnX = editorX;
        statusBtnY = editorY + 116;
        statusBtnW = editorW;
    }

    // ------------------------------------------------------------------
    //  Render
    // ------------------------------------------------------------------

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, width, height, SCRIM);

        // Rounded sheet: a 1px rounded outline with the body inset by one pixel.
        RenderUtils.fillRoundedRaw(ctx, px, py, PANEL_W, PANEL_H, 8, BORDER);
        RenderUtils.fillRounded(ctx, px + 1, py + 1, PANEL_W - 2, PANEL_H - 2, 7, PANEL);
        RenderUtils.fill(ctx, px + 10, py + 1, px + PANEL_W - 10, py + 2, 0x22FFFFFF);

        drawHeader(ctx, mouseX, mouseY);
        drawTabs(ctx, mouseX, mouseY);

        search.render(ctx, textRenderer, searchX, searchY, searchW, 18);

        if (historyMode) {
            renderHistory(ctx, mouseX, mouseY);
        } else {
            renderList(ctx, mouseX, mouseY);
            renderEditor(ctx, mouseX, mouseY);
        }

        drawFooter(ctx);

        if (!status.isEmpty()) {
            RenderUtils.drawText(ctx, textRenderer, status, px + 14, py + PANEL_H - 40,
                    statusColor, false);
        }
    }

    private void drawHeader(DrawContext ctx, int mouseX, int mouseY) {
        // Small amber mark, like the reference client's donut badge.
        RenderUtils.fillRounded(ctx, px + 14, py + 14, 12, 12, 4, ACCENT);
        RenderUtils.fillRounded(ctx, px + 17, py + 17, 6, 6, 2, PANEL);
        RenderUtils.drawText(ctx, textRenderer, "ANORMAL", px + 32, py + 12, TEXT, true);
        RenderUtils.drawText(ctx, textRenderer, "STAFF LIST / EVIDENCE", px + 32, py + 23, TEXT_FAINT, false);

        boolean hover = inside(mouseX, mouseY, closeX, closeY, closeW, 16);
        RenderUtils.fillRounded(ctx, closeX, closeY, closeW, 16, 4, hover ? CARD_HOVER : 0x00000000);
        RenderUtils.drawText(ctx, textRenderer, "x", closeX + 6, closeY + 4,
                hover ? TEXT : TEXT_DIM, false);
    }

    private void drawTabs(DrawContext ctx, int mouseX, int mouseY) {
        drawTab(ctx, "Staff", tabStaffX, tabY, tabW, !historyMode, mouseX, mouseY);
        drawTab(ctx, "History", tabHistX, tabY, tabW, historyMode, mouseX, mouseY);
    }

    private void drawTab(DrawContext ctx, String label, int x, int y, int w, boolean active,
                         int mouseX, int mouseY) {
        boolean hover = inside(mouseX, mouseY, x, y, w, tabH);
        RenderUtils.fillRounded(ctx, x, y, w, tabH, 4,
                active ? 0xFF2C2A22 : (hover ? CARD_HOVER : CARD));
        if (active) RenderUtils.fillRounded(ctx, x, y, w, 1, 1, ACCENT);
        RenderUtils.drawText(ctx, textRenderer, label, x + 8, y + 5,
                active ? ACCENT : TEXT_DIM, false);
    }

    private void renderList(DrawContext ctx, int mouseX, int mouseY) {
        String key = StaffTracker.serverKey();
        shown = StaffListManager.view(search.get(), key, module.useServerLists.isEnabled());
        drawListFrame(ctx);

        rowYs = new int[shown.size()];
        int rowH = 28;
        int rowY = listY + 4 - scroll;
        for (int i = 0; i < shown.size(); i++) {
            StaffEntry entry = shown.get(i);
            rowYs[i] = rowY;
            if (rowY + rowH < listY || rowY > listY + listH) {
                rowY += rowH + 2;
                continue;
            }
            boolean hover = inside(mouseX, mouseY, listX, rowY, listW, rowH);
            boolean active = entry == selected;
            RenderUtils.fillRounded(ctx, listX, rowY, listW, rowH, 4,
                    active ? CARD_ACTIVE : (hover ? CARD_HOVER : CARD));
            if (active) {
                RenderUtils.fillRounded(ctx, listX, rowY + 6, 2, rowH - 12, 1, ACCENT);
            }

            RenderUtils.drawText(ctx, textRenderer, trim(entry.displayName(), 120),
                    listX + 10, rowY + 5, active ? TEXT : 0xFFD7DAE3, true);

            String meta = entry.statusEnum().display();
            if (entry.group != null && !entry.group.isBlank()) meta += "  -  " + entry.group;
            RenderUtils.drawText(ctx, textRenderer, trim(meta, 150), listX + 10, rowY + 16,
                    TEXT_DIM, false);

            // Status dot on the right, like the reference module rows.
            int dotColor = entry.statusEnum() == StaffStatus.CONFIRMED ? GREEN
                    : entry.statusEnum() == StaffStatus.WATCHLIST ? ACCENT : TEXT_FAINT;
            RenderUtils.fillRounded(ctx, listX + listW - 14, rowY + 11, 6, 6, 3, dotColor);

            String stamp = entry.lastObserved == 0L ? "never" : time(entry.lastObserved);
            RenderUtils.drawText(ctx, textRenderer, stamp,
                    listX + listW - 24 - textRenderer.getWidth(stamp), rowY + 16, TEXT_FAINT, false);

            rowY += rowH + 2;
        }
        if (shown.isEmpty()) {
            RenderUtils.drawText(ctx, textRenderer, "No staff entries for this server",
                    listX + 10, listY + 10, TEXT_DIM, false);
            RenderUtils.drawText(ctx, textRenderer, "Add one on the right",
                    listX + 10, listY + 21, TEXT_FAINT, false);
        }

        drawButton(ctx, "Add", addX, addY, addW, mouseX, mouseY, false, false);
        drawButton(ctx, "Save", saveX, addY, saveW, mouseX, mouseY, selected == null, true);
        drawButton(ctx, removeArmed ? "Confirm" : "Remove", removeX, addY, removeW,
                mouseX, mouseY, selected == null, false);
        drawButton(ctx, "Import", importX, addY - 22, importW, mouseX, mouseY, false, false);
        drawButton(ctx, "Export", exportX, addY - 22, exportW, mouseX, mouseY, false, false);

        if (selected != null) {
            String badge = selected.hasUuid() ? "UUID known" : "UUID not verified";
            RenderUtils.drawText(ctx, textRenderer, badge,
                    removeX + removeW + 10, addY + 5,
                    selected.hasUuid() ? GREEN : TEXT_FAINT, false);
        }
    }

    private void drawListFrame(DrawContext ctx) {
        RenderUtils.fillRounded(ctx, listX, listY, listW, listH, 6, 0xB3101216);
        RenderUtils.drawBorder(ctx, listX, listY, listX + listW, listY + listH, 1, BORDER);
    }

    private void renderEditor(DrawContext ctx, int mouseX, int mouseY) {
        fieldLabel(ctx, "USERNAME", editorX, editorY);
        username.render(ctx, textRenderer, editorX, editorY + 8, editorW, 18);
        fieldLabel(ctx, "UUID", editorX, editorY + 28);
        uuidField.render(ctx, textRenderer, editorX, editorY + 36, editorW, 18);
        fieldLabel(ctx, "GROUP / RANK", editorX, editorY + 56);
        group.render(ctx, textRenderer, editorX, editorY + 64, editorW, 18);
        fieldLabel(ctx, "NOTES", editorX, editorY + 84);
        notes.render(ctx, textRenderer, editorX, editorY + 92, editorW, 18);

        String label = selected == null ? "Status: -" : "Status: " + selected.statusEnum().display();
        boolean hover = inside(mouseX, mouseY, statusBtnX, statusBtnY, statusBtnW, 18);
        RenderUtils.fillRounded(ctx, statusBtnX, statusBtnY, statusBtnW, 18, 4,
                hover ? CARD_HOVER : CARD);
        RenderUtils.drawText(ctx, textRenderer, label, statusBtnX + 8, statusBtnY + 5, TEXT, false);
        RenderUtils.drawText(ctx, textRenderer, "cycle", statusBtnX + statusBtnW - 8
                - textRenderer.getWidth("cycle"), statusBtnY + 5, TEXT_FAINT, false);

        String last = selected == null || selected.lastObserved == 0L
                ? "Last observed: never" : "Last observed: " + time(selected.lastObserved);
        RenderUtils.drawText(ctx, textRenderer, last, editorX, statusBtnY + 22, TEXT_FAINT, false);
        int evidence = selected == null || selected.evidence == null ? 0 : selected.evidence.size();
        RenderUtils.drawText(ctx, textRenderer, "Evidence records: " + evidence,
                editorX, statusBtnY + 34, TEXT_FAINT, false);
    }

    private void renderHistory(DrawContext ctx, int mouseX, int mouseY) {
        drawListFrame(ctx);
        List<StaffHistory.Record> rows = module.history().view(search.get(), "All", 40);
        int rowY = listY + 4 - scroll;
        for (StaffHistory.Record row : rows) {
            if (rowY + 24 < listY || rowY > listY + listH) {
                rowY += 24;
                continue;
            }
            boolean hover = inside(mouseX, mouseY, listX, rowY, listW, 22);
            if (hover) RenderUtils.fillRounded(ctx, listX, rowY, listW, 22, 4, CARD_HOVER);
            RenderUtils.drawText(ctx, textRenderer, row.time + "   " + row.name + "   " + row.confidence,
                    listX + 8, rowY + 3, TEXT, false);
            RenderUtils.drawText(ctx, textRenderer, trim(row.type + " - " + row.evidence, 235),
                    listX + 8, rowY + 12, TEXT_DIM, false);
            rowY += 24;
        }
        if (rows.isEmpty()) {
            RenderUtils.drawText(ctx, textRenderer, "No evidence recorded yet",
                    listX + 10, listY + 10, TEXT_DIM, false);
        }
        drawButton(ctx, "Clear", clearX, addY, clearW, mouseX, mouseY, false, false);
        drawButton(ctx, "Export CSV", csvX, addY, csvW, mouseX, mouseY, false, false);
    }

    private void drawFooter(DrawContext ctx) {
        int sepY = py + PANEL_H - 20;
        RenderUtils.fill(ctx, px + 14, sepY, px + PANEL_W - 14, sepY + 1, BORDER);
        RenderUtils.drawText(ctx, textRenderer, "ANORMAL  /  STAFF LIST", px + 14, sepY + 7, TEXT_FAINT, false);

        String right = (historyMode ? "evidence history" : shown.size() + " entries") + "  -  "
                + StaffTracker.serverKey();
        RenderUtils.drawText(ctx, textRenderer, trim(right, 220),
                px + PANEL_W - 14 - Math.min(220, textRenderer.getWidth(right)), sepY + 7,
                TEXT_FAINT, false);
    }

    private void section(DrawContext ctx, String label, int x, int y) {
        RenderUtils.drawText(ctx, textRenderer, label, x, y, TEXT_FAINT, false);
    }

    private void fieldLabel(DrawContext ctx, String label, int x, int y) {
        RenderUtils.drawText(ctx, textRenderer, label, x + 1, y, TEXT_FAINT, false);
    }

    private void drawButton(DrawContext ctx, String label, int x, int y, int w,
                            int mouseX, int mouseY, boolean disabled, boolean primary) {
        boolean hover = !disabled && inside(mouseX, mouseY, x, y, w, 18);
        int fill = disabled ? 0xFF1D1D22
                : primary ? (hover ? 0xFFFFD479 : ACCENT)
                : (hover ? CARD_HOVER : CARD);
        RenderUtils.fillRounded(ctx, x, y, w, 18, 4, fill);
        if (!primary) RenderUtils.drawBorder(ctx, x, y, x + w, y + 18, 1, BORDER);
        int color = disabled ? TEXT_FAINT : (primary ? 0xFF1A1A1A : TEXT);
        RenderUtils.drawText(ctx, textRenderer, label, x + (w - textRenderer.getWidth(label)) / 2,
                y + 5, color, false);
    }

    // ------------------------------------------------------------------
    //  Input
    // ------------------------------------------------------------------

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        return handleClick(click.x(), click.y(), click.button());
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return handleClick(mouseX, mouseY, button);
    }

    private boolean handleClick(double mouseX, double mouseY, int button) {
        if (inside(mouseX, mouseY, closeX, closeY, closeW, 16)) {
            close();
            return true;
        }
        if (inside(mouseX, mouseY, tabStaffX, tabY, tabW, tabH)) {
            historyMode = false;
            scroll = 0;
            return true;
        }
        if (inside(mouseX, mouseY, tabHistX, tabY, tabW, tabH)) {
            historyMode = true;
            scroll = 0;
            return true;
        }

        if (search.click(mouseX, mouseY, searchX, searchY, searchW, 18)) {
            focus(search);
            return true;
        }
        if (historyMode) {
            if (inside(mouseX, mouseY, clearX, addY, clearW, 18)) {
                module.history().clear();
                setStatus("History cleared", TEXT_DIM);
                return true;
            }
            if (inside(mouseX, mouseY, csvX, addY, csvW, 18)) {
                copy(module.history().toCsv());
                setStatus("History CSV copied to clipboard", TEXT_DIM);
                return true;
            }
            return true;
        }

        if (inside(mouseX, mouseY, addX, addY, addW, 18)) {
            addEntry();
            return true;
        }
        if (inside(mouseX, mouseY, saveX, addY, saveW, 18)) {
            saveEntry();
            return true;
        }
        if (inside(mouseX, mouseY, removeX, addY, removeW, 18)) {
            removeEntry();
            return true;
        }
        if (inside(mouseX, mouseY, importX, addY - 22, importW, 18)) {
            importList();
            return true;
        }
        if (inside(mouseX, mouseY, exportX, addY - 22, exportW, 18)) {
            copy(StaffListManager.exportJson());
            setStatus("Staff list JSON copied to clipboard", TEXT_DIM);
            return true;
        }
        if (inside(mouseX, mouseY, statusBtnX, statusBtnY, statusBtnW, 18) && selected != null) {
            selected.status = selected.statusEnum().next().name();
            StaffListManager.save();
            setStatus("Status: " + selected.statusEnum().display(), TEXT_DIM);
            return true;
        }

        if (username.click(mouseX, mouseY, editorX, editorY + 8, editorW, 18)) {
            focus(username);
            return true;
        }
        if (uuidField.click(mouseX, mouseY, editorX, editorY + 36, editorW, 18)) {
            focus(uuidField);
            return true;
        }
        if (group.click(mouseX, mouseY, editorX, editorY + 64, editorW, 18)) {
            focus(group);
            return true;
        }
        if (notes.click(mouseX, mouseY, editorX, editorY + 92, editorW, 18)) {
            focus(notes);
            return true;
        }

        for (int i = 0; i < rowYs.length && i < shown.size(); i++) {
            if (inside(mouseX, mouseY, listX, rowYs[i], listW, 28)) {
                select(shown.get(i));
                return true;
            }
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (historyMode) {
            int max = Math.max(0, module.history().view(search.get(), "All", 500).size() * 24 - listH);
            scroll = Math.max(0, Math.min(max, scroll - (int) (vertical * 24)));
        } else {
            int max = Math.max(0, shown.size() * 30 - listH);
            scroll = Math.max(0, Math.min(max, scroll - (int) (vertical * 30)));
        }
        return true;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        if (username.keyPressed(input)) return true;
        if (uuidField.keyPressed(input)) return true;
        if (group.keyPressed(input)) return true;
        if (notes.keyPressed(input)) return true;
        if (search.keyPressed(input)) return true;
        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
        if (username.charTyped(input)) return true;
        if (uuidField.charTyped(input)) return true;
        if (group.charTyped(input)) return true;
        if (notes.charTyped(input)) return true;
        if (search.charTyped(input)) return true;
        return super.charTyped(input);
    }

    // ------------------------------------------------------------------
    //  Actions
    // ------------------------------------------------------------------

    private void addEntry() {
        String name = username.get().trim();
        String id = uuidField.get().trim();
        if (name.isEmpty() && id.isEmpty()) {
            setStatus("Enter a username or a UUID first", RED);
            return;
        }
        StaffEntry entry = new StaffEntry(name);
        entry.uuid = id;
        entry.group = group.get().trim();
        entry.notes = notes.get().trim();
        entry.serverKey = module.useServerLists.isEnabled() ? StaffTracker.serverKey() : "";
        entry.evidence.add(new StaffEvidence("Manually added by user",
                "Added through the staff list screen", System.currentTimeMillis(),
                com.anormal.client.util.staff.StaffConfidence.MEDIUM, "Manually added by user",
                entry.serverKey));
        StaffListManager.add(entry);
        select(entry);
        setStatus("Added " + entry.displayName(), GREEN);
    }

    private void saveEntry() {
        if (selected == null) {
            setStatus("Select an entry first", RED);
            return;
        }
        String name = username.get().trim();
        String id = uuidField.get().trim();
        if (name.isEmpty() && id.isEmpty()) {
            setStatus("An entry needs a username or a UUID", RED);
            return;
        }
        selected.username = name;
        selected.uuid = id;
        selected.group = group.get().trim();
        selected.notes = notes.get().trim();
        StaffListManager.save();
        setStatus("Saved " + selected.displayName(), GREEN);
    }

    private void removeEntry() {
        if (selected == null) {
            setStatus("Select an entry first", RED);
            return;
        }
        if (!removeArmed) {
            removeArmed = true;
            setStatus("Click Confirm to delete this entry", ACCENT);
            return;
        }
        StaffListManager.remove(selected);
        removeArmed = false;
        selected = null;
        username.set("");
        uuidField.set("");
        group.set("");
        notes.set("");
        setStatus("Entry removed", TEXT_DIM);
    }

    private void importList() {
        String json = "";
        try {
            json = client == null ? "" : client.keyboard.getClipboard();
        } catch (Throwable ignored) {
        }
        int imported = StaffListManager.importJson(json);
        if (imported < 0) {
            setStatus("Clipboard does not contain a valid staff list", RED);
            return;
        }
        setStatus("Imported " + imported + " entries (list replaced)", GREEN);
    }

    private void select(StaffEntry entry) {
        selected = entry;
        removeArmed = false;
        username.set(entry.username == null ? "" : entry.username);
        uuidField.set(entry.uuid == null ? "" : entry.uuid);
        group.set(entry.group == null ? "" : entry.group);
        notes.set(entry.notes == null ? "" : entry.notes);
    }

    private void focus(TextField field) {
        search.setFocused(field == search);
        username.setFocused(field == username);
        uuidField.setFocused(field == uuidField);
        group.setFocused(field == group);
        notes.setFocused(field == notes);
    }

    private void copy(String text) {
        try {
            if (client != null) client.keyboard.setClipboard(text == null ? "" : text);
        } catch (Throwable ignored) {
        }
    }

    private void setStatus(String message, int color) {
        status = message == null ? "" : message;
        statusColor = color;
    }

    private boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private String trim(String value, int maxWidth) {
        if (value == null) return "";
        if (textRenderer.getWidth(value) <= maxWidth) return value;
        String out = value;
        while (out.length() > 3 && textRenderer.getWidth(out + "...") > maxWidth) {
            out = out.substring(0, out.length() - 1);
        }
        return out + "...";
    }

    private String time(long stamp) {
        return String.format(java.util.Locale.ROOT, "%tR", new java.util.Date(stamp));
    }
}
