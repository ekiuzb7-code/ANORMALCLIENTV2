package com.anormal.client.module.impl.kuro;

import com.anormal.client.lucid.WorldRender3D;
import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.module.impl.client.Friends;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.List;

/**
 * kuro {@code ReachCircle} port: har o'yinchi oyog'i ostidagi yerda uning
 * zarba masofasini ko'rsatuvchi doira. Radius = hujum masofasi (survivalda
 * 3.0 blok) + sizning yarim hitboxingiz, chunki zarba uning masofasi SIZNING
 * biror qismingizga teganda tushadi. Siz doira ichida turganda u "In reach"
 * (xavfli) rangga o'tadi. Doira o'yinchiga ergashadi va uning pastidagi
 * yerda chiziladi.
 *
 * <p>kuro'dagi {@code Setting.Hue} rang g'ildiragi va UiThemes Anormalda
 * yo'q: ranglar doimiy (pushti / qizil) ishlatiladi. kuro'daki
 * WorldGfx.ring yo'q — halqalar {@code WorldRender3D.drawLine} segmentlari
 * bilan chiziladi.</p>
 */
public class KuroReachCircle extends Module {
    private static final int ACCENT = 0xFFE57FD4;               // kuro follow-theme accent (pushti)
    private static final int DANGER = 0xFFFF6E5A;               // "In reach" rangi

    private final NumberSetting reach = new NumberSetting("Reach (blocks)", "Attacker reach radius", 3.0, 2.0, 6.0, 0.1);
    private final BooleanSetting addHitbox = new BooleanSetting("Add your hitbox", "Half your own hitbox is added", true);
    private final NumberSetting range = new NumberSetting("Range", "Max distance to draw", 40.0, 8.0, 100.0, 4.0);
    private final BooleanSetting bubble = new BooleanSetting("Filled bubble", "Faint inner disc", true);
    private final BooleanSetting glow = new BooleanSetting("Glow", "Wide faint band around the ring", true);
    private final BooleanSetting pulse = new BooleanSetting("Pulse", "Breathing opacity", true);
    private final BooleanSetting friends = new BooleanSetting("Friends", "Also draw friends", false);
    private final BooleanSetting self = new BooleanSetting("Include yourself", false);

    private boolean registered;

    public KuroReachCircle() {
        super("Reach Circle", "Shows every player's attack reach as a ground circle", Category.KURO);
        addSetting(reach);
        addSetting(addHitbox);
        addSetting(range);
        addSetting(bubble);
        addSetting(glow);
        addSetting(pulse);
        addSetting(friends);
        addSetting(self);
    }

    @Override
    public void onEnable() {
        if (registered) return;
        registered = true;
        WorldRenderEvents.END_MAIN.register(this::dispatchWorld);
    }

    private void dispatchWorld(WorldRenderContext wrc) {
        if (!isEnabled() || mc.world == null || mc.player == null) return;
        Camera camera = wrc.gameRenderer().getCamera();
        if (camera == null || !camera.isReady()) return;
        MatrixStack matrices = wrc.matrices();
        VertexConsumerProvider consumers = wrc.consumers();
        if (matrices == null || consumers == null) return;

        double max2 = range.getValue() * range.getValue();
        double radius = reach.getValue() + (addHitbox.isEnabled() ? 0.3 : 0.0);
        double wave = pulse.isEnabled()
                ? 0.5 + 0.5 * Math.sin((mc.world.getTime() + tickDelta(wrc)) * 0.15) : 0.5;
        Vec3d me = mc.player.getLerpedPos(tickDelta(wrc));
        List<AbstractClientPlayerEntity> players = mc.world.getPlayers();

        WorldRender3D.begin(matrices, camera);
        try {
            for (PlayerEntity p : players) {
                if (p == mc.player && !self.isEnabled()) continue;
                if (p.isSpectator() || !p.isAlive() || KuroAntiBot.isBot(p)) continue;
                if (Friends.isFriend(p.getGameProfile().name()) && !friends.isEnabled()) continue;
                if (p.squaredDistanceTo(mc.player) > max2) continue;
                Vec3d pos = p.getLerpedPos(tickDelta(wrc));
                double y = groundY(pos) + 0.02;
                boolean inReach = p != mc.player
                        && Math.hypot(me.x - pos.x, me.z - pos.z) <= radius
                        && Math.abs(me.y - pos.y) < 3.0;
                int rgb = (inReach ? DANGER : ACCENT) & 0xFFFFFF;
                int a = (int) (0xE0 * (0.75 + 0.25 * wave));
                if (bubble.isEnabled()) ringFill(consumers, pos.x, y, pos.z, radius, (0x1C << 24) | rgb);
                double w = 0.05;
                if (glow.isEnabled()) band(consumers, pos.x, y, pos.z, radius - w * 3.5, radius + w * 3.5, 24, (0x30 << 24) | rgb);
                band(consumers, pos.x, y, pos.z, radius - w, radius + w, 64, (a << 24) | brighten(rgb));
            }
        } finally {
            WorldRender3D.end(matrices);
        }
    }

    /** Halqa bandi: r0..r1 oralig'ini segments chiziq bilan yopadi (kuro'dagi ring ko'rinishiga yaqin). */
    private static void band(VertexConsumerProvider consumers, double cx, double cy, double cz,
                             double r0, double r1, int segments, int argb) {
        for (int i = 0; i < segments; i++) {
            double a0 = (Math.PI * 2.0) * i / segments;
            double a1 = (Math.PI * 2.0) * (i + 1) / segments;
            Vec3d p0 = new Vec3d(cx + Math.cos(a0) * r0, cy, cz + Math.sin(a0) * r0);
            Vec3d p1 = new Vec3d(cx + Math.cos(a1) * r0, cy, cz + Math.sin(a1) * r0);
            Vec3d p2 = new Vec3d(cx + Math.cos(a1) * r1, cy, cz + Math.sin(a1) * r1);
            Vec3d p3 = new Vec3d(cx + Math.cos(a0) * r1, cy, cz + Math.sin(a0) * r1);
            WorldRender3D.drawLine(consumers, p0, p1, argb);
            WorldRender3D.drawLine(consumers, p1, p2, argb);
            WorldRender3D.drawLine(consumers, p2, p3, argb);
            WorldRender3D.drawLine(consumers, p3, p0, argb);
        }
    }

    /** Ichki soha: radial chiziqlar bilan (kuro'dagi faint bubble taxminan). */
    private static void ringFill(VertexConsumerProvider consumers, double cx, double cy, double cz, double r, int argb) {
        int segments = 48;
        for (int i = 0; i < segments; i++) {
            double a = (Math.PI * 2.0) * i / segments;
            WorldRender3D.drawLine(consumers, new Vec3d(cx, cy, cz),
                    new Vec3d(cx + Math.cos(a) * r, cy, cz + Math.sin(a) * r), argb);
        }
    }

    /** O'yinchining oyog'i ostidagi yer balandligi (6 blokqa qadar pastga qaraydi); bo'sh bo'lsa oyoqi. */
    private double groundY(Vec3d feet) {
        Vec3d from = feet.add(0, 0.3, 0), to = feet.add(0, -6.0, 0);
        BlockHitResult r = mc.world.raycast(new RaycastContext(from, to,
                RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player));
        return r.getType() == HitResult.Type.MISS ? feet.y : r.getPos().y;
    }

    private static int brighten(int rgb) {
        int r = (int) (((rgb >> 16) & 0xFF) * 0.45 + 255 * 0.55);
        int g = (int) (((rgb >> 8) & 0xFF) * 0.45 + 255 * 0.55);
        int b = (int) ((rgb & 0xFF) * 0.45 + 255 * 0.55);
        return (r << 16) | (g << 8) | b;
    }

    private static float tickDelta(WorldRenderContext wrc) {
        return mc.getRenderTickCounter().getTickProgress(true);
    }
}
