package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.RenderUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

public class BedPlates extends Module {
    public final NumberSetting range = new NumberSetting("Range", "Bed scan radius in blocks", 30.0, 5.0, 100.0, 1.0);
    public final ColorSetting color = new ColorSetting("Color", "Marker text color", 0xFFFF5555);
    private int bedCount = 0;
    private double nearest = -1.0;
    private int scanTicks = 0;

    public BedPlates() {
        super("BedPlates", "Counts bed blocks in range like a bed search", Category.UZNY11);
        addSetting(range);
        addSetting(color);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;
        try {
            if (scanTicks++ < 40) return;
            scanTicks = 0;
            BlockPos pp = mc.player.getBlockPos();
            int r = Math.min(48, range.getValue().intValue());
            int count = 0;
            double near = -1.0;
            for (int dx = -r; dx <= r; dx += 2) {
                for (int dy = -r / 2; dy <= r / 2; dy += 2) {
                    for (int dz = -r; dz <= r; dz += 2) {
                        BlockPos bp = pp.add(dx, dy, dz);
                        Identifier id = Registries.BLOCK.getId(mc.world.getBlockState(bp).getBlock());
                        if (id != null && id.getPath().contains("bed")) {
                            count++;
                            double d = Math.sqrt(bp.getSquaredDistance(pp));
                            if (near < 0 || d < near) near = d;
                        }
                    }
                }
            }
            bedCount = count;
            nearest = near;
        } catch (Throwable ignored) {}
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.textRenderer == null) return;
        try {
            String s = nearest < 0 ? "Beds: " + bedCount : "Beds: " + bedCount + " nearest " + String.format("%.1f", nearest) + "m";
            RenderUtils.drawText(context, mc.textRenderer, s, 4, 34, color.getValue(), true);
        } catch (Throwable ignored) {}
    }
}
