package com.anormal.client.module.impl.kuro;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
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
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/**
 * kuro {@code PlayerInfo} port: o'yinchilar boshining ustida suzan plitka —
 * nom, ping, salomatlik va zirhning qolgan chidamliligi. Masofa va qo'ldagi
 * buyumlar sozlab yoqiladi. kuro'daki Look.panel / SdfText / Look.bar Anormal
 * uslublariga moslandi (KuroStopwash'dagi panel chizilishi va
 * RenderUtils.drawText).
 */
public class KuroPlayerInfo extends Module {
    private static final EquipmentSlot[] ARMOR = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };

    private static final int TEXT = 0xFFE8E8F0, MUTED = 0xFF8D8D98;
    private static final int BG = 0xB0101214;
    private static final int GOOD = 0xFF7FFFA0, WARN = 0xFFFFD27F, BAD = 0xFFFF6E5A;

    private final NumberSetting range = new NumberSetting("Range", "Max draw distance", 128.0, 16.0, 256.0, 8.0);
    private final BooleanSetting showName = new BooleanSetting("Name", true);
    private final BooleanSetting ping = new BooleanSetting("Ping", true);
    private final BooleanSetting distance = new BooleanSetting("Distance", false);
    private final BooleanSetting held = new BooleanSetting("Held items", false);

    public KuroPlayerInfo() {
        super("Player Info", "Name, ping, health and armour above players' heads", Category.KURO);
        addSetting(range);
        addSetting(showName);
        addSetting(ping);
        addSetting(distance);
        addSetting(held);
    }

    @Override
    public void onRender2D(DrawContext g, float tickDelta) {
        if (mc.player == null || mc.world == null) return;
        ProjectionUtil.beginFrame(tickDelta);
        boolean cam = mc.getCameraEntity() != null && mc.getCameraEntity() != mc.player;
        Vec3d c = ProjectionUtil.eye(tickDelta);
        double max2 = range.getValue() * range.getValue();
        for (PlayerEntity p : mc.world.getPlayers()) {
            if (p == mc.player && !cam) continue;
            if (p.isSpectator()) continue;
            Vec3d pos = ProjectionUtil.lerpPos(p, tickDelta);
            double dx = pos.x - c.x, dy = pos.y - c.y, dz = pos.z - c.z;
            if (dx * dx + dy * dy + dz * dz > max2) continue;
            int[] o = ProjectionUtil.project(new Vec3d(pos.x, pos.y + p.getHeight() + 0.9, pos.z), tickDelta);
            if (o == null) continue;
            drawTag(g, p, o[0], o[1], tickDelta);
        }
    }

    private void drawTag(DrawContext g, PlayerEntity p, int cx, int bottom, float tickDelta) {
        TextRenderer tr = mc.textRenderer;
        if (tr == null) return;

        List<ItemStack> stacks = new ArrayList<>();
        for (EquipmentSlot s : ARMOR) stacks.add(p.getEquippedStack(s));
        if (held.isEnabled()) { stacks.add(p.getMainHandStack()); stacks.add(p.getOffHandStack()); }
        stacks.removeIf(ItemStack::isEmpty);

        int latency = 0;
        if (ping.isEnabled() && mc.getNetworkHandler() != null) {
            PlayerListEntry e = mc.getNetworkHandler().getPlayerListEntry(p.getUuid());
            if (e != null) latency = Math.max(0, e.getLatency());
        }
        boolean nameLine = showName.isEnabled() || latency > 0 || distance.isEnabled();

        int total = (nameLine ? 15 : 0) + 11 + (stacks.isEmpty() ? 0 : 30);
        int y = bottom - total - 2;

        if (nameLine) {
            // nom o'q rangda, so'ng ping (sifatga qarab yashil/sariq/qizil) va masofa kulrang, bitta qatorda
            String name = showName.isEnabled() ? p.getName().getString() : "";
            String pg = latency > 0 ? (name.isEmpty() ? "" : "  ") + latency + "ms" : "";
            String ds = distance.isEnabled()
                    ? (name.isEmpty() && pg.isEmpty() ? "" : "  ") + Math.round(mc.player.distanceTo(p)) + "m" : "";
            int lw = tr.getWidth(name) + tr.getWidth(pg) + tr.getWidth(ds);
            int x = cx - lw / 2;
            g.fill(x - 9, y - 3, x + lw + 5, y + 10, BG);
            g.fill(x - 9, y - 3, x - 6, y + 10, healthColor(p.getHealth() / Math.max(1f, p.getMaxHealth())));
            RenderUtils.drawText(g, tr, name, x, y, TEXT, false);
            x += tr.getWidth(name);
            RenderUtils.drawText(g, tr, pg, x, y, latency < 80 ? GOOD : latency < 150 ? WARN : BAD, false);
            x += tr.getWidth(pg);
            RenderUtils.drawText(g, tr, ds, x, y, MUTED, false);
            y += 15;
        }

        float hp = p.getHealth(), max = Math.max(1f, p.getMaxHealth()), abs = p.getAbsorptionAmount();
        String hs = String.format(java.util.Locale.ROOT, "%.1f/%.0f (%d%%)",
                hp, max, Math.round(hp / max * 100f));
        if (abs > 0) hs += String.format(java.util.Locale.ROOT, " +%.0f", abs);
        center(g, tr, hs, cx, y, healthColor(hp / max));
        y += 11;

        int slot = 20;
        int x = cx - stacks.size() * slot / 2 + (slot - 16) / 2;
        for (ItemStack st : stacks) {
            g.drawItem(st, x, y);
            if (st.isDamageable() && st.getMaxDamage() > 0) {
                float frac = 1f - (float) st.getDamage() / st.getMaxDamage();
                int col = healthColor(frac);
                g.fill(x, y + 17, x + 16, y + 19, 0xFF33333A);
                g.fill(x, y + 17, x + (int) (16 * frac), y + 19, col);
                center(g, tr, String.valueOf(Math.round(frac * 100f)), x + 8, y + 21, col);
            }
            x += slot;
        }
    }

    private static int healthColor(float frac) {
        frac = Math.max(0f, Math.min(1f, frac));
        return frac >= 0.5f ? GOOD : frac >= 0.25f ? WARN : BAD;
    }

    private static void center(DrawContext g, TextRenderer tr, String s, int cx, int y, int color) {
        RenderUtils.drawText(g, tr, s, cx - tr.getWidth(s) / 2, y, color, true);
    }
}
