package com.anormal.client.module.impl.kuro;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.module.impl.client.Friends;
import com.anormal.client.util.ProjectionUtil;
import com.anormal.client.util.RenderUtils;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * kuro {@code Nametags} port: har o'yinchi boshining ustida ixcham plitka —
 * nom, salomatlik chizig'i (shikast "...trail" soyasi va oltin absorpciya
 * chizig'i bilan), aniq salomatlik, ixtiyoriy zirh qatorlari va haqiqiy
 * (IRL) vaqt.
 *
 * <p>Anormaldagi farqlar: vanilla nomteglarini yashirish mixin'i yo'q
 * (kuro'da NametagMixin bor) — bu portda vanilla teg ustidan chiziladi;
 * world-space joylashuv {@code ProjectionUtil} orqali; Look/SdfText
 * Anormal uslublariga moslandi.</p>
 */
public class KuroNametags extends Module {
    private static final EquipmentSlot[] ARMOR = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };
    private static final DateTimeFormatter T24 = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter T24S = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter T12 = DateTimeFormatter.ofPattern("h:mm a");
    private static final DateTimeFormatter T12S = DateTimeFormatter.ofPattern("h:mm:ss a");
    private static final int ABSORB = 0xFFFFD84A;
    private static final int TRAIL = 0xFFE9EDF5;
    private static final int TEXT = 0xFFE8E8F0, MUTED = 0xFF8D8D98, BG = 0xB0101214;
    private static final int GOOD = 0xFF7FFFA0, WARN = 0xFFFFD27F, BAD = 0xFFFF6E5A;

    private static KuroNametags instance;

    private final NumberSetting range = new NumberSetting("Range", "Max draw distance", 128.0, 16.0, 256.0, 8.0);
    private final BooleanSetting showName = new BooleanSetting("Name", true);
    private final BooleanSetting ping = new BooleanSetting("Ping", false);
    private final BooleanSetting distance = new BooleanSetting("Distance", false);
    private final BooleanSetting healthBar = new BooleanSetting("Health Bar", true);
    private final BooleanSetting healthText = new BooleanSetting("Health Text", true);
    private final BooleanSetting armor = new BooleanSetting("Armor", true);
    private final BooleanSetting held = new BooleanSetting("Held Items", false);
    private final BooleanSetting irlTime = new BooleanSetting("IRL Time", true);
    private final BooleanSetting clock24 = new BooleanSetting("24h Clock", true);
    private final BooleanSetting seconds = new BooleanSetting("Seconds", false);

    private final Map<UUID, Float> shown = new HashMap<>();   // sekinlashgan salomatlik, oq "damage" p/oqi sifatida chiziladi
    private final Set<UUID> seen = new HashSet<>();
    private String timeText = "";

    public KuroNametags() {
        super("Nametags", "Compact plates with name, health bar, armour and IRL time", Category.KURO);
        instance = this;
        addSetting(range);
        addSetting(showName);
        addSetting(ping);
        addSetting(distance);
        addSetting(healthBar);
        addSetting(healthText);
        addSetting(armor);
        addSetting(held);
        addSetting(irlTime);
        addSetting(clock24);
        addSetting(seconds);
    }

    @Override
    public void onDisable() { shown.clear(); seen.clear(); }

    @Override
    public void onRender2D(DrawContext g, float tickDelta) {
        if (mc.player == null || mc.world == null) return;
        ProjectionUtil.beginFrame(tickDelta);
        boolean cam = mc.getCameraEntity() != null && mc.getCameraEntity() != mc.player;
        Vec3d c = ProjectionUtil.eye(tickDelta);
        double max2 = range.getValue() * range.getValue();

        timeText = irlTime.isEnabled() ? currentTime() : "";     // frame boshida bir marta formatlanadi
        seen.clear();

        for (PlayerEntity p : mc.world.getPlayers()) {
            if (p == mc.player && !cam) continue;
            if (p.isSpectator()) continue;
            Vec3d pos = ProjectionUtil.lerpPos(p, tickDelta);
            double dx = pos.x - c.x, dy = pos.y - c.y, dz = pos.z - c.z;
            if (dx * dx + dy * dy + dz * dz > max2) continue;
            int[] o = ProjectionUtil.project(new Vec3d(pos.x, pos.y + p.getHeight() + 0.4, pos.z), tickDelta);
            if (o == null) continue;
            seen.add(p.getUuid());
            draw(g, p, o[0], o[1]);
        }
        shown.keySet().retainAll(seen);
    }

    private String currentTime() {
        LocalTime now = LocalTime.now();
        DateTimeFormatter f = clock24.isEnabled() ? (seconds.isEnabled() ? T24S : T24) : (seconds.isEnabled() ? T12S : T12);
        return now.format(f);
    }

    private void draw(DrawContext g, PlayerEntity p, int cx, int bottom) {
        TextRenderer tr = mc.textRenderer;
        if (tr == null) return;
        float hp = p.getHealth(), max = Math.max(1f, p.getMaxHealth()), abs = p.getAbsorptionAmount();
        float frac = MathHelper.clamp(hp / max, 0f, 1f);
        int accent = healthColor(frac);
        boolean friend = Friends.isFriend(p.getName().getString());

        // ---- matn qismlari
        String nm = showName.isEnabled() ? p.getName().getString() : "";
        String pg = "", ds = "";
        if (ping.isEnabled() && mc.getNetworkHandler() != null) {
            PlayerListEntry e = mc.getNetworkHandler().getPlayerListEntry(p.getUuid());
            if (e != null && e.getLatency() > 0) pg = (nm.isEmpty() ? "" : "  ") + e.getLatency() + "ms";
        }
        if (distance.isEnabled()) ds = (nm.isEmpty() && pg.isEmpty() ? "" : "  ") + Math.round(mc.player.distanceTo(p)) + "m";
        boolean nameRow = !(nm.isEmpty() && pg.isEmpty() && ds.isEmpty());

        String hs = "";
        if (healthText.isEnabled()) {
            hs = String.format(java.util.Locale.ROOT, "%.1f", hp);
            if (abs > 0) hs += String.format(java.util.Locale.ROOT, " +%.0f", abs);
        }
        String ts = timeText;
        String gap = (!hs.isEmpty() && !ts.isEmpty()) ? "   " : "";
        boolean infoRow = !hs.isEmpty() || !ts.isEmpty();

        int nameW = tr.getWidth(nm) + tr.getWidth(pg) + tr.getWidth(ds);
        int infoW = tr.getWidth(hs) + tr.getWidth(gap) + tr.getWidth(ts);
        int inner = Math.max(60, Math.max(nameW, infoW));
        int plateW = inner + 14;

        int rows = (nameRow ? 12 : 0) + (healthBar.isEnabled() ? 8 : 0) + (infoRow ? 11 : 0);
        if (rows == 0) return;
        int plateH = rows + 5;
        int y2 = bottom, y1 = y2 - plateH;
        int x1 = cx - plateW / 2, x2 = x1 + plateW;

        // panel: bg + border + accent chiziqcha
        g.fill(x1, y1, x2, y2, BG);
        g.fill(x1, y1, x2, y1 + 1, 0xFF3A3A44);
        g.fill(x1, y2 - 1, x2, y2, 0xFF3A3A44);
        g.fill(x1, y1, x1 + 3, y2, friend ? 0xFF7FD4FF : accent);
        int x = x1 + 8, y = y1 + 3;

        if (nameRow) {
            int nx = cx - nameW / 2 + 3;
            RenderUtils.drawText(g, tr, nm, nx, y, friend ? 0xFF7FD4FF : TEXT, false);
            nx += tr.getWidth(nm);
            if (!pg.isEmpty()) {
                int lat = parseLatency(pg);
                RenderUtils.drawText(g, tr, pg, nx, y, lat < 80 ? GOOD : lat < 150 ? WARN : BAD, false);
                nx += tr.getWidth(pg);
            }
            RenderUtils.drawText(g, tr, ds, nx, y, MUTED, false);
            y += 12;
        }

        if (healthBar.isEnabled()) {
            // sekinlashgan qiymat: shikastdan keyin pastga silliq tushadi, davolanishda bir zum to'ladi
            UUID id = p.getUuid();
            float prev = shown.getOrDefault(id, frac);
            float now = frac >= prev ? frac : prev + (frac - prev) * 0.12f;
            if (now - frac < 0.004f) now = frac;
            shown.put(id, now);

            int bx = x - 1, bw = inner + 0;
            if (now > frac) bar(g, bx, y, bw, 5, now, TRAIL);
            bar(g, bx, y, bw, 5, frac, accent);
            if (abs > 0) bar(g, bx, y, bw, 2, MathHelper.clamp(abs / max, 0f, 1f), ABSORB);
            y += 8;
        }

        if (infoRow) {
            int ix = cx - infoW / 2 + 3;
            RenderUtils.drawText(g, tr, hs, ix, y, accent, false);
            ix += tr.getWidth(hs) + tr.getWidth(gap);
            RenderUtils.drawText(g, tr, ts, ix, y, MUTED, false);
        }

        if (armor.isEnabled() || held.isEnabled()) drawItems(g, p, cx, y1 - 2);
    }

    private static void bar(DrawContext g, int x, int y, int w, int h, float frac, int color) {
        g.fill(x, y, x + w, y + h, 0xFF2A2A30);
        g.fill(x, y, x + (int) (w * MathHelper.clamp(frac, 0f, 1f)), y + h, color);
    }

    private static int parseLatency(String s) {
        try { return Integer.parseInt(s.replaceAll("[^0-9]", "")); } catch (NumberFormatException e) { return 0; }
    }

    /** Zirh (va ixtiyoriy qo'ldagi buyumlar) — plitka ustidagi qatorda chidamlilik chizig'i bilan. */
    private void drawItems(DrawContext g, PlayerEntity p, int cx, int bottom) {
        List<ItemStack> stacks = new ArrayList<>();
        if (armor.isEnabled()) for (EquipmentSlot s : ARMOR) stacks.add(p.getEquippedStack(s));
        if (held.isEnabled()) { stacks.add(p.getMainHandStack()); stacks.add(p.getOffHandStack()); }
        stacks.removeIf(ItemStack::isEmpty);
        if (stacks.isEmpty()) return;

        int slot = 20;
        int x = cx - stacks.size() * slot / 2 + (slot - 16) / 2;
        int y = bottom - 20;
        for (ItemStack st : stacks) {
            g.drawItem(st, x, y);
            if (st.isDamageable() && st.getMaxDamage() > 0) {
                float f = 1f - (float) st.getDamage() / st.getMaxDamage();
                bar(g, x, y + 17, 16, 2, f, healthColor(f));
            }
            x += slot;
        }
    }

    private static int healthColor(float frac) {
        frac = MathHelper.clamp(frac, 0f, 1f);
        return frac >= 0.5f ? GOOD : frac >= 0.25f ? WARN : BAD;
    }
}
