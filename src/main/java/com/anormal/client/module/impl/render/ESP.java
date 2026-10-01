package com.anormal.client.module.impl.render;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.util.ColorUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

public class ESP extends Module {
    public final ModeSetting mode = new ModeSetting("Mode", "ESP visual mode", "2D Box", "Glow", "2D Box", "Corner");
    public final BooleanSetting players = new BooleanSetting("Players", "Highlight other players", true);
    public final BooleanSetting mobs = new BooleanSetting("Monsters", "Highlight hostile mobs", false);
    public final BooleanSetting animals = new BooleanSetting("Animals", "Highlight passive animals", false);
    public final BooleanSetting items = new BooleanSetting("Items", "Highlight dropped items", false);
    public final BooleanSetting healthBar = new BooleanSetting("Health Bar", "Show health indicators", true);
    public final ColorSetting color = new ColorSetting("Color", "ESP highlight color", ColorUtils.rgba(255, 60, 60, 255));

    public ESP() {
        super("ESP", "Highlights players, mobs, and items through walls with customizable styles", Category.RENDER);
        addSetting(mode);
        addSetting(players);
        addSetting(mobs);
        addSetting(animals);
        addSetting(items);
        addSetting(healthBar);
        addSetting(color);
    }

    @Override
    public void onTick() {
        if (mc.world == null || mc.player == null) return;

        for (Entity entity : mc.world.getEntities()) {
            if (entity == mc.player) continue;

            boolean shouldHighlight = false;
            if (entity instanceof PlayerEntity && players.isEnabled()) shouldHighlight = true;
            else if (entity instanceof Monster && mobs.isEnabled()) shouldHighlight = true;
            else if (entity instanceof AnimalEntity && animals.isEnabled()) shouldHighlight = true;
            else if (entity instanceof ItemEntity && items.isEnabled()) shouldHighlight = true;

            // Glow mode only; box modes draw in onRender2D — clear stale glow there
            try {
                entity.setGlowing(shouldHighlight && mode.is("Glow"));
            } catch (Throwable ignored) {}
        }
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mode.is("Glow") || mc.world == null || mc.player == null) return;
        boolean corners = mode.is("Corner");
        for (Entity entity : mc.world.getEntities()) {
            try {
                if (entity == mc.player) continue;
                if (entity instanceof PlayerEntity) { if (!players.isEnabled()) continue; }
                else if (entity instanceof Monster) { if (!mobs.isEnabled()) continue; }
                else if (entity instanceof AnimalEntity) { if (!animals.isEnabled()) continue; }
                else if (entity instanceof ItemEntity) { if (!items.isEnabled()) continue; }
                else continue;

                Box box = entity.getBoundingBox();
                Vec3d top = new Vec3d((box.minX + box.maxX) / 2.0, box.maxY + 0.1, (box.minZ + box.maxZ) / 2.0);
                Vec3d bottom = new Vec3d((box.minX + box.maxX) / 2.0, box.minY, (box.minZ + box.maxZ) / 2.0);
                int[] sTop = project(top);
                int[] sBot = project(bottom);
                if (sTop == null || sBot == null) continue;
                int h = Math.max(4, sBot[1] - sTop[1]);
                int w = Math.max(4, h / 3);
                int x = (sTop[0] + sBot[0]) / 2;
                int yTop = Math.min(sTop[1], sBot[1]);
                int yBot = yTop + h;
                int col = entity instanceof PlayerEntity ? color.getValue() : 0xFFFFAA00;

                if (corners) {
                    int cl = Math.min(w / 3, 8);
                    // top-left, top-right, bottom-left, bottom-right corners
                    context.fill(x - w / 2, yTop, x - w / 2 + cl, yTop + 1, col);
                    context.fill(x - w / 2, yTop, x - w / 2 + 1, yTop + cl, col);
                    context.fill(x + w / 2 - cl, yTop, x + w / 2, yTop + 1, col);
                    context.fill(x + w / 2 - 1, yTop, x + w / 2, yTop + cl, col);
                    context.fill(x - w / 2, yBot - 1, x - w / 2 + cl, yBot, col);
                    context.fill(x - w / 2, yBot - cl, x - w / 2 + 1, yBot, col);
                    context.fill(x + w / 2 - cl, yBot - 1, x + w / 2, yBot, col);
                    context.fill(x + w / 2 - 1, yBot - cl, x + w / 2, yBot, col);
                } else {
                    context.fill(x - w / 2, yTop, x + w / 2, yTop + 1, col);
                    context.fill(x - w / 2, yBot - 1, x + w / 2, yBot, col);
                    context.fill(x - w / 2, yTop, x - w / 2 + 1, yBot, col);
                    context.fill(x + w / 2 - 1, yTop, x + w / 2, yBot, col);
                }

                if (healthBar.isEnabled() && entity instanceof LivingEntity living) {
                    float maxHp = Math.max(1.0f, living.getMaxHealth());
                    float pct = Math.max(0.0f, Math.min(1.0f, living.getHealth() / maxHp));
                    int barH = (int) (h * pct);
                    int barCol = pct > 0.6 ? 0xFF55FF55 : (pct > 0.3 ? 0xFFFFFF55 : 0xFFFF5555);
                    context.fill(x - w / 2 - 4, yTop, x - w / 2 - 2, yBot, 0xAA222222);
                    context.fill(x - w / 2 - 4, yBot - barH, x - w / 2 - 2, yBot, barCol);
                }
            } catch (Throwable ignored) {}
        }
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
            double cx = dx * rx + dz * rz;
            double cy = dx * ux + dy * uy + dz * uz;
            int w = mc.getWindow().getScaledWidth(), hh = mc.getWindow().getScaledHeight();
            double f = (hh / 2.0) / Math.tan(Math.toRadians(35.0));
            return new int[]{(int) (w / 2.0 + cx / depth * f), (int) (hh / 2.0 - cy / depth * f)};
        } catch (Throwable t) {
            return null;
        }
    }

    @Override
    public void onDisable() {
        if (mc.world == null) return;
        for (Entity entity : mc.world.getEntities()) {
            entity.setGlowing(false);
        }
    }
}
