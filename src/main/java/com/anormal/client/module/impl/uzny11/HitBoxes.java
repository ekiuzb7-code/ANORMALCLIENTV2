package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.RenderUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerEntity;

import java.util.ArrayList;
import java.util.List;

public class HitBoxes extends Module {
    public final NumberSetting expand = new NumberSetting("Expand", "Marker box width factor", 2.0, 1.0, 5.0, 0.5);
    public final NumberSetting range = new NumberSetting("Range", "Scan range in blocks", 30.0, 5.0, 100.0, 1.0);

    public HitBoxes() {
        super("HitBoxes", "Visual target markers only, cannot change server hitboxes", Category.UZNY11);
        addSetting(expand);
        addSetting(range);
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.player == null || mc.world == null || mc.textRenderer == null) return;
        try {
            List<String> lines = new ArrayList<>();
            for (PlayerEntity p : mc.world.getPlayers()) {
                if (p == null || p == mc.player || !p.isAlive()) continue;
                double d = mc.player.distanceTo(p);
                if (d > range.getValue()) continue;
                lines.add(p.getName().getString() + " " + String.format("%.1f", d) + "m");
            }
            int y = 20;
            int pad = 4 + (int) (expand.getValue() * 4.0);
            for (String s : lines) {
                int w = mc.textRenderer.getWidth(s) + pad * 2;
                context.fill(4, y - 2, 4 + w, y + 10, 0x88000000);
                RenderUtils.drawText(context, mc.textRenderer, s, 4 + pad, y, 0xFFFF5555, true);
                y += 14;
                if (y > 300) break;
            }
        } catch (Throwable ignored) {}
    }
}
