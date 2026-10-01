package com.anormal.client.module.impl.render;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.setting.KeybindSetting;
import net.minecraft.client.option.Perspective;
import org.lwjgl.glfw.GLFW;

public class FreeLook extends Module {
    public final ModeSetting activate = new ModeSetting("Activate", "Hold while key down or Toggle on press", "Toggle", "Hold", "Toggle");
    public final ModeSetting startingPosition = new ModeSetting("Starting Position", "Camera direction on activation", "Forward", "Forward", "Backward");
    public final BooleanSetting useCustomSensitivity = new BooleanSetting("Custom Sensitivity", "Override mouse sensitivity while active", false);
    public final NumberSetting sensitivity = new NumberSetting("Sensitivity", "Freelook mouse sensitivity", 0.5, 0.05, 2.0, 0.05);
    public final BooleanSetting restoreView = new BooleanSetting("Restore View", "Restores your view on disable", true);

    private Perspective savedPerspective;
    private double savedSensitivity = -1;

    public FreeLook() {
        super("FreeLook", "Detached 3rd-person camera allowing free view rotations", Category.RENDER, GLFW.GLFW_KEY_V);
        addSetting(activate);
        addSetting(startingPosition);
        addSetting(useCustomSensitivity);
        addSetting(sensitivity);
        addSetting(restoreView);
    }

    @Override
    public void onEnable() {
        try {
            savedPerspective = mc.options.getPerspective();
            // Forward = see where you're going (back cam), Backward = face cam (behind you)
            mc.options.setPerspective(startingPosition.is("Backward") ? Perspective.THIRD_PERSON_FRONT : Perspective.THIRD_PERSON_BACK);
            if (useCustomSensitivity.isEnabled()) {
                try {
                    savedSensitivity = mc.options.getMouseSensitivity().getValue();
                    mc.options.getMouseSensitivity().setValue(sensitivity.getValue());
                } catch (Throwable ignored) {
                    savedSensitivity = -1;
                }
            }
        } catch (Throwable t) {
            savedPerspective = null;
        }
    }

    @Override
    public void onDisable() {
        try {
            if (restoreView.isEnabled() && savedPerspective != null) mc.options.setPerspective(savedPerspective);
            if (savedSensitivity >= 0) {
                try {
                    mc.options.getMouseSensitivity().setValue(savedSensitivity);
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {
        } finally {
            savedPerspective = null;
            savedSensitivity = -1;
        }
    }

    @Override
    public void onTick() {
        // Hold mode: auto-disable the moment the bind is released
        if (!activate.is("Hold") || mc.getWindow() == null) return;
        try {
            long window = mc.getWindow().getHandle();
            int key = getKey();
            boolean down;
            if (KeybindSetting.isMouseCode(key)) {
                int btn = KeybindSetting.mouseButton(key);
                down = btn >= 0 && GLFW.glfwGetMouseButton(window, btn) == GLFW.GLFW_PRESS;
            } else if (key > 0 && key <= GLFW.GLFW_KEY_LAST) {
                down = GLFW.glfwGetKey(window, key) == GLFW.GLFW_PRESS;
            } else {
                return;
            }
            if (!down) setEnabled(false);
        } catch (Throwable ignored) {}
    }
}
