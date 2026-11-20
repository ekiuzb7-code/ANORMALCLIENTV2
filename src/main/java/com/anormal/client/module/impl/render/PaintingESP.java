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
import net.minecraft.entity.decoration.painting.PaintingEntity;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.Locale;

/**
 * Marks paintings.
 *
 * <p>Modernised: the marker follows the painting's real size (a 4x2 painting is not drawn like a
 * 1x1), the colour fades with distance, the tracer is a single line and the label is a chip that
 * also reports the painting's block size.</p>
 */
public class PaintingESP extends Module {
    public final BooleanSetting outline = new BooleanSetting("Outline", "Glow outline", true);
    public final BooleanSetting box = new BooleanSetting("Box", "Draw box marker", true);
    public final BooleanSetting tracer = new BooleanSetting("Tracer", "Line from crosshair", false);
    public final BooleanSetting showDistance = new BooleanSetting("Show Distance", "Show distance", true);
    public final NumberSetting range = new NumberSetting("Range", "Max distance", 48.0, 8.0, 128.0, 8.0);
    public final NumberSetting verticalRange = new NumberSetting("Vertical Range",
            "How far above and below you to search", 48.0, 8.0, 160.0, 4.0);
    public final ColorSetting color = new ColorSetting("Color", "Marker color", ColorUtils.rgba(180, 120, 255, 255));

    public PaintingESP() {
        super("PaintingESP", "Marks paintings", Category.RENDER);
        addSetting(outline);
        addSetting(box);
        addSetting(tracer);
        addSetting(showDistance);
        addSetting(range);
        addSetting(verticalRange);
        addSetting(color);
    }

    private boolean isPainting(Entity e) {
        if (e instanceof PaintingEntity) return true;
        try {
            return Registries.ENTITY_TYPE.getId(e.getType()).getPath().contains("painting");
        } catch (Throwable ignored) {
            return false;
        }
    }

    @Override
    public void onTick() {
        if (mc.world == null || mc.player == null) return;
        for (Entity e : mc.world.getEntities()) {
            if (!isPainting(e)) continue;
            try {
                e.setGlowing(outline.isEnabled() && mc.player.distanceTo(e) <= range.getValue());
            } catch (Throwable ignored) {}
        }
    }

    @Override
    public void onDisable() {
        if (mc.world == null) return;
        for (Entity e : mc.world.getEntities()) {
            try {
                if (isPainting(e)) e.setGlowing(false);
            } catch (Throwable ignored) {}
        }
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.player == null || mc.world == null || mc.textRenderer == null) return;
        try {
            int centreX = RenderUtils.screenWidth() / 2;
            int centreY = RenderUtils.screenHeight() / 2;
            int base = color.getValue();
            double max = range.getValue();

            for (Entity e : mc.world.getEntities()) {
                try {
                    if (!isPainting(e) || !e.isAlive()) continue;
                    double d = mc.player.distanceTo(e);
                    if (d > max) continue;
                    if (Math.abs(e.getY() - mc.player.getY()) > verticalRange.getValue()) continue;

                    Vec3d lerped = ProjectionUtil.lerpPos(e, tickDelta);
                    Box bb = e.getBoundingBox();
                    int[] sTop = ProjectionUtil.project(new Vec3d(lerped.x, bb.maxY, lerped.z), tickDelta);
                    int[] sBot = ProjectionUtil.project(new Vec3d(lerped.x, bb.minY, lerped.z), tickDelta);
                    if (sTop == null || sBot == null) continue;

                    int height = Math.max(4, Math.abs(sBot[1] - sTop[1]));
                    int blocksW = Math.max(1, (int) Math.round(Math.max(bb.maxX - bb.minX, bb.maxZ - bb.minZ)));
                    int blocksH = Math.max(1, (int) Math.round(bb.maxY - bb.minY));
                    int width = Math.max(4, (int) Math.round(height * (blocksW / (double) blocksH)));
                    int x = (sTop[0] + sBot[0]) / 2;
                    int yTop = Math.min(sTop[1], sBot[1]);
                    int yBot = yTop + height;
                    int col = RenderUtils.fadeByDistance(base, d, max);

                    if (tracer.isEnabled()) {
                        RenderUtils.drawLine(context, centreX, centreY, x, yBot, col, 1.0f);
                    }

                    if (box.isEnabled()) {
                        RenderUtils.fill(context, x - width / 2, yTop, x + width / 2, yBot,
                                (col & 0x00FFFFFF) | 0x2E000000);
                        RenderUtils.drawCorners(context, x - width / 2, yTop, x + width / 2, yBot,
                                col, Math.max(3, width / 4));
                    } else {
                        RenderUtils.fill(context, x - 2, (yTop + yBot) / 2 - 2,
                                x + 2, (yTop + yBot) / 2 + 2, col);
                    }

                    String size = blocksW + "x" + blocksH;
                    String label = showDistance.isEnabled()
                            ? String.format(Locale.ROOT, "Painting %s  %dm", size, (int) d)
                            : "Painting " + size;
                    RenderUtils.drawChip(context, mc.textRenderer, label, x, yTop - 13, col);
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
    }
}
