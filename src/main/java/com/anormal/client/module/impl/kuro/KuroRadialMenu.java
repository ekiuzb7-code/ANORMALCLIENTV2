package com.anormal.client.module.impl.kuro;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.module.impl.kuro.KuroBaritoneControl;
import com.anormal.client.setting.BooleanSetting;

import net.minecraft.client.network.ClientPlayNetworkHandler;

import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * kuro {@code RadialMenu} port (Player): radial tez-chat menyusi. Modul
 * kalitiga bog'lanadi (yoki boshqa kalit yo‘q bo‘lsa oching); yoqilsa ekran
 * o‘rtasida uch slice ochiladi. Biriga bosing: matni chat sifatida yoki boshida
 * "/" bilan buyruq sifatida yuboriladi. O‘rtani bosing — faqat yopadi.
 * O‘ng bosing: slice matnini tahrirlash ekrani ochiladi. Uch matn
 * config/anormal-radial.txt faylida saqlanadi.
 *
 * <p>Anormaldagi farqlar: kuro's Screen (gui/RadialMenuScreen) shaderli UI bilan
 * bo'lganligi uchun bu yerda Anormal DrawContext'ida qayta chizilgan (u slice
 * segmentlari emissiyasi); "Release to select" giranda yo‘q (Anormal modul
 * kaliti menu ochadi, bosib yuborish bilan emas), faqat "Close after sending"
 * ishlaydi.</p>
 */
public class KuroRadialMenu extends Module {
    public static final int SLOTS = 3;

    private final BooleanSetting closeAfter = new BooleanSetting("Close after sending", "Yuborilgach menyuni yopadi", true);
    private final BooleanSetting releaseSelect = new BooleanSetting("Release to select", "Anormal portida kuro'dagidek kalit tushi bilan ishlamaydi (ochish-toggle sozlamasi)", false);

    private final String[] slots = {"", "", ""};
    private int pendingEdit = -1;
    private boolean screenClosing;

    public KuroRadialMenu() {
        super("Radial Menu", "Radial quick-chat menu with three slots (kuro port)", Category.KURO);
        addSetting(closeAfter);
        addSetting(releaseSelect);
        load();
    }

    public boolean closeAfterSending() { return closeAfter.isEnabled(); }

    public String slot(int i) { return i >= 0 && i < SLOTS ? slots[i] : ""; }

    public void setSlot(int i, String text) {
        slots[i] = clean(text);
        save();
    }

    public int getPendingEdit() { return pendingEdit; }

    /** Boshqa joyda belgi/simvol va § belgisi hech qachon yuborilmaydi, maksimal 256. */
    private static String clean(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) if (!Character.isISOControl(c) && c != '§') sb.append(c);
        String t = sb.toString().strip();
        return t.length() > 256 ? t.substring(0, 256) : t;
    }

    /** slot i yuboriladi: "/buyruq" buyruq sifatida, barchasi chat sifatida (client buyruqlar ham). */
    public void send(int i) {
        String t = slot(i);
        ClientPlayNetworkHandler net = mc.player != null ? mc.player.networkHandler : null;
        if (t.isEmpty() || net == null) return;
        if (t.startsWith("/")) { if (t.length() > 1) net.sendChatCommand(t.substring(1)); }
        else KuroBaritoneControl.sendPlainChat(net, t);   // oddiy chat Baritone chat boshqaruviga yig'ilmaydi
    }

    public void openEditor(int slot) {
        if (mc.player == null) return;
        if (isEnabled()) {
            mc.setScreen(new KuroRadialEditScreen(this, slot));
        } else {
            pendingEdit = slot;
            setEnabled(true);
        }
    }

    public void openMenu() {
        if (mc.player == null) return;
        int edit = pendingEdit;
        pendingEdit = -1;
        mc.setScreen(edit >= 0 ? new KuroRadialEditScreen(this, edit) : new KuroRadialMenuScreen(this));
    }

    @Override
    public void onEnable() {
        if (mc.player == null) { setEnabled(false); return; }
        openMenu();
    }

    /** Radial ekran yopilganda chaqiriladi: modul o‘chadi, ekran yana yopilmaydi. */
    public void screenRemoved() {
        screenClosing = true;
        try { setEnabled(false); } finally { screenClosing = false; }
    }

    @Override
    public void onDisable() {
        if (!screenClosing && mc.currentScreen instanceof KuroRadialMenuScreen) mc.setScreen(null);
    }

    private static Path file() { return FabricLoader.getInstance().getConfigDir().resolve("anormal-radial.txt"); }

    private void load() {
        try {
            Path p = file();
            if (!Files.exists(p)) return;
            List<String> lines = Files.readAllLines(p);
            for (int i = 0; i < SLOTS && i < lines.size(); i++) slots[i] = clean(lines.get(i));
        } catch (Exception ignored) {}
    }

    private void save() {
        try { Files.writeString(file(), String.join("\n", slots)); } catch (Exception ignored) {}
    }
}
