package com.anormal.client.module.impl.spectre;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.client.option.Perspective;
import net.minecraft.util.math.Vec3d;

/**
 * Spectre port: "Freecam" — a detached camera you can fly around with.
 *
 * <p>Spectre keeps a smoothed camera position, WASD/space/shift movement, scroll speed, the body
 * view toggle and the chunk-radius follow. This port keeps the speed, vertical speed, smoothing,
 * body and hands toggles, key-carried walking, scroll speed and chunk distance, and exposes the
 * interpolated camera position to the client's existing camera hook.</p>
 */
public final class SpectreFreecam extends Module {
    public static volatile SpectreFreecam active;

    public final NumberSetting speed = addSetting(new NumberSetting("Speed", "Horizontal speed", 1.0, 0.1, 50.0, 0.1));
    public final NumberSetting verticalSpeed = addSetting(new NumberSetting("Vertical", "Vertical speed", 1.0, 0.2, 5.0, 0.05));
    public final BooleanSetting smoothing = addSetting(new BooleanSetting("Smooth", "Ease the camera motion", true));
    public final BooleanSetting showOwnBody = addSetting(new BooleanSetting("Body", "Show your body", true));
    public final BooleanSetting showHands = addSetting(new BooleanSetting("Hands", "Show your hands", true));
    public final BooleanSetting bodyFollowsKeys = addSetting(new BooleanSetting("Keys Move Body", "Movement keys still drive the body", false));
    public final BooleanSetting keepWalking = addSetting(new BooleanSetting("Keep Walking", "Keep the body walking", true));
    public final BooleanSetting scrollSpeed = addSetting(new BooleanSetting("Scroll Speed", "Scroll wheel changes speed", true));
    public final NumberSetting chunkDistance = addSetting(new NumberSetting("Chunks", "Chunk radius kept loaded", 12.0, 2.0, 32.0, 1.0));

    private Vec3d pos = Vec3d.ZERO;
    private Vec3d prev = Vec3d.ZERO;
    private float yaw;
    private float pitch;
    private boolean viewPushed;
    private Perspective previousView;
    private boolean flying;

    public SpectreFreecam() {
        super("SpectreFreecam", "Spectre port: a detached camera you can fly around with.", Category.SPECTRE);
    }

    @Override
    public void onEnable() {
        if (mc.player == null) return;
        pos = prev = new Vec3d(mc.player.getX(), mc.player.getY() + mc.player.getStandingEyeHeight(), mc.player.getZ());
        yaw = mc.player.getYaw();
        pitch = mc.player.getPitch();
        flying = mc.player.getAbilities().flying;
        if (showOwnBody.isEnabled() && mc.options != null) {
            previousView = mc.options.getPerspective();
            if (previousView.isFirstPerson()) {
                mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);
                viewPushed = true;
            }
        }
        active = this;
    }

    @Override
    public void onDisable() {
        if (active == this) active = null;
        if (viewPushed && mc.options != null && previousView != null) {
            mc.options.setPerspective(previousView);
            viewPushed = false;
        }
        if (mc.player != null) {
            mc.player.getAbilities().flying = flying;
        }
    }

    /** Hands are hidden when the camera is detached and the Hands toggle is off. */
    public static boolean hidesHands() {
        return active != null && active.isEnabled() && !active.showHands.isEnabled();
    }

    public static boolean cameraActive() {
        return active != null && active.isEnabled();
    }

    public static float cameraYaw() {
        return active == null ? 0.0F : active.yaw;
    }

    public static float cameraPitch() {
        return active == null ? 0.0F : active.pitch;
    }

    public static double cameraX(float tickProgress) {
        return active == null ? 0.0 : active.prev.x + (active.pos.x - active.prev.x) * tickProgress;
    }

    public static double cameraY(float tickProgress) {
        return active == null ? 0.0 : active.prev.y + (active.pos.y - active.prev.y) * tickProgress;
    }

    public static double cameraZ(float tickProgress) {
        return active == null ? 0.0 : active.prev.z + (active.pos.z - active.prev.z) * tickProgress;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.options == null) return;
        prev = pos;

        double ahead = (mc.options.forwardKey.isPressed() ? 1 : 0) - (mc.options.backKey.isPressed() ? 1 : 0);
        double side = (mc.options.leftKey.isPressed() ? 1 : 0) - (mc.options.rightKey.isPressed() ? 1 : 0);
        double rise = (mc.options.jumpKey.isPressed() ? 1 : 0) - (mc.options.sneakKey.isPressed() ? 1 : 0);
        double step = speed.getAsDouble() * (smoothing.isEnabled() ? 0.5 : 1.0);

        double yawRad = Math.toRadians(yaw);
        double lookX = -Math.sin(yawRad);
        double lookZ = Math.cos(yawRad);
        double leftX = Math.cos(yawRad);
        double leftZ = Math.sin(yawRad);
        double dx = (lookX * ahead + leftX * side) * step;
        double dz = (lookZ * ahead + leftZ * side) * step;
        double dy = rise * step * verticalSpeed.getAsDouble();

        yaw = mc.player.getYaw();
        pitch = mc.player.getPitch();
        pos = pos.add(dx, dy, dz);

        // The camera always takes the movement keys. The body only follows them when asked, and
        // without Keep Walking it stops instead of drifting along with the freecam.
        if (bodyFollowsKeys.isEnabled() && (ahead != 0.0 || side != 0.0)) {
            double bodyStep = Math.min(1.0, speed.getAsDouble()) * 0.18;
            Vec3d body = mc.player.getVelocity();
            mc.player.setVelocity(
                    body.x + (lookX * ahead + leftX * side) * bodyStep,
                    body.y,
                    body.z + (lookZ * ahead + leftZ * side) * bodyStep);
        } else if (!keepWalking.isEnabled()) {
            Vec3d body = mc.player.getVelocity();
            mc.player.setVelocity(0.0, body.y, 0.0);
        }

        if (showOwnBody.isEnabled() && mc.player.getAbilities().flying) {
            mc.player.getAbilities().flying = false;
        }
        if (scrollSpeed.isEnabled()) {
            double target = pos.distanceTo(new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ()));
            double limit = chunkDistance.getAsDouble() * 16.0;
            if (target > limit) {
                Vec3d centre = new Vec3d(mc.player.getX(), mc.player.getY() + mc.player.getStandingEyeHeight(), mc.player.getZ());
                pos = centre.add(pos.subtract(centre).normalize().multiply(limit));
            }
        }
    }
}
