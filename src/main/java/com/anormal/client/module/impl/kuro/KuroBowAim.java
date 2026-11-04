package com.anormal.client.module.impl.kuro;

import com.anormal.client.lucid.WorldRender3D;
import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.module.impl.client.Friends;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.NumberSetting;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ChargedProjectilesComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BowItem;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Ported from kuro (dev.kuro.client.module.impl.BowAim).
 *
 * <p>Bow Aim: shows where the arrow will land. While you draw a bow (or hold a loaded crossbow)
 * the exact flight path is simulated every frame from your real eye position, view direction,
 * the draw power and your own movement (arrows inherit it), and drawn as a line with a marker
 * box where it hits a block. A player or mob the path crosses is boxed. Arrows: gravity 0.05,
 * drag 0.99. Bow speed = 3.0 x draw power, crossbow bolt = 3.15. Nothing is sent to the server;
 * this only draws.</p>
 */
public class KuroBowAim extends Module {
    private final BooleanSetting crossbows = new BooleanSetting("Crossbows",
            "Also show the bolt for loaded crossbows", true);
    private final BooleanSetting preview = new BooleanSetting("Show bow when not drawing",
            "Full-draw preview while a bow is merely held", false);
    private final ColorSetting color = new ColorSetting("Color", "Path line colour", 0xFF3FE0C0);
    private final ColorSetting hitColor = new ColorSetting("Hit color", "Colour of the impact part + marker", 0xFFFF3B3B);
    private final BooleanSetting landing = new BooleanSetting("Landing marker",
            "Draw a small box where the arrow lands", true);
    private final BooleanSetting hitEntities = new BooleanSetting("Detect entities",
            "Box the entity the path hits", true);
    private final NumberSetting maxTicks = new NumberSetting("Max flight (ticks)",
            "Simulation length", 100.0, 20.0, 200.0, 10.0);

    private static final double GRAVITY = 0.05, DRAG = 0.99;

    private Vec3d end;
    private BlockHitResult blockHit;
    private Entity entityHit;
    private final List<Vec3d> pts = new ArrayList<>();
    private boolean registered;

    public KuroBowAim() {
        super("BowAim", "Draws the arrow's flight path and landing spot (kuro port)", Category.KURO);
        addSetting(crossbows);
        addSetting(preview);
        addSetting(color);
        addSetting(hitColor);
        addSetting(landing);
        addSetting(hitEntities);
        addSetting(maxTicks);
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
        if (!isEnabled()) { end = null; pts.clear(); return; }
        renderWorld(wrc);
    }

    /** Launch speed for what you are shooting, or 0 for "nothing to show". */
    private double launchSpeed() {
        PlayerEntity p = mc.player;
        if (p.isUsingItem()) {
            ItemStack use = p.getActiveItem();
            if (use.getItem() instanceof BowItem) {
                float f = p.getItemUseTime() / 20.0f;
                f = (f * f + f * 2.0f) / 3.0f;
                f = Math.min(f, 1.0f);
                return f < 0.1f ? 0.0 : f * 3.0;     // vanilla: a draw under 0.1 does not fire
            }
            return 0.0;
        }
        if (crossbows.isEnabled()) {
            ItemStack s = loadedCrossbow(p);
            if (s != null) return 3.15;
        }
        if (preview.isEnabled() && (p.getMainHandStack().getItem() instanceof BowItem
                || p.getOffHandStack().getItem() instanceof BowItem)) return 3.0;
        return 0.0;
    }

    /** The loaded crossbow in either hand that holds an arrow (a firework rocket flies differently, so it is skipped). */
    private static ItemStack loadedCrossbow(PlayerEntity p) {
        for (ItemStack s : new ItemStack[]{p.getMainHandStack(), p.getOffHandStack()}) {
            if (!CrossbowItem.isCharged(s)) continue;
            ChargedProjectilesComponent charged = s.get(DataComponentTypes.CHARGED_PROJECTILES);
            if (charged == null || charged.isEmpty()) continue;
            if (charged.contains(Items.FIREWORK_ROCKET)) continue;
            return s;
        }
        return null;
    }

    void renderWorld(WorldRenderContext wrc) {
        if (mc.player == null || mc.world == null || mc.player.isSpectator()) { end = null; pts.clear(); return; }
        double speed = launchSpeed();
        if (speed <= 0.0) { end = null; pts.clear(); return; }

        Vec3d look = mc.player.getRotationVec(1.0f).normalize();
        Vec3d inherited = mc.player.getVelocity();
        Vec3d vel = new Vec3d(
                look.x * speed + inherited.x,
                look.y * speed + (mc.player.isOnGround() ? 0.0 : inherited.y),
                look.z * speed + inherited.z);
        Vec3d start = mc.player.getEyePos().subtract(0, 0.1, 0);   // arrows spawn 0.1 below the eyes
        simulate(start, vel);

        Camera camera = wrc.gameRenderer().getCamera();
        if (camera == null || !camera.isReady()) return;
        MatrixStack matrices = wrc.matrices();
        VertexConsumerProvider consumers = wrc.consumers();
        if (matrices == null || consumers == null) return;

        int c = color.getColor() & 0xFFFFFF;
        int hc = hitColor.getColor() & 0xFFFFFF;
        WorldRender3D.begin(matrices, camera);
        try {
            for (int i = 1; i < pts.size(); i++) {
                // the last few ticks before the impact take the hit colour so the end of the path stands out
                WorldRender3D.drawLine(consumers, pts.get(i - 1), pts.get(i),
                        i >= pts.size() - 2 ? hc : c);
            }
            if (landing.isEnabled() && end != null) {
                int rgb = entityHit != null || blockHit != null ? hc : c;
                Box marker = new Box(end.x - 0.18, end.y - 0.18, end.z - 0.18,
                        end.x + 0.18, end.y + 0.18, end.z + 0.18);
                WorldRender3D.drawBox(consumers, marker, 0xFF000000 | rgb);
            }
            if (hitEntities.isEnabled() && entityHit != null) {
                WorldRender3D.drawBox(consumers, entityHit.getBoundingBox().expand(0.05), 0xFF000000 | hc);
            }
        } finally {
            WorldRender3D.end(matrices);
        }
    }

    /** Simple arrow integration step by step; stops at the first block or entity the payload crosses. */
    private void simulate(Vec3d start, Vec3d vel) {
        pts.clear();
        end = null;
        blockHit = null;
        entityHit = null;
        Vec3d pos = start;
        Vec3d motion = vel;
        pts.add(pos);
        int ticks = maxTicks.getValue().intValue();
        for (int t = 0; t < ticks; t++) {
            Vec3d nextPos = pos.add(motion.x, motion.y * 1.0, motion.z);
            // entity crossing: any living target whose box the segment enters
            if (hitEntities.isEnabled()) {
                Entity hitEntity = null;
                for (Entity e : mc.world.getEntities()) {
                    if (!(e instanceof net.minecraft.entity.LivingEntity le) || le == mc.player || !le.isAlive()) continue;
                    Box b = le.getBoundingBox().expand(0.1);
                    if (b.raycast(pos, nextPos).isPresent()) { hitEntity = le; break; }
                }
                if (hitEntity != null) {
                    entityHit = hitEntity;
                    end = pts.get(pts.size() - 1);
                    return;
                }
            }
            BlockHitResult block = mc.world.raycast(new RaycastContext(pos, nextPos,
                    RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player));
            if (block.getType() == HitResult.Type.BLOCK) {
                blockHit = block;
                Vec3d hit = block.getPos();
                if (pts.get(pts.size() - 1).squaredDistanceTo(hit) > 1.0e-4) pts.add(hit);
                end = hit;
                return;
            }
            pos = nextPos;
            motion = new Vec3d(motion.x * DRAG, motion.y * DRAG, motion.z * DRAG);
            motion = new Vec3d(motion.x, motion.y - GRAVITY, motion.z);
            pts.add(pos);
        }
        end = pts.get(pts.size() - 1);
    }
}
