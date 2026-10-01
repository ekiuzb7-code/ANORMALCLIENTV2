package com.anormal.client.module.impl.world;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Vec3d;

public class Freecam extends Module {
    public final NumberSetting speed = new NumberSetting("Speed", "Flight camera speed", 1.5, 0.5, 5.0, 0.5);
    public final BooleanSetting allowInteracting = new BooleanSetting("Allow Interacting", "Interact with blocks and entities", false);
    public final BooleanSetting spawnFake = new BooleanSetting("Spawn Fake", "Keep client-side snapshot at origin", true);
    public final BooleanSetting moveFake = new BooleanSetting("Move Fake", "Let snapshot track camera anchor", false);
    public final BooleanSetting showPlayer = new BooleanSetting("Show Player", "Mark your real body position", true);

    // Frozen real body
    private double anchorX, anchorY, anchorZ;
    private float anchorYaw, anchorPitch;
    // Free camera (driven by CameraMixin)
    private double camX, camY, camZ;
    private float camYaw, camPitch;
    private double fakeX, fakeY, fakeZ;
    private boolean active = false;

    public Freecam() {
        super("Freecam", "Detached camera fly (body stays frozen, server-safe look)", Category.WORLD);
        addSetting(speed);
        addSetting(allowInteracting);
        addSetting(spawnFake);
        addSetting(moveFake);
        addSetting(showPlayer);
    }

    @Override
    public void onEnable() {
        active = false;
        if (mc.player == null) return;
        anchorX = mc.player.getX();
        anchorY = mc.player.getY();
        anchorZ = mc.player.getZ();
        anchorYaw = mc.player.getYaw();
        anchorPitch = mc.player.getPitch();
        // Camera starts at eyes, then flies free (through walls AND underground)
        Vec3d eye = mc.player.getEyePos();
        camX = eye.x;
        camY = eye.y;
        camZ = eye.z;
        camYaw = anchorYaw;
        camPitch = anchorPitch;
        fakeX = anchorX;
        fakeY = anchorY;
        fakeZ = anchorZ;
        active = true;
    }

    @Override
    public void onDisable() {
        active = false;
        if (mc.player == null) return;
        // Body never moved: just make sure it's exactly on anchor
        try {
            mc.player.setPosition(anchorX, anchorY, anchorZ);
            mc.player.setVelocity(new Vec3d(0.0, 0.0, 0.0));
            mc.player.fallDistance = 0.0f;
        } catch (Throwable ignored) {}
    }

    @Override
    public void onTick() {
        if (mc.player == null || !active) return;
        // Freeze the real body on anchor (no fall, no drift, no server correction)
        try {
            mc.player.setPosition(anchorX, anchorY, anchorZ);
            mc.player.setVelocity(new Vec3d(0.0, 0.0, 0.0));
            mc.player.fallDistance = 0.0f;
        } catch (Throwable ignored) {}

        if (!allowInteracting.isEnabled()) {
            mc.options.attackKey.setPressed(false);
            mc.options.useKey.setPressed(false);
        }

        // Mouse-look drives the CAMERA: steal player rotation, then restore body facing
        try {
            camYaw = mc.player.getYaw();
            camPitch = mc.player.getPitch();
            mc.player.setYaw(anchorYaw);
            mc.player.setPitch(anchorPitch);
        } catch (Throwable ignored) {}

        // Fly the CAMERA (not the player) — no collision, goes underground freely
        double s = speed.getValue() * 0.35;
        double rad = Math.toRadians(camYaw);
        double radP = Math.toRadians(camPitch);
        double fwdX = -Math.sin(rad) * Math.cos(radP);
        double fwdY = -Math.sin(radP);
        double fwdZ = Math.cos(rad) * Math.cos(radP);
        double mx = 0.0, my = 0.0, mz = 0.0;
        if (mc.options.forwardKey.isPressed()) { mx += fwdX; my += fwdY; mz += fwdZ; }
        if (mc.options.backKey.isPressed()) { mx -= fwdX; my -= fwdY; mz -= fwdZ; }
        if (mc.options.leftKey.isPressed()) { mx += fwdZ; mz -= fwdX; }
        if (mc.options.rightKey.isPressed()) { mx -= fwdZ; mz += fwdX; }
        if (mc.options.jumpKey.isPressed()) my += 1.0;
        if (mc.options.sneakKey.isPressed()) my -= 1.0;
        camX += mx * s;
        camY += my * s;
        camZ += mz * s;

        if (spawnFake.isEnabled() && moveFake.isEnabled()) {
            fakeX = camX;
            fakeY = camY - 1.62;
            fakeZ = camZ;
        }
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        // Character outline on the frozen body so you always see where YOU are
        if (!showPlayer.isEnabled() || mc.player == null || !active) return;
        int[] feet = project(new Vec3d(anchorX, anchorY, anchorZ));
        int[] head = project(new Vec3d(anchorX, anchorY + 1.8, anchorZ));
        if (feet == null || head == null) return;
        int top = Math.min(feet[1], head[1]);
        int bottom = Math.max(feet[1], head[1]);
        int half = Math.max(6, (bottom - top) / 5);
        int cx = (feet[0] + head[0]) / 2;
        int col = 0xFF55FF55;
        // Outline rect
        context.fill(cx - half, top, cx + half, top + 1, col);
        context.fill(cx - half, bottom - 1, cx + half, bottom, col);
        context.fill(cx - half, top, cx - half + 1, bottom, col);
        context.fill(cx + half - 1, top, cx + half, bottom, col);
        if (mc.textRenderer != null) {
            String label = "YOU";
            context.drawText(mc.textRenderer, label, cx - mc.textRenderer.getWidth(label) / 2, top - 11, col, true);
        }
    }

    private int[] project(Vec3d p) {
        try {
            Camera cam = mc.gameRenderer.getCamera();
            Vec3d c = new Vec3d(camX, camY, camZ);
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
            int w = mc.getWindow().getScaledWidth(), h = mc.getWindow().getScaledHeight();
            double f = (h / 2.0) / Math.tan(Math.toRadians(35.0));
            return new int[]{(int) (w / 2.0 + cx / depth * f), (int) (h / 2.0 - cy / depth * f)};
        } catch (Throwable t) {
            return null;
        }
    }

    // Called every frame by CameraMixin while active
    public boolean isCameraActive() {
        return active && mc.player != null;
    }

    // Camera look follows mouse while freecam is active
    public void onCameraLook(float yaw, float pitch) {
        this.camYaw = yaw;
        this.camPitch = pitch;
    }

    public double getCamX() { return camX; }
    public double getCamY() { return camY; }
    public double getCamZ() { return camZ; }
    public float getCamYaw() { return camYaw; }
    public float getCamPitch() { return camPitch; }

    public Vec3d getFakePos() {
        return new Vec3d(fakeX, fakeY, fakeZ);
    }
}
