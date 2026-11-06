package com.anormal.client.module.impl.spectre;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.ProjectionUtil;
import com.anormal.client.util.RenderUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.AmbientEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.mob.WaterCreatureEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/**
 * Spectre port: "Tracers" — displays tracer lines to specified entities
 * (Lines style port; the Offscreen arrow HUD needs Spectre's ImGui pipeline,
 * so it draws a screen arrow with the shared 2D renderer instead).
 */
public final class SpectreTracers extends Module {
    public final BooleanSetting players = addSetting(new BooleanSetting("Players", "Trace players", true));
    public final BooleanSetting animals = addSetting(new BooleanSetting("Animals", "Trace animals", false));
    public final BooleanSetting waterAnimals = addSetting(new BooleanSetting("Water Animals", "Trace water animals", false));
    public final BooleanSetting monsters = addSetting(new BooleanSetting("Monsters", "Trace monsters", false));
    public final BooleanSetting ambient = addSetting(new BooleanSetting("Ambient", "Trace ambient creatures", false));
    public final BooleanSetting misc = addSetting(new BooleanSetting("Misc", "Trace every other entity", false));
    public final BooleanSetting ignoreSelf = addSetting(new BooleanSetting("Ignore Self", "Skip your own body", false));
    public final BooleanSetting ignoreFriends = addSetting(new BooleanSetting("Ignore Friends", "Do not trace friends", false));
    public final BooleanSetting showInvisible = addSetting(new BooleanSetting("Show Invisible", "Trace invisible entities", true));
    public final ModeSetting style = addSetting(new ModeSetting("Style", "Render style", "Lines", "Lines", "Offscreen"));
    public final ModeSetting target = addSetting(new ModeSetting("Target", "Tracer anchor point", "Body", "Head", "Body", "Feet"));
    public final BooleanSetting stem = addSetting(new BooleanSetting("Stem", "Drop a vertical stem line at the entity", true));
    public final NumberSetting maxDistance = addSetting(new NumberSetting("Max Distance", "Tracer range", 256.0, 0.0, 256.0, 1.0));
    public final NumberSetting distanceOffscreen = addSetting(new NumberSetting("Distance Offscreen", "Arrow distance from screen centre", 200.0, 0.0, 500.0, 1.0));
    public final BooleanSetting distanceColors = addSetting(new BooleanSetting("Distance Colors", "Color by distance", false));
    public final ColorSetting friendColor = addSetting(new ColorSetting("Friend Color", "Friend tracer color", 0xFF00FF88));
    public final ColorSetting playersColor = addSetting(new ColorSetting("Players Color", "Player tracer color", 0xFF8081F3));
    public final ColorSetting animalsColor = addSetting(new ColorSetting("Animals Color", "Animal tracer color", 0xFF7FB93B));
    public final ColorSetting waterAnimalsColor = addSetting(new ColorSetting("Water Animals Color", "Water animal color", 0xFF34BF5F));
    public final ColorSetting monstersColor = addSetting(new ColorSetting("Monsters Color", "Monster color", 0xFFFC5451));
    public final ColorSetting ambientColor = addSetting(new ColorSetting("Ambient Color", "Ambient color", 0xFF7F527F));
    public final ColorSetting miscColor = addSetting(new ColorSetting("Misc Color", "Misc color", 0xFF7F7F80));

    public SpectreTracers() {
        super("SpectreTracers", "Spectre port: displays tracer lines to specified entities.", Category.SPECTRE);
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (!isEnabled() || mc.world == null || mc.player == null) return;
        Vec3d eye = ProjectionUtil.eye(tickDelta);

        if (style.is("Lines")) {
            for (Entity entity : targets()) {
                int color = colorOf(entity);
                Vec3d feet = ProjectionUtil.lerpPos(entity, tickDelta);
                double height = entity.getBoundingBox().getLengthY();

                double up = switch (target.getValue()) {
                    case "Head" -> height;
                    case "Body" -> height / 2.0;
                    default -> 0.0;
                };
                drawLineDot(context, tickDelta, eye, feet.add(0.0, up, 0.0), color);
                if (stem.isEnabled()) {
                    drawLineDot(context, tickDelta, eye, feet, color);
                }
            }
        } else {
            // Offscreen: screen-edge arrows for entities outside the view
            int sw = RenderUtils.screenWidth();
            int sh = RenderUtils.screenHeight();
            float reach = (float) distanceOffscreen.getAsDouble();
            for (Entity entity : targets()) {
                int color = colorOf(entity);
                Vec3d feet = ProjectionUtil.lerpPos(entity, tickDelta);
                double[] cs = ProjectionUtil.cameraSpace(feet.add(0, entity.getBoundingBox().getLengthY() / 2, 0), tickDelta);
                if (cs == null) continue;
                if (cs[0] == 0 && cs[1] == 0) continue;
                double angle;
                if (cs[0] != 0 || cs[1] != 0) {
                    angle = Math.atan2(cs[1], cs[0]);
                } else {
                    continue;
                }
                // Behind the camera flips the arrow to the mirrored side
                if (cs[2] > 0) {
                    angle += Math.PI;
                }
                arrow(context, sw / 2.0 + reach * Math.cos(angle) - 5, sh / 2.0 + reach * Math.sin(angle) - 5, angle, color);
            }
        }
    }

    private List<Entity> targets() {
        List<Entity> out = new ArrayList<>();
        for (Entity entity : mc.world.getEntities()) {
            if (ignoreSelf.isEnabled() && entity == mc.player) continue;
            if (ignoreFriends.isEnabled() && entity instanceof PlayerEntity player) {
                String name = player.getGameProfile().name();
                if (name != null && com.anormal.client.module.impl.client.Friends.isFriend(name)) continue;
            }
            if (!isVisible(entity)) continue;
            double dist = mc.player.distanceTo(entity);
            if (maxDistance.getValue() > 0 && dist > maxDistance.getValue()) continue;
            if (!category(entity)) continue;
            out.add(entity);
        }
        return out;
    }

    private boolean isVisible(Entity entity) {
        return showInvisible.isEnabled() || !entity.isInvisible();
    }

    private boolean category(Entity entity) {
        if (entity instanceof PlayerEntity) return players.isEnabled();
        if (entity instanceof Monster || entity instanceof MobEntity && entity instanceof net.minecraft.entity.mob.HostileEntity) return monsters.isEnabled();
        if (entity instanceof WaterCreatureEntity) return waterAnimals.isEnabled();
        if (entity instanceof AnimalEntity) return animals.isEnabled();
        if (entity instanceof AmbientEntity) return ambient.isEnabled();
        return misc.isEnabled();
    }

    private int colorOf(Entity entity) {
        if (entity instanceof PlayerEntity player) {
            String name = player.getGameProfile().name();
            if (name != null && com.anormal.client.module.impl.client.Friends.isFriend(name)) {
                return friendColor.getValue();
            }
        }
        if (distanceColors.isEnabled()) {
            double dist = mc.player == null ? 0 : mc.player.distanceTo(entity);
            double f = Math.min(1.0, dist / 60.0);
            int r = (int) (255 * f);
            int g = (int) (255 * (1 - f));
            return 0xFF000000 | (r << 16) | (g << 8);
        }
        if (entity instanceof PlayerEntity) return playersColor.getValue();
        if (entity instanceof WaterCreatureEntity) return waterAnimalsColor.getValue();
        if (entity instanceof AnimalEntity) return animalsColor.getValue();
        if (entity instanceof AmbientEntity) return ambientColor.getValue();
        if (entity instanceof Monster || (entity instanceof net.minecraft.entity.mob.HostileEntity)) return monstersColor.getValue();
        return miscColor.getValue();
    }

    private static void drawLineDot(DrawContext context, float tickDelta, Vec3d eye, Vec3d target, int color) {
        int[] from = ProjectionUtil.project(eye, tickDelta);
        int[] to = ProjectionUtil.project(target, tickDelta);
        if (from == null || to == null) return;
        RenderUtils.drawLine(context, from[0], from[1], to[0], to[1], color, 1);
    }

    private void arrow(DrawContext context, double cx, double cy, double angle, int color) {
        // small screen-space arrow rotated toward the entity
        double s = 10;
        double tipX = cx + (s * 1.4) * Math.cos(angle);
        double tipY = cy + (s * 1.4) * Math.sin(angle);
        double a1 = angle + Math.PI - 0.4, a2 = angle + Math.PI + 0.4;
        int p1x = (int) (cx + s * Math.cos(a1) + 5), p1y = (int) (cy + s * Math.sin(a1) + 5);
        int p2x = (int) (cx + s * Math.cos(a2) + 5), p2y = (int) (cy + s * Math.sin(a2) + 5);
        RenderUtils.drawLine(context, p1x, p1y, (int) tipX, (int) tipY, color, 1);
        RenderUtils.drawLine(context, p2x, p2y, (int) tipX, (int) tipY, color, 1);
        RenderUtils.drawLine(context, p1x, p1y, p2x, p2y, color, 1);
    }
}
