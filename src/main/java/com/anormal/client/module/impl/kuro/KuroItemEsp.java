package com.anormal.client.module.impl.kuro;

import com.anormal.client.lucid.WorldRender3D;
import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.util.ProjectionUtil;
import com.anormal.client.util.RenderUtils;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * kuro {@code ItemEsp} port: tashlangan buyumlarni devor orqasidan chizadi —
 * nom, son va masofa bilan. Id ichida "highlight" so'zlaringizdan biri
 * (diamond, netherite, totem ...) bor buyumlar alohida rang bilan chiziladi.
 * Yaqin yotgan bir xil uyumlar bitta nomga to'planadi, shuning uchun
 * qirg'oq toshi yig'indisi ekranni "ko'mmaydi".
 *
 * <p>kuro'dagi Setting.Text/Button Anormalda yo'q — ularning o'rnida ochiq
 * metodlar: {@link #addWord(String)} (so'z ham qo'shadi, yana yozilsa
 * olib tashlaydi), {@link #showHighlightList()}, {@link #resetHighlightList()},
 * {@link #clearHighlightList()}. Rang g'ildiragi yo'q — doimiy ranglar.
 * "Tracers" ekrandan chiqqan nishonni cheklaydigan shakl yo'q, faqat ko'rinsa
 * chiziladi.</p>
 */
public class KuroItemEsp extends Module {
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("kuro-itemesp.txt");
    private static final String[] DEFAULTS = {"diamond", "netherite", "ancient_debris", "totem", "elytra", "enchanted_golden_apple",
            "shulker_box", "nether_star", "beacon", "trident", "mace", "dragon_egg"};

    private static final int NORMAL = 0xFF4FE3C0;                // kuro Color hue 0.55 (yashil-siyoh)
    private static final int GOOD = 0xFFFFC04F;                  // kuro Highlight hue 0.12 (olovli to'q sariq)
    private static final int TEXT = 0xFFE8E8F0, BG = 0xB0101214;

    private final NumberSetting range = new NumberSetting("Range", "Max draw distance", 64.0, 8.0, 256.0, 8.0);
    private final NumberSetting maxLabels = new NumberSetting("Max labels", "Labels drawn at once", 30.0, 5.0, 80.0, 5.0);
    private final BooleanSetting boxes = new BooleanSetting("Boxes", true);
    private final BooleanSetting labels = new BooleanSetting("Labels", true);
    private final BooleanSetting distance = new BooleanSetting("Show distance", true);
    private final BooleanSetting tracers = new BooleanSetting("Tracers", false);
    private final BooleanSetting onlyList = new BooleanSetting("Only highlighted items", false);

    private final List<String> words = new ArrayList<>();
    private boolean registered;

    private record Drop(Item item, Vec3d pos, boolean hl) {}

    private static final class Cluster {
        final Item item; final String name; final boolean hl; final Vec3d pos; final double dist;
        int count;
        Cluster(Item item, String name, int count, boolean hl, Vec3d pos, double dist) {
            this.item = item; this.name = name; this.count = count; this.hl = hl; this.pos = pos; this.dist = dist;
        }
    }

    private final List<Drop> drops = new ArrayList<>();
    private final List<Cluster> clusters = new ArrayList<>();

    public KuroItemEsp() {
        super("Item ESP", "Outlines dropped items through walls with names and counts", Category.KURO);
        addSetting(range);
        addSetting(maxLabels);
        addSetting(boxes);
        addSetting(labels);
        addSetting(distance);
        addSetting(tracers);
        addSetting(onlyList);
        load();
    }

    // ------------------------------------------------------------------ highlight so'zlar ro'yxati

    /** kuro'dagi Setting.Text: so'z qo'shadi; bir xil so'z yana yozilsa ro'yxatdan olib tashlaydi. */
    public void addWord(String text) {
        String w = text.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        if (w.isEmpty()) return;
        if (words.remove(w)) { save(); say("Removed \"" + w + "\" from the highlight list."); return; }
        words.add(w);
        save();
        say("Highlighting items with \"" + w + "\" in their id. (Type it again to remove it.)");
    }

    public void showHighlightList() { say("Highlighted: " + (words.isEmpty() ? "(none)" : String.join(", ", words))); }

    public void resetHighlightList() {
        words.clear();
        for (String w : DEFAULTS) words.add(w);
        save();
        say("Highlight list reset.");
    }

    public void clearHighlightList() {
        words.clear();
        save();
        say("Highlight list cleared.");
    }

    private boolean matches(Item item) {
        String path = Registries.ITEM.getId(item).getPath();
        for (String w : words) if (path.contains(w)) return true;
        return false;
    }

    private void load() {
        try {
            if (Files.exists(FILE)) {
                for (String line : Files.readAllLines(FILE)) { String w = line.trim(); if (!w.isEmpty()) words.add(w); }
                return;
            }
        } catch (IOException ignored) {}
        for (String w : DEFAULTS) words.add(w);
    }

    private void save() {
        try { Files.write(FILE, words); } catch (IOException ignored) {}
    }

    private static void say(String s) {
        if (mc.player != null) mc.player.sendMessage(net.minecraft.text.Text.literal("[Item ESP] " + s), false);
    }

    // ------------------------------------------------------------------ har frame

    private void gather() {
        drops.clear();
        clusters.clear();
        if (mc.world == null || mc.player == null) return;
        Vec3d cam = mc.getCameraEntity() != null ? mc.getCameraEntity().getLerpedPos(mc.getRenderTickCounter().getTickProgress(true))
                : mc.player.getEyePos();
        double r2 = range.getValue() * range.getValue();
        float tick = mc.getRenderTickCounter().getTickProgress(true);
        for (Entity e : mc.world.getEntities()) {
            if (!(e instanceof ItemEntity ie)) continue;
            ItemStack st = ie.getStack();
            if (st.isEmpty()) continue;
            Vec3d p = ie.getLerpedPos(tick);
            double dx = p.x - cam.x, dy = p.y - cam.y, dz = p.z - cam.z;
            double d2 = dx * dx + dy * dy + dz * dz;
            if (d2 > r2) continue;
            boolean hl = matches(st.getItem());
            if (onlyList.isEnabled() && !hl) continue;
            drops.add(new Drop(st.getItem(), p, hl));

            Cluster c = null;
            for (Cluster k : clusters) {
                if (k.item == st.getItem() && k.pos.squaredDistanceTo(p) < 4.0) { c = k; break; }
            }
            if (c == null) clusters.add(new Cluster(st.getItem(), st.getName().getString(), st.getCount(), hl, p, Math.sqrt(d2)));
            else c.count += st.getCount();
        }
        clusters.sort((a, b) -> Double.compare(a.dist, b.dist));
    }

    @Override
    public void onEnable() {
        if (registered) return;
        registered = true;
        WorldRenderEvents.END_MAIN.register(this::dispatchWorld);
    }

    private void dispatchWorld(WorldRenderContext wrc) {
        if (!isEnabled() || mc.world == null) return;
        gather();
        if (!boxes.isEnabled()) return;
        Camera camera = wrc.gameRenderer().getCamera();
        if (camera == null || !camera.isReady()) return;
        MatrixStack matrices = wrc.matrices();
        VertexConsumerProvider consumers = wrc.consumers();
        if (matrices == null || consumers == null) return;
        int n = 0;
        WorldRender3D.begin(matrices, camera);
        try {
            for (Drop d : drops) {
                if (n++ >= 400) break;
                int c = d.hl ? GOOD : NORMAL;
                Vec3d p = d.pos;
                WorldRender3D.drawBox(consumers, new Box(p.x - 0.22, p.y, p.z - 0.22, p.x + 0.22, p.y + 0.45, p.z + 0.22),
                        0xFF000000 | c);
            }
        } finally {
            WorldRender3D.end(matrices);
        }
    }

    @Override
    public void onRender2D(DrawContext g, float tickDelta) {
        if (!isEnabled() || mc.player == null || (!labels.isEnabled() && !tracers.isEnabled())) return;
        ProjectionUtil.beginFrame(tickDelta);
        double cx = RenderUtils.screenWidth() / 2.0, cy = RenderUtils.screenHeight() / 2.0;
        int shown = 0;
        for (Cluster c : clusters) {
            if (shown >= maxLabels.getValue().intValue()) break;
            int rgb = c.hl ? GOOD : NORMAL;
            if (tracers.isEnabled()) {
                int[] pt = ProjectionUtil.project(new Vec3d(c.pos.x, c.pos.y + 0.2, c.pos.z), tickDelta);
                if (pt != null) RenderUtils.drawLine(g, (int) cx, (int) cy, pt[0], pt[1], (0xC8 << 24) | (rgb & 0xFFFFFF), 1);
            }
            if (!labels.isEnabled()) continue;
            int[] pt = ProjectionUtil.project(new Vec3d(c.pos.x, c.pos.y + 0.75, c.pos.z), tickDelta);
            if (pt == null) continue;
            String text = c.name + (c.count > 1 ? " x" + c.count : "")
                    + (distance.isEnabled() ? "  " + (int) Math.round(c.dist) + "m" : "");
            drawTag(g, text, pt[0], pt[1] - 14, 0xFF000000 | rgb);
            shown++;
        }
    }

    private void drawTag(DrawContext g, String text, int cx, int y, int argb) {
        TextRenderer tr = mc.textRenderer;
        if (tr == null) return;
        int w = tr.getWidth(text);
        g.fill(cx - w / 2 - 4, y - 3, cx + w / 2 + 4, y + 9, BG);
        g.fill(cx - w / 2 - 4, y - 3, cx - w / 2 - 1, y + 9, argb);
        RenderUtils.drawText(g, tr, text, cx - w / 2, y, TEXT, false);
    }
}
