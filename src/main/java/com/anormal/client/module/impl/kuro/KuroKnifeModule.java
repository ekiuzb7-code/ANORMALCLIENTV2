package com.anormal.client.module.impl.kuro;

import com.anormal.client.knife.KnifeRenderer;
import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;

import net.minecraft.text.Text;

import org.lwjgl.glfw.GLFW;

/**
 * kuro {@code KnifeModule} port: qo‘ldagi qilichni birinchi shaxs qo‘lida
 * pichoq bilan almashtiradi (Karambit, Butterfly va CS2 to‘plami — kuro
 * model/mesh tizimidan {@code com.anormal.client.knife} paketi bilan), qurol
 * almashtirilganda chizish animatsiyasi, kalitga bosib ko‘rish (inspect) tsikli
 * va ixtiyoriy harakat izi (trail).
 *
 * <p>Anormaldagi farqlar: kuro'daki Setting.Num.labelled ro‘yxat ModeSetting
 * ga o‘zgartirildi; DebugChat o‘rniga oddiy chat xabari; kuro'dagi "Bind inspect
 * key" tugmasi bu yerda ham ishlaydi (modul yoqilganda keyingi yangi bosish —
 * Esc bekor qiladi). Kuro'dagi "Attack animation off" sozlamasi 1.21.11
 * Yarn API'sida renderArmWithItem ko‘rinishi yo‘qligi uchun Mixin-hooksiz:
 * sozl saqlanadi, lekin hozircha ta'sir qilmaydi.</p>
 */
public class KuroKnifeModule extends Module {
    private static KuroKnifeModule instance;

    private final ModeSetting knife = new ModeSetting("Knife", "Pichoq turi", KnifeRenderer.KNIVES[0], KnifeRenderer.KNIVES);
    private final NumberSetting size = new NumberSetting("Size", "Pichoqning o‘lchami", 1.0, 0.5, 2.0, 0.05);
    private final BooleanSetting trail = new BooleanSetting("Trail", "Harakat izi", true);
    private final NumberSetting trailLength = new NumberSetting("Trail length (s)", "Izning davomiyligi", 0.2, 0.1, 0.8, 0.05);
    private final NumberSetting inspectKey = new NumberSetting("Inspect key (GLFW code)", "Ko‘rish kaliti (GLFW kodi)", 71.0, 32.0, 348.0, 1.0);   // 71 = G
    private final NumberSetting offX = new NumberSetting("Offset X", "Boshlang‘ich X o‘rnashuv", 0.0, -0.4, 0.4, 0.01);
    private final NumberSetting offY = new NumberSetting("Offset Y", "Boshlang‘ich Y o‘rnashuv", 0.0, -0.4, 0.4, 0.01);
    private final NumberSetting offZ = new NumberSetting("Offset Z (+ = yaqin)", "Boshlang‘ich Z o‘rnashuv", 0.0, -0.4, 0.4, 0.01);
    private final BooleanSetting material = new BooleanSetting("Match sword material", "Qilich materiali bilan palitra", true);
    private final BooleanSetting deploy = new BooleanSetting("Draw animation", "Chizish animatsiyasi", true);
    private final BooleanSetting cancelOnSwing = new BooleanSetting("Hit cancels inspect", "Uraish ko‘rishni to‘xtatadi", true);
    private final BooleanSetting attackAnimation = new BooleanSetting("Attack animation", "Uraishda qo‘l harakati (1.21.11 API cheklovi: hozircha ta'sir qilmaydi)", true);

    private boolean listening;

    public KuroKnifeModule() {
        super("Knife", "Replaces the sword in your first-person hand with a knife (kuro port)", Category.KURO);
        instance = this;
        addSetting(knife);
        addSetting(size);
        addSetting(trail);
        addSetting(trailLength);
        addSetting(inspectKey);
        addSetting(offX);
        addSetting(offY);
        addSetting(offZ);
        addSetting(material);
        addSetting(deploy);
        addSetting(cancelOnSwing);
        addSetting(attackAnimation);
    }

    public static boolean active() { return instance != null && instance.isEnabled(); }
    public static KuroKnifeModule get() { return instance; }

    public float size() { return (float) (double) size.getValue(); }
    public boolean matchMaterial() { return material.isEnabled(); }
    public float offX() { return (float) (double) offX.getValue(); }
    public float offY() { return (float) (double) offY.getValue(); }
    public float offZ() { return (float) (double) offZ.getValue(); }
    public int knifeIndex() { return Math.max(1, /* 1-based */ knifeNameIndex()) + 0; }
    public int knifeNumber() { return knifeNameIndex() <= 0 ? 1 : knifeNameIndex(); }
    public boolean attackAnimation() { return attackAnimation.isEnabled(); }
    public float trailSeconds() { return trail.isEnabled() ? (float) (double) trailLength.getValue() : 0.0f; }

    private int knifeNameIndex() {
        String n = knife.getValue();
        for (int i = 0; i < KnifeRenderer.KNIVES.length; i++) if (KnifeRenderer.KNIVES[i].equals(n)) return i + 1;
        return 1;
    }

    private int inspectKeyCode() { return inspectKey.getValue().intValue(); }

    private void say(String msg) {
        if (mc.player != null) mc.player.sendMessage(Text.literal("§7[knife] §f" + msg), false);
    }

    @Override
    public void onEnable() { KnifeRenderer.reset(); }

    @Override
    public void onDisable() { KnifeRenderer.reset(); listening = false; }

    public void startBinding() {
        if (!isEnabled()) { say("Pichoq modulni avval yoqib, orqadan bog'lang."); return; }
        listening = true;
        snapshotKeys();
        say("Ko‘rish kalitini bosing (Esc bekor qiladi).");
    }

    private final boolean[] wasDown = new boolean[GLFW.GLFW_KEY_LAST + 1];

    private void snapshotKeys() {
        long win = mc.getWindow().getHandle();
        for (int k = GLFW.GLFW_KEY_SPACE; k <= GLFW.GLFW_KEY_LAST; k++) wasDown[k] = GLFW.glfwGetKey(win, k) == GLFW.GLFW_PRESS;
    }

    private int freshKey() {
        long win = mc.getWindow().getHandle();
        int found = -1;
        for (int k = GLFW.GLFW_KEY_SPACE; k <= GLFW.GLFW_KEY_LAST; k++) {
            boolean down = GLFW.glfwGetKey(win, k) == GLFW.GLFW_PRESS;
            if (down && !wasDown[k] && found < 0) found = k;
            wasDown[k] = down;
        }
        return found;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.getWindow() == null) return;
        if (listening) {
            int k = freshKey();
            if (k == GLFW.GLFW_KEY_ESCAPE) {
                listening = false;
                say("Bekor qilindi (ko‘rish kaliti hozirgi qoldi).");
            } else if (k == GLFW.GLFW_KEY_RIGHT_SHIFT) {
                say("Right Shift menyu turgan — boshqa kalit tanlang.");
            } else if (k >= 0) {
                listening = false;
                inspectKey.setValue((double) k);
                say("Ko‘rish kaliti o‘rnatildi: " + keyName(k) + ".");
            }
            KnifeRenderer.tick(false, deploy.isEnabled(), cancelOnSwing.isEnabled());
            return;
        }
        boolean down = mc.currentScreen == null && GLFW.glfwGetKey(mc.getWindow().getHandle(), inspectKeyCode()) == GLFW.GLFW_PRESS;
        KnifeRenderer.tick(down, deploy.isEnabled(), cancelOnSwing.isEnabled());
    }

    static String keyName(int key) {
        String n = GLFW.glfwGetKeyName(key, 0);
        if (n != null) return n.toUpperCase(java.util.Locale.ROOT);
        return switch (key) {
            case GLFW.GLFW_KEY_SPACE -> "Space";
            case GLFW.GLFW_KEY_LEFT_SHIFT -> "Left Shift";
            case GLFW.GLFW_KEY_LEFT_CONTROL -> "Left Ctrl";
            case GLFW.GLFW_KEY_LEFT_ALT -> "Left Alt";
            case GLFW.GLFW_KEY_RIGHT_ALT -> "Right Alt";
            case GLFW.GLFW_KEY_TAB -> "Tab";
            case GLFW.GLFW_KEY_CAPS_LOCK -> "Caps Lock";
            default -> "key " + key;
        };
    }
}
