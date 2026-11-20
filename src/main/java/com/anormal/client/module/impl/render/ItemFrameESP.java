package com.anormal.client.module.impl.render;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.ColorUtils;
import com.anormal.client.util.HudStyle;
import com.anormal.client.util.ProjectionUtil;
import com.anormal.client.util.RenderUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * Marks item frames.
 *
 * <p>Modernised: interpolated projection (no marker bounce), corner brackets sized from the frame's
 * AABB, a single tracer line instead of one fill per pixel, a distance-faded colour and a chip
 * label showing the framed item's name. The unused entity-id helper was removed.</p>
 */
public class ItemFrameESP extends Module {
    public final BooleanSetting filledOnly = new BooleanSetting("Filled Only", "Only frames holding items", false);
    public final BooleanSetting outline = new BooleanSetting("Outline", "Glow outline", true);
    public final BooleanSetting box = new BooleanSetting("Box", "Draw box marker", true);
    public final BooleanSetting tracer = new BooleanSetting("Tracer", "Line from crosshair", false);
    public final BooleanSetting showDistance = new BooleanSetting("Show Distance", "Show distance", true);
    public final NumberSetting range = new NumberSetting("Range", "Max distance", 48.0, 8.0, 128.0, 8.0);
    public final NumberSetting verticalRange = new NumberSetting("Vertical Range",
            "How far above and below you to search", 48.0, 8.0, 160.0, 4.0);
    public final ColorSetting color = new ColorSetting("Color", "Marker color", ColorUtils.rgba(255, 170, 0, 255));

    public ItemFrameESP() {
        super("ItemFrameESP", "Marks item frames, optionally filled only", Category.RENDER);
        addSetting(filledOnly);
        addSetting(outline);
        addSetting(box);
        addSetting(tracer);
        addSetting(showDistance);
        addSetting(range);
        addSetting(verticalRange);
        addSetting(color);
    }

    private boolean filled(ItemFrameEntity frame) {
        try {
            return !frame.getHeldItemStack().isEmpty();
        } catch (Throwable ignored) {
            return false;
        }
    }

    @Override
    public void onTick() {
        if (mc.world == null || mc.player == null) return;
        for (Entity e : mc.world.getEntities()) {
            if (!(e instanceof ItemFrameEntity frame)) continue;
            try {
                boolean show = mc.player.distanceTo(e) <= range.getValue()
                        && (!filledOnly.isEnabled() || filled(frame));
                e.setGlowing(outline.isEnabled() && show);
            } catch (Throwable ignored) {}
        }
    }

    @Override
    public void onDisable() {
        if (mc.world == null) return;
        for (Entity e : mc.world.getEntities()) {
            if (e instanceof ItemFrameEntity) {
                try {
                    e.setGlowing(false);
                } catch (Throwable ignored) {}
            }
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
                    if (!(e instanceof ItemFrameEntity frame) || !e.isAlive()) continue;
                    if (filledOnly.isEnabled() && !filled(frame)) continue;
                    double d = mc.player.distanceTo(e);
                    if (d > max) continue;
                    if (Math.abs(e.getY() - mc.player.getY()) > verticalRange.getValue()) continue;

                    Vec3d lerped = ProjectionUtil.lerpPos(e, tickDelta);
                    Box bb = e.getBoundingBox();
                    int[] sMid = ProjectionUtil.project(new Vec3d(lerped.x, lerped.y + 0.5, lerped.z), tickDelta);
                    if (sMid == null) continue;

                    int height = Math.max(7, (int) Math.round(Math.abs(bb.maxY - bb.minY) * 46.0 / Math.max(1.0, d)));
                    int width = Math.max(7, (int) Math.round(height * Math.max(0.6, Math.min(1.6,
                            Math.max(bb.maxX - bb.minX, bb.maxZ - bb.minZ) / Math.max(0.2, bb.maxY - bb.minY)))));
                    int x = sMid[0];
                    int yTop = sMid[1] - height / 2;
                    int yBot = yTop + height;
                    int col = RenderUtils.fadeByDistance(base, d, max);

                    if (tracer.isEnabled()) {
                        RenderUtils.drawLine(context, centreX, centreY, x, yBot, col, 1.0f);
                    }

                    if (box.isEnabled()) {
                        RenderUtils.fill(context, x - width / 2, yTop, x + width / 2, yBot,
                                (col & 0x00FFFFFF) | 0x2E000000);
                        RenderUtils.drawCorners(context, x - width / 2, yTop, x + width / 2, yBot,
                                col, Math.max(3, width / 3));
                    } else {
                        RenderUtils.fill(context, x - 2, sMid[1] - 2, x + 2, sMid[1] + 2, col);
                    }

                    String item = "";
                    try {
                        if (filled(frame)) item = frame.getHeldItemStack().getName().getString();
                    } catch (Throwable ignored) {}
                    String label = showDistance.isEnabled()
                            ? (item.isEmpty() ? (int) d + "m" : item + "  " + (int) d + "m")
                            : (item.isEmpty() ? "Frame" : item);
                    RenderUtils.drawChip(context, mc.textRenderer,
                            HudStyle.trim(mc.textRenderer, label, 60), x, yTop - 13, col);
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
    }
}
