package com.anormal.client.module.impl.kuro;

import com.anormal.client.lucid.WorldRender3D;
import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.module.impl.client.Friends;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.ColorUtils;
import com.anormal.client.util.ProjectionUtil;
import com.anormal.client.util.RenderUtils;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * kuro {@code LogoutSpots} port: o'yinchalar server/dunyodan chiqib ketganda
 * (logout) ularning oxirgi joyini belgilab ko'rsatadi — quti, yengil to'ldiril
 * shaffof johasi va yuqorida ism+vaqt yorlig'i. Server yoki o'lcham
 * o'zgarsa ro'yxatlar tozalanadi, yorliqlar muddati bilan o'tib ketadi.
 *
 * <p>Anormaldagi farqlar: kuro'daki {@code WorldGfx.box}/beton render util
 * o'rniga {@link WorldRender3D#drawBox} va {@link WorldRender3D#drawFilledBoxExactAlpha}
 * ishlatiladi; yorliq {@link ProjectionUtil#project} bilan ekran koordinatasi
 * topilib, {@link RenderUtils#drawText} bilan chiziladi; {@code Setting.Hue}
 * o'rniga {@link ColorSetting} (doimiy uslub), {@code Notifications.info()} xabari
 * bu portda yo'q — Anormalda umumiy xabar API ham yo'q.</p>
 */
public class KuroLogoutSpots extends Module {
    private static final int MAX_LEAVERS = 100;

    private final ColorSetting color = new ColorSetting("Color", "Quti va yorliq rangi", ColorUtils.rgba(224, 90, 184, 255));
    private final NumberSetting timeout = new NumberSetting("Timeout (minutes)", "Yorliqlarni bu qancha vaqtda o'chirish", 10.0, 1.0, 120.0, 1.0);
    private final NumberSetting range = new NumberSetting("Range", "Ko'rsatish masofasi (blok)", 128.0, 8.0, 512.0, 8.0);
    private final BooleanSetting filled = new BooleanSetting("Filled", "Shaffof to'ldiruvchi ham ko'rsatadi", true);
    private final BooleanSetting tags = new BooleanSetting("Name tags", "Ism va vaqt yorlig'i", true);
    private final BooleanSetting friends = new BooleanSetting("Friends", "Do'stlar ham belgilanadi", true);

    /** Chiqib ketgan o'yinchining saqlangan nuqtasi. */
    private static final class Leaver {
        final String name;
        final double x, y, z;
        final String dim;
        final long time;
        Leaver(String name, double x, double y, double z, String dim, long time) {
            this.name = name; this.x = x; this.y = y; this.z = z; this.dim = dim; this.time = time;
        }
    }

    private final Map<UUID, double[]> pos = new HashMap<>();
    private final Map<UUID, String> names = new HashMap<>();
    private final List<Leaver> leavers = new ArrayList<>();
    private String worldKey = "";   // server + o'lcham indikatori

    public KuroLogoutSpots() {
        super("Logout Spots", "Marks where players were when they logged out (kuro port)", Category.KURO);
        addSetting(color);
        addSetting(timeout);
        addSetting(range);
        addSetting(filled);
        addSetting(tags);
        addSetting(friends);
        WorldRenderEvents.END_MAIN.register(this::dispatchWorld);
    }

    @Override
    public void onEnable() { clean(); }

    @Override
    public void onDisable() { clean(); }

    private void clean() {
        pos.clear(); names.clear(); leavers.clear();
    }

    private String curDim() {
        try { return mc.world.getRegistryKey().getValue().getPath(); }
        catch (Throwable ignored) { return ""; }
    }

    private String serverKey() {
        try {
            ServerInfo s = mc.getCurrentServerEntry();
            return s == null ? "single" : String.valueOf(s.address);
        } catch (Throwable ignored) { return "?"; }
    }

    @Override
    public void onTick() {
        if (mc.world == null || mc.player == null) return;
        String key = serverKey() + "/" + curDim();
        if (!key.equals(worldKey)) { worldKey = key; clean(); return; }

        long now = System.currentTimeMillis();
        Set<UUID> seen = new HashSet<>();
        for (AbstractClientPlayerEntity p : mc.world.getPlayers()) {
            UUID id = p.getUuid();
            seen.add(id);
            double[] v = pos.get(id);
            if (v == null) { v = new double[]{p.getX(), p.getY(), p.getZ()}; pos.put(id, v); }
            else { v[0] = p.getX(); v[1] = p.getY(); v[2] = p.getZ(); }
            names.put(id, p.getName().getString());
        }

        for (UUID id : new ArrayList<>(pos.keySet())) {
            if (!seen.contains(id)) {
                double[] v = pos.remove(id);
                String nm = names.remove(id);
                if (v != null && leavers.size() < MAX_LEAVERS) {
                    boolean isFriend = nm != null && Friends.isFriend(nm);
                    if (!(isFriend && !friends.isEnabled()))
                        leavers.add(new Leaver(nm == null ? "?" : nm, v[0], v[1], v[2], curDim(), now));
                }
            }
        }

        long ttl = (long) (timeout.getValue() * 60_000.0);
        for (Iterator<Leaver> it = leavers.iterator(); it.hasNext(); ) if (now - it.next().time > ttl) it.remove();
    }

    private void dispatchWorld(WorldRenderContext wrc) {
        if (!isEnabled() || leavers.isEmpty() || mc.world == null) return;
        Camera camera = wrc.gameRenderer().getCamera();
        if (camera == null || !camera.isReady()) return;
        MatrixStack matrices = wrc.matrices();
        VertexConsumerProvider consumers = wrc.consumers();
        if (matrices == null || consumers == null) return;

        double camX = camera.getCameraPos().x, camY = camera.getCameraPos().y, camZ = camera.getCameraPos().z;
        double max2 = range.getValue() * range.getValue();
        String dim = curDim();
        WorldRender3D.begin(matrices, camera);
        try {
            for (Leaver l : leavers) {
                if (!l.dim.equals(dim)) continue;
                double dx = l.x - camX, dy = l.y - camY, dz = l.z - camZ;
                if (dx * dx + dy * dy + dz * dz > max2) continue;
                int rgb = Friends.isFriend(l.name) ? 0x00FF00 : (color.getValue() & 0xFFFFFF);
                Box box = new Box(l.x - 0.3, l.y, l.z - 0.3, l.x + 0.3, l.y + 1.8, l.z + 0.3);
                if (filled.isEnabled()) WorldRender3D.drawFilledBoxExactAlpha(consumers, box, (0x30 << 24) | rgb);
                WorldRender3D.drawBox(consumers, box, (0x90 << 24) | rgb);
            }
        } finally {
            WorldRender3D.end(matrices);
        }
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (!isEnabled() || leavers.isEmpty() || !tags.isEnabled() || mc.player == null || mc.world == null) return;
        String dim = curDim();
        if (dim.isEmpty()) return;
        ProjectionUtil.beginFrame(tickDelta);
        TextRenderer tr = mc.textRenderer;
        long now = System.currentTimeMillis();
        double cx = mc.player.getX(), cz = mc.player.getZ();
        double max2 = range.getValue() * range.getValue();
        for (Leaver l : leavers) {
            if (!l.dim.equals(dim)) continue;
            double dx = l.x - cx, dz = l.z - cz;
            if (dx * dx + dz * dz > max2) continue;
            int[] pt = ProjectionUtil.project(new Vec3d(l.x, l.y + 1.95, l.z), tickDelta);
            if (pt == null) continue;
            int rgb = Friends.isFriend(l.name) ? 0x00FF00 : (color.getValue() & 0xFFFFFF);
            long left = (long) (timeout.getValue() * 60_000.0) - (now - l.time);
            if (left < 0) left = 0;
            String label = String.format("%s  %d:%02d", l.name, left / 60_000, (left % 60_000) / 1000);
            int w = tr.getWidth(label);
            RenderUtils.fill(context, pt[0] - w / 2 - 3, pt[1] - 10, pt[0] + w / 2 + 3, pt[1] + 2, 0x7F101018);
            RenderUtils.drawText(context, tr, label, pt[0] - w / 2, pt[1] - 8, 0xFF000000 | rgb, true);
        }
    }
}
