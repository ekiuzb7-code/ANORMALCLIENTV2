package com.anormal.client.module.impl.legit;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.theme.ThemeManager;
import com.anormal.client.util.HudStyle;
import com.anormal.client.util.RenderUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffectUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * Active potion effects.
 *
 * <p>Modernised from loose right-aligned strips into one anchored panel: header with the effect
 * count, then a row per effect with its level in Roman numerals and the remaining time on the
 * right, sorted so the ones about to expire are last.</p>
 */
public class PotionStatus extends Module {
    public final NumberSetting posX = new NumberSetting("Pos X", "Right-edge anchor X", 1918.0, 0.0, 1920.0, 1.0);
    public final NumberSetting posY = new NumberSetting("Pos Y", "Top anchor Y", 900.0, 0.0, 1080.0, 1.0);
    public final NumberSetting scale = new NumberSetting("Scale", "HUD scale (0.05 = tiny, 2.0 = huge)", 1.0, 0.05, 2.0, 0.01);

    private static final int MAX_ROWS = 10;

    public PotionStatus() {
        super("PotionStatus", "Displays active potion effects and remaining durations on HUD", Category.LEGIT);
        addSetting(posX);
        addSetting(posY);
        addSetting(scale);
    }

    private static String roman(int n) {
        return switch (n) {
            case 1 -> "";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> n > 5 ? "VI+" : "";
        };
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.player == null || mc.textRenderer == null) return;
        try {
            Collection<StatusEffectInstance> active = mc.player.getStatusEffects();
            if (active.isEmpty()) return;

            List<StatusEffectInstance> effects = new ArrayList<>(active);
            // Instant effects first (they matter most), then the longest lasting.
            effects.sort(Comparator
                    .comparingInt((StatusEffectInstance e) -> e.getDuration() == -1 ? 0 : 1)
                    .thenComparingInt(StatusEffectInstance::getDuration));
            int rows = Math.min(MAX_ROWS, effects.size());

            int nameW = 0;
            for (int i = 0; i < rows; i++) {
                nameW = Math.max(nameW, mc.textRenderer.getWidth(label(effects.get(i))));
            }
            int w = nameW + 92;
            int rowH = 13;
            int h = 24 + rows * rowH + 4;

            int rightEdge = posX.getValue().intValue();
            int[] at = HudStyle.place(rightEdge - w, posY.getValue().intValue(), w, h);
            int x = at[0], y = at[1];
            int desired = rightEdge - w;
            if (desired >= 2 && desired + w <= RenderUtils.screenWidth() - 2) x = desired;

            int accent = ThemeManager.getAccentColor();
            HudStyle.panel(context, x, y, w, h, accent);
            HudStyle.title(context, mc.textRenderer, "Effects", x + 10, y + 7);
            String count = String.valueOf(active.size());
            int cw = mc.textRenderer.getWidth(count);
            RenderUtils.drawText(context, mc.textRenderer, count, x + w - 10 - cw, y + 7,
                    0xFF868A99, true);

            int ly = y + 24;
            for (int i = 0; i < rows; i++) {
                StatusEffectInstance effect = effects.get(i);
                boolean expiring = effect.getDuration() >= 0 && effect.getDuration() < 200;
                int rowColor = expiring ? 0xFFFBBF24 : 0xFFDDE1E9;

                RenderUtils.fill(context, x + 10, ly + 4, x + 13, ly + 8, accent);
                RenderUtils.drawText(context, mc.textRenderer,
                        HudStyle.trim(mc.textRenderer, label(effect), nameW), x + 18, ly + 1,
                        rowColor, false);

                String duration = StatusEffectUtil.getDurationText(effect, 1.0f, 20.0f).getString();
                int dw = mc.textRenderer.getWidth(duration);
                RenderUtils.drawText(context, mc.textRenderer, duration, x + w - 10 - dw, ly + 1,
                        expiring ? 0xFFFBBF24 : 0xFF8A90A0, false);
                ly += rowH;
            }
        } catch (Throwable ignored) {}
    }

    private String label(StatusEffectInstance effect) {
        String name = effect.getEffectType().value().getName().getString();
        String level = roman(effect.getAmplifier() + 1);
        return level.isEmpty() ? name : name + " " + level;
    }
}
