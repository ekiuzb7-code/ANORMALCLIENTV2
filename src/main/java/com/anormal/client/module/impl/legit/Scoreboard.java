package com.anormal.client.module.impl.legit;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.theme.ThemeManager;
import com.anormal.client.util.HudStyle;
import com.anormal.client.util.RenderUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardEntry;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * Repositionable sidebar scoreboard.
 *
 * <p>Modernised: accent header with the objective name, alternating row stripes, right-aligned
 * scores that turn red when negative, and every line is trimmed to the panel instead of running
 * past its edge.</p>
 */
public class Scoreboard extends Module {
    public final NumberSetting posX = new NumberSetting("Pos X", "Right-edge anchor X", 1914.0, 0.0, 1920.0, 1.0);
    public final NumberSetting posY = new NumberSetting("Pos Y", "Top anchor Y", 60.0, 0.0, 1080.0, 1.0);
    public final NumberSetting scale = new NumberSetting("Scale", "HUD scale (0.05 = tiny, 2.0 = huge)", 1.0, 0.05, 2.0, 0.01);
    public final NumberSetting maxLines = new NumberSetting("Max Lines", "Max sidebar lines shown", 12.0, 3.0, 20.0, 1.0);
    public final BooleanSetting showNumbers = new BooleanSetting("Show Numbers", "Show sidebar scores", false);

    public Scoreboard() {
        super("Scoreboard", "Repositionable sidebar scoreboard display", Category.LEGIT);
        addSetting(posX);
        addSetting(posY);
        addSetting(scale);
        addSetting(maxLines);
        addSetting(showNumbers);
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.world == null || mc.textRenderer == null) return;
        try {
            net.minecraft.scoreboard.Scoreboard board = mc.world.getScoreboard();
            ScoreboardObjective objective = board.getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR);
            if (objective == null) return;

            List<String> names = new ArrayList<>();
            List<Integer> values = new ArrayList<>();
            for (ScoreboardEntry entry : board.getScoreboardEntries(objective)) {
                String name = entry.owner();
                if (name == null || name.startsWith("#")) continue;
                names.add(name);
                values.add(entry.value());
                if (names.size() >= maxLines.getValue().intValue()) break;
            }
            if (names.isEmpty()) return;

            Text title = objective.getDisplayName();
            String titleText = title.getString();
            int valueW = 0;
            if (showNumbers.isEnabled()) {
                for (int v : values) valueW = Math.max(valueW, mc.textRenderer.getWidth(String.valueOf(v)) + 8);
            }

            int textW = mc.textRenderer.getWidth(titleText);
            int bodyW = 0;
            for (String n : names) {
                bodyW = Math.max(bodyW, mc.textRenderer.getWidth(stripCodes(n)));
            }
            int contentW = Math.max(textW, bodyW + valueW);

            int w = contentW + 20;
            int rowH = 11;
            int h = 24 + names.size() * rowH + 4;

            int rightEdge = posX.getValue().intValue();
            int[] at = HudStyle.place(rightEdge - w, posY.getValue().intValue(), w, h);
            int x = at[0], y = at[1];
            // Keep hugging the requested right edge when there is room for it.
            int desired = rightEdge - w;
            if (desired >= 2 && desired + w <= RenderUtils.screenWidth() - 2) x = desired;

            int accent = ThemeManager.getAccentColor();
            HudStyle.panel(context, x, y, w, h, accent);

            int tw = mc.textRenderer.getWidth(titleText);
            RenderUtils.drawText(context, mc.textRenderer, titleText, x + (w - tw) / 2, y + 7,
                    accent, true);
            RenderUtils.fill(context, x + 8, y + 20, x + w - 8, y + 21, 0x22FFFFFF);

            int ly = y + 24;
            for (int i = 0; i < names.size(); i++) {
                if ((i & 1) == 0) {
                    RenderUtils.fill(context, x + 6, ly - 1, x + w - 6, ly + rowH - 2, 0x0CFFFFFF);
                }
                String label = HudStyle.trim(mc.textRenderer, stripCodes(names.get(i)),
                        w - 20 - valueW);
                RenderUtils.drawText(context, mc.textRenderer, label, x + 10, ly, 0xFFDDE1E9, false);

                if (showNumbers.isEnabled()) {
                    int value = values.get(i);
                    String valueText = String.valueOf(value);
                    int vw = mc.textRenderer.getWidth(valueText);
                    RenderUtils.drawText(context, mc.textRenderer, valueText, x + w - 10 - vw, ly,
                            value < 0 ? 0xFFF87171 : 0xFF8A90A0, false);
                }
                ly += rowH;
            }
        } catch (Throwable ignored) {}
    }

    /** Sidebar entries can contain legacy colour codes; they confuse width maths. */
    private static String stripCodes(String text) {
        if (text == null) return "";
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\u00a7' && i + 1 < text.length()) {
                i++;
                continue;
            }
            out.append(c);
        }
        return out.toString();
    }
}
