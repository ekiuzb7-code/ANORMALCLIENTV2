package com.anormal.client.module.impl.spectre;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.BlockScanner;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.util.HashMap;
import java.util.Map;

/**
 * Spectre port: "Light Debug" — highlights light sources around you.
 *
 * <p>The old version marked every <i>solid block</i> that happened to be lit, which flooded
 * the screen with thousands of boxes and still showed nothing useful. Spectre's light layer
 * marks the emitters themselves, so this port scans for blocks with a luminance at or above
 * "Min Light" and keeps the result stable: the sweep fills a pending map and swaps it in when
 * the full sphere has been walked, instead of clearing the markers every single tick.</p>
 */
public final class SpectreLightEsp extends Module {
    private static final int MAX_BOXES = 12000;
    /** Blocks looked up per tick; one r=16 sphere is ~36k cells, so it takes two ticks. */
    private static final int SCAN_BUDGET = 18000;

    public final NumberSetting sRadius = addSetting(new NumberSetting("Render Radius", "Scan radius", 24.0, 1.0, 48.0, 1.0));
    public final NumberSetting sMinLight = addSetting(new NumberSetting("Min Light", "Minimum emitted light level to draw", 5.0, 1.0, 15.0, 1.0));
    public final BooleanSetting sDenoise = addSetting(new BooleanSetting("Denoise", "Merge neighbouring light boxes", true));
    public final ColorSetting sColor = addSetting(new ColorSetting("Color", "Box color", 0x80FF80, false));

    private final Map<Long, Integer> renderData = new HashMap<>();
    private final Map<Long, Integer> pending = new HashMap<>();
    private final BlockScanner scanner = new BlockScanner();
    private final BlockPos.Mutable scratch = new BlockPos.Mutable();
    private net.minecraft.client.world.ClientWorld sessionLevel;
    private int lastRadius = -1;

    public SpectreLightEsp() {
        super("SpectreLightEsp", "Spectre port: highlights light sources around you.", Category.SPECTRE);
    }

    @Override
    public void onTick() {
        var level = mc.world;
        if (level == null || mc.player == null) {
            resetScan();
            return;
        }
        if (level != sessionLevel) {
            resetScan();
            sessionLevel = level;
        }

        int radius = Math.max(1, (int) Math.round(sRadius.getAsDouble()));
        if (radius != lastRadius) {
            lastRadius = radius;
            pending.clear();
            scanner.reset();
        }
        int minLight = Math.max(1, Math.round((float) sMinLight.getAsDouble()));
        int color = sColor.getValue() | (0xA8 << 24);

        scanner.scan(level, mc.player.getBlockX(), mc.player.getBlockY(), mc.player.getBlockZ(), radius,
                SCAN_BUDGET, scratch, null, new BlockScanner.Callback() {
                    @Override
                    public void accept(BlockPos.Mutable pos, net.minecraft.block.BlockState state) {
                        if (state.isAir()) return;
                        int luminance = state.getLuminance();
                        if (luminance < minLight) return;
                        if (pending.size() >= MAX_BOXES) return;
                        pending.put(pos.asLong(), color);
                    }

                    @Override
                    public void onSweepComplete() {
                        // Full sphere walked: publish and start collecting the next one.
                        renderData.clear();
                        renderData.putAll(pending);
                        pending.clear();
                    }
                });
    }

    private void resetScan() {
        sessionLevel = null;
        lastRadius = -1;
        renderData.clear();
        pending.clear();
        scanner.reset();
    }

    @Override
    public void onDisable() {
        resetScan();
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (!isEnabled() || mc.player == null || renderData.isEmpty()) return;
        int color = sColor.getValue() | (0xA8 << 24);
        int fill = (color & 0x00FFFFFF) | 0x30000000;
        if (!sDenoise.isEnabled()) {
            for (long k : renderData.keySet()) {
                SpectreHoleEsp.drawBox(context, tickDelta, boxAt(k), color, fill, true, true, 1);
            }
            return;
        }
        // Denoise: consecutive lit blocks on the same row become one box instead of many.
        for (long k : renderData.keySet()) {
            int x = BlockPos.unpackLongX(k);
            int y = BlockPos.unpackLongY(k);
            int z = BlockPos.unpackLongZ(k);
            if (renderData.containsKey(BlockPos.asLong(x - 1, y, z))) continue;
            int run = 1;
            while (run < 16 && renderData.containsKey(BlockPos.asLong(x + run, y, z))) run++;
            Box merged = new Box(x, y, z, x + run, y + 1, z + 1);
            SpectreHoleEsp.drawBox(context, tickDelta, merged, color, fill, true, true, 1);
        }
    }

    private static Box boxAt(long key) {
        int x = BlockPos.unpackLongX(key);
        int y = BlockPos.unpackLongY(key);
        int z = BlockPos.unpackLongZ(key);
        return new Box(x, y, z, x + 1, y + 1, z + 1);
    }
}
