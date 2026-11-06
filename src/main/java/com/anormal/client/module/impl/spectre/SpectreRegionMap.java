package com.anormal.client.module.impl.spectre;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.ColorUtils;
import com.anormal.client.util.RenderUtils;
import net.minecraft.client.gui.DrawContext;

/**
 * Spectre port: "Region Map" — a DonutSMP region map with a directional indicator.
 *
 * <p>Spectre renders the region grid with its own widget set; this port draws the same grid
 * (cell size, gap, player scale, text scale, opacity, shadow, region ids, legend, coordinates)
 * into the Anormal HUD pass and derives the region id from the 256-block region grid.</p>
 */
public final class SpectreRegionMap extends Module {
    private static final int REGION_SIZE = 256;

    public final NumberSetting cellSize = addSetting(new NumberSetting("Cell Size", "Size of one map cell", 22.0, 10.0, 50.0, 1.0));
    public final NumberSetting cellGap = addSetting(new NumberSetting("Cell Gap", "Gap between cells", 2.0, 0.0, 10.0, 1.0));
    public final NumberSetting gridSize = addSetting(new NumberSetting("Grid Size", "Cells per side (3 = 3x3, 5 = 5x5)", 3.0, 1.0, 9.0, 1.0));
    public final NumberSetting playerScale = addSetting(new NumberSetting("Player Scale", "Player indicator scale", 1.0, 0.5, 3.0, 0.1));
    public final NumberSetting textScale = addSetting(new NumberSetting("Text Scale", "Label scale", 1.0, 0.1, 2.0, 0.1));
    public final NumberSetting opacity = addSetting(new NumberSetting("Opacity", "Panel opacity", 1.0, 0.1, 1.0, 0.05));
    public final BooleanSetting center = addSetting(new BooleanSetting("Center", "Center the map on screen (otherwise X/Y Pos is used)", true));
    public final NumberSetting posX = addSetting(new NumberSetting("X Pos", "Map left edge (screen X) when not centered", 0.0, -2000.0, 4000.0, 1.0));
    public final NumberSetting posY = addSetting(new NumberSetting("Y Pos", "Map top edge (screen Y) when not centered", 0.0, -2000.0, 4000.0, 1.0));
    public final BooleanSetting shadow = addSetting(new BooleanSetting("Shadow", "Draw text with a shadow", true));
    public final BooleanSetting showRegionIds = addSetting(new BooleanSetting("Show Region IDs", "Label each cell with its region id", true));
    public final BooleanSetting showLegend = addSetting(new BooleanSetting("Show Legend", "Show the region legend", true));
    public final BooleanSetting showCoordinates = addSetting(new BooleanSetting("Show Coordinates", "Show your block coordinates", true));
    public final ColorSetting playerIndicatorColor = addSetting(new ColorSetting("Player Indicator", "Colour of your marker", ColorUtils.rgba(0, 255, 170, 255)));
    public final BooleanSetting showCompass = addSetting(new BooleanSetting("Compass Arrow", "Show which way you are facing (needle on your marker, N at the top)", true));
    public final NumberSetting arrowSize = addSetting(new NumberSetting("Arrow Size", "Compass needle length", 9.0, 4.0, 20.0, 1.0));
    public final ColorSetting arrowColor = addSetting(new ColorSetting("Arrow Color", "Compass needle colour", 0xFFFF5555, false));

    public SpectreRegionMap() {
        super("SpectreRegionMap", "Spectre port: a DonutSMP region map with a directional indicator.", Category.SPECTRE);
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        var player = mc.player;
        if (player == null || mc.world == null || mc.options.hudHidden) return;

        int cell = (int) cellSize.getAsDouble();
        int gap = (int) cellGap.getAsDouble();
        int span = cell + gap;
        int cells = Math.max(1, (int) gridSize.getAsDouble());
        int min = -(cells / 2);
        int max = min + cells - 1;
        boolean centered = center.isEnabled();
        int originX = centered
                ? mc.getWindow().getScaledWidth() / 2 - span * cells / 2
                : (int) posX.getAsDouble();
        int originY = centered
                ? mc.getWindow().getScaledHeight() / 2 - span * cells / 2
                : (int) posY.getAsDouble();

        int centreRegionX = Math.floorDiv((int) player.getX(), REGION_SIZE);
        int centreRegionZ = Math.floorDiv((int) player.getZ(), REGION_SIZE);
        int alpha = (int) (opacity.getAsDouble() * 255.0) << 24;

        for (int dz = min; dz <= max; dz++) {
            for (int dx = min; dx <= max; dx++) {
                int regionX = centreRegionX + dx;
                int regionZ = centreRegionZ + dz;
                int x1 = originX + (dx - min) * span;
                int y1 = originY + (dz - min) * span;
                int x2 = x1 + cell;
                int y2 = y1 + cell;
                boolean here = dx == 0 && dz == 0;
                int tint = regionTint(regionX, regionZ);
                context.fill(x1, y1, x2, y2, (tint & 0x00FFFFFF) | (here ? 0xCC000000 : 0x99000000 | 0));
                context.fill(x1, y1, x2, y1 + 1, (tint & 0x00FFFFFF) | alpha);
                context.fill(x1, y2 - 1, x2, y2, (tint & 0x00FFFFFF) | alpha);
                context.fill(x1, y1, x1 + 1, y2, (tint & 0x00FFFFFF) | alpha);
                context.fill(x2 - 1, y1, x2, y2, (tint & 0x00FFFFFF) | alpha);

                if (showRegionIds.isEnabled()) {
                    String id = regionId(regionX, regionZ);
                    drawScaledText(context, id, x1 + 2, y1 + 2, 0xFFFFFFFF);
                }
                if (here) {
                    int markerX = (int) (x1 + cell * fraction(player.getX(), regionX));
                    int markerZ = (int) (y1 + cell * fraction(player.getZ(), regionZ));
                    int size = Math.max(1, (int) (2 * playerScale.getAsDouble()));
                    context.fill(markerX - size, markerZ - size, markerX + size, markerZ + size, playerIndicatorColor.getColor());

                    // Compass needle on the player marker. The map is north-up (+X right,
                    // +Z down), so the facing vector maps straight onto screen axes.
                    if (showCompass.isEnabled()) {
                        double yaw = Math.toRadians(player.getYaw(tickDelta));
                        double dirX = -Math.sin(yaw);
                        double dirY = -Math.cos(yaw);
                        double len = Math.min(arrowSize.getAsDouble(), cell * 0.45);
                        int tipX = (int) Math.round(markerX + dirX * len);
                        int tipY = (int) Math.round(markerZ + dirY * len);
                        int ac = arrowColor.getColor();
                        RenderUtils.drawLine(context, markerX, markerZ, tipX, tipY, ac, 1.5f);
                        double angle = Math.atan2(dirY, dirX);
                        for (double off : new double[]{Math.toRadians(150), Math.toRadians(-150)}) {
                            int bx = (int) Math.round(tipX - Math.cos(angle + off) * len * 0.55);
                            int by = (int) Math.round(tipY - Math.sin(angle + off) * len * 0.55);
                            RenderUtils.drawLine(context, tipX, tipY, bx, by, ac, 1.5f);
                        }
                    }
                }
            }
        }

        if (showCompass.isEnabled() && originY >= 10) {
            drawScaledText(context, "N", originX + span * cells / 2 - 2, originY - 9, 0xFFFFFFFF);
        }

        int textY = originY + span * cells + 4;
        if (showCoordinates.isEnabled()) {
            drawScaledText(context,
                    String.format("X %d  Z %d  (region %s)", (int) player.getX(), (int) player.getZ(),
                            regionId(centreRegionX, centreRegionZ)),
                    originX, textY, 0xFFFFFFFF);
            textY += (int) (10 * textScale.getAsDouble());
        }
        if (showLegend.isEnabled()) {
            drawScaledText(context, "green = you · border = current region",
                    originX, textY, 0xFFAAAAAA);
        }
    }

    /** Draws a label scaled by Text Scale (vanilla text has one size only). */
    private void drawScaledText(DrawContext context, String text, int x, int y, int color) {
        float scale = textScale.getValue().floatValue();
        if (scale <= 0.0F) return;
        var matrices = context.getMatrices();
        matrices.pushMatrix();
        matrices.translate((float) x, (float) y);
        matrices.scale(scale, scale);
        context.drawText(mc.textRenderer, text, 0, 0, color, shadow.isEnabled());
        matrices.popMatrix();
    }

    private static double fraction(double coordinate, int region) {
        double offset = coordinate - (double) region * REGION_SIZE;
        return Math.max(0.0, Math.min(1.0, offset / REGION_SIZE));
    }

    private static String regionId(int regionX, int regionZ) {
        char letter = (char) ('A' + Math.floorMod(regionX, 26));
        int number = Math.floorMod(regionZ, 100);
        return "" + letter + number;
    }

    private static int regionTint(int regionX, int regionZ) {
        int hash = regionX * 374761393 + regionZ * 668265263;
        hash = (hash ^ (hash >> 13)) * 1274126177;
        int r = 60 + Math.floorMod(hash, 90);
        int g = 60 + Math.floorMod(hash >> 8, 90);
        int b = 90 + Math.floorMod(hash >> 16, 90);
        return (r << 16) | (g << 8) | b;
    }
}
