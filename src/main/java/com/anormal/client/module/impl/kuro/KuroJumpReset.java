package com.anormal.client.module.impl.kuro;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.InputForcer;

/**
 * Ported from kuro (dev.kuro.client.module.impl.JumpReset).
 *
 * <p>Jump reset: the moment you are hit while standing on the ground, you jump for one tick.
 * The upward movement cuts the horizontal knockback you would take from a ground hit. The jump
 * key is pressed through Anormal's forced-key hook ([{@link InputForcer}]), which a mixin
 * re-asserts after every hardware refresh, so it behaves like a real keypress.</p>
 */
public class KuroJumpReset extends Module {
    private final NumberSetting chance = new NumberSetting("Chance %",
            "Chance to jump on being hit", 100.0, 10.0, 100.0, 5.0);

    private boolean forcing;
    private int lastHurt;

    public KuroJumpReset() {
        super("JumpReset", "Jumps the moment you are hit to cut knockback (kuro port)", Category.KURO);
        addSetting(chance);
    }

    @Override
    public void onEnable() {
        lastHurt = 0;
    }

    @Override
    public void onDisable() {
        release();
    }

    @Override
    public void onTick() {
        // The jump lasts a single tick: whatever forced from the previous tick is released now,
        // after that tick's movement has consumed it.
        if (forcing) release();

        if (mc.player == null || mc.world == null) return;
        int hurt = mc.player.hurtTime;
        boolean fresh = hurt > lastHurt && hurt >= 8;   // damage was taken since the last tick
        lastHurt = hurt;
        if (!fresh) return;
        if (mc.currentScreen != null || mc.player.isSpectator()) return;
        if (!mc.player.isOnGround() || mc.player.isTouchingWater() || mc.player.isInLava()
                || mc.player.hasVehicle() || mc.player.getAbilities().flying) return;
        if (Math.random() * 100 >= chance.getValue()) return;
        InputForcer.jump = true;
        forcing = true;
    }

    private void release() {
        if (!forcing) return;
        forcing = false;
        InputForcer.jump = false;
        mc.options.jumpKey.setPressed(isJumpPhysicallyDown());
    }

    private boolean isJumpPhysicallyDown() {
        try {
            long handle = mc.getWindow().getHandle();
            return org.lwjgl.glfw.GLFW.glfwGetKey(handle, org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE)
                    == org.lwjgl.glfw.GLFW.GLFW_PRESS;
        } catch (Throwable b) {
            return false;
        }
    }
}
