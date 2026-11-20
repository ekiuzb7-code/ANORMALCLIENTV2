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
import net.minecraft.entity.passive.AbstractDonkeyEntity;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/**
 * Marks donkeys, mules and llamas.
 *
 * <p>Markers used to be frozen world positions captured by a 10-tick rescan: they lagged half a
 * second behind a moving animal and stayed in place after it left. The cache now keeps the entity
 * itself and projects its interpolated position every frame, the label is the entity's real name,
 * distances are live, and chest-carrying animals can be filtered in.</p>
 */
public class DonkeyFinder extends Module {
    public final NumberSetting range = new NumberSetting("Range", "Detection range", 64.0, 16.0, 128.0, 8.0);
    public final NumberSetting verticalRange = new NumberSetting("Vertical Range",
            "How far above and below you to search", 48.0, 8.0, 160.0, 4.0);
    public final ColorSetting color = new ColorSetting("Color", "Marker color", ColorUtils.rgba(150, 100, 50, 255));
    public final NumberSetting maxShown = new NumberSetting("Max Shown", "Max entity markers", 32.0, 8.0, 64.0, 1.0);
    public final BooleanSetting showDistance = new BooleanSetting("Show Distance", "Show distance on marker", true);
    public final BooleanSetting onlyChest = new BooleanSetting("Only With Chest", "Only mark animals carrying a chest", false);

    private final List<Entity> cache = new ArrayList<>();
    private int ticks = 0;

    public DonkeyFinder() {
        super("DonkeyFinder", "Marks donkeys mules and llamas", Category.RENDER);
        addSetting(range);
        addSetting(verticalRange);
        addSetting(color);
        addSetting(maxShown);
        addSetting(showDistance);
        addSetting(onlyChest);
    }

    @Override
    public void onDisable() {
        cache.clear();
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;
        if (++ticks < 10) return;
        ticks = 0;

        cache.clear();
        try {
            for (Entity e : mc.world.getEntities()) {
                try {
                    if (e == mc.player || !e.isAlive()) continue;
                    String path = Registries.ENTITY_TYPE.getId(e.getType()).getPath();
                    if (!path.contains("donkey") && !path.contains("mule") && !path.contains("llama")) continue;
                    if (onlyChest.isEnabled() && !(e instanceof AbstractDonkeyEntity donkey && donkey.hasChest())) continue;
                    if (mc.player.distanceTo(e) > range.getValue()) continue;
                    if (Math.abs(e.getY() - mc.player.getY()) > verticalRange.getValue()) continue;
                    cache.add(e);
                    if (cache.size() >= maxShown.getValue().intValue()) break;
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.player == null || mc.textRenderer == null || cache.isEmpty()) return;
        try {
            int c = color.getValue();
            int screenW = mc.getWindow().getScaledWidth();
            int screenH = mc.getWindow().getScaledHeight();

            for (Entity e : cache) {
                if (e == null || !e.isAlive() || e.isRemoved()) continue;

                // Re-check live: animals walk away between the (half-second) rescans.
                double dist = mc.player.distanceTo(e);
                if (dist > range.getValue()) continue;
                if (Math.abs(e.getY() - mc.player.getY()) > verticalRange.getValue()) continue;
                if (onlyChest.isEnabled() && !(e instanceof AbstractDonkeyEntity donkey && donkey.hasChest())) continue;

                Vec3d pos = ProjectionUtil.lerpPos(e, tickDelta).add(0.0, 1.0, 0.0);
                int[] sc = ProjectionUtil.project(pos, tickDelta);
                if (sc == null) continue;

                context.fill(sc[0] - 3, sc[1] - 3, sc[0] + 3, sc[1] + 3, (c & 0x00FFFFFF) | 0x66000000);
                context.fill(sc[0] - 3, sc[1] - 3, sc[0] + 3, sc[1] - 2, c);
                context.fill(sc[0] - 3, sc[1] + 2, sc[0] + 3, sc[1] + 3, c);
                context.fill(sc[0] - 3, sc[1] - 2, sc[0] - 2, sc[1] + 2, c);
                context.fill(sc[0] + 2, sc[1] - 2, sc[0] + 3, sc[1] + 2, c);

                // Real name instead of the registry path ("donkey", "trader_llama"), kept on screen.
                String text = e.getName().getString()
                        + (showDistance.isEnabled() ? " " + (int) dist + "m" : "");
                int tw = mc.textRenderer.getWidth(text);
                int tx = Math.max(2, Math.min(screenW - tw - 2, sc[0] + 5));
                int ty = Math.max(2, Math.min(screenH - 10, sc[1] - 4));
                RenderUtils.drawText(context, mc.textRenderer, text, tx, ty, c, true);
            }
        } catch (Throwable ignored) {
        }
    }
}
