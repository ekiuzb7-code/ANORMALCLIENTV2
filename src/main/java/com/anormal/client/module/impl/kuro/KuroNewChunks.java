package com.anormal.client.module.impl.kuro;

import com.anormal.client.lucid.WorldRender3D;
import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.WorldChunk;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * kuro {@code NewChunks} port: yangi kabi ko'rinadigan chunklar belgilanadi.
 *
 * <p>kuro buni server yuborgan oqim (flowing) suv/lava blok yangilanishlaridan
 * aniqlaydi va blok-yangilanish mixinidan foydalanadi. Anormal'da bunday
 * mixin yo'q, shuning uchun bu portda chunk prestojga ega bo'lganda Fabric'ning
 * {@code ClientChunkEvents.CHUNK_LOAD} hodisasi ishlatiladi: bu saqlanqichdan
 * yuklangan eski chunklarni ham ko'radi (taxmin bo'lsa kerak), chunk turlari
 * ko'rish este paytligini achiар va detallarga yaqinlashishiga chiqish
 * metodi bilan ko'ramsiz.</p>
 *
 * <p>Belgilangan chunklar server + o'lcham bo'yicha saqlanadi, shuning uchun
 * yangi chunklar izi kimning sayohat qilgani ko'rsatadi. Rendering
 * {@link WorldRender3D} orqali (KuroAncientDebris'dagi pattern).</p>
 */
public class KuroNewChunks extends Module {
    private static KuroNewChunks instance;

    private static final int MAX_FILLED = 48;      // fill budget per frame

    private final NumberSetting range = new NumberSetting("Range (chunks)", "Ring size drawn", 8.0, 2.0, 16.0, 1.0);
    private final BooleanSetting outline = new BooleanSetting("Outline", true);
    private final BooleanSetting fill = new BooleanSetting("Fill", true);
    private final NumberSetting fillOpacity = new NumberSetting("Fill opacity", "Fill alpha", 40.0, 10.0, 150.0, 10.0);
    private final NumberSetting fillRange = new NumberSetting("Fill range (chunks)", "Only fill this close", 6.0, 1.0, 16.0, 1.0);
    private final BooleanSetting fade = new BooleanSetting("Fade with distance", true);
    private final BooleanSetting followY = new BooleanSetting("Follow my height", true);
    private final NumberSetting height = new NumberSetting("Fixed height", "Y when not following", 64.0, -64.0, 320.0, 1.0);
    private static final int RGB = 0xFFC038FF;     // kuro hue 0.75 (binafsha-ko'k) — g'ildirak Anormalda yo'q

    private final Set<Long> chunks = new HashSet<>();
    /** Har chunk birinchi marta aniqlangan vaqt (ms); saqlanqichdan kelganlarida yo'q. */
    private final Map<Long, Long> times = new HashMap<>();
    private String lastKey = "";
    private boolean dirty, registered;
    private int saveTimer;

    public KuroNewChunks() {
        super("New Chunks", "Marks chunks that look newly generated", Category.KURO);
        instance = this;
        addSetting(range);
        addSetting(outline);
        addSetting(fill);
        addSetting(fillOpacity);
        addSetting(fillRange);
        addSetting(fade);
        addSetting(followY);
        addSetting(height);
    }

    public static KuroNewChunks get() { return instance; }

    /** Hozirgi dunyodagi har aniqlangan yangi chunk (paketlangan ChunkPos); o'qish uchun. */
    public Set<Long> chunkSet() { return chunks; }
    /** Chunk bo'yicha aniqlash vaqti (ms), faqat shu sessiyada topilganlar. */
    public Map<Long, Long> chunkTimes() { return times; }

    // ------------------------------------------------------------------ hodisa / saqlash

    @Override
    public void onEnable() {
        if (registered) return;
        registered = true;
        ClientChunkEvents.CHUNK_LOAD.register(this::onChunkLoad);
    }

    @Override
    public void onDisable() {
        // Fabric Event'da unregister yo'q: onChunkLoad o'zi isEnabled() bilan to'xtaydi.
        registered = false;
        save(lastKey);
    }

    private void onChunkLoad(net.minecraft.client.world.ClientWorld world, WorldChunk chunk) {
        if (!isEnabled() || mc.world == null || world != mc.world) return;
        if (!syncWorld()) return;
        long key = ChunkPos.toLong(chunk.getPos().x, chunk.getPos().z);
        if (chunks.add(key)) { dirty = true; times.put(key, System.currentTimeMillis()); }
    }

    private String worldKey() {
        String server = mc.getCurrentServerEntry() != null ? mc.getCurrentServerEntry().address : "singleplayer";
        String dim = mc.world != null ? mc.world.getRegistryKey().getValue().toString() : "?";
        return (server + "_" + dim).replaceAll("[^A-Za-z0-9._\\-]", "_");
    }

    private Path file(String key) {
        return FabricLoader.getInstance().getConfigDir().resolve("kuro").resolve("newchunks").resolve(key + ".txt");
    }

    /** Server + o'lcham o'zgarsa, shu dunyoning saqlangan to'plamiga o'tadi. */
    private boolean syncWorld() {
        if (mc.world == null || mc.player == null) return false;
        String k = worldKey();
        if (!k.equals(lastKey)) {
            if (!lastKey.isEmpty()) save(lastKey);
            lastKey = k;
            chunks.clear();
            times.clear();
            dirty = false;
            saveTimer = 0;
            load(k);
        }
        return true;
    }

    public void clear() {
        chunks.clear();
        times.clear();
        dirty = true;
        save(lastKey);
        say("New chunks cleared for this world.");
    }

    @Override
    public void onTick() {
        if (mc.world == null || mc.player == null) {               // dunyodan chiqdi: topganimizni yo'qotmaymiz
            if (dirty) save(lastKey);
            return;
        }
        syncWorld();
        if (dirty && ++saveTimer >= 600) { saveTimer = 0; save(lastKey); }   // ko'pi bilan har 30 soniyada yozadi
    }

    private void load(String key) {
        try {
            Path f = file(key);
            if (!Files.exists(f)) return;
            for (String line : Files.readAllLines(f)) {
                String[] p = line.trim().split(" ");
                if (p.length < 2) continue;
                try { chunks.add(ChunkPos.toLong(Integer.parseInt(p[0]), Integer.parseInt(p[1]))); }
                catch (NumberFormatException ignored) {}
            }
        } catch (IOException ignored) {}
    }

    private void save(String key) {
        if (key == null || key.isEmpty() || !dirty) return;
        try {
            List<String> lines = new ArrayList<>(chunks.size());
            for (long c : chunks) lines.add(ChunkPos.getPackedX(c) + " " + ChunkPos.getPackedZ(c));
            Files.createDirectories(file(key).getParent());
            Files.write(file(key), lines);
            dirty = false;
        } catch (IOException ignored) {}
    }

    // ------------------------------------------------------------------ chizish

    private void dispatchWorld(WorldRenderContext wrc) {
        if (!isEnabled() || mc.world == null || mc.player == null || chunks.isEmpty()) return;
        Camera camera = wrc.gameRenderer().getCamera();
        if (camera == null || !camera.isReady()) return;
        MatrixStack matrices = wrc.matrices();
        VertexConsumerProvider consumers = wrc.consumers();
        if (matrices == null || consumers == null) return;

        int rad = range.getValue().intValue(), fillRad = Math.min(rad, fillRange.getValue().intValue());
        int pcx = mc.player.getBlockPos().getX() >> 4, pcz = mc.player.getBlockPos().getZ() >> 4;
        double y = followY.isEnabled() ? Math.floor(mc.player.getY()) : height.getValue();
        int filled = 0;
        WorldRender3D.begin(matrices, camera);
        try {
            // eng yaqin halqalar avval: fill byudjeti atrofdagi chunklarga sarflanadi
            for (int ring = 0; ring <= rad; ring++) {
                for (int dx = -ring; dx <= ring; dx++) {
                    int step = Math.abs(dx) == ring ? 1 : Math.max(1, ring * 2);
                    for (int dz = -ring; dz <= ring; dz += step) {
                        int cx = pcx + dx, cz = pcz + dz;
                        if (!chunks.contains(ChunkPos.toLong(cx, cz))) continue;
                        double f = fade.isEnabled() ? 1.0 - 0.6 * ring / Math.max(1, rad) : 1.0;
                        filled = drawChunk(consumers, cx, cz, y, f, ring <= fillRad, filled);
                    }
                }
            }
        } finally {
            WorldRender3D.end(matrices);
        }
    }

    private int drawChunk(VertexConsumerProvider consumers, int cx, int cz, double y, double f, boolean allowFill, int filled) {
        Box box = new Box(cx * 16.0, y, cz * 16.0, cx * 16.0 + 16, y + 0.02, cz * 16.0 + 16);
        if (fill.isEnabled() && allowFill && filled < MAX_FILLED) {
            WorldRender3D.drawFilledBox(consumers, box, ((int) (fillOpacity.getValue() * f) << 24) | (RGB & 0xFFFFFF));
            filled++;
        }
        if (outline.isEnabled()) {
            int line = ((int) (255 * f) << 24) | (RGB & 0xFFFFFF);
            WorldRender3D.drawBox(consumers, box, line);
        }
        return filled;
    }

    private static void say(String s) {
        if (mc.player != null) mc.player.sendMessage(net.minecraft.text.Text.literal("[New Chunks] " + s), false);
    }
}
