package com.anormal.client.module.impl.legit;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.theme.ThemeManager;
import com.anormal.client.util.HudStyle;
import com.anormal.client.util.RenderUtils;
import net.minecraft.client.gui.DrawContext;

/**
 * Compass ribbon for the top of the screen.
 *
 * <p>Modernised from a single "DIR S 180°" card into a real ribbon: the four cardinal letters slide
 * horizontally with your yaw, minor ticks every 15°, and a fixed accent marker in the middle shows
 * the heading you are looking at.</p>
 *
 * <p>Its positions are absolute screen coordinates now (they used to be centre-based, which let the
 * stored X/Y drift off screen); {@link HudStyle#place} keeps the whole ribbon visible.</p>
 */
public class Compass extends Module {
    public final NumberSetting posX = new NumberSetting("Pos X", "X Position on screen", 860.0, 0.0, 1920.0, 1.0);
    public final NumberSetting posY = new NumberSetting("Pos Y", "Y Position on screen", 4.0, 0.0, 1080.0, 1.0);
    public final NumberSetting scale = new NumberSetting("Scale", "HUD scale (0.05 = tiny, 2.0 = huge)", 1.0, 0.05, 2.0, 0.01);

    private static final int WIDTH = 220;
    private static final int HEIGHT = 30;
    /** How many degrees of the horizon the ribbon shows. */
    private static final double SPAN = 120.0;

    public Compass() {
        super("Compass", "Displays a directional compass ribbon on the top HUD", Category.LEGIT);
        addSetting(posX);
        addSetting(posY);
        addSetting(scale);
    }

    /** Shortest signed difference between two angles, in degrees. */
    private static double wrap(double degrees) {
        double d = degrees % 360.0;
        if (d > 180.0) d -= 360.0;
        if (d < -180.0) d += 360.0;
        return d;
    }

    private static String cardinal(double yaw) {
        if (yaw >= 315 || yaw < 45) return "S";
        if (yaw < 135) return "W";
        if (yaw < 225) return "N";
        return "E";
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.player == null || mc.textRenderer == null) return;
        try {
            float yaw = (mc.player.getYaw() % 360 + 360) % 360;
            int accent = ThemeManager.getAccentColor();

            int[] at = HudStyle.place(posX.getValue().intValue(), posY.getValue().intValue(), WIDTH, HEIGHT);
            int x = at[0], y = at[1];
            int cx = x + WIDTH / 2;

            HudStyle.panel(context, x, y, WIDTH, HEIGHT, accent);

            // Minor ticks every 15 degrees, fading out towards the ribbon edges.
            for (int deg = 0; deg < 360; deg += 15) {
                double delta = wrap(deg - yaw);
                if (Math.abs(delta) > SPAN / 2) continue;
                int tx = cx + (int) Math.round(delta / (SPAN / 2) * (WIDTH / 2 - 12));
                boolean major = deg % 90 == 0;
                int tickColor = major ? 0x88FFFFFF : 0x33FFFFFF;
                RenderUtils.fill(context, tx, y + (major ? 5 : 7), tx + 1, y + 9,
                        tickColor);
            }

            // Cardinal letters.
            double[] headings = {0, 90, 180, 270};
            String[] labels = {"S", "W", "N", "E"};
            for (int i = 0; i < headings.length; i++) {
                double delta = wrap(headings[i] - yaw);
                if (Math.abs(delta) > SPAN / 2) continue;
                int tx = cx + (int) Math.round(delta / (SPAN / 2) * (WIDTH / 2 - 12));
                int lw = mc.textRenderer.getWidth(labels[i]);
                boolean facing = Math.abs(delta) < 22.5;
                RenderUtils.drawText(context, mc.textRenderer, labels[i], tx - lw / 2, y + 9,
                        facing ? accent : 0xFF8A90A0, facing);
            }

            // Centre marker + heading readout.
            RenderUtils.fill(context, cx, y + 4, cx + 1, y + 12, accent);
            RenderUtils.fill(context, cx - 2, y + 4, cx + 3, y + 5, accent);

            String heading = cardinal(yaw) + "  " + Math.round(yaw) + "\u00b0";
            int hw = mc.textRenderer.getWidth(heading);
            RenderUtils.drawText(context, mc.textRenderer, heading, cx - hw / 2, y + 17,
                    0xFFDFE3EA, false);
        } catch (Throwable ignored) {}
    }
}
