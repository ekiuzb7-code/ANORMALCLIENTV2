package com.anormal.client.module.impl.kuro;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.NumberSetting;

import com.anormal.client.mixin.KuroKeyBindingAccessor;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import org.lwjgl.glfw.GLFW;

/**
 * kuro {@code Freecam} porti (dev/kuro/client/module/impl/Freecam.java).
 *
 * <p>Anormal'ning o'z Freecamigidan farqi: kamera ko'z pozitsiyasidan boshlanadi,
 * WASD kamera yo'nalishida uchadi (sprint = qo'sh tezlik), sichqoncha kamera
 * burilishiga xizmat qiladi — real o'yinchi butunlay muzlatiladi (harakat
 * klavishlari har tick bo'shatiladi) va {@code CameraMixin} orqali kamera
 * interpolatsiyalangan pozitsiyaga qo'yiladi.</p>
 *
 * <p>Ulanish nuqtalari: {@code KuroFreecamCameraMixin} (kamera pozitsiyasi/burilishi
 * va uchinchi shaxs flagi) hamda {@code KuroFreecamEntityMixin}
 * (sichqoncha ko'rishini kameraga yo'naltirish).</p>
 */
public class KuroFreecam extends Module {
    private static KuroFreecam instance;

    public final NumberSetting speed = new NumberSetting("Speed", "Kamera harakat tezligi", 0.8, 0.1, 4.0, 0.1);

    public double x, y, z, px, py, pz;
    public float yaw, pitch;
    private Object owner;

    public KuroFreecam() {
        super("Kuro Freecam", "kuro freecam: kamera uchadi, tana muzlaydi, ko'rish erkin (kuro port)", Category.KURO);
        instance = this;
        addSetting(speed);
    }

    public static KuroFreecam get() { return instance; }
    public static boolean active() { return instance != null && instance.isEnabled(); }

    @Override
    public void onEnable() {
        if (mc.player == null) { setEnabled(false); return; }
        owner = mc.player;
        Vec3d e = mc.player.getEyePos();
        x = px = e.x; y = py = e.y; z = pz = e.z;
        yaw = mc.player.getYaw();
        pitch = mc.player.getPitch();
    }

    @Override
    public void onDisable() {
        owner = null;
        KeyBinding.unpressAll(); // hali bosilgan qolgan klavishlarni sinxronlash
    }

    /**
     * Har frame ishga tushiriladi: KeyMapping holatini GLFW press eventlardan keyin ham
     * tozerli holida saqlaydi (oyinchi harakatini kesib ketmaydi).
     */
    @Override
    public void onRender2D(DrawContext g, float tickDelta) {
        freezeInput();
    }

    private void freezeInput() {
        GameOptions o = mc.options;
        if (o == null) return;
        KeyBinding[] frozen = { o.forwardKey, o.backKey, o.leftKey, o.rightKey,
                o.jumpKey, o.sneakKey, o.sprintKey, o.attackKey, o.useKey };
        for (KeyBinding k : frozen) ((KuroKeyBindingAccessor) k).setPressed(false);
    }

    @Override
    public void onTick() {
        // dunyodan chiqish / o'lchan o'zgarishi player obyektini almashtiradi
        if (mc.player == null || mc.player != owner) { setEnabled(false); return; }

        px = x; py = y; pz = z;

        // real o'yinchini muzlatish: bu onTick tomonidan har haraket tick bo'shatiladigan bo'ladi
        freezeInput();

        if (mc.currentScreen != null) return;
        GameOptions o = mc.options;
        if (o == null) return;

        long w = mc.getWindow().getHandle();
        double yr = Math.toRadians(yaw), pr = Math.toRadians(pitch);
        double fx = -Math.sin(yr) * Math.cos(pr), fy = -Math.sin(pr), fz = Math.cos(yr) * Math.cos(pr);
        double rx = -Math.cos(yr), rz = -Math.sin(yr);

        double f = (down(w, o.forwardKey) ? 1 : 0) - (down(w, o.backKey) ? 1 : 0);
        double s = (down(w, o.rightKey) ? 1 : 0) - (down(w, o.leftKey) ? 1 : 0);
        double u = (down(w, o.jumpKey) ? 1 : 0) - (down(w, o.sneakKey) ? 1 : 0);

        double dx = fx * f + rx * s, dy = fy * f + u, dz = fz * f + rz * s;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len > 1e-6) {
            double sp = speed.getValue().doubleValue() * (down(w, o.sprintKey) ? 2.0 : 1.0);
            x += dx / len * sp; y += dy / len * sp; z += dz / len * sp;
        }
    }

    private static boolean down(long window, KeyBinding k) {
        InputUtil.Key key = KeyBindingHelper.getBoundKeyOf(k);
        return key.getCategory() == InputUtil.Type.KEYSYM
                && GLFW.glfwGetKey(window, key.getCode()) == GLFW.GLFW_PRESS;
    }

    /** EntityMixin o'rniga chaqiriladi: sichqoncha harakati real o'yinchiga emas, kameraga boradi. */
    public void look(double dx, double dy) {
        yaw += (float) (dx * 0.15);
        pitch = MathHelper.clamp(pitch + (float) (dy * 0.15), -90f, 90f);
    }

    public double lx(float t) { return px + (x - px) * t; }
    public double ly(float t) { return py + (y - py) * t; }
    public double lz(float t) { return pz + (z - pz) * t; }
}
