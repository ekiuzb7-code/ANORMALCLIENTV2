package com.anormal.client.module.impl.render;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ModeSetting;
import net.minecraft.client.option.Perspective;
import org.lwjgl.glfw.GLFW;

public class FreeLook extends Module {
    public final ModeSetting mode = new ModeSetting("Mode", "Which third-person view to use", "Back", "Back", "Front");
    public final BooleanSetting restoreView = new BooleanSetting("Restore View", "Restores your view on disable", true);

    private Perspective saved;

    public FreeLook() {
        super("FreeLook", "Detached 3rd-person camera allowing free view rotations", Category.RENDER, GLFW.GLFW_KEY_V);
        addSetting(mode);
        addSetting(restoreView);
    }

    @Override
    public void onEnable() {
        try {
            saved = mc.options.getPerspective();
            mc.options.setPerspective(mode.is("Front") ? Perspective.THIRD_PERSON_FRONT : Perspective.THIRD_PERSON_BACK);
        } catch (Throwable t) {
            saved = null;
        }
    }

    @Override
    public void onDisable() {
        try {
            if (restoreView.isEnabled() && saved != null) mc.options.setPerspective(saved);
        } catch (Throwable ignored) {
        } finally {
            saved = null;
        }
    }
}
