package com.anormal.client.util;

import com.anormal.client.mixin.GameRendererMixin;
import com.anormal.client.module.ModuleManager;
import com.anormal.client.module.impl.render.FreeLook;
import com.anormal.client.module.impl.world.Freecam;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;

// Single world→screen projector for every marker module.
// Uses the LOOK direction (player yaw/pitch, or detached camera angles in
// freecam/freelook) instead of Camera getters, plus the real effective FOV.
public final class ProjectionUtil {
    private ProjectionUtil() {}

    public static int[] project(Vec3d p, float tickDelta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.gameRenderer == null || mc.getWindow() == null) return null;
        try {
            float yaw = mc.player.getYaw();
            float pitch = mc.player.getPitch();
            Vec3d origin = mc.player.getEyePos();
            try {
                Freecam freecam = ModuleManager.getModule(Freecam.class);
                if (freecam != null && freecam.isEnabled() && freecam.isCameraActive()) {
                    yaw = freecam.getCamYaw();
                    pitch = freecam.getCamPitch();
                    origin = new Vec3d(freecam.getCamX(), freecam.getCamY(), freecam.getCamZ());
                } else {
                    FreeLook freeLook = ModuleManager.getModule(FreeLook.class);
                    if (freeLook != null && freeLook.isEnabled() && freeLook.isCameraActive()) {
                        yaw = freeLook.getLookYaw();
                        pitch = freeLook.getLookPitch();
                    }
                }
            } catch (Throwable ignored) {}

            // Interpolate the eye to render time: the GPU camera sits between ticks,
            // a tick-frozen origin made every marker swim while flying fast.
            try {
                Vec3d pv = mc.player.getVelocity();
                double back = 1.0 - Math.max(0.0, Math.min(1.0, tickDelta));
                origin = new Vec3d(origin.x - pv.x * back, origin.y - pv.y * back, origin.z - pv.z * back);
            } catch (Throwable ignored) {}

            double yawRad = Math.toRadians(yaw);
            double pitchRad = Math.toRadians(pitch);
            double cosP = Math.cos(pitchRad);
            double fx = -Math.sin(yawRad) * cosP;
            double fy = -Math.sin(pitchRad);
            double fz = Math.cos(yawRad) * cosP;
            double dx = p.x - origin.x, dy = p.y - origin.y, dz = p.z - origin.z;
            double depth = dx * fx + dy * fy + dz * fz;
            if (depth < 0.1) return null;
            double rx = -Math.cos(yawRad), rz = -Math.sin(yawRad);
            double ux = -rz * fy, uy = rz * fx - rx * fz, uz = rx * fy;
            double cx = dx * rx + dz * rz;
            double cy = dx * ux + dy * uy + dz * uz;
            int w = mc.getWindow().getScaledWidth(), h = mc.getWindow().getScaledHeight();
            float effFov = 70.0f;
            try {
                effFov = ((GameRendererMixin) (Object) mc.gameRenderer).callGetFov(
                        mc.gameRenderer.getCamera(), tickDelta, true);
            } catch (Throwable ignored) {
                try {
                    effFov = mc.options.getFov().getValue();
                } catch (Throwable ignored2) {}
            }
            double f = (h / 2.0) / Math.tan(Math.toRadians(Math.max(30, Math.min(110, effFov)) / 2.0));
            return new int[]{(int) (w / 2.0 + cx / depth * f), (int) (h / 2.0 - cy / depth * f)};
        } catch (Throwable t) {
            return null;
        }
    }
}
