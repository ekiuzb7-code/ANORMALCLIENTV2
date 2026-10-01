package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.RenderUtils;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;

public class CPSMod extends Module {
    public final BooleanSetting showLeft = new BooleanSetting("Show Left", "Show left click CPS", true);
    public final BooleanSetting showRight = new BooleanSetting("Show Right", "Show right click CPS", true);
    public final NumberSetting posX = new NumberSetting("Pos X", "HUD X position", 20.0, 0.0, 1920.0, 1.0);
    public final NumberSetting posY = new NumberSetting("Pos Y", "HUD Y position", 200.0, 0.0, 1080.0, 1.0);
    private final List<Long> left = new ArrayList<>();
    private final List<Long> right = new ArrayList<>();
    private boolean leftWas = false;
    private boolean rightWas = false;

    public CPSMod() {
        super("CPSMod", "HUD click-per-second counter", Category.UZNY11);
        addSetting(showLeft);
        addSetting(showRight);
        addSetting(posX);
        addSetting(posY);
    }

    @Override
    public void onTick() {
        if (mc.options == null) return;
        try {
            long now = System.currentTimeMillis();
            boolean l = mc.options.attackKey.isPressed();
            if (l && !leftWas) left.add(now);
            leftWas = l;
            boolean r = mc.options.useKey.isPressed();
            if (r && !rightWas) right.add(now);
            rightWas = r;
            left.removeIf(t -> now - t > 1000);
            right.removeIf(t -> now - t > 1000);
        } catch (Throwable ignored) {}
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.textRenderer == null) return;
        try {
            int x = posX.getValue().intValue();
            int y = posY.getValue().intValue();
            if (showLeft.isEnabled()) {
                RenderUtils.drawText(context, mc.textRenderer, "LMB " + left.size() + " CPS", x, y, 0xFFFFFFFF, true);
                y += 12;
            }
            if (showRight.isEnabled()) {
                RenderUtils.drawText(context, mc.textRenderer, "RMB " + right.size() + " CPS", x, y, 0xFFFFFFFF, true);
            }
        } catch (Throwable ignored) {}
    }
}
