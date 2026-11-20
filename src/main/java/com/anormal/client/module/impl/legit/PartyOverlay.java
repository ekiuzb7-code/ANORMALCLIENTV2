package com.anormal.client.module.impl.legit;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.theme.ThemeManager;
import com.anormal.client.util.HudStyle;
import com.anormal.client.util.RenderUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Nearby-player list.
 *
 * <p>Modernised into the shared HUD panel: one row per player with their held item icon, name,
 * distance and a health bar coloured by how hurt they are, sorted nearest first.</p>
 */
public class PartyOverlay extends Module {
    public final NumberSetting posX = new NumberSetting("Pos X", "X Position on screen", 10.0, 0.0, 1920.0, 1.0);
    public final NumberSetting posY = new NumberSetting("Pos Y", "Y Position on screen", 300.0, 0.0, 1080.0, 1.0);
    public final NumberSetting scale = new NumberSetting("Scale", "HUD scale (0.05 = tiny, 2.0 = huge)", 1.0, 0.05, 2.0, 0.01);
    public final NumberSetting maxShown = new NumberSetting("Max Shown", "Max players listed", 6.0, 1.0, 20.0, 1.0);
    public final BooleanSetting showSelf = new BooleanSetting("Show Self", "Include yourself in list", false);

    public PartyOverlay() {
        super("PartyOverlay", "Displays nearby players health, distance and held item", Category.LEGIT);
        addSetting(posX);
        addSetting(posY);
        addSetting(scale);
        addSetting(maxShown);
        addSetting(showSelf);
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.player == null || mc.world == null || mc.textRenderer == null) return;
        try {
            List<PlayerEntity> nearby = new ArrayList<>();
            for (Entity e : mc.world.getEntities()) {
                if (e instanceof PlayerEntity p && p.isAlive()) {
                    if (p == mc.player && !showSelf.isEnabled()) continue;
                    nearby.add(p);
                }
            }
            nearby.sort(Comparator.comparingDouble(p -> p.distanceTo(mc.player)));

            int shown = Math.min(nearby.size(), maxShown.getValue().intValue());
            int accent = ThemeManager.getAccentColor();

            int w = 216;
            int rowH = 18;
            int h = 24 + Math.max(1, shown) * rowH + 4;
            int[] at = HudStyle.place(posX.getValue().intValue(), posY.getValue().intValue(), w, h);
            int x = at[0], y = at[1];

            HudStyle.panel(context, x, y, w, h, accent);
            HudStyle.title(context, mc.textRenderer, "Party", x + 10, y + 7);
            String count = nearby.size() + " nearby";
            int cw = mc.textRenderer.getWidth(count);
            RenderUtils.drawText(context, mc.textRenderer, count, x + w - 10 - cw, y + 7,
                    0xFF868A99, false);

            if (shown == 0) {
                RenderUtils.drawText(context, mc.textRenderer, "no players nearby", x + 10, y + 24,
                        0xFF5E6472, false);
                return;
            }

            int ly = y + 24;
            for (int i = 0; i < shown; i++) {
                PlayerEntity p = nearby.get(i);
                ItemStack held = p.getMainHandStack();
                if (!held.isEmpty()) {
                    context.drawItem(held, x + 8, ly + 1);
                } else {
                    RenderUtils.fill(context, x + 10, ly + 8, x + 24, ly + 9, 0x22FFFFFF);
                }

                float hp = p.getHealth() + p.getAbsorptionAmount();
                float maxHp = Math.max(1.0f, p.getMaxHealth());
                int pct = Math.round(Math.min(1.0f, hp / maxHp) * 100);
                int hpColor = HudStyle.durabilityColor(pct);

                RenderUtils.fill(context, x + 8, ly + 15, x + 26, ly + 17, 0x18FFFFFF);
                RenderUtils.fill(context, x + 8, ly + 15, x + 8 + (int) (18 * pct / 100.0f), ly + 17, hpColor);

                String name = HudStyle.trim(mc.textRenderer,
                        com.anormal.client.module.impl.client.NameProtect.replaceName(p), w - 108);
                RenderUtils.drawText(context, mc.textRenderer, name, x + 30, ly + 2, 0xFFEDEFF5, false);

                String dist = String.format(java.util.Locale.ROOT, "%.0fm", p.distanceTo(mc.player));
                int dw = mc.textRenderer.getWidth(dist);
                RenderUtils.drawText(context, mc.textRenderer, dist, x + w - 10 - dw, ly + 2,
                        0xFF868A99, false);

                String hpText = Math.round(hp) + " hp";
                RenderUtils.drawText(context, mc.textRenderer, hpText, x + 30, ly + 10,
                        hpColor, false);
                ly += rowH;
            }
        } catch (Throwable ignored) {}
    }
}
