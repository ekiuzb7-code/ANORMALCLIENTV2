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
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Saves where players were last seen and marks the spot after they disconnect.
 *
 * <p>Modernised: spots expire after {@code Keep For} minutes instead of staying forever, only the
 * current dimension is drawn, the marker is a real 3D box plus a rounded chip, and the tracer goes
 * from the crosshair like the other marker modules.</p>
 */
public class LogoutSpots extends Module {
    public final ColorSetting color = new ColorSetting("Color", "Logout marker color", ColorUtils.rgba(255, 80, 255, 255));
    public final BooleanSetting showName = new BooleanSetting("Show Name", "Show the player name", true);
    public final BooleanSetting showDistance = new BooleanSetting("Show Distance", "Show distance on marker", true);
    public final BooleanSetting showCoords = new BooleanSetting("Show Coordinates", "Show coordinates", true);
    public final BooleanSetting showTime = new BooleanSetting("Show Time", "Show the logout time", true);
    public final BooleanSetting box = new BooleanSetting("Box", "Draw a 3D box", true);
    public final BooleanSetting tracer = new BooleanSetting("Tracer", "Line from crosshair", false);
    public final NumberSetting maxShown = new NumberSetting("Max Shown", "Max logout markers drawn", 5.0, 1.0, 20.0, 1.0);
    public final NumberSetting keepFor = new NumberSetting("Keep For (min)", "Minutes before a spot is forgotten", 5.0, 1.0, 60.0, 1.0);

    private static final class Spot {
        final String name;
        final String dim;
        final String time;
        final double x, y, z;
        final long savedAt;

        Spot(String name, double x, double y, double z, String dim, String time) {
            this.name = name;
            this.x = x;
            this.y = y;
            this.z = z;
            this.dim = dim;
            this.time = time;
            this.savedAt = System.currentTimeMillis();
        }
    }

    private static final class Known {
        String name = "";
        String dim = "";
        double x, y, z;
        long last = 0;
    }

    private final Map<UUID, Known> known = new HashMap<>();
    private final List<Spot> spots = new ArrayList<>();

    public LogoutSpots() {
        super("LogoutSpots", "Saves logout positions with markers", Category.RENDER);
        addSetting(color);
        addSetting(showName);
        addSetting(showDistance);
        addSetting(showCoords);
        addSetting(showTime);
        addSetting(box);
        addSetting(tracer);
        addSetting(maxShown);
        addSetting(keepFor);
    }

    @Override
    public void onDisable() {
        known.clear();
        spots.clear();
    }

    private String curDim() {
        try {
            return mc.world.getRegistryKey().getValue().getPath();
        } catch (Throwable ignored) {
            return "";
        }
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;
        try {
            long now = System.currentTimeMillis();
            long ttl = (long) (keepFor.getValue() * 60_000L);
            spots.removeIf(spot -> now - spot.savedAt > ttl);

            String dim = curDim();
            Set<UUID> current = new HashSet<>();
            for (Entity e : mc.world.getEntities()) {
                try {
                    if (!(e instanceof PlayerEntity p) || p == mc.player) continue;
                    UUID id = p.getUuid();
                    current.add(id);
                    Known k = known.get(id);
                    if (k == null) {
                        k = new Known();
                        known.put(id, k);
                    }
                    k.name = p.getName().getString();
                    k.x = p.getX();
                    k.y = p.getY();
                    k.z = p.getZ();
                    k.dim = dim;
                    k.last = now;
                } catch (Throwable ignored) {
                }
            }

            List<UUID> gone = new ArrayList<>();
            for (Map.Entry<UUID, Known> entry : known.entrySet()) {
                if (current.contains(entry.getKey())) continue;
                if (now - entry.getValue().last <= 500) continue;
                Known k = entry.getValue();
                String time = java.time.LocalTime.now()
                        .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"));
                spots.add(0, new Spot(k.name, k.x, k.y, k.z, k.dim, time));
                while (spots.size() > 20) spots.remove(spots.size() - 1);
                gone.add(entry.getKey());
            }
            for (UUID id : gone) known.remove(id);
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.player == null || spots.isEmpty()) return;
        int col = color.getValue();
        int drawn = 0;
        int limit = maxShown.getValue().intValue();
        String dim = curDim();

        for (Spot spot : spots) {
            if (drawn >= limit) break;
            if (!spot.dim.isEmpty() && !spot.dim.equals(dim)) continue;

            Vec3d pos = new Vec3d(spot.x, spot.y, spot.z);
            if (box.isEnabled()) {
                RenderUtils.drawBox3D(context,
                        new Box(pos.x - 0.35, pos.y, pos.z - 0.35, pos.x + 0.35, pos.y + 1.8, pos.z + 0.35),
                        col, (col & 0x00FFFFFF) | 0x2A000000);
            }

            int[] sc = ProjectionUtil.project(pos.add(0.0, 2.1, 0.0), tickDelta);
            if (sc == null) continue;
            drawn++;

            if (tracer.isEnabled()) {
                RenderUtils.drawLine(context, RenderUtils.screenWidth() / 2,
                        RenderUtils.screenHeight() / 2, sc[0], sc[1],
                        (col & 0x00FFFFFF) | 0x80000000, 1.0f);
            }
            if (mc.textRenderer == null) continue;

            StringBuilder label = new StringBuilder();
            if (showName.isEnabled()) label.append(spot.name);
            if (showCoords.isEnabled()) {
                if (label.length() > 0) label.append("  ");
                label.append((int) spot.x).append(' ').append((int) spot.y).append(' ').append((int) spot.z);
            }
            if (showDistance.isEnabled()) {
                double dx = spot.x - mc.player.getX();
                double dy = spot.y - mc.player.getY();
                double dz = spot.z - mc.player.getZ();
                if (label.length() > 0) label.append("  ");
                label.append((int) Math.sqrt(dx * dx + dy * dy + dz * dz)).append('m');
            }
            if (showTime.isEnabled()) {
                if (label.length() > 0) label.append("  ");
                label.append(spot.time);
            }
            if (label.length() > 0) {
                RenderUtils.drawChip(context, mc.textRenderer, label.toString(), sc[0], sc[1] + 3, col);
            }
        }
    }
}
