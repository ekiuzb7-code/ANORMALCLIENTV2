package com.anormal.client.module.impl.render;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.theme.ThemeManager;
import com.anormal.client.util.ColorUtils;
import com.anormal.client.util.HudStyle;
import com.anormal.client.util.RenderUtils;
import net.minecraft.block.BlockState;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.LightType;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * Spawn-proof overlay: marks every block inside a radius where a hostile mob could spawn.
 *
 * <p>A spot counts when the floor block is solid, the two blocks above it are air and the light
 * level is at or below the threshold (mobs need light 0 at the block they stand on in the
 * overworld). The area is scanned in slices - a fixed number of checks per tick - so a 24 block
 * radius does not freeze the game; the finished result is cached and only rebuilt when the
 * player moves away or the refresh timer runs out.</p>
 */
public class SpawnProofOverlay extends Module {

    public final NumberSetting radius = new NumberSetting("Radius", "Horizontal scan radius in blocks", 16.0, 4.0, 48.0, 1.0);
    public final NumberSetting yRange = new NumberSetting("Y Range", "How many blocks up and down from your feet are scanned", 6.0, 2.0, 24.0, 1.0);
    public final NumberSetting lightThreshold = new NumberSetting("Light Threshold", "Light level at or below this is treated as spawnable (vanilla is 0)", 0.0, 0.0, 7.0, 1.0);
    public final NumberSetting scanBudget = new NumberSetting("Scan Budget", "Block checks per tick (higher = faster but heavier)", 2500.0, 200.0, 20000.0, 100.0);
    public final NumberSetting maxMarkers = new NumberSetting("Max Markers", "Upper limit of drawn blocks", 900.0, 50.0, 4000.0, 50.0);
    public final NumberSetting refreshSeconds = new NumberSetting("Refresh (s)", "Seconds between full rescans", 3.0, 1.0, 30.0, 1.0);
    public final BooleanSetting fill = new BooleanSetting("Fill", "Filled marker", true);
    public final BooleanSetting outline = new BooleanSetting("Outline", "Outlined marker", true);
    public final BooleanSetting requireSkyAccess = new BooleanSetting("Require Sky Access",
            "Also require the spot to see the sky (so torch-lit caves are ignored)", false);
    public final BooleanSetting showPanel = new BooleanSetting("Show Panel", "Shows the spot count card", true);
    public final NumberSetting fillOpacity = new NumberSetting("Fill Opacity", "Fill alpha (0-255)", 60.0, 5.0, 255.0, 5.0);
    public final ColorSetting fillColor = new ColorSetting("Fill Color", "Marker fill color", ColorUtils.rgba(255, 90, 60, 255));
    public final ColorSetting outlineColor = new ColorSetting("Outline Color", "Marker outline color", ColorUtils.rgba(255, 130, 60, 255));
    public final NumberSetting posX = new NumberSetting("Pos X", "X Position on screen", 6.0, 0.0, 1920.0, 1.0);
    public final NumberSetting posY = new NumberSetting("Pos Y", "Y Position on screen", 400.0, 0.0, 1080.0, 1.0);

    private final Set<BlockPos> spots = new HashSet<>();
    private final ArrayDeque<BlockPos> queue = new ArrayDeque<>();
    private BlockPos origin;
    private int refreshTicks;

    public SpawnProofOverlay() {
        super("SpawnProofOverlay", "Marks blocks where hostile mobs can spawn so you can light them up", Category.RENDER);
        addSetting(radius);
        addSetting(yRange);
        addSetting(lightThreshold);
        addSetting(scanBudget);
        addSetting(maxMarkers);
        addSetting(refreshSeconds);
        addSetting(fill);
        addSetting(outline);
        addSetting(requireSkyAccess);
        addSetting(showPanel);
        addSetting(fillOpacity);
        addSetting(fillColor);
        addSetting(outlineColor);
        addSetting(posX);
        addSetting(posY);
    }

    @Override
    public void onEnable() {
        origin = null;
        queue.clear();
        spots.clear();
        refreshTicks = 0;
    }

    @Override
    public void onDisable() {
        queue.clear();
        spots.clear();
        origin = null;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;
        BlockPos here = mc.player.getBlockPos();
        int limit = (int) refreshSeconds.getValue().intValue() * 20;
        boolean moved = origin == null || here.getSquaredDistance(origin) > 16.0;
        if (moved || refreshTicks <= 0) {
            rebuild(here);
            origin = here;
            refreshTicks = Math.max(20, limit);
        } else {
            refreshTicks--;
        }

        int budget = (int) scanBudget.getValue().intValue();
        while (budget > 0 && !queue.isEmpty()) {
            BlockPos column = queue.poll();
            scanColumn(column);
            budget -= (int) (yRange.getValue() * 2 + 1);
        }
    }

    private void rebuild(BlockPos here) {
        queue.clear();
        spots.clear();
        int r = (int) radius.getValue().intValue();
        int rSq = r * r;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (dx * dx + dz * dz > rSq) continue;
                queue.add(new BlockPos(here.getX() + dx, here.getY(), here.getZ() + dz));
            }
        }
    }

    private void scanColumn(BlockPos column) {
        if (mc.world == null) return;
        int range = (int) yRange.getValue().intValue();
        int minY = mc.world.getBottomY() + 1;
        int maxY = mc.world.getTopYInclusive() - 2;
        for (int dy = -range; dy <= range; dy++) {
            int y = column.getY() + dy;
            if (y < minY || y > maxY) continue;
            BlockPos floor = new BlockPos(column.getX(), y, column.getZ());
            if (canSpawn(floor)) spots.add(floor);
        }
    }

    private boolean canSpawn(BlockPos floor) {
        try {
            if (mc.world == null) return false;
            BlockState floorState = mc.world.getBlockState(floor);
            if (!floorState.isSolidBlock(mc.world, floor)) return false;

            BlockPos feet = floor.up();
            BlockPos head = feet.up();
            if (!mc.world.getBlockState(feet).isAir()) return false;
            if (!mc.world.getBlockState(head).isAir()) return false;

            int threshold = (int) lightThreshold.getValue().intValue();
            int blockLight = mc.world.getLightLevel(LightType.BLOCK, feet);
            if (blockLight > threshold) return false;
            int skyLight = mc.world.getLightLevel(LightType.SKY, feet);
            if (skyLight > threshold) return false;
            if (requireSkyAccess.isEnabled() && !mc.world.isSkyVisible(feet)) return false;
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.player == null || mc.world == null) return;
        int outlineCol = outlineColor.getColor();
        int fillCol = withAlpha(fillColor.getColor(), (int) fillOpacity.getValue().intValue());
        int drawn = 0;
        int limit = (int) maxMarkers.getValue().intValue();
        try {
            Iterator<BlockPos> it = spots.iterator();
            while (it.hasNext() && drawn < limit) {
                BlockPos pos = it.next();
                int[] box = RenderUtils.projectBlockBounds(pos, tickDelta);
                if (box == null) continue;
                if (fill.isEnabled()) {
                    RenderUtils.fill(context, box[0], box[1], box[2], box[3], fillCol);
                }
                if (outline.isEnabled()) {
                    RenderUtils.drawBorder(context, box[0], box[1], box[2], box[3], 1, outlineCol);
                }
                drawn++;
            }
        } catch (Throwable ignored) {
        }

        if (!showPanel.isEnabled() || mc.textRenderer == null) return;
        int w = 150, h = 42;
        int[] at = HudStyle.place(posX.getValue().intValue(), posY.getValue().intValue(), w, h);
        int x = at[0], y = at[1];
        int accent = ThemeManager.getAccentColor();
        HudStyle.panel(context, x, y, w, h, accent);
        HudStyle.title(context, mc.textRenderer, "Spawn Proof", x + 10, y + 7);
        String count = spots.size() + " spots";
        int cw = mc.textRenderer.getWidth(count);
        RenderUtils.drawText(context, mc.textRenderer, count, x + w - 10 - cw, y + 7,
                spots.isEmpty() ? 0xFF4ADE80 : 0xFFF87171, true);
        String state = queue.isEmpty() ? "scan complete" : "scanning " + queue.size();
        RenderUtils.drawText(context, mc.textRenderer, state, x + 10, y + 20, 0xFF8A8A96, false);
        String hint = "light <= " + lightThreshold.getValue().intValue()
                + ", r" + radius.getValue().intValue();
        RenderUtils.drawText(context, mc.textRenderer, hint, x + 10, y + 30, 0xFF5E6470, false);
    }

    private int withAlpha(int color, int alpha) {
        int a = Math.max(0, Math.min(255, alpha));
        return (a << 24) | (color & 0x00FFFFFF);
    }
}
