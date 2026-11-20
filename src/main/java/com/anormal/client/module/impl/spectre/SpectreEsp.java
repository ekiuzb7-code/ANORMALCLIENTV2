package com.anormal.client.module.impl.spectre;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.module.impl.client.Friends;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.ColorUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Spectre port: "ESP" — highlights players, hostile and passive mobs with outlines, boxes
 * and colours.
 *
 * <p>Spectre draws gizmo cuboids and a glow pass; this port keeps the entity grouping, the
 * priority sort (players first, then distance), the per-group colours and the friend
 * highlight, draws the boxes through the Spectre box renderer and uses the client-side glow
 * flag for the "Glow" shape.</p>
 */
public final class SpectreEsp extends Module {
    private static final int ENTITY_CAP = 96;

    public final BooleanSetting espPlayers = addSetting(new BooleanSetting("Players", "Highlight players", true));
    public final BooleanSetting espHostiles = addSetting(new BooleanSetting("Hostile Mobs", "Highlight hostile mobs", true));
    public final BooleanSetting espPassives = addSetting(new BooleanSetting("Passive Mobs", "Highlight passive mobs", false));
    public final BooleanSetting ignoreSelf = addSetting(new BooleanSetting("Ignore Self", "Never highlight yourself", true));
    public final ModeSetting shape = addSetting(new ModeSetting("Shape", "How targets are drawn", "Glow", "Glow", "Box", "Fill", "Both"));
    public final NumberSetting range = addSetting(new NumberSetting("Range", "Maximum target distance", 96.0, 16.0, 256.0, 1.0));
    public final NumberSetting maxEntities = addSetting(new NumberSetting("Max Entities", "How many targets are drawn", 48.0, 8.0, 96.0, 1.0));
    public final ColorSetting playerColor = addSetting(new ColorSetting("Player Color", "Colour for players", ColorUtils.rgba(255, 255, 255, 255)));
    public final BooleanSetting friends = addSetting(new BooleanSetting("Highlight Friends", "Use a separate colour for friends", true));
    public final ColorSetting friendColor = addSetting(new ColorSetting("Friend Color", "Colour for friends", ColorUtils.rgba(100, 200, 255, 255)));
    public final ColorSetting mobColor = addSetting(new ColorSetting("Hostile Color", "Colour for hostile mobs", ColorUtils.rgba(255, 60, 60, 255)));
    public final ColorSetting animalColor = addSetting(new ColorSetting("Passive Color", "Colour for passive mobs", ColorUtils.rgba(120, 255, 120, 255)));
    public final NumberSetting fillAlpha = addSetting(new NumberSetting("Fill Alpha", "Fill opacity", 0.3, 0.0, 1.0, 0.05));
    public final NumberSetting lineWidth = addSetting(new NumberSetting("Line Width", "Outline width", 1.5, 0.5, 5.0, 0.5));

    private final List<Entity> glowing = new ArrayList<>();

    public SpectreEsp() {
        super("SpectreEsp", "Spectre port: highlights players, hostile and passive mobs with outlines and boxes.", Category.SPECTRE);
    }

    @Override
    public void onDisable() {
        clearGlow();
    }

    @Override
    public void onTick() {
        if (!shape.is("Glow")) {
            clearGlow();
            return;
        }
        clearGlow();
        for (Target target : targets()) {
            target.entity.setGlowing(true);
            glowing.add(target.entity);
        }
    }

    private void clearGlow() {
        for (Entity entity : glowing) {
            if (entity != null) entity.setGlowing(false);
        }
        glowing.clear();
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        // "Glow" also draws the outline box: the glow flag alone is easy to miss
        // (team-colour dependent), so the box guarantees the ESP is always visible.
        if (mc.world == null || mc.player == null) return;
        boolean box = !shape.is("Fill");
        boolean fill = shape.is("Fill") || shape.is("Both");
        int alpha = (int) Math.round(fillAlpha.getAsDouble() * 255.0) << 24;

        for (Target target : targets()) {
            int color = colorFor(target.entity);
            Box interpolated = interpolatedBox(target.entity, tickDelta);
            int fillColor = fill ? (color & 0x00FFFFFF) | alpha : 0;
            SpectreHoleEsp.drawBox(context, tickDelta, interpolated, color, fillColor, box, fill, (int) Math.round(lineWidth.getAsDouble()));
        }
    }

    private List<Target> targets() {
        if (mc.world == null || mc.player == null) return List.of();
        int max = (int) Math.max(1, Math.min(ENTITY_CAP, maxEntities.getAsDouble()));
        double maxDistance = range.getAsDouble();
        double maxSq = maxDistance * maxDistance;
        List<Target> result = new ArrayList<>();

        for (Entity entity : mc.world.getEntities()) {
            int group = groupOf(entity);
            if (group < 0 || !shown(group)) continue;
            if (entity == mc.player && ignoreSelf.isEnabled()) continue;
            if (entity instanceof PlayerEntity player && player.isSpectator()) continue;
            if (entity.isRemoved()) continue;
            if (entity instanceof LivingEntity living && living.isDead()) continue;
            double distanceSq = entity.squaredDistanceTo(mc.player);
            if (distanceSq > maxSq) continue;
            result.add(new Target(entity, group, distanceSq));
        }
        result.sort(Comparator.<Target, Boolean>comparing(target -> target.group != 0)
                .thenComparingDouble(target -> target.distanceSq));
        return result.size() > max ? result.subList(0, max) : result;
    }

    private static int groupOf(Entity entity) {
        if (entity instanceof PlayerEntity) return 0;
        if (entity instanceof HostileEntity) return 1;
        return entity instanceof MobEntity ? 2 : -1;
    }

    private boolean shown(int group) {
        return switch (group) {
            case 0 -> espPlayers.isEnabled();
            case 1 -> espHostiles.isEnabled();
            case 2 -> espPassives.isEnabled();
            default -> false;
        };
    }

    private int colorFor(Entity entity) {
        if (entity instanceof PlayerEntity player) {
            String name = player.getGameProfile().name();
            if (friends.isEnabled() && name != null && Friends.isFriend(name)) return friendColor.getColor();
            return playerColor.getColor();
        }
        return entity instanceof HostileEntity ? mobColor.getColor() : animalColor.getColor();
    }

    private static Box interpolatedBox(Entity entity, float tickDelta) {
        double x = entity.getX();
        double y = entity.getY();
        double z = entity.getZ();
        double lerpX = entity.lastRenderX + (x - entity.lastRenderX) * tickDelta - x;
        double lerpY = entity.lastRenderY + (y - entity.lastRenderY) * tickDelta - y;
        double lerpZ = entity.lastRenderZ + (z - entity.lastRenderZ) * tickDelta - z;
        return entity.getBoundingBox().offset(lerpX, lerpY, lerpZ);
    }

    private record Target(Entity entity, int group, double distanceSq) { }
}
