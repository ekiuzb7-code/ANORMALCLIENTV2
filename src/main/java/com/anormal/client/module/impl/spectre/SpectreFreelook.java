package com.anormal.client.module.impl.spectre;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.client.option.Perspective;

/**
 * Spectre port: "Freelook" — turns the camera and the player separately.
 *
 * <p>Player mode keeps the body still and moves only the camera; Camera mode turns the
 * body with the camera. The camera rotation is written by {@code SpectreFreelookMixin}
 * after vanilla updates it, and the arrow keys nudge the view like Spectre.</p>
 */
public final class SpectreFreelook extends Module {
    public final ModeSetting mode = addSetting(new ModeSetting("Mode", "Player keeps the body still, Camera turns both", "Player", "Player", "Camera"));
    public final BooleanSetting togglePerspective = addSetting(new BooleanSetting("Toggle Perspective", "Switch to third person while looking around", true));
    public final NumberSetting sensitivity = addSetting(new NumberSetting("Camera Sensitivity", "Camera mode mouse scale", 8.0, 0.0, 10.0, 0.1));
    public final BooleanSetting arrows = addSetting(new BooleanSetting("Arrows Control Opposite", "Arrow keys rotate the view", true));
    public final NumberSetting arrowSpeed = addSetting(new NumberSetting("Arrow Speed", "Degrees per tick for the arrow keys", 4.0, 0.0, 10.0, 0.5));

    private static SpectreFreelook instance;
    private float cameraYaw;
    private float cameraPitch;
    private Perspective previousView;

    public SpectreFreelook() {
        super("SpectreFreelook", "Spectre port: turns the camera and the player separately.", Category.SPECTRE);
        instance = this;
    }

    @Override
    public void onEnable() {
        var player = mc.player;
        if (player == null) {
            setEnabled(false);
            return;
        }
        cameraYaw = player.getYaw();
        cameraPitch = player.getPitch();
        previousView = mc.options.getPerspective();
        if (togglePerspective.isEnabled() && !previousView.isFirstPerson()) return;
        if (togglePerspective.isEnabled()) mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);
    }

    @Override
    public void onDisable() {
        if (togglePerspective.isEnabled() && previousView != null
                && mc.options.getPerspective() != previousView) {
            mc.options.setPerspective(previousView);
        }
        previousView = null;
    }

    @Override
    public void onTick() {
        var player = mc.player;
        if (player == null) return;

        if (arrows.isEnabled() && mc.currentScreen == null) {
            float turn = arrowSpeed.getValue().floatValue();
            float yawTurn = (org.lwjgl.glfw.GLFW.glfwGetKey(mc.getWindow().getHandle(), org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT) == org.lwjgl.glfw.GLFW.GLFW_PRESS ? turn : 0.0F)
                    - (org.lwjgl.glfw.GLFW.glfwGetKey(mc.getWindow().getHandle(), org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT) == org.lwjgl.glfw.GLFW.GLFW_PRESS ? turn : 0.0F);
            float pitchTurn = (org.lwjgl.glfw.GLFW.glfwGetKey(mc.getWindow().getHandle(), org.lwjgl.glfw.GLFW.GLFW_KEY_UP) == org.lwjgl.glfw.GLFW.GLFW_PRESS ? turn : 0.0F)
                    - (org.lwjgl.glfw.GLFW.glfwGetKey(mc.getWindow().getHandle(), org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN) == org.lwjgl.glfw.GLFW.GLFW_PRESS ? turn : 0.0F);

            if (mode.is("Player")) {
                cameraYaw += yawTurn;
                cameraPitch += pitchTurn;
            } else if (yawTurn != 0.0F || pitchTurn != 0.0F) {
                player.setYaw(player.getYaw() + yawTurn);
                player.setPitch(player.getPitch() + pitchTurn);
            }
        }
        cameraPitch = Math.max(-90.0F, Math.min(90.0F, cameraPitch));
    }

    /**
     * Mouse-look routing, port of Spectre's {@code turnCamera}. The deltas arrive in
     * degrees (vanilla's {@code delta * 0.15}), so {@code Camera Sensitivity} is mapped
     * around the default of 8 = 1:1 with vanilla and clamped to a usable range.
     */
    public void turnCamera(double yawDegrees, double pitchDegrees) {
        double divisor = Math.max(sensitivity.getValue().floatValue(), 0.1F);
        double scale = Math.max(0.1, Math.min(4.0, 8.0 / divisor));
        cameraYaw += (float) (yawDegrees * scale);
        cameraPitch += (float) (pitchDegrees * scale);
        cameraPitch = Math.max(-90.0F, Math.min(90.0F, cameraPitch));
    }

    /** Called from {@code SpectreLookMixin}: rotate the detached camera. */
    public static boolean routeLook(double yawDegrees, double pitchDegrees) {
        SpectreFreelook module = instance;
        if (module == null || !active()) return false;
        module.turnCamera(yawDegrees, pitchDegrees);
        return true;
    }

    public static float activeYaw() {
        SpectreFreelook module = instance;
        return module == null ? 0.0F : module.cameraYaw;
    }

    public static float activePitch() {
        SpectreFreelook module = instance;
        return module == null ? 0.0F : module.cameraPitch;
    }

    /**
     * The camera is only detached in Player mode. In Camera mode the mouse turns the body
     * (and the camera with it), so vanilla rotation has to run untouched.
     */
    public static boolean active() {
        return instance != null && instance.isEnabled() && instance.mode.is("Player");
    }

    public boolean cameraMode() {
        return isEnabled() && mode.is("Camera");
    }

    public float cameraYaw() {
        return cameraYaw;
    }

    public float cameraPitch() {
        return cameraPitch;
    }
}
