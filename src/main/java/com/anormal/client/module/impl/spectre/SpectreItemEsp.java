package com.anormal.client.module.impl.spectre;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.MultiSelectSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.ProjectionUtil;
import com.anormal.client.util.RenderUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Spectre port: "Item ESP" — highlights dropped items with outlines, tracers and labels.
 *
 * <p>Two things made the old version look dead: it only matched the six whitelisted
 * keywords (so every normal drop was filtered out), and that filter treated an empty
 * selection as "highlight nothing". Now an empty selection means <i>every</i> drop is
 * highlighted, the entities come from a class query around the player instead of the full
 * entity list, and each label shows the stack name and amount.</p>
 */
public final class SpectreItemEsp extends Module {
    private static final int MAX_DRAWN = 250;

    public final NumberSetting sRange = addSetting(new NumberSetting("Range", "Highlight range", 128.0, 10.0, 512.0, 1.0));
    public final BooleanSetting sOutline = addSetting(new BooleanSetting("Outline", "Draw the item box outline", true));
    public final BooleanSetting sTracers = addSetting(new BooleanSetting("Tracers", "Draw tracer lines", false));
    public final BooleanSetting sLabels = addSetting(new BooleanSetting("Labels", "Show the item name and amount", true));
    public final MultiSelectSetting sItemKinds = addSetting(new MultiSelectSetting("Item Colors", "Which drops to highlight (nothing selected = all)", "Diamond", "Emerald", "Netherite", "Gold", "Iron", "Enchanted"));

    public SpectreItemEsp() {
        super("SpectreItemEsp", "Spectre port: highlights dropped items with outlines, tracers and labels.", Category.SPECTRE);
    }

    private static String idOf(ItemEntity item) {
        return Registries.ITEM.getId(item.getStack().getItem()).getPath();
    }

    /** No selection means "everything", otherwise match the selected material keywords. */
    private boolean wants(ItemEntity item) {
        if (!sItemKinds.anySelected()) return true;
        String id = idOf(item);
        if (id.contains("diamond") && sItemKinds.isSelected("Diamond")) return true;
        if (id.contains("emerald") && sItemKinds.isSelected("Emerald")) return true;
        if (id.contains("netherite") && sItemKinds.isSelected("Netherite")) return true;
        if (id.contains("gold") && sItemKinds.isSelected("Gold")) return true;
        if (id.contains("iron") && sItemKinds.isSelected("Iron")) return true;
        if (item.getStack().hasEnchantments() && sItemKinds.isSelected("Enchanted")) return true;
        return false;
    }

    private int colorOf(ItemEntity item) {
        String id = idOf(item);
        if (id.contains("netherite")) return 0xFF9B7BD4;
        if (id.contains("diamond")) return 0xFF4AEDD9;
        if (id.contains("emerald")) return 0xFF17DD62;
        if (id.contains("gold")) return 0xFFF5C542;
        if (id.contains("iron")) return 0xFFD8D8D8;
        if (item.getStack().hasEnchantments()) return 0xFFBC68F8;
        return 0xFFFFFFFF;
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (!isEnabled() || mc.world == null || mc.player == null) return;
        if (!sOutline.isEnabled() && !sTracers.isEnabled() && !sLabels.isEnabled()) return;

        double range = sRange.getAsDouble();
        Vec3d eye = ProjectionUtil.eye(tickDelta);
        Vec3d playerPos = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        Box area = mc.player.getBoundingBox().expand(range);

        List<ItemEntity> items = new ArrayList<>(mc.world.getEntitiesByClass(ItemEntity.class, area,
                item -> item != null && !item.isRemoved() && !item.getStack().isEmpty() && wants(item)));
        if (items.isEmpty()) return;
        items.sort(Comparator.comparingDouble(item -> item.squaredDistanceTo(playerPos)));
        if (items.size() > MAX_DRAWN) items = items.subList(0, MAX_DRAWN);

        int[] from = sTracers.isEnabled() ? ProjectionUtil.project(eye, tickDelta) : null;

        for (ItemEntity item : items) {
            ItemStack stack = item.getStack();
            int color = colorOf(item);
            Vec3d pos = ProjectionUtil.lerpPos(item, tickDelta);
            Box box = item.getBoundingBox().offset(
                    pos.x - item.getX(), pos.y - item.getY(), pos.z - item.getZ()).expand(0.12);

            if (sOutline.isEnabled()) {
                SpectreHoleEsp.drawBox(context, tickDelta, box, color, 0, true, false, 1);
            }

            if (from != null) {
                int[] to = ProjectionUtil.project(new Vec3d(pos.x, box.minY + box.getLengthY() * 0.5, pos.z), tickDelta);
                if (to != null) {
                    RenderUtils.drawLine(context, from[0], from[1], to[0], to[1], color, 1);
                }
            }

            if (sLabels.isEnabled()) {
                int[] top = ProjectionUtil.project(new Vec3d(pos.x, box.maxY + 0.35, pos.z), tickDelta);
                if (top == null) continue;
                String name = stack.getName().getString();
                if (stack.getCount() > 1) name = name + " x" + stack.getCount();
                int distance = (int) Math.round(Math.sqrt(item.squaredDistanceTo(playerPos)));
                String text = name + " \u00a77" + distance + "m";
                int w = mc.textRenderer.getWidth(text);
                RenderUtils.drawText(context, mc.textRenderer, text,
                        top[0] - w / 2, top[1] - 8, color, true);
            }
        }
    }
}
