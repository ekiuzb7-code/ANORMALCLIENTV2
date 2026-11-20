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
        // Refresh the shared camera cache for this frame; a stale cache is one of the reasons
        // the lines could disappear entirely.
        ProjectionUtil.beginFrame(tickDelta);

        int sw = RenderUtils.screenWidth();
        int sh = RenderUtils.screenHeight();
        int cx = sw / 2;
        int cy = sh / 2;

        if (style.is("Lines")) {
            // The line starts at the crosshair. Projecting the camera position itself always
            // returned null (its depth is 0), so the projection failed and nothing was drawn.
            for (Entity entity : targets()) {
                int color = colorOf(entity);
                Vec3d feet = ProjectionUtil.lerpPos(entity, tickDelta);
                double height = entity.getBoundingBox().getLengthY();

                double up = switch (target.getValue()) {
                    case "Head" -> height;
                    case "Body" -> height / 2.0;
                    default -> 0.0;
                };
                int[] to = ProjectionUtil.project(feet.add(0.0, up, 0.0), tickDelta);
                if (to == null) continue; // behind the camera

                RenderUtils.drawLine(context, cx, cy, to[0], to[1], color, 1);
                if (stem.isEnabled() && up > 0.01) {
                    int[] base = ProjectionUtil.project(feet, tickDelta);
                    if (base != null) {
                        RenderUtils.drawLine(context, to[0], to[1], base[0], base[1], color, 1);
                    }
                }
            }
            return;
        }

        // Offscreen: arrows around the crosshair pointing at entities that are outside the view.
        double reach = distanceOffscreen.getAsDouble();
        for (Entity entity : targets()) {
            int color = colorOf(entity);
            Vec3d pos = ProjectionUtil.lerpPos(entity, tickDelta)
                    .add(0.0, entity.getBoundingBox().getLengthY() / 2.0, 0.0);

            int[] onScreen = ProjectionUtil.project(pos, tickDelta);
            if (onScreen != null && onScreen[0] >= 0 && onScreen[0] <= sw
                    && onScreen[1] >= 0 && onScreen[1] <= sh) {
                continue; // already visible - the arrows are for offscreen targets only
            }

            double[] cs = ProjectionUtil.cameraSpace(pos, tickDelta);
            if (cs == null) continue;
            if (Math.abs(cs[0]) < 1.0E-4 && Math.abs(cs[1]) < 1.0E-4) continue;

            // Camera space (Y up) -> screen space (Y down).
            double angle = Math.atan2(-cs[1], cs[0]);
            if (cs[2] < 0) angle += Math.PI; // behind the camera: mirror to the opposite side
            double ax = cx + Math.cos(angle) * reach;
            double ay = cy + Math.sin(angle) * reach;
            arrow(context, ax, ay, angle, color);
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

    /** Small arrow whose tip points along {@code angle} (screen-space radians, Y down). */
    private void arrow(DrawContext context, double cx, double cy, double angle, int color) {
        double s = 7.0;
        int tx = (int) Math.round(cx + Math.cos(angle) * s * 1.7);
        int ty = (int) Math.round(cy + Math.sin(angle) * s * 1.7);
        int x1 = (int) Math.round(cx + Math.cos(angle + 2.5) * s);
        int y1 = (int) Math.round(cy + Math.sin(angle + 2.5) * s);
        int x2 = (int) Math.round(cx + Math.cos(angle - 2.5) * s);
        int y2 = (int) Math.round(cy + Math.sin(angle - 2.5) * s);
        RenderUtils.drawLine(context, x1, y1, tx, ty, color, 2);
        RenderUtils.drawLine(context, x2, y2, tx, ty, color, 2);
        RenderUtils.drawLine(context, x1, y1, x2, y2, color, 2);
    }
}
