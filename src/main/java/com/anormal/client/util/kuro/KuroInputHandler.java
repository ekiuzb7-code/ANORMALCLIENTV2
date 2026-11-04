package com.anormal.client.util.kuro;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/**
 * Kuro InputHandler port (echo handlers/InputHandler'dan Anormal mapping'larga).
 *
 * Bind-state yordamchilari: klaviatura / sichqoncha tugmalarining haqiqiy
 * holatini o'qish. Bind kodlash: klaviatura = GLFW kodi, sichqoncha =
 * 0x80000000 | tugma kodi (echo bilan bir xil format).
 */
public final class KuroInputHandler {
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    private static final int MOUSE_BIND_FLAG = 0x80000000;

    private KuroInputHandler() {}

    public static boolean isKeyDown(int key) {
        if (mc == null || mc.getWindow() == null) return false;
        return GLFW.glfwGetKey(mc.getWindow().getHandle(), key) == GLFW.GLFW_PRESS;
    }

    public static boolean isBindDown(int bind) {
        if (bind == -1 || bind == 0) return false;
        if ((bind & MOUSE_BIND_FLAG) != 0) {
            int mb = bind & 0xFF;
            if (mc == null || mc.getWindow() == null) return false;
            return GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), mb) == GLFW.GLFW_PRESS;
        }
        return isKeyDown(bind);
    }

    public static boolean isBindDown(KeyBinding keyBinding) {
        if (keyBinding == null) return false;
        return isBindDown(keyToBind(KuroInventoryUtils.keyBindingToKey(keyBinding)));
    }

    /** KeyBinding click hisobini oshiradi (input simulyatsiya uchun). */
    public static void incrementClickCountForKeyMapping(KeyBinding keyBinding) {
        if (keyBinding == null) return;
        InputUtil.Key key = KuroInventoryUtils.keyBindingToKey(keyBinding);
        if (key != null) KeyBinding.onKeyPressed(key);
    }

    /** InputUtil.Key'dan bind kodini yasaydi (klaviatura = GLFW kodi, sichqoncha = 0x80000000 | tugma). */
    public static int keyToBind(InputUtil.Key key) {
        if (key == null) return -1;
        if (key.getCategory() == InputUtil.Type.MOUSE) {
            return MOUSE_BIND_FLAG | key.getCode();
        }
        return key.getCode();
    }
}
