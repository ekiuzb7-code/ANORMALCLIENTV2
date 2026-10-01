package com.anormal.client.module.impl.render;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.util.ColorUtils;
import com.anormal.client.util.RenderUtils;
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
    public final ModeSetting mode = new ModeSetting("Mode", "ESP visual mode", "2D Box", "Glow", "2D Box", "Corner", "Outline", "V2");
    public final BooleanSetting players = new BooleanSetting("Players", "Highlight other players", true);
    public final BooleanSetting mobs = new BooleanSetting("Monsters", "Highlight hostile mobs", false);
    public final BooleanSetting animals = new BooleanSetting("Animals", "Highlight passive animals", false);
    public final BooleanSetting items = new BooleanSetting("Items", "Highlight dropped items", false);
    public final BooleanSetting healthBar = new BooleanSetting("Health Bar", "Show health indicators", true);
    public final ColorSetting color = new ColorSetting("Color", "ESP highlight color", ColorUtils.rgba(255, 60, 60, 255));
    public final ColorSetting mobColor = new ColorSetting("Mob Color", "Hostile mob color", ColorUtils.rgba(255, 170, 0, 255));
    public final ColorSetting animalColor = new ColorSetting("Animal Color", "Passive animal color", ColorUtils.rgba(85, 255, 85, 255));
    public final ColorSetting itemColor = new ColorSetting("Item Color", "Dropped item color", ColorUtils.rgba(255, 255, 85, 255));
    public final BooleanSetting outline = new BooleanSetting("Outline", "Bright outer edge on boxes", true);
    public final BooleanSetting fill = new BooleanSetting("Fill", "Translucent box fill (V2)", true);

    public ESP() {
        super("ESP", "Highlights players, mobs, and items through walls with customizable styles", Category.RENDER);
        addSetting(mode);
        addSetting(players);
        addSetting(mobs);
        addSetting(animals);
        addSetting(items);
        addSetting(healthBar);
        addSetting(color);
        addSetting(mobColor);
        addSetting(animalColor);
        addSetting(itemColor);
        addSetting(outline);
        addSetting(fill);
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

            // Glow + Outline modes use wall-through outline; box modes draw in onRender2D
            try {
                entity.setGlowing(shouldHighlight && (mode.is("Glow") || mode.is("Outline")));
            } catch (Throwable ignored) {}
        }
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mode.is("Glow") || mc.world == null || mc.player == null) return;
        boolean corners = mode.is("Corner"); // Outline draws full box + glow outline
        for (Entity entity : mc.world.getEntities()) {
            try {
                if (entity == mc.player) continue;
                if (entity instanceof PlayerEntity) { if (!players.isEnabled()) continue; }
                else if (entity instanceof Monster) { if (!mobs.isEnabled()) continue; }
                else if (entity instanceof AnimalEntity) { if (!animals.isEnabled()) continue; }
                else if (entity instanceof ItemEntity) { if (!items.isEnabled()) continue; }
                else continue;

                Box box = entity.getBoundingBox();
                double cx0 = (box.minX + box.maxX) / 2.0;
                double cz0 = (box.minZ + box.maxZ) / 2.0;
                int[] sTop = project(new Vec3d(cx0, box.maxY + 0.1, cz0));
                int[] sBot = project(new Vec3d(cx0, box.minY, cz0));
                // X from MID-height projection: top/bottom centers skew under perspective,
                // which pushed the box ahead of / behind the hitbox
                int[] sMid = project(new Vec3d(cx0, (box.minY + box.maxY) / 2.0, cz0));
                if (sTop == null || sBot == null || sMid == null) continue;
                int h = Math.max(4, sBot[1] - sTop[1]);
                int w = Math.max(4, h / 3);
                int x = sMid[0];
                int yTop = Math.min(sTop[1], sBot[1]);
                int yBot = yTop + h;
                int col = typeColor(entity);
                boolean v2 = mode.is("V2");

                if (v2 && fill.isEnabled()) {
                    // Translucent 3D-box feel: filled body with bright edge
                    context.fill(x - w / 2, yTop, x + w / 2, yBot, (col & 0x00FFFFFF) | 0x32000000);
                }

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

                if (outline.isEnabled() && !corners) {
                    // Extra bright outer edge
                    context.fill(x - w / 2 - 1, yTop - 1, x + w / 2 + 1, yTop, col);
                    context.fill(x - w / 2 - 1, yBot, x + w / 2 + 1, yBot + 1, col);
                    context.fill(x - w / 2 - 1, yTop, x - w / 2, yBot, col);
                    context.fill(x + w / 2, yTop, x + w / 2 + 1, yBot, col);
                }

                if (v2 && mc.textRenderer != null) {
                    try {
                        String nm = entity.getName().getString();
                        RenderUtils.drawText(context, mc.textRenderer, nm, x - mc.textRenderer.getWidth(nm) / 2, yTop - 11, 0xFFFFFFFF, true);
                    } catch (Throwable ignored) {}
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

    private int typeColor(Entity entity) {
        try {
            if (entity instanceof PlayerEntity) return color.getValue();
            if (entity instanceof Monster) return mobColor.getValue();
            if (entity instanceof AnimalEntity) return animalColor.getValue();
            if (entity instanceof ItemEntity) return itemColor.getValue();
        } catch (Throwable ignored) {}
        return color.getValue();
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
            int fov = 70;
            try {
                fov = mc.options.getFov().getValue();
            } catch (Throwable ignored) {}
            double f = (hh / 2.0) / Math.tan(Math.toRadians(Math.max(30, Math.min(110, fov)) / 2.0));
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
