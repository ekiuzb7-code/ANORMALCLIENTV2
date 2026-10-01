package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.RenderUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;

public class PropHunt extends Module {
    public final NumberSetting range = new NumberSetting("Range", "Scan range in blocks", 30.0, 5.0, 100.0, 1.0);

    public PropHunt() {
        super("PropHunt", "Marks players holding block items", Category.UZNY11);
        addSetting(range);
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.player == null || mc.world == null || mc.textRenderer == null) return;
        try {
            int y = 60;
            for (PlayerEntity p : mc.world.getPlayers()) {
                if (p == null || p == mc.player || !p.isAlive()) continue;
                if (mc.player.distanceTo(p) > range.getValue()) continue;
                if (p.getMainHandStack().getItem() instanceof BlockItem || p.getOffHandStack().getItem() instanceof BlockItem) {
                    RenderUtils.drawText(context, mc.textRenderer, "[Prop] " + p.getName().getString(), 4, y, 0xFF55FF55, true);
                    y += 12;
                    if (y > 300) break;
                }
            }
        } catch (Throwable ignored) {}
    }
}
