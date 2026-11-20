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
 * Highlights where a player disappeared with a pulsing "ghost" marker.
 *
 * <p>Modernised with the same shape language as the other Render markers: a real 3D ghost box with
 * a soft fill, a rounded name chip, an optional tracer, dimension filtering and a keep-for timer so
 * old ghosts do not pile up.</p>
 */
public class LogoutESP extends Module {
    public final ColorSetting color = new ColorSetting("Color", "Logout glow color", ColorUtils.rgba(255, 60, 60, 255));
    public final BooleanSetting showName = new BooleanSetting("Show Name", "Show name tag on marker", true);
    public final BooleanSetting showDistance = new BooleanSetting("Show Distance", "Show distance", true);
    public final BooleanSetting box = new BooleanSetting("Ghost Box", "Draw the ghost body box", true);
    public final BooleanSetting tracer = new BooleanSetting("Tracer", "Line from crosshair", false);
    public final NumberSetting maxShown = new NumberSetting("Max Shown", "Max logout ghosts drawn", 5.0, 1.0, 20.0, 1.0);
    public final NumberSetting keepFor = new NumberSetting("Keep For (min)", "Minutes before a ghost fades", 5.0, 1.0, 60.0, 1.0);

    private static final class Ghost {
        final String name;
        final String dim;
        final double x, y, z;
        final long savedAt;

        Ghost(String name, double x, double y, double z, String dim) {
            this.name = name;
            this.x = x;
            this.y = y;
            this.z = z;
            this.dim = dim;
            this.savedAt = System.currentTimeMillis();
        }
    }

    private static final class Seen {
        String name = "";
        String dim = "";
        double x, y, z;
        long last = 0;
    }

    private final Map<UUID, Seen> seen = new HashMap<>();
    private final List<Ghost> ghosts = new ArrayList<>();

    public LogoutESP() {
        super("LogoutESP", "Highlights logout spots with glowing markers", Category.RENDER);
        addSetting(color);
        addSetting(showName);
        addSetting(showDistance);
        addSetting(box);
        addSetting(tracer);
        addSetting(maxShown);
        addSetting(keepFor);
    }

    @Override
    public void onDisable() {
        seen.clear();
        ghosts.clear();
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
            ghosts.removeIf(g -> now - g.savedAt > ttl);

            String dim = curDim();
            Set<UUID> current = new HashSet<>();
            for (Entity e : mc.world.getEntities()) {
                try {
                    if (!(e instanceof PlayerEntity p) || p == mc.player) continue;
                    UUID id = p.getUuid();
                    current.add(id);
                    Seen s = seen.get(id);
                    if (s == null) {
                        s = new Seen();
                        seen.put(id, s);
                    }
                    s.name = p.getName().getString();
                    s.x = p.getX();
                    s.y = p.getY();
                    s.z = p.getZ();
                    s.dim = dim;
                    s.last = now;
                } catch (Throwable ignored) {
                }
            }

            List<UUID> gone = new ArrayList<>();
            for (Map.Entry<UUID, Seen> entry : seen.entrySet()) {
                if (current.contains(entry.getKey())) continue;
                if (now - entry.getValue().last <= 500) continue;
                Seen s = entry.getValue();
                ghosts.add(0, new Ghost(s.name, s.x, s.y, s.z, s.dim));
                while (ghosts.size() > 20) ghosts.remove(ghosts.size() - 1);
                gone.add(entry.getKey());
            }
            for (UUID id : gone) seen.remove(id);
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.player == null || ghosts.isEmpty()) return;
        int col = color.getValue();
        int drawn = 0;
        int limit = maxShown.getValue().intValue();
        String dim = curDim();

        // Breathing glow so a logout marker reads differently from a live player.
        double phase = 0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 300.0);
        int fillAlpha = 0x22 + (int) (0x2C * phase);
        int fill = (col & 0x00FFFFFF) | (fillAlpha << 24);

        for (Ghost ghost : ghosts) {
            if (drawn >= limit) break;
            if (!ghost.dim.isEmpty() && !ghost.dim.equals(dim)) continue;

            Vec3d pos = new Vec3d(ghost.x, ghost.y, ghost.z);
            if (box.isEnabled()) {
                RenderUtils.drawBox3D(context,
                        new Box(pos.x - 0.3, pos.y, pos.z - 0.3, pos.x + 0.3, pos.y + 1.8, pos.z + 0.3),
                        col, fill);
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
            if (showName.isEnabled()) label.append(ghost.name);
            if (showDistance.isEnabled()) {
                double dx = ghost.x - mc.player.getX();
                double dy = ghost.y - mc.player.getY();
                double dz = ghost.z - mc.player.getZ();
                if (label.length() > 0) label.append("  ");
                label.append((int) Math.sqrt(dx * dx + dy * dy + dz * dz)).append('m');
            }
            if (label.length() > 0) {
                RenderUtils.drawChip(context, mc.textRenderer, label.toString(), sc[0], sc[1] + 3, col);
            }
        }
    }
}
