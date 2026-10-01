package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.util.RenderUtils;
import net.minecraft.client.gui.DrawContext;

public class PotionSaver extends Module {
    public final BooleanSetting showEffects = new BooleanSetting("Show Effects", "Show active effect count on HUD", true);

    public PotionSaver() {
        super("PotionSaver", "Tracks active effects, display only", Category.UZNY11);
        addSetting(showEffects);
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (!showEffects.isEnabled() || mc.player == null || mc.textRenderer == null) return;
        try {
            int n = mc.player.getStatusEffects().size();
            RenderUtils.drawText(context, mc.textRenderer, "Effects: " + n, 4, 4, 0xFFFFFFFF, true);
        } catch (Throwable ignored) {}
    }
}
