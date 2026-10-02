package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.ColorUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.util.math.Vec3d;

public class Projectiles extends Module {
    public final BooleanSetting showArrows = new BooleanSetting("Show Arrows", "Renders trajectories for arrows in flight", true);
    public final BooleanSetting showPearls = new BooleanSetting("Show Pearls", "Renders trajectories for pearls in flight", true);
    public final BooleanSetting showPotions = new BooleanSetting("Show Potions", "Renders trajectories for potions in flight", true);
    public final BooleanSetting showEggs = new BooleanSetting("Show Eggs", "Renders trajectories for eggs in flight", false);
    public final BooleanSetting showSnowballs = new BooleanSetting("Show Snowballs", "Renders trajectories for snowballs in flight", false);
    public final NumberSetting trailLength = new NumberSetting("Trail Length", "How far back the trail renders in blocks", 8.0, 2.0, 20.0, 1.0);
    public final ColorSetting color = new ColorSetting("Color", "Projectile trail color", ColorUtils.rgba(120, 220, 255, 230));

    public Projectiles() {
        super("Projectiles", "Renders predicted ballistic flight trajectories for arrows and pearls", Category.UZNY11);
        addSetting(showArrows);
        addSetting(showPearls);
        addSetting(showPotions);
        addSetting(showEggs);
        addSetting(showSnowballs);
        addSetting(trailLength);
        addSetting(color);
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.player == null || mc.world == null) return;
        try {
            for (Entity e : mc.world.getEntities()) {
                if (!(e instanceof ProjectileEntity)) continue;
                String kind = kind(e);
                if (kind.isEmpty() || !typeEnabled(kind)) continue;
                Vec3d pos = new Vec3d(e.getX(), e.getY(), e.getZ());
                Vec3d vel;
                try {
                    vel = e.getVelocity();
                } catch (Throwable t) {
                    continue;
                }
                int[] head = project(pos);
                if (head == null) continue;
                Vec3d tail = pos.subtract(vel.multiply(trailLength.getValue() / Math.max(0.2, vel.length())));
                int[] tailS = project(tail);
                int col = color.getValue();
                if (tailS != null) line(context, tailS[0], tailS[1], head[0], head[1], col);
                context.fill(head[0] - 1, head[1] - 1, head[0] + 2, head[1] + 2, col);
            }
        } catch (Throwable ignored) {}
    }

    private boolean typeEnabled(String kind) {
        if (kind.equals("arrow")) return showArrows.isEnabled();
        if (kind.equals("pearl")) return showPearls.isEnabled();
        if (kind.equals("potion")) return showPotions.isEnabled();
        if (kind.equals("egg")) return showEggs.isEnabled();
        return showSnowballs.isEnabled();
    }

    private String kind(Entity e) {
        String n;
        try {
            n = e.getClass().getSimpleName().toLowerCase();
        } catch (Throwable t) {
            return "";
        }
        if (n.contains("pearl")) return "pearl";
        if (n.contains("potion")) return "potion";
        if (n.contains("egg")) return "egg";
        if (n.contains("snowball")) return "snowball";
        if (n.contains("arrow") || n.contains("trident") || n.contains("spectral")) return "arrow";
        return "";
    }

    private int[] project(Vec3d p) {
        try {
            Camera cam = mc.gameRenderer.getCamera();
            Vec3d c = mc.player.getEyePos();
            double dx = p.x - c.x, dy = p.y - c.y, dz = p.z - c.z;
            double yaw = Math.toRadians(cam.getYaw());
            double pitch = Math.toRadians(cam.getPitch());
            double cosP = Math.cos(pitch);
            double fx = -Math.sin(yaw) * cosP, fy = -Math.sin(pitch), fz = Math.cos(yaw) * cosP;
            double depth = dx * fx + dy * fy + dz * fz;
            if (depth < 0.1) return null;
            double rx = -Math.cos(yaw), rz = -Math.sin(yaw);
            double ux = -rz * fy, uy = rz * fx - rx * fz, uz = rx * fy;
            double vx = dx * rx + dz * rz;
            double vy = dx * ux + dy * uy + dz * uz;
            int w = mc.getWindow().getScaledWidth(), h = mc.getWindow().getScaledHeight();
            int fov = 70;
            try {
                fov = mc.options.getFov().getValue();
            } catch (Throwable ignored) {}
            double f = (h / 2.0) / Math.tan(Math.toRadians(Math.max(30, Math.min(110, fov)) / 2.0));
            return new int[]{(int) (w / 2.0 + vx / depth * f), (int) (h / 2.0 - vy / depth * f)};
        } catch (Throwable t) {
            return null;
        }
    }

    private void line(DrawContext context, int x1, int y1, int x2, int y2, int col) {
        int steps = Math.min(120, Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1)));
        if (steps == 0) {
            context.fill(x1, y1, x1 + 1, y1 + 1, col);
            return;
        }
        for (int i = 0; i <= steps; i++) {
            int x = x1 + (x2 - x1) * i / steps, y = y1 + (y2 - y1) * i / steps;
            context.fill(x, y, x + 1, y + 1, col);
        }
    }
}
