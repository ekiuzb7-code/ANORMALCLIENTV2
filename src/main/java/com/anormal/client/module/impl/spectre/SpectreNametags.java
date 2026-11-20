package com.anormal.client.module.impl.spectre;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.ProjectionUtil;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Spectre port: "Nametags" — player nametags with held items, armour, health, ping and
 * distance.
 *
 * <p>Spectre draws a textured panel through its label renderer. This port keeps the content
 * (name, ping, health bar, armour durability, distance), the range, the tag cap and the
 * background alpha, and draws the panel in the Anormal 2D pass.</p>
 */
public final class SpectreNametags extends Module {
    private static final EquipmentSlot[] ARMOUR = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    public final BooleanSetting ping = addSetting(new BooleanSetting("Ping", "Show latency", true));
    public final BooleanSetting health = addSetting(new BooleanSetting("Health", "Show a health bar", true));
    public final BooleanSetting heldItems = addSetting(new BooleanSetting("Held Items", "Show the main hand and off hand item icons", true));
    public final BooleanSetting armor = addSetting(new BooleanSetting("Armor", "Show armour item icons", true));
    public final BooleanSetting durability = addSetting(new BooleanSetting("Durability", "Show durability bars and counts", true));
    public final NumberSetting range = addSetting(new NumberSetting("Range", "Maximum tag distance", 128.0, 8.0, 512.0, 1.0));
    public final NumberSetting maxTags = addSetting(new NumberSetting("Max Tags", "How many tags may draw at once", 64.0, 1.0, 64.0, 1.0));
    public final NumberSetting scale = addSetting(new NumberSetting("Scale", "Panel scale", 1.0, 0.5, 2.0, 0.1));
    public final NumberSetting backgroundAlpha = addSetting(new NumberSetting("Background Alpha", "Panel opacity", 0.45, 0.0, 1.0, 0.05));
    public final BooleanSetting shadow = addSetting(new BooleanSetting("Shadow", "Draw text with a shadow", true));

    public SpectreNametags() {
        super("SpectreNametags", "Spectre port: player nametags with held items, armour, health, ping and distance.", Category.SPECTRE);
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        var self = mc.player;
        if (self == null || mc.world == null || mc.options.hudHidden || mc.textRenderer == null) return;

        double max = range.getAsDouble();
        int budget = (int) maxTags.getAsDouble();
        List<PlayerEntity> players = new ArrayList<>();
        for (PlayerEntity other : mc.world.getPlayers()) {
            if (other == self || other.isRemoved() || other.isDead() || other.isSpectator()) continue;
            if (self.distanceTo(other) > max) continue;
            players.add(other);
        }
        if (players.isEmpty()) return;
        players.sort(Comparator.comparingDouble(self::distanceTo));
        if (players.size() > budget) players = players.subList(0, budget);

        // Far tags first so near tags are drawn on top.
        for (int i = players.size() - 1; i >= 0; i--) {
            try {
                drawTag(context, players.get(i), tickDelta);
            } catch (Throwable ignored) {
            }
        }
    }

    private void drawTag(DrawContext context, PlayerEntity other, float tickDelta) {
        var self = mc.player;
        Vec3d base = ProjectionUtil.lerpPos(other, tickDelta);
        int[] a = ProjectionUtil.project(base.add(0.0, other.getHeight() + (other.isSneaking() ? 0.2 : 0.35), 0.0), tickDelta);
        if (a == null) return;

        String name = other.getName().getString();
        int distance = (int) Math.round(self.distanceTo(other));
        int latency = 0;
        if (ping.isEnabled() && mc.getNetworkHandler() != null) {
            var entry = mc.getNetworkHandler().getPlayerListEntry(other.getUuid());
            latency = entry == null ? 0 : entry.getLatency();
        }

        float hp = other.getHealth() + other.getAbsorptionAmount();
        float fraction = Math.max(0.0F, Math.min(1.0F, other.getHealth() / Math.max(1.0F, other.getMaxHealth())));
        int hpColor = fraction > 0.6F ? 0xFF55FF55 : (fraction > 0.3F ? 0xFFFFFF55 : 0xFFFF5555);

        // Collect the item row.
        List<ItemStack> stacks = new ArrayList<>();
        if (armor.isEnabled()) for (EquipmentSlot slot : ARMOUR) stacks.add(other.getEquippedStack(slot));
        if (heldItems.isEnabled()) {
            stacks.add(other.getMainHandStack());
            stacks.add(other.getOffHandStack());
        }
        stacks.removeIf(ItemStack::isEmpty);

        String title = name + " §7" + distance + "m";
        String pingText = ping.isEnabled() ? latency + "ms" : "";
        String hpText = health.isEnabled() ? String.format("%.1f", hp) : "";
        int titleW = mc.textRenderer.getWidth(title);
        int pingW = pingText.isEmpty() ? 0 : mc.textRenderer.getWidth(pingText) + 6;
        int itemW = stacks.size() * 18;
        int width = Math.max(Math.max(titleW + pingW, itemW), 60) + 8;

        int height = 14;
        if (!stacks.isEmpty()) height += 19;
        if (health.isEnabled()) height += 8;

        float sf = (float) Math.max(0.5, Math.min(2.0, scale.getAsDouble()));
        var m = context.getMatrices();
        m.pushMatrix();
        m.translate((float) a[0], (float) a[1]);
        m.scale(sf, sf);
        try {
            int x = -width / 2;
            int y = -height - 2;
            int bg = ((int) (backgroundAlpha.getAsDouble() * 255.0) << 24) | 0x00101010;

            context.fill(x, y, x + width, y + height, bg);
            context.fill(x, y, x + width, y + 1, 0xAA00D0FF);

            boolean sh = shadow.isEnabled();
            context.drawText(mc.textRenderer, title, x + 4, y + 4, 0xFFEEEEEE, sh);
            if (!pingText.isEmpty()) {
                int pc = latency < 80 ? 0xFF55FF55 : (latency < 160 ? 0xFFFFFF55 : 0xFFFF5555);
                context.drawText(mc.textRenderer, pingText, x + width - 4 - mc.textRenderer.getWidth(pingText), y + 4, pc, sh);
            }

            int cy = y + 14;
            if (!stacks.isEmpty()) {
                int ix = -itemW / 2 + 1;
                for (ItemStack st : stacks) {
                    context.drawItem(st, ix, cy + 1);
                    if (durability.isEnabled()) {
                        if (st.isDamageable() && st.getMaxDamage() > 0) {
                            float f = Math.max(0.0F, Math.min(1.0F, 1.0F - (float) st.getDamage() / st.getMaxDamage()));
                            int col = f > 0.6F ? 0xFF55FF55 : (f > 0.3F ? 0xFFFFFF55 : 0xFFFF5555);
                            context.fill(ix + 1, cy + 15, ix + 15, cy + 17, 0xFF000000);
                            context.fill(ix + 1, cy + 15, ix + 1 + Math.max(1, Math.round(14 * f)), cy + 17, col);
                        }
                        if (st.getCount() > 1) {
                            String c = String.valueOf(st.getCount());
                            context.drawText(mc.textRenderer, c, ix + 17 - mc.textRenderer.getWidth(c), cy + 9, 0xFFFFFFFF, true);
                        }
                    }
                    ix += 18;
                }
                cy += 19;
            }

            if (health.isEnabled()) {
                int bx0 = x + 3, bx1 = x + width - 3;
                context.fill(bx0, cy, bx1, cy + 5, 0x99000000);
                context.fill(bx0, cy, bx0 + Math.max(0, (int) ((bx1 - bx0) * fraction)), cy + 5, hpColor);
                // HP number centred over the bar at half size.
                m.pushMatrix();
                m.translate((float) ((bx0 + bx1) / 2.0), (float) cy + 0.5f);
                m.scale(0.5F, 0.5F);
                context.drawText(mc.textRenderer, hpText, -mc.textRenderer.getWidth(hpText) / 2, 0, 0xFFFFFFFF, true);
                m.popMatrix();
            }
        } finally {
            m.popMatrix();
        }
    }
}
