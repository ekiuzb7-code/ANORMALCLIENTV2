package com.anormal.client.module.impl.spectre;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.ColorUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.Box;

/**
 * Spectre port: "Spectral Wisps" — ghosts, stars, moons and wisps drift around your player.
 *
 * <p>Spectre draws glyph meshes; this port keeps the motion model (gravity, buoyancy, air
 * drag, orbit pull, wander, spin, bounce off blocks) and the count, size, spread, drift,
 * colour and halo settings, drawing each relic as an orbiting box.</p>
 */
public final class SpectreFloatingRelics extends Module {
    private static final float GRAVITY = 0.016F;
    private static final float BUOYANCY = 0.045F;
    private static final float AIR_DRAG = 0.982F;

    public final ModeSetting symbols = addSetting(new ModeSetting("Symbols", "Relic shape", "Mixed", "Mixed", "Ghost", "Star", "Crescent", "Wisp", "Crystal", "Orbit"));
    public final NumberSetting count = addSetting(new NumberSetting("Count", "How many relics orbit you", 9.0, 1.0, 32.0, 1.0));
    public final NumberSetting size = addSetting(new NumberSetting("Size", "Relic size", 0.3, 0.15, 0.75, 0.05));
    public final NumberSetting spread = addSetting(new NumberSetting("Spread", "Orbit radius", 2.6, 1.2, 6.0, 0.1));
    public final NumberSetting bounce = addSetting(new NumberSetting("Bounce", "Vertical bounce", 0.6, 0.1, 0.95, 0.05));
    public final NumberSetting drift = addSetting(new NumberSetting("Drift", "Wander strength", 0.5, 0.0, 2.5, 0.1));
    public final NumberSetting spinSpeed = addSetting(new NumberSetting("Spin", "Orbit speed", 1.0, 0.2, 3.0, 0.1));
    public final ModeSetting colorMode = addSetting(new ModeSetting("Color", "Theme or manual colours", "Theme", "Theme", "Manual"));
    public final ColorSetting mainColor = addSetting(new ColorSetting("Main Color", "Primary relic colour", ColorUtils.rgba(90, 220, 220, 255)));
    public final ColorSetting secondColor = addSetting(new ColorSetting("Second Color", "Secondary relic colour", ColorUtils.rgba(160, 130, 255, 255)));
    public final BooleanSetting halo = addSetting(new BooleanSetting("Halo", "Draw a halo ring around each relic", true));
    public final BooleanSetting blockBounce = addSetting(new BooleanSetting("Block Bounce", "Bounce off solid blocks", true));

    private static final String[] SYMBOLS = {"Ghost", "Star", "Crescent", "Wisp", "Crystal", "Orbit"};

    private float phase;
    private double[] heights = new double[0];

    public SpectreFloatingRelics() {
        super("SpectreFloatingRelics", "Spectre port: ghosts, stars and wisps drift around your player.", Category.SPECTRE);
    }

    @Override
    public void onEnable() {
        phase = 0.0F;
        heights = new double[(int) count.getAsDouble()];
    }

    @Override
    public void onTick() {
        phase += 0.02F * spinSpeed.getValue().floatValue();
        int wanted = (int) count.getAsDouble();
        if (heights.length != wanted) heights = new double[wanted];
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        var player = mc.player;
        if (player == null || mc.world == null || mc.options.hudHidden) return;

        int total = (int) count.getAsDouble();
        float radius = spread.getValue().floatValue();
        float scale = size.getValue().floatValue();
        int main = colorMode.is("Manual") ? mainColor.getColor() : ColorUtils.rgba(90, 220, 220, 255);
        int second = colorMode.is("Manual") ? secondColor.getColor() : ColorUtils.rgba(160, 130, 255, 255);
        float bob = bounce.getValue().floatValue();
        float wander = drift.getValue().floatValue();

        for (int i = 0; i < total; i++) {
            double angle = phase + i * (Math.PI * 2.0 / Math.max(1, total));
            double wobble = Math.sin(phase * 1.7 + i) * wander;
            double x = player.getX() + Math.cos(angle) * (radius + wobble);
            double z = player.getZ() + Math.sin(angle) * (radius + wobble);
            double base = player.getY() + 0.6 + Math.sin(phase * 2.0 + i * 0.7) * bob * 1.6;
            double y = base + heights[i] * 0.4;

            if (blockBounce.isEnabled() && mc.world != null) {
                boolean solid = !mc.world.getBlockState(net.minecraft.util.math.BlockPos.ofFloored(x, y - 0.1, z)).isAir();
                if (solid) y += BUOYANCY;
                else if (heights[i] > -1.2) heights[i] -= GRAVITY;
                heights[i] *= AIR_DRAG;
                heights[i] = Math.max(-1.2, Math.min(1.2, heights[i]));
            }

            double half = scale * 0.5;
            Box box = new Box(x - half, y - half, z - half, x + half, y + half, z + half);
            int color = ((i % 2 == 0) ? main : second);
            String symbol = symbols.is("Mixed") ? SYMBOLS[i % SYMBOLS.length] : symbols.getValue();
            drawSymbol(context, tickDelta, symbol, x, y, z, half, color);
            if (halo.isEnabled()) {
                SpectreHoleEsp.drawBox(context, tickDelta, box.expand(scale * 0.6), (color & 0x00FFFFFF) | 0x55FFFFFF, 0, true, false, 1);
            }
        }
    }

    private static void drawSymbol(DrawContext context, float tickDelta, String symbol,
                                   double x, double y, double z, double half, int color) {
        int fill = (color & 0x00FFFFFF) | 0x33000000;
        switch (symbol) {
            case "Ghost" -> SpectreHoleEsp.drawBox(context, tickDelta,
                    new Box(x - half * 0.75, y - half * 1.5, z - half * 0.75, x + half * 0.75, y + half * 1.5, z + half * 0.75),
                    color, fill, true, true, 1);
            case "Star" -> {
                SpectreHoleEsp.drawBox(context, tickDelta, new Box(x - half * 1.4, y - half * 0.18, z - half * 0.18, x + half * 1.4, y + half * 0.18, z + half * 0.18), color, 0, true, false, 1);
                SpectreHoleEsp.drawBox(context, tickDelta, new Box(x - half * 0.18, y - half * 1.4, z - half * 0.18, x + half * 0.18, y + half * 1.4, z + half * 0.18), color, 0, true, false, 1);
                SpectreHoleEsp.drawBox(context, tickDelta, new Box(x - half * 0.18, y - half * 0.18, z - half * 1.4, x + half * 0.18, y + half * 0.18, z + half * 1.4), color, 0, true, false, 1);
            }
            case "Crescent" -> {
                for (int k = 0; k < 6; k++) {
                    double a = Math.PI * 0.65 + k * (Math.PI * 0.7 / 5.0);
                    double cx = x + Math.cos(a) * half * 1.2;
                    double cy = y + Math.sin(a) * half * 1.2;
                    SpectreHoleEsp.drawBox(context, tickDelta,
                            new Box(cx - half * 0.28, cy - half * 0.28, z - half * 0.28,
                                    cx + half * 0.28, cy + half * 0.28, z + half * 0.28), color, fill, true, true, 1);
                }
            }
            case "Wisp" -> {
                SpectreHoleEsp.drawBox(context, tickDelta, new Box(x - half * 0.55, y - half * 0.55, z - half * 0.55, x + half * 0.55, y + half * 0.55, z + half * 0.55), color, fill, true, true, 1);
                SpectreHoleEsp.drawBox(context, tickDelta, new Box(x + half * 0.7, y + half * 0.2, z - half * 0.2, x + half * 1.0, y + half * 0.5, z + half * 0.2), color, 0, true, false, 1);
            }
            case "Crystal" -> {
                SpectreHoleEsp.drawBox(context, tickDelta, new Box(x - half * 0.6, y, z - half * 0.6, x + half * 0.6, y + half, z + half * 0.6), color, fill, true, true, 1);
                SpectreHoleEsp.drawBox(context, tickDelta, new Box(x - half * 0.6, y - half, z - half * 0.6, x + half * 0.6, y, z + half * 0.6), color, fill, true, true, 1);
            }
            default -> SpectreHoleEsp.drawBox(context, tickDelta,
                    new Box(x - half, y - half, z - half, x + half, y + half, z + half), color, fill, true, true, 1);
        }
    }
}
