package com.anormal.client.module.impl.spectre;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ModeSetting;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.network.ClientPlayerEntity;

/**
 * Spectre port: "Auto Walk" — automatically walks forward/backwards/left/right.
 */
public final class SpectreAutoWalk extends Module implements SpectreMovementHooks.MoveHook {
    public final ModeSetting direction = addSetting(new ModeSetting("Direction", "Walk direction", "Forwards", "Forwards", "Backwards", "Left", "Right"));
    public final BooleanSetting disableOnInput = addSetting(new BooleanSetting("Disable On Input", "Stop when you press a movement key", false));
    public final BooleanSetting disableOnY = addSetting(new BooleanSetting("Disable On Y Change", "Stop when your Y changes (fall/jump)", false));
    public final BooleanSetting waitChunks = addSetting(new BooleanSetting("No Unloaded Chunks", "Never walk into unloaded chunks", true));

    private double lastY = Double.NaN;
    private boolean wasFreecam;

    public SpectreAutoWalk() {
        super("SpectreAutoWalk", "Spectre port: automatically walks forward.", Category.SPECTRE);
    }

    @Override
    public void onEnable() {
        lastY = Double.NaN;
        SpectreMovementHooks.rebind();
    }

    @Override
    public void onDisable() {
        // restore keys like Spectre: only the fake press disappears; a physically
        // held key stays pressed via the InputUtil poll below.
        for (var key : new net.minecraft.client.option.KeyBinding[]{
                mc.options.forwardKey, mc.options.backKey, mc.options.leftKey, mc.options.rightKey}) {
            key.setPressed(isPhysicallyHeld(key));
        }
        SpectreMovementHooks.rebind();
    }

    @Override
    public void onPreTick() {
        var player = mc.player;
        if (player == null || mc.world == null) return;
        if (disableOnY.isEnabled()) {
            if (!Double.isNaN(lastY) && player.getY() != lastY) {
                setEnabled(false);
                return;
            }
        }
        lastY = player.getY();
        keyFor().setPressed(true);
    }

    @Override
    public void onTick() {
        boolean inGame = mc.currentScreen == null && mc.player != null;
        // Disable On Input: any physical movement key press stops the walk
        if (inGame && disableOnInput.isEnabled()) {
            var keys = new net.minecraft.client.option.KeyBinding[]{mc.options.forwardKey, mc.options.backKey,
                    mc.options.leftKey, mc.options.rightKey, mc.options.sneakKey, mc.options.jumpKey};
            for (var key : keys) {
                if (isPhysicallyHeld(key) && key != keyFor()) {
                    setEnabled(false);
                    return;
                }
            }
        }
    }

    @Override
    public Vec3d onMove(Entity entity, MovementType type, Vec3d movement) {
        if (!waitChunks.isEnabled()) return movement;
        if (!(entity instanceof ClientPlayerEntity player) || mc.world == null) return movement;
        int chunkX = ((int) Math.floor(player.getX() + movement.x * 2.0)) >> 4;
        int chunkZ = ((int) Math.floor(player.getZ() + movement.z * 2.0)) >> 4;
        try {
            if (!mc.world.getChunkManager().isChunkLoaded(chunkX, chunkZ)) {
                return new Vec3d(0.0, movement.y, 0.0);
            }
        } catch (Throwable ignored) {
        }
        return movement;
    }

    private net.minecraft.client.option.KeyBinding keyFor() {
        return switch (direction.getValue()) {
            case "Backwards" -> mc.options.backKey;
            case "Left" -> mc.options.leftKey;
            case "Right" -> mc.options.rightKey;
            default -> mc.options.forwardKey;
        };
    }

    private static boolean isPhysicallyHeld(net.minecraft.client.option.KeyBinding key) {
        if (!key.isUnbound()) return false;
        try {
            String boundKey = key.getBoundKeyTranslationKey();
            var input = net.minecraft.client.util.InputUtil.fromTranslationKey(boundKey);
            if (input.getCategory() == net.minecraft.client.util.InputUtil.Type.KEYSYM) {
                return net.minecraft.client.util.InputUtil.isKeyPressed(mc.getWindow(), input.getCode());
            }
            if (input.getCategory() == net.minecraft.client.util.InputUtil.Type.MOUSE) {
                return org.lwjgl.glfw.GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), input.getCode())
                        == org.lwjgl.glfw.GLFW.GLFW_PRESS;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }
}
