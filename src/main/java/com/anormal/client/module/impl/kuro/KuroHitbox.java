package com.anormal.client.module.impl.kuro;

import com.anormal.client.lucid.WorldRender3D;
import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.module.ModuleManager;
import com.anormal.client.module.impl.client.Friends;
import com.anormal.client.module.impl.world.Freecam;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.NumberSetting;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ColorHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Ported from kuro (dev.kuro.client.module.impl.Hitbox).
 *
 * <p>Outlined boxes around players (optionally mobs and end crystals), drawn through walls with
 * the shared Lucid world renderer ({@code WorldRender3D}, depth test disabled). Friends get the
 * Friends colour. "Show expanded box" includes the size that KuroHitboxExpand adds (the targeting
 * margin), and "Smooth motion" slides the box between tick positions.</p>
 */
public class KuroHitbox extends Module {
    private final BooleanSetting players = new BooleanSetting("Players", "Box other players", true);
    private final BooleanSetting mobs = new BooleanSetting("Mobs", "Box other living entities", false);
    private final BooleanSetting crystals = new BooleanSetting("End crystals", "Box end crystals", false);
    private final NumberSetting range = new NumberSetting("Range (blocks)", "Maximum draw distance", 64.0, 8.0, 200.0, 8.0);
    private final NumberSetting fillOpacity = new NumberSetting("Fill opacity %", "Alpha of the fill (0 = fill off)", 45.0, 0.0, 100.0, 5.0);
    private final BooleanSetting showExpanded = new BooleanSetting("Show expanded box", "Include the KuroHitboxExpand targeting margin", true);
    private final BooleanSetting smooth = new BooleanSetting("Smooth motion", "Slide boxes between tick positions", false);
    private final ColorSetting color = new ColorSetting("Color", "Box colour", 0xFF55CCFF);
    private final BooleanSetting friendColor = new BooleanSetting("Friend colour", "Use the Friends colour for friends", true);
    private final BooleanSetting invisible = new BooleanSetting("Invisible", "Draw invisible entities too", true);

    private static final int MAX_BOXES = 200;
    private boolean registered;

    public KuroHitbox() {
        super("Hitbox", "Draws hitboxes through walls (kuro port)", Category.KURO);
        addSetting(players);
        addSetting(mobs);
        addSetting(crystals);
        addSetting(range);
        addSetting(fillOpacity);
        addSetting(showExpanded);
        addSetting(smooth);
        addSetting(color);
        addSetting(friendColor);
        addSetting(invisible);
    }

    @Override
    public void onEnable() {
        if (registered) return;
        try {
            net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents.END_MAIN.register(this::anormal$dispatchWorld);
            registered = true;
        } catch (Throwable ignored) {
        }
    }

    private void anormal$dispatchWorld(WorldRenderContext wrc) {
        if (!isEnabled()) return;
        renderWorld(wrc);
    }

    void renderWorld(WorldRenderContext wrc) {
        if (mc.world == null || mc.player == null) return;
        Camera camera = wrc.gameRenderer().getCamera();
        if (camera == null || !camera.isReady()) return;
        MatrixStack matrices = wrc.matrices();
        VertexConsumerProvider consumers = wrc.consumers();
        if (matrices == null || consumers == null) return;

        Freecam freecam = ModuleManager.getModule(Freecam.class);
        if (freecam != null && freecam.isEnabled()) return;          // camera far from the head boxes: off for clarity
        Vec3d cam = camera.getCameraPos();
        double max2 = range.getValue() * range.getValue();
        int rgb = color.getColor() & 0xFFFFFF;
        int alpha = (int) (fillOpacity.getAsDouble() / 100.0 * 255.0) & 0xFF;
        int fill = alpha == 0 ? 0 : (alpha << 24) | rgb;
        int line = 0xFF000000 | rgb;
        int drawn = 0;

        WorldRender3D.begin(matrices, camera);
        try {
            for (Entity e : mc.world.getEntities()) {
                if (drawn >= MAX_BOXES) break;
                if (e == mc.player) continue;
                if (e.isRemoved()) continue;
                if (e instanceof PlayerEntity p) {
                    if (!players.isEnabled() || p.isSpectator()) continue;
                } else if (e instanceof EndCrystalEntity) {
                    if (!crystals.isEnabled()) continue;
                } else if (e instanceof LivingEntity && !(e instanceof ArmorStandEntity)) {
                    if (!mobs.isEnabled()) continue;
                } else continue;
                if (!invisible.isEnabled() && e.isInvisible()) continue;

                // The box the game really aims at: the entity's own bounding box (at its last tick
                // position) grown by its targeting margin — where KuroHitboxExpand adds its size.
                Box bb = e.getBoundingBox();
                Box ghost = KuroBacktrack.ghostBox(e);   // Backtrack (Ghost hitbox mode): the box lags behind the player
                if (ghost != null) bb = ghost;
                else if (smooth.isEnabled()) {
                    Vec3d lp = e.getLerpedPos(mc.getRenderTickCounter().getTickProgress(false));
                    bb = bb.offset(lp.x - e.getEntityPos().x, lp.y - e.getEntityPos().y, lp.z - e.getEntityPos().z);
                }
                double m = showExpanded.isEnabled() ? e.getTargetingMargin() : 0;
                if (m > 0) bb = bb.expand(m);
                double cx = (bb.minX + bb.maxX) / 2, cy = (bb.minY + bb.maxY) / 2, cz = (bb.minZ + bb.maxZ) / 2;
                double dx = cx - cam.x, dy = cy - cam.y, dz = cz - cam.z;
                if (dx * dx + dy * dy + dz * dz > max2) continue;

                boolean friend = friendColor.isEnabled()
                        && e instanceof PlayerEntity fp
                        && Friends.isFriend(fp.getName().getString());
                if (friend) {
                    int frgb = ColorHelper.getArgb(255, 90, 200, 255) & 0xFFFFFF;
                    if (fill != 0) WorldRender3D.drawFilledBox(consumers, bb, (alpha << 24) | frgb);
                    WorldRender3D.drawBox(consumers, bb, 0xFF000000 | frgb);
                } else {
                    if (fill != 0) WorldRender3D.drawFilledBox(consumers, bb, fill);
                    WorldRender3D.drawBox(consumers, bb, line);
                }
                drawn++;
            }
        } finally {
            WorldRender3D.end(matrices);
        }
    }
}
