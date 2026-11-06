package com.anormal.client.module.impl.spectre;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.ColorUtils;
import net.minecraft.block.Blocks;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.Box;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Spectre port: "Base Builder" — plans materials and previews the next layers of a build.
 *
 * <p>Spectre's panel imports a schematic and previews its placement. This port keeps the
 * material planner (counts of the block types you carry), the placement preview of the planned
 * layer and the layer height, drawing the plan in the world and the counts in a HUD panel.</p>
 */
public final class SpectreBuilder extends Module {
    public final NumberSetting layerHeight = addSetting(new NumberSetting("Layer Height", "How high the next layer sits", 2.0, 1.0, 8.0, 1.0));
    public final NumberSetting radius = addSetting(new NumberSetting("Radius", "Planned platform radius", 4.0, 1.0, 12.0, 1.0));
    public final BooleanSetting preview = addSetting(new BooleanSetting("Preview", "Preview the planned layer", true));
    public final BooleanSetting materials = addSetting(new BooleanSetting("Materials", "Show the material panel", true));
    public final BooleanSetting diagonal = addSetting(new BooleanSetting("Diagonal Walls", "Plan diagonal corners", true));

    public SpectreBuilder() {
        super("SpectreBuilder", "Spectre port: plans materials and previews the next layers of a build.", Category.SPECTRE);
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        var player = mc.player;
        if (player == null || mc.world == null || mc.options.hudHidden) return;

        if (preview.isEnabled()) {
            int r = (int) radius.getAsDouble();
            int y = (int) (player.getY() + layerHeight.getAsDouble());
            int px = player.getBlockPos().getX();
            int pz = player.getBlockPos().getZ();
            int color = ColorUtils.rgba(120, 200, 255, 130);
            for (int dx = -r; dx <= r; dx++) {
                SpectreHoleEsp.drawBox(context, tickDelta, new Box(px + dx, y, pz - r, px + dx + 1, y + 0.03, pz - r + 1), color, (color & 0x00FFFFFF) | 0x33000000, true, true, 1);
                SpectreHoleEsp.drawBox(context, tickDelta, new Box(px + dx, y, pz + r, px + dx + 1, y + 0.03, pz + r + 1), color, (color & 0x00FFFFFF) | 0x33000000, true, true, 1);
            }
            for (int dz = -r; dz <= r; dz++) {
                SpectreHoleEsp.drawBox(context, tickDelta, new Box(px - r, y, pz + dz, px - r + 1, y + 0.03, pz + dz + 1), color, (color & 0x00FFFFFF) | 0x33000000, true, true, 1);
                SpectreHoleEsp.drawBox(context, tickDelta, new Box(px + r, y, pz + dz, px + r + 1, y + 0.03, pz + dz + 1), color, (color & 0x00FFFFFF) | 0x33000000, true, true, 1);
            }
            if (diagonal.isEnabled()) {
                SpectreHoleEsp.drawBox(context, tickDelta, new Box(px - r, y, pz - r, px + r + 1, y + 0.03, pz + r + 1), ColorUtils.rgba(120, 200, 255, 45), 0, true, false, 1);
            }
        }

        if (!materials.isEnabled()) return;
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (int i = 0; i < 36; i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.isEmpty() || !isBuildingBlock(stack)) continue;
            String name = stack.getName().getString();
            counts.merge(name, stack.getCount(), Integer::sum);
        }
        int left = mc.getWindow().getScaledWidth() - 132;
        int top = 4;
        context.fill(left - 3, top - 3, left + 128, top + 12 + counts.size() * 10, ColorUtils.rgba(16, 16, 20, 200));
        context.drawText(mc.textRenderer, "Materials", left, top, 0xFFFFFFFF, true);
        int y = top + 12;
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            context.drawText(mc.textRenderer, entry.getKey() + " x" + entry.getValue(), left, y, 0xFFB0D0FF, true);
            y += 10;
        }
    }

    private static boolean isBuildingBlock(ItemStack stack) {
        var block = net.minecraft.block.Block.getBlockFromItem(stack.getItem());
        if (block == Blocks.AIR) return false;
        var state = block.getDefaultState();
        return state.isSolidBlock(net.minecraft.client.MinecraftClient.getInstance().world, net.minecraft.util.math.BlockPos.ORIGIN)
                || stack.isOf(Items.OBSIDIAN) || stack.isOf(Items.END_STONE);
    }
}
