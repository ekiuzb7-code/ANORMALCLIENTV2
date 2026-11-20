package com.anormal.client.module.impl.legit;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.theme.ThemeManager;
import com.anormal.client.util.ColorUtils;
import com.anormal.client.util.HudStyle;
import com.anormal.client.util.RenderUtils;
import net.minecraft.client.gui.DrawContext;

/**
 * Shows how far the last landed hit was.
 *
 * <p><b>Why it was invisible:</b> {@link #updateReach(double)} had no caller anywhere in the
 * client, so {@code lastHitTime} stayed {@code 0} and the old {@code > 5000ms} guard returned
 * before drawing a single pixel. It is now fed from
 * {@code InteractionManagerMixin#afterAttack}, the choke point every attack (vanilla click and
 * aura alike) goes through.</p>
 *
 * <p><b>Why it slid off screen:</b> it was the only overlay using centre-based coordinates
 * ({@code screenHeight / 2 + Pos Y}, X centred on the text), so the HUD editor's absolute drag
 * clamp could not keep it reachable and the stored Y could go negative. Positions are now plain
 * screen coordinates like every other overlay, and the panel is clamped on screen while drawing.</p>
 */
public class ReachDisplay extends Module {
    public final NumberSetting posX = new NumberSetting("Pos X", "Panel X position", 8.0, 0.0, 1920.0, 1.0);
    public final NumberSetting posY = new NumberSetting("Pos Y", "Panel Y position", 8.0, 0.0, 1080.0, 1.0);
    public final NumberSetting scale = new NumberSetting("Scale", "HUD scale (0.05 = tiny, 2.0 = huge)", 1.0, 0.05, 2.0, 0.01);
    public final BooleanSetting showBar = new BooleanSetting("Show Bar", "Draw the reach meter", true);
    public final BooleanSetting keepValue = new BooleanSetting("Keep Value", "Keep showing the last reach after 5s", true);
    public final ColorSetting color = new ColorSetting("Color", "Accent + meter color",
            ColorUtils.rgba(255, 85, 0, 255));

    /** Longest reach the meter maps to, in blocks. */
    private static final double METER_MAX = 6.0;

    private static double lastReach = 0.0;
    private static long lastHitTime = 0L;
    private static boolean hasHit = false;

    public ReachDisplay() {
        super("ReachDisplay", "Displays the distance of your last landed attack on HUD", Category.LEGIT);
        addSetting(posX);
        addSetting(posY);
        addSetting(scale);
        addSetting(showBar);
        addSetting(keepValue);
        addSetting(color);
    }

    /** Called by the attack mixin with the eye-to-hitbox distance of the attack just sent. */
    public static void updateReach(double reach) {
        if (reach <= 0.0 || !Double.isFinite(reach)) return;
        lastReach = reach;
        lastHitTime = System.currentTimeMillis();
        hasHit = true;
    }

    @Override
    public void onDisable() {
        hasHit = false;
        lastReach = 0.0;
        lastHitTime = 0L;
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.textRenderer == null) return;
        try {
            long age = System.currentTimeMillis() - lastHitTime;
            boolean fresh = hasHit && age < 5000L;
            if (!fresh && !keepValue.isEnabled()) return;

            int accent = color.getValue();
            // A recent hit glows at full strength, an old one dims so it is clearly stale.
            int body = fresh ? accent : (accent & 0x00FFFFFF) | 0x66000000;
            int textValue = fresh ? 0xFFFFFFFF : 0xFF9AA0AE;

            String value = hasHit ? String.format(java.util.Locale.ROOT, "%.2f", lastReach) : "--";
            int valueW = mc.textRenderer.getWidth(value);

            int w = Math.max(132, 14 + mc.textRenderer.getWidth("REACH") + 14 + valueW + 16);
            int h = showBar.isEnabled() ? 40 : 30;
            int[] at = HudStyle.place(posX.getValue().intValue(), posY.getValue().intValue(), w, h);
            int x = at[0], y = at[1];

            HudStyle.panel(context, x, y, w, h, body);
            HudStyle.title(context, mc.textRenderer, "Reach", x + 10, y + 8);
            RenderUtils.drawText(context, mc.textRenderer, value, x + w - 10 - valueW, y + 5,
                    textValue, true);
            RenderUtils.drawText(context, mc.textRenderer, "m", x + w - 8,
                    y + 6 + mc.textRenderer.fontHeight, 0xFF868A99, false);

            if (showBar.isEnabled()) {
                float frac = hasHit ? (float) (lastReach / METER_MAX) : 0.0f;
                HudStyle.bar(context, x + 10, y + h - 11, w - 20, 3, frac, body);
            }
        } catch (Throwable ignored) {
        }
    }
}
