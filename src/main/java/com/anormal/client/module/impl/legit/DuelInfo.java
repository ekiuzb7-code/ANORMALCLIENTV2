package com.anormal.client.module.impl.legit;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.theme.ThemeManager;
import com.anormal.client.util.HudStyle;
import com.anormal.client.util.RenderUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.LivingEntity;

/**
 * Hit / potion advantage tracker for 1v1s.
 *
 * <p>Modernised into a duel card: opponent name in the header, the hit advantage as the big signed
 * number, a comparison bar and the raw hit counts underneath. The old panel also showed a potion
 * "difference" that was always negative because the opponent's potion count was never tracked, so
 * the card now reports your own potions used instead of inventing a comparison.</p>
 */
public class DuelInfo extends Module {
    public final NumberSetting posX = new NumberSetting("Pos X", "X Position on screen", 10.0, 0.0, 1920.0, 1.0);
    public final NumberSetting posY = new NumberSetting("Pos Y", "Y Position on screen", 200.0, 0.0, 1080.0, 1.0);
    public final NumberSetting scale = new NumberSetting("Scale", "HUD scale (0.05 = tiny, 2.0 = huge)", 1.0, 0.05, 2.0, 0.01);

    private String opponentName = "\u2014";
    private int hitsGiven = 0;
    private int hitsTaken = 0;
    private int potsUsed = 0;
    private long lastReset = 0;
    private boolean wasHurt = false;
    private int swingCooldown = 0;

    public DuelInfo() {
        super("DuelInfo", "Tracks hit and potion advantages during 1v1 duels", Category.LEGIT);
        addSetting(posX);
        addSetting(posY);
        addSetting(scale);
    }

    @Override
    public void onEnable() {
        hitsGiven = 0;
        hitsTaken = 0;
        potsUsed = 0;
        swingCooldown = 0;
        wasHurt = false;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;
        try {
            long now = System.currentTimeMillis();

            if (mc.targetedEntity instanceof LivingEntity living && living != mc.player) {
                String name = living.getName().getString();
                if (!name.equals(opponentName)) {
                    opponentName = name;
                    hitsGiven = 0;
                    hitsTaken = 0;
                    potsUsed = 0;
                    lastReset = now;
                }
            }

            if (swingCooldown > 0) swingCooldown--;
            if (mc.options.attackKey.isPressed() && mc.targetedEntity instanceof LivingEntity
                    && swingCooldown == 0) {
                hitsGiven++;
                swingCooldown = 6;
            }

            boolean hurt = mc.player.hurtTime > 0;
            if (hurt && !wasHurt) hitsTaken++;
            wasHurt = hurt;

            if (mc.options.useKey.isPressed() && now - lastReset > 1000) {
                String main = mc.player.getMainHandStack().getItem().toString();
                String off = mc.player.getOffHandStack().getItem().toString();
                if (main.contains("potion") || off.contains("potion")) {
                    potsUsed++;
                    lastReset = now;
                }
            }
        } catch (Throwable ignored) {}
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.textRenderer == null) return;
        try {
            int diff = hitsGiven - hitsTaken;
            int diffColor = diff > 0 ? 0xFF4ADE80 : (diff < 0 ? 0xFFF87171 : 0xFF9AA0AE);
            int accent = ThemeManager.getAccentColor();

            int w = 204, h = 58;
            int[] at = HudStyle.place(posX.getValue().intValue(), posY.getValue().intValue(), w, h);
            int x = at[0], y = at[1];

            HudStyle.panel(context, x, y, w, h, accent);
            HudStyle.title(context, mc.textRenderer, "Duel", x + 10, y + 7);
            String opp = HudStyle.trim(mc.textRenderer, opponentName, w - 74);
            int ow = mc.textRenderer.getWidth(opp);
            RenderUtils.drawText(context, mc.textRenderer, opp, x + w - 10 - ow, y + 7,
                    0xFFC7CBD6, false);

            String diffText = (diff > 0 ? "+" : "") + diff;
            int dw = mc.textRenderer.getWidth(diffText);
            RenderUtils.drawText(context, mc.textRenderer, diffText, x + 10, y + 20, diffColor, true);
            RenderUtils.drawText(context, mc.textRenderer, "hit lead", x + 14 + mc.textRenderer.getWidth(diffText),
                    y + 21, 0xFF6E7482, false);

            // Comparison bar: how much of the exchange you are winning.
            int total = hitsGiven + hitsTaken;
            float frac = total == 0 ? 0.5f : hitsGiven / (float) total;
            HudStyle.bar(context, x + 10, y + 33, w - 20, 4, frac, diffColor);

            String hits = "hits " + hitsGiven + " / " + hitsTaken;
            RenderUtils.drawText(context, mc.textRenderer, hits, x + 10, y + 41, 0xFF868A99, false);
            String pots = "pots " + potsUsed;
            int pw = mc.textRenderer.getWidth(pots);
            RenderUtils.drawText(context, mc.textRenderer, pots, x + w - 10 - pw, y + 41,
                    0xFF868A99, false);
        } catch (Throwable ignored) {}
    }
}
