package com.anormal.client.module.impl.kuro;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.module.ModuleManager;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.util.RenderUtils;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;

import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * kuro {@code Hud} port: klient ekranidagi ustki HUD qatlamlari —
 * sarlavha (nom | fps | ping | tezlik), klavishlar (keybinds) paneli, zirh paneli,
 * maqsad HUD va yoqilgan modullar ro'yxati (kuro manbasidan 1:1).
 *
 * <p>Anormaldagi farqlar: kuro'dagi UI tema tizimi (UiThemes/Theme/ShaderUi),
 * ShardUiddirgichlar va HUD layout muharriri (HudEditScreen) port qilinmagan —
 * Anormal'da panellar o'zining default joyida turadi va faqat
 * slayderlar bilan yoqiladi/olib tashlanadi. Ranglar doimiy
 * (Setting.Hue yo'q, KuroStopwash palitrasidagi konstantalar).
 * Aura maqsadi (AttackAura.currentTarget) yo'q — faqat nishon
 * {@code mc.targetedEntity} bilan olinadi.</p>
 */
public class KuroHud extends Module {
    public static final String CLIENT_NAME = "Kuro";
    public static final String CLIENT_VERSION = "1.0";

    private final BooleanSetting watermark = new BooleanSetting("Title pill", true);         // yuqori markaz: nom | fps | ping | tezlik
    private final BooleanSetting keybinds = new BooleanSetting("Keybinds panel", true);        // chap yuqori: [bind] [modul nomi]
    private final BooleanSetting armorPanel = new BooleanSetting("Armor panel", true);        // chap yuqori: har zirh uchun % qandirlik
    private final BooleanSetting targetHud = new BooleanSetting("Target HUD", true);          // hotbar ustida: yuz, nom, sog'liq, zirh
    private final BooleanSetting list = new BooleanSetting("Module list", true);              // o'ng qirg'oq: yoqilgan modullar pilikasi

    private static final int BG = 0xC0101010;             // plate fonlari (kuro ranglari, Setting.Hue yo'q)
    private static final int BORDER = 0xFF2A2A2A;
    private static final int TEXT = 0xFFECEFF1;
    private static final int MUTED = 0xFF9E9E9E;
    private static final int ACCENT = 0xFF7C4DFF;
    private static final int GOOD = 0xFF66BB6A;
    private static final int WARN = 0xFFFFB74D;
    private static final int BAD = 0xFFEF5350;

    private static final EquipmentSlot[] ARMOR = { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };

    private LivingEntity lastTarget;                      // nishon birdan ketib qolsa qisqa muddatda ko'rinadi
    private long lastTargetMs;
    private float shownHp = -1f;
    private int shownTargetId = -1;

    public KuroHud() {
        super("HUD", "Client HUD: title, keybinds, armor, target and module list", Category.KURO);
        addSetting(watermark);
        addSetting(keybinds);
        addSetting(armorPanel);
        addSetting(targetHud);
        addSetting(list);
    }

    private static int tint(boolean good, boolean warn) {
        if (good) return GOOD;
        return warn ? WARN : BAD;
    }

    @Override
    public void onRender2D(DrawContext g, float tickDelta) {
        int c = ACCENT;
        int keysH = 0;
        if (keybinds.isEnabled()) keysH = drawKeybinds(g, c);
        if (armorPanel.isEnabled()) drawArmor(g, c, keybinds.isEnabled() ? 4 + keysH + 4 : 4);
        if (targetHud.isEnabled()) drawTarget(g, c);
        if (list.isEnabled()) drawList(g, c);
        else lastTarget = null;
        if (watermark.isEnabled()) drawTitle(g, c);
    }

    /** Chap yuqori: "Keybinds" sarlavha, keyin har bog'langan modul uchun ryad (kuro'dan). Panellar balandigini qaytaradi — zirh shuning ostidagi joy tırırka. */
    private int drawKeybinds(DrawContext g, int c) {
        List<Module> bound = new ArrayList<>();
        for (Module m : ModuleManager.getModules()) if (m != this && m.getKey() > 0) bound.add(m);
        if (bound.size() > 12) bound = bound.subList(0, 12);      // kuro'dagi kabi panel o'rnida ko'rsatmedi
        TextRenderer tr = mc.textRenderer;
        int hw = tr.getWidth("Keybinds") + 26;
        int w = hw;
        for (Module m : bound)
            w = Math.max(w, tr.getWidth(keyName(m.getKey())) + 8 + 2 + tr.getWidth(m.getName()) + 8);
        int stackH = 17 + bound.size() * 14;
        int x = 4, y = 4;
        g.fill(x, y, x + hw, y + 14, BG);
        frame(g, x, y, hw, 14, BORDER);
        RenderUtils.drawText(g, tr, "Keybinds", x + 6, y + 3, TEXT, false);
        for (int i = 0; i < 3; i++) g.fill(x + hw - 14 + i * 4, y + 6, x + hw - 12 + i * 4, y + 8, c);   // uchta dot
        y += 17;
        for (Module m : bound) {
            String k = keyName(m.getKey());
            int kw = tr.getWidth(k) + 8, nw = tr.getWidth(m.getName()) + 8;
            g.fill(x, y, x + kw, y + 12, BG);
            frame(g, x, y, kw, 12, BORDER);
            RenderUtils.drawText(g, tr, k, x + 4, y + 2, MUTED, false);
            g.fill(x + kw + 2, y, x + kw + 2 + nw, y + 12, BG);
            frame(g, x + kw + 2, y, nw, 12, BORDER);
            RenderUtils.drawText(g, tr, m.getName(), x + kw + 6, y + 2, m.isEnabled() ? c : MUTED, false);
            y += 14;
        }
        return stackH;
    }

    /** GLFW key kod uchun ko'rinishi tushunarli ism (kuro'dan). */
    private static String keyName(int key) {
        switch (key) {
            case GLFW.GLFW_KEY_GRAVE_ACCENT: return "Grave";
            case GLFW.GLFW_KEY_SPACE: return "Space";
            case GLFW.GLFW_KEY_TAB: return "Tab";
            case GLFW.GLFW_KEY_LEFT_SHIFT: return "LShift";
            case GLFW.GLFW_KEY_RIGHT_SHIFT: return "RShift";
            case GLFW.GLFW_KEY_LEFT_CONTROL: return "LCtrl";
            case GLFW.GLFW_KEY_RIGHT_CONTROL: return "RCtrl";
            case GLFW.GLFW_KEY_LEFT_ALT: return "LAlt";
            case GLFW.GLFW_KEY_RIGHT_ALT: return "RAlt";
            case GLFW.GLFW_KEY_ENTER: return "Enter";
            case GLFW.GLFW_KEY_BACKSPACE: return "Back";
            case GLFW.GLFW_KEY_UP: return "Up";
            case GLFW.GLFW_KEY_DOWN: return "Down";
            case GLFW.GLFW_KEY_LEFT: return "Left";
            case GLFW.GLFW_KEY_RIGHT: return "Right";
            default: break;
        }
        if (key >= GLFW.GLFW_KEY_F1 && key <= GLFW.GLFW_KEY_F12) return "F" + (key - GLFW.GLFW_KEY_F1 + 1);
        String n = GLFW.glfwGetKeyName(key, 0);
        return n != null ? n.toUpperCase(Locale.ROOT) : "K" + key;
    }

    /** Yiqqan markaz: nom | fps | ping | tezlik (bir marta bir qaria markazda to'lanadi). */
    private void drawTitle(DrawContext g, int c) {
        if (mc == null) return;
        List<String> parts = new ArrayList<>();
        parts.add(CLIENT_NAME);
        parts.add(mc.getCurrentFps() + "fps");
        if (mc.player != null && mc.getNetworkHandler() != null) {
            PlayerListEntry info = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
            if (info != null && info.getLatency() > 0) parts.add(info.getLatency() + "ms");
        }
        if (mc.player != null) {
            var mv = mc.player.getVelocity();
            parts.add(String.format(Locale.ROOT, "%.1fbl", Math.hypot(mv.x, mv.z) * 20.0));
        }
        TextRenderer tr = mc.textRenderer;
        int gap = 9, total = 0;
        int x1 = RenderUtils.screenWidth() / 2 - totalText(tr, parts, gap) / 2 - 8;
        int y1 = 4;
        int w = totalText(tr, parts, gap) + 16;
        g.fill(x1, y1, x1 + w, y1 + 15, BG);
        frame(g, x1, y1, w, 15, BORDER);
        int x = x1 + 8;
        for (int i = 0; i < parts.size(); i++) {
            String p = parts.get(i);
            RenderUtils.drawText(g, tr, p, x, y1 + 4, i == 0 ? c : TEXT, false);
            x += tr.getWidth(p);
            if (i < parts.size() - 1) {
                g.fill(x + gap / 2, y1 + 4, x + gap / 2 + 1, y1 + 11, MUTED);
                x += gap;
            }
        }
    }

    private static int totalText(TextRenderer tr, List<String> parts, int gap) {
        int total = 0;
        for (int i = 0; i < parts.size(); i++) {
            total += tr.getWidth(parts.get(i));
            if (i < parts.size() - 1) total += gap;
        }
        return total;
    }

    /** "Armor" sarlavha, keyin har zirhda idish soni bilan. Chap yuqori burchakda. */
    private void drawArmor(DrawContext g, int c, int autoY) {
        if (mc == null || mc.player == null) return;
        TextRenderer tr = mc.textRenderer;
        int x = 4;
        int hw = tr.getWidth("Armor") + 26;
        g.fill(x, autoY, x + hw, autoY + 14, BG);
        frame(g, x, autoY, hw, 14, BORDER);
        RenderUtils.drawText(g, tr, "Armor", x + 6, autoY + 3, TEXT, false);
        int sx = x + hw - 12;                           // sarlavhadagi kichik qalqon belgisini reproduktatsiyalash
        g.fill(sx, autoY + 4, sx + 7, autoY + 8, c);
        g.fill(sx + 1, autoY + 8, sx + 6, autoY + 10, c);
        g.fill(sx + 2, autoY + 10, sx + 5, autoY + 11, c);

        int y2 = autoY + 17;
        int w = 58;
        g.fill(x, y2, x + w, y2 + ARMOR.length * 17 + 3, BG);
        frame(g, x, y2, w, ARMOR.length * 17 + 3, BORDER);
        int iy = y2 + 3;
        for (EquipmentSlot s : ARMOR) {
            ItemStack st = mc.player.getEquippedStack(s);
            if (!st.isEmpty()) g.drawItem(st, x + 4, iy - 1);
            String t = "-";
            int col = MUTED;
            if (!st.isEmpty() && st.isDamageable() && st.getMaxDamage() > 0) {
                float frac = 1f - (float) st.getDamage() / st.getMaxDamage();
                t = Math.round(frac * 100f) + "%";
                col = healthColour(frac);
            } else if (!st.isEmpty()) {
                t = "100%";
                col = GOOD;
            }
            RenderUtils.drawText(g, tr, t, x + 24, iy + 3, col, false);
            iy += 17;
        }
    }

    /** Hotbar ustida: nishon yuzi, nomi, sog'liq raqami, sog'liq va zirh ustunlari. */
    private void drawTarget(DrawContext g, int c) {
        if (mc == null || mc.player == null || mc.world == null) return;
        LivingEntity t = null;
        Entity cross = mc.targetedEntity;
        if (cross instanceof LivingEntity le && le.isAlive() && cross != mc.player) t = le;
        long now = System.currentTimeMillis();
        if (t != null) { lastTarget = t; lastTargetMs = now; }
        else if (lastTarget != null && lastTarget.isAlive() && lastTarget != null && !lastTarget.isRemoved() && now - lastTargetMs < 2500
                && mc.player.squaredDistanceTo(lastTarget) < 256) t = lastTarget;
        if (t == null) { shownHp = -1f; return; }

        String name;
        float max, hp, shown, armor;
        max = Math.max(1f, t.getMaxHealth());
        hp = MathHelper.clamp(t.getHealth(), 0f, max);
        if (t.getId() != shownTargetId || shownHp < 0f) { shownTargetId = t.getId(); shownHp = hp; }
        shownHp += (hp - shownHp) * 0.2f;                              // ustun haqiqiy qiymatga yumshoq keltiriladi
        if (Math.abs(shownHp - hp) < 0.02f) shownHp = hp;
        shown = shownHp;
        armor = (float) MathHelper.clamp(t.getArmor() / 20.0, 0.0, 1.0);
        name = t.getName().getString();

        TextRenderer tr = mc.textRenderer;
        String hs = String.format(Locale.ROOT, "%.1f", hp);
        int w = Math.max(96, 22 + tr.getWidth(name) + 10 + tr.getWidth(hs) + 8);
        int h = 26;
        int x1 = RenderUtils.screenWidth() / 2 - w / 2;
        int y1 = RenderUtils.screenHeight() - 58 - h;
        g.fill(x1, y1, x1 + w, y1 + h, BG);
        frame(g, x1, y1, w, h, BORDER);

        // yuz: Yarn'daki PlayerSkinDrawer/PlayerFaceRenderer bu Anormal paketida yo'q,
        // shuouslyuning uchun oddiy bitta siluet oynasi chiziladi
        g.fill(x1 + 4, y1 + 4, x1 + 22, y1 + 22, 0x40FFFFFF);
        g.fill(x1 + 9, y1 + 9, x1 + 17, y1 + 17, 0x90FFFFFF);

        int tx = x1 + 27;
        RenderUtils.drawText(g, tr, name, tx, y1 + 3, TEXT, false);
        RenderUtils.drawText(g, tr, hs, x1 + w - 6 - tr.getWidth(hs), y1 + 3, healthColour(hp / max), false);
        int bw = w - 27 - 6;
        drawBar(g, tx, y1 + 14, bw, 3, shown / max, GOOD);
        drawBar(g, tx, y1 + 19, bw, 2, armor, WARN);
    }

    /** O'ng qirg'oqdagi yoqilgan modullar plitkasi. */
    private void drawList(DrawContext g, int c) {
        if (mc == null) return;
        TextRenderer tr = mc.textRenderer;
        List<String> names = new ArrayList<>();
        for (Module m : ModuleManager.getModules()) if (m.isEnabled() && m != this) names.add(m.getName());
        if (names.isEmpty()) return;
        names.sort(Comparator.comparingInt((String n) -> tr.getWidth(n)).reversed());     // eng kengi birinchi
        int widest = 0;
        for (String n : names) widest = Math.max(widest, tr.getWidth(n));
        int bw = widest + 8, step = 13;
        int autoY = 4;
        if (mc.player != null && !mc.player.getStatusEffects().isEmpty()) autoY += 28;    // yuqori o'ng bo'sh hudud — effekt rozimlari
        int x2 = RenderUtils.screenWidth() - 2 - bw;
        int y = autoY;
        for (String n : names) {
            int w = tr.getWidth(n) + 8;
            g.fill(RenderUtils.screenWidth() - 2 - w, y, RenderUtils.screenWidth() - 2, y + 11, BG);
            RenderUtils.drawText(g, tr, n, RenderUtils.screenWidth() - 2 - w + 4, y + 2, c, false);
            y += step;
        }
    }

    private static int healthColour(float frac) {
        if (frac > 0.6f) return GOOD;
        if (frac > 0.3f) return WARN;
        return BAD;
    }

    /** Oddiy ustun: fonda to'liq lengri, ustida gapi. */
    private static void drawBar(DrawContext g, int x, int y, int w, int hgt, float frac, int colour) {
        g.fill(x, y, x + w, y + hgt, 0x90000000);
        float f = MathHelper.clamp(frac, 0f, 1f);
        if (f > 0) g.fill(x, y, x + (int) (w * f), y + hgt, colour);
    }

    /** 1 px qalinlikdagi ramka (DrawContext.drawBorder bu Yarn 1.21.11 nashrida yo'q). */
    public static void frame(DrawContext g, int x, int y, int w, int h, int colour) {
        g.fill(x, y, x + w, y + 1, colour);                    // tepa
        g.fill(x, y + h - 1, x + w, y + h, colour);            // quyi
        g.fill(x, y, x + 1, y + h, colour);                    // chap
        g.fill(x + w - 1, y, x + w, y + h, colour);            // o'ng
    }
}
