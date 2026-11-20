package com.anormal.client.module.impl.render;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.ColorUtils;
import com.anormal.client.util.ProjectionUtil;
import com.anormal.client.util.RenderUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * Marks armor stands.
 *
 * <p>Modernised: markers are corner brackets sized from the stand's real AABB, the colour fades
 * with distance, the tracer is one anti-aliased line instead of a loop of pixels and the label is
 * drawn as a chip so it stays readable over any background.</p>
 */
public class ArmorStandESP extends Module {
    public final BooleanSetting glow = new BooleanSetting("Glow", "Give armor stands a glowing outline", true);
    public final BooleanSetting box = new BooleanSetting("Box", "Draw a box marker", true);
    public final BooleanSetting tracer = new BooleanSetting("Tracer", "Line from the crosshair", false);
    public final BooleanSetting showDistance = new BooleanSetting("Show Distance", "Show the distance", true);
    public final BooleanSetting showName = new BooleanSetting("Show Name", "Show the armor stand's custom name", false);
    public final NumberSetting range = new NumberSetting("Range", "Max distance", 48.0, 8.0, 128.0, 8.0);
    public final NumberSetting verticalRange = new NumberSetting("Vertical Range",
            "How far above and below you to search", 48.0, 8.0, 160.0, 4.0);
    public final NumberSetting lineWidth = new NumberSetting("Line Width", "Tracer line width", 1.0, 0.5, 3.0, 0.5);
    public final ColorSetting color = new ColorSetting("Color", "Marker color", ColorUtils.rgba(255, 220, 0, 255));

    public ArmorStandESP() {
        super("ArmorStandESP", "Marks armor stands", Category.RENDER);
        addSetting(glow);
        addSetting(box);
        addSetting(tracer);
        addSetting(showDistance);
        addSetting(showName);
        addSetting(range);
        addSetting(verticalRange);
        addSetting(lineWidth);
        addSetting(color);
    }

    @Override
    public void onTick() {
        if (mc.world == null || mc.player == null) return;
        double max = range.getValue();
        for (Entity e : mc.world.getEntities()) {
            if (!(e instanceof ArmorStandEntity)) continue;
            try {
                e.setGlowing(glow.isEnabled() && mc.player.distanceTo(e) <= max);
            } catch (Throwable ignored) {
            }
        }
    }

    @Override
    public void onDisable() {
        if (mc.world == null) return;
        for (Entity e : mc.world.getEntities()) {
            if (e instanceof ArmorStandEntity) {
                try {
                    e.setGlowing(false);
                } catch (Throwable ignored) {
                }
            }
        }
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.player == null || mc.world == null || mc.textRenderer == null) return;
        try {
            int centreX = RenderUtils.screenWidth() / 2;
            int centreY = RenderUtils.screenHeight() / 2;
            float lw = lineWidth.getValue().floatValue();
            int base = color.getValue();
            double max = range.getValue();

            for (Entity e : mc.world.getEntities()) {
                try {
                    if (!(e instanceof ArmorStandEntity) || !e.isAlive()) continue;
                    double d = mc.player.distanceTo(e);
                    if (d > max) continue;
                    if (Math.abs(e.getY() - mc.player.getY()) > verticalRange.getValue()) continue;

                    // Interpolated position + real AABB: no jitter, correct proportions.
                    Vec3d lerped = ProjectionUtil.lerpPos(e, tickDelta);
                    Box bb = e.getBoundingBox();
                    int[] sTop = ProjectionUtil.project(new Vec3d(lerped.x, bb.maxY, lerped.z), tickDelta);
                    int[] sBot = ProjectionUtil.project(new Vec3d(lerped.x, bb.minY, lerped.z), tickDelta);
                    if (sTop == null || sBot == null) continue;

                    int height = Math.max(4, Math.abs(sBot[1] - sTop[1]));
                    double ratio = (bb.maxX - bb.minX) / Math.max(0.2, bb.maxY - bb.minY);
                    int width = Math.max(4, (int) Math.round(height * Math.min(1.4, Math.max(0.25, ratio))));
                    int x = (sTop[0] + sBot[0]) / 2;
                    int yTop = Math.min(sTop[1], sBot[1]);
                    int yBot = yTop + height;
                    int col = RenderUtils.fadeByDistance(base, d, max);

                    if (tracer.isEnabled()) {
                        RenderUtils.drawLine(context, centreX, centreY, x, yBot, col, lw);
                    }

                    if (box.isEnabled()) {
                        RenderUtils.fill(context, x - width / 2, yTop, x + width / 2, yBot,
                                (col & 0x00FFFFFF) | 0x2E000000);
                        RenderUtils.drawCorners(context, x - width / 2, yTop, x + width / 2, yBot,
                                col, Math.max(3, width / 3));
                    }

                    String label = null;
                    if (showName.isEnabled() && e.hasCustomName() && e.getCustomName() != null) {
                        label = e.getCustomName().getString();
                    }
                    if (showDistance.isEnabled()) {
                        String dist = (int) d + "m";
                        label = label == null ? dist : label + "  " + dist;
                    }
                    if (label != null) {
                        RenderUtils.drawChip(context, mc.textRenderer,
                                HudTrim(label, 28), x, yTop - 13, col);
                    }
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
    }

    /** Keeps a label inside a sane width without pulling in the HUD helper. */
    private static String HudTrim(String text, int max) {
        if (text == null) return "";
        return text.length() <= max ? text : text.substring(0, max - 1) + "\u2026";
    }
}
