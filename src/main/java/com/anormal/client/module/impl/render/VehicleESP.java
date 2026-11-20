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
import net.minecraft.registry.Registries;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * Marks boats and minecarts.
 *
 * <p>Uses per-frame interpolated positions (the old raw {@code getX()} made the markers jitter
 * while the vehicle moved), draws a real 3D box around the vehicle and renders the label as a
 * rounded chip.</p>
 */
public class VehicleESP extends Module {
    public final BooleanSetting boats = new BooleanSetting("Boats", "Mark boats", true);
    public final BooleanSetting chestBoats = new BooleanSetting("Chest Boats", "Mark chest boats", true);
    public final BooleanSetting minecarts = new BooleanSetting("Minecarts", "Mark minecarts", true);
    public final BooleanSetting chestMinecarts = new BooleanSetting("Chest Minecarts", "Mark chest minecarts", true);
    public final BooleanSetting other = new BooleanSetting("Other Vehicles", "Mark other vehicles", false);
    public final BooleanSetting showName = new BooleanSetting("Show Name", "Show vehicle name", true);
    public final BooleanSetting showDistance = new BooleanSetting("Show Distance", "Show distance", true);
    public final BooleanSetting showCoords = new BooleanSetting("Show Coordinates", "Show coordinates", false);
    public final BooleanSetting box = new BooleanSetting("Box", "Draw a 3D box marker", true);
    public final BooleanSetting tracer = new BooleanSetting("Tracer", "Line from crosshair", false);
    public final NumberSetting range = new NumberSetting("Range", "Max distance", 64.0, 8.0, 160.0, 8.0);
    public final NumberSetting maxDistance = new NumberSetting("Max Distance", "Skip beyond this", 300.0, 50.0, 1000.0, 25.0);
    public final ColorSetting color = new ColorSetting("Color", "Marker color", ColorUtils.rgba(255, 170, 0, 255));

    public VehicleESP() {
        super("VehicleESP", "Marks boats and minecarts", Category.RENDER);
        addSetting(boats);
        addSetting(chestBoats);
        addSetting(minecarts);
        addSetting(chestMinecarts);
        addSetting(other);
        addSetting(showName);
        addSetting(showDistance);
        addSetting(showCoords);
        addSetting(box);
        addSetting(tracer);
        addSetting(range);
        addSetting(maxDistance);
        addSetting(color);
    }

    private String kind(Entity e) {
        String path;
        try {
            path = Registries.ENTITY_TYPE.getId(e.getType()).getPath();
        } catch (Throwable ignored) {
            return null;
        }
        boolean isBoat = path.contains("boat") && !path.contains("chest");
        boolean isChestBoat = path.contains("chest_boat");
        boolean isCart = path.contains("minecart") && !path.contains("chest")
                && !path.contains("furnace") && !path.contains("tnt") && !path.contains("hopper");
        boolean isChestCart = path.contains("chest_minecart");
        boolean isOther = (path.contains("minecart") || path.contains("boat"))
                && !isBoat && !isChestBoat && !isCart && !isChestCart;
        if (isBoat && boats.isEnabled()) return "Boat";
        if (isChestBoat && chestBoats.isEnabled()) return "Chest Boat";
        if (isCart && minecarts.isEnabled()) return "Minecart";
        if (isChestCart && chestMinecarts.isEnabled()) return "Chest Minecart";
        if (isOther && other.isEnabled()) return path;
        return null;
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.player == null || mc.world == null) return;
        double maxR = Math.min(range.getValue(), maxDistance.getValue());
        int col = color.getValue();

        for (Entity entity : mc.world.getEntities()) {
            try {
                if (entity == mc.player || !entity.isAlive()) continue;
                String kind = kind(entity);
                if (kind == null) continue;
                double dist = mc.player.distanceTo(entity);
                if (dist > maxR) continue;

                Vec3d pos = ProjectionUtil.lerpPos(entity, tickDelta);
                Vec3d tick = new Vec3d(entity.getX(), entity.getY(), entity.getZ());
                Box bb = entity.getBoundingBox().offset(pos.subtract(tick));

                if (box.isEnabled()) {
                    RenderUtils.drawBox3D(context, bb, col, (col & 0x00FFFFFF) | 0x33000000);
                }

                int[] sc = ProjectionUtil.project(
                        pos.add(0.0, entity.getBoundingBox().getLengthY() + 0.35, 0.0), tickDelta);
                if (sc == null) continue;

                if (tracer.isEnabled()) {
                    RenderUtils.drawLine(context, RenderUtils.screenWidth() / 2,
                            RenderUtils.screenHeight() / 2, sc[0], sc[1],
                            (col & 0x00FFFFFF) | 0x80000000, 1.0f);
                }
                if (mc.textRenderer == null) continue;

                StringBuilder label = new StringBuilder();
                if (showName.isEnabled()) label.append(kind);
                if (showDistance.isEnabled()) {
                    if (label.length() > 0) label.append("  ");
                    label.append((int) dist).append('m');
                }
                if (showCoords.isEnabled()) {
                    if (label.length() > 0) label.append("  ");
                    label.append((int) pos.x).append(' ').append((int) pos.y).append(' ').append((int) pos.z);
                }
                if (label.length() > 0) {
                    RenderUtils.drawChip(context, mc.textRenderer, label.toString(), sc[0], sc[1] + 3, col);
                }
            } catch (Throwable ignored) {
            }
        }
    }
}
