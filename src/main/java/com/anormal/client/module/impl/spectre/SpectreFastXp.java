package com.anormal.client.module.impl.spectre;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import net.minecraft.item.Items;
import org.lwjgl.glfw.GLFW;

/**
 * Spectre port: "FastXP" — throws experience bottles every 100 ms while the right
 * mouse button is held.
 */
public final class SpectreFastXp extends Module {
    private long lastThrowTime;

    public SpectreFastXp() {
        super("SpectreFastXp", "Spectre port: throws experience bottles every 100 ms while right click is held.", Category.SPECTRE);
    }

    @Override
    public void onEnable() {
        lastThrowTime = 0L;
    }

    @Override
    public void onTick() {
        var player = mc.player;
        if (player == null || mc.interactionManager == null || mc.currentScreen != null) return;
        if (!player.getMainHandStack().isOf(Items.EXPERIENCE_BOTTLE)) return;
        if (GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_RIGHT) != GLFW.GLFW_PRESS) return;

        long now = System.currentTimeMillis();
        if (now - lastThrowTime >= 100L) {
            var stack = player.getMainHandStack();
            mc.interactionManager.interactItem(player, net.minecraft.util.Hand.MAIN_HAND);
            lastThrowTime = now;
        }
    }
}
