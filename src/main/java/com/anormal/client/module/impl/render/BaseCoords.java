package com.anormal.client.module.impl.render;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.ColorUtils;
import com.anormal.client.util.ProjectionUtil;
import com.anormal.client.util.RenderUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * Saves one base position with a recall.
 *
 * <p>Besides the HUD line it now draws the base in the world (3D box + optional tracer) and the HUD
 * entry is a rounded chip instead of bare unpositioned text, so it matches the rest of the Render
 * markers.</p>
 */
public class BaseCoords extends Module {
    public final BooleanSetting saveHere = new BooleanSetting("Save Here", "Save current position as base", false);
    public final BooleanSetting recallChat = new BooleanSetting("Recall Chat", "Print saved base in chat", false);
    public final BooleanSetting showHere = new BooleanSetting("Show Here", "Show the saved base on screen", true);
    public final BooleanSetting box = new BooleanSetting("Box", "Draw the base as a 3D box in the world", true);
    public final BooleanSetting tracer = new BooleanSetting("Tracer", "Line from crosshair to the base", false);
    public final NumberSetting posX = new NumberSetting("Pos X", "HUD text X", 10.0, 0.0, 1920.0, 1.0);
    public final NumberSetting posY = new NumberSetting("Pos Y", "HUD text Y", 40.0, 0.0, 1080.0, 1.0);
    public final ColorSetting color = new ColorSetting("Color", "Base marker color", ColorUtils.rgba(85, 255, 85, 255));

    private double bx, by, bz;
    private String dim = "";
    private boolean has = false;

    public BaseCoords() {
        super("BaseCoords", "Saves one base position with recall", Category.RENDER);
        addSetting(saveHere);
        addSetting(recallChat);
        addSetting(showHere);
        addSetting(box);
        addSetting(tracer);
        addSetting(posX);
        addSetting(posY);
        addSetting(color);
    }

    private String curDim() {
        try {
            return mc.world.getRegistryKey().getValue().getPath();
        } catch (Throwable ignored) {
            return "";
        }
    }

    @Override
    public void onTick() {
        if (saveHere.isEnabled()) {
            try { saveHere.setValue(false); } catch (Throwable ignored) {}
            if (mc.player != null) {
                try {
                    bx = mc.player.getX();
                    by = mc.player.getY();
                    bz = mc.player.getZ();
                    dim = curDim();
                    has = true;
                    if (mc.inGameHud != null) {
                        mc.inGameHud.getChatHud().addMessage(Text.literal(
                                "§a[Base] §fsaved " + (int) bx + "/" + (int) by + "/" + (int) bz + " (" + dim + ")"));
                    }
                } catch (Throwable ignored) {
                }
            }
        }
        if (recallChat.isEnabled()) {
            try { recallChat.setValue(false); } catch (Throwable ignored) {}
            if (has && mc.inGameHud != null) {
                try {
                    mc.inGameHud.getChatHud().addMessage(Text.literal(
                            "§a[Base] §f" + (int) bx + " / " + (int) by + " / " + (int) bz + " (" + dim + ")"));
                } catch (Throwable ignored) {
                }
            }
        }
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (!has || mc.player == null || mc.textRenderer == null) return;
        try {
            int col = color.getValue();
            Vec3d base = new Vec3d(bx, by, bz);
            boolean rightDim = dim.isEmpty() || dim.equals(curDim());

            double dx = bx - mc.player.getX();
            double dy = by - mc.player.getY();
            double dz = bz - mc.player.getZ();
            int distance = (int) Math.sqrt(dx * dx + dy * dy + dz * dz);

            if (rightDim && box.isEnabled()) {
                RenderUtils.drawBox3D(context, new Box(bx - 0.5, by, bz - 0.5, bx + 0.5, by + 2.0, bz + 0.5),
                        col, (col & 0x00FFFFFF) | 0x22000000);
            }

            if (rightDim && tracer.isEnabled()) {
                int[] sc = ProjectionUtil.project(base.add(0.0, 1.0, 0.0), tickDelta);
                if (sc != null) {
                    RenderUtils.drawLine(context, RenderUtils.screenWidth() / 2,
                            RenderUtils.screenHeight() / 2, sc[0], sc[1],
                            (col & 0x00FFFFFF) | 0x80000000, 1.0f);
                }
            }

            if (!showHere.isEnabled()) return;
            String label = "Base  " + (int) bx + " " + (int) by + " " + (int) bz + "  " + distance + "m";
            int x = posX.getValue().intValue();
            int y = posY.getValue().intValue();
            RenderUtils.drawChip(context, mc.textRenderer, label, x + mc.textRenderer.getWidth(label) / 2, y, col);
        } catch (Throwable ignored) {
        }
    }
}
