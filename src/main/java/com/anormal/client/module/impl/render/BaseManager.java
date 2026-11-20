package com.anormal.client.module.impl.render;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.util.ColorUtils;
import com.anormal.client.util.ProjectionUtil;
import com.anormal.client.util.RenderUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * Three base slots with world markers.
 *
 * <p>Modernised: the markers respect the render frame delta (the old code hardcoded {@code 1.0f},
 * so they drifted while moving), each slot draws a 3D box plus a rounded "A / B / C" chip, and an
 * optional tracer points at them.</p>
 */
public class BaseManager extends Module {
    public final BooleanSetting setA = new BooleanSetting("Set A", "Save current position as A", false);
    public final BooleanSetting setB = new BooleanSetting("Set B", "Save current position as B", false);
    public final BooleanSetting setC = new BooleanSetting("Set C", "Save current position as C", false);
    public final BooleanSetting clearAll = new BooleanSetting("Clear All", "Forget all bases", false);
    public final BooleanSetting showDistance = new BooleanSetting("Show Distance", "Show distance on marker", true);
    public final BooleanSetting box = new BooleanSetting("Box", "Draw the bases as 3D boxes", true);
    public final BooleanSetting tracer = new BooleanSetting("Tracer", "Line from crosshair to each base", false);
    public final ColorSetting color = new ColorSetting("Color", "Base marker color", ColorUtils.rgba(85, 255, 85, 255));

    private static final class Slot {
        double x, y, z;
        String dim = "";
        boolean has = false;
    }

    private final Slot a = new Slot(), b = new Slot(), c = new Slot();

    public BaseManager() {
        super("BaseManager", "Three base slots with markers", Category.RENDER);
        addSetting(setA);
        addSetting(setB);
        addSetting(setC);
        addSetting(clearAll);
        addSetting(showDistance);
        addSetting(box);
        addSetting(tracer);
        addSetting(color);
    }

    private String curDim() {
        try {
            return mc.world.getRegistryKey().getValue().getPath();
        } catch (Throwable ignored) {
            return "";
        }
    }

    private void save(Slot slot, String tag) {
        try {
            slot.x = mc.player.getX();
            slot.y = mc.player.getY();
            slot.z = mc.player.getZ();
            slot.dim = curDim();
            slot.has = true;
            if (mc.inGameHud != null) {
                mc.inGameHud.getChatHud().addMessage(Text.literal(
                        "§a[Base " + tag + "] §fsaved " + (int) slot.x + "/" + (int) slot.y + "/" + (int) slot.z));
            }
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void onTick() {
        if (clearAll.isEnabled()) {
            try { clearAll.setValue(false); } catch (Throwable ignored) {}
            a.has = false;
            b.has = false;
            c.has = false;
            return;
        }
        if (mc.player == null) return;
        if (setA.isEnabled()) {
            try { setA.setValue(false); } catch (Throwable ignored) {}
            save(a, "A");
        }
        if (setB.isEnabled()) {
            try { setB.setValue(false); } catch (Throwable ignored) {}
            save(b, "B");
        }
        if (setC.isEnabled()) {
            try { setC.setValue(false); } catch (Throwable ignored) {}
            save(c, "C");
        }
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.player == null) return;
        draw(context, a, "A", tickDelta);
        draw(context, b, "B", tickDelta);
        draw(context, c, "C", tickDelta);
    }

    private void draw(DrawContext context, Slot slot, String tag, float tickDelta) {
        if (!slot.has || mc.player == null) return;
        try {
            if (!slot.dim.isEmpty() && !slot.dim.equals(curDim())) return;
            int col = color.getValue();
            Vec3d pos = new Vec3d(slot.x, slot.y, slot.z);

            if (box.isEnabled()) {
                RenderUtils.drawBox3D(context,
                        new Box(slot.x - 0.5, slot.y, slot.z - 0.5, slot.x + 0.5, slot.y + 2.0, slot.z + 0.5),
                        col, (col & 0x00FFFFFF) | 0x22000000);
            }

            int[] sc = ProjectionUtil.project(pos.add(0.0, 2.1, 0.0), tickDelta);
            if (sc == null || mc.textRenderer == null) return;

            if (tracer.isEnabled()) {
                RenderUtils.drawLine(context, RenderUtils.screenWidth() / 2,
                        RenderUtils.screenHeight() / 2, sc[0], sc[1],
                        (col & 0x00FFFFFF) | 0x80000000, 1.0f);
            }

            StringBuilder label = new StringBuilder(tag);
            label.append("  ").append((int) slot.x).append(' ').append((int) slot.y).append(' ')
                    .append((int) slot.z);
            if (showDistance.isEnabled()) {
                double dx = slot.x - mc.player.getX();
                double dy = slot.y - mc.player.getY();
                double dz = slot.z - mc.player.getZ();
                label.append("  ").append((int) Math.sqrt(dx * dx + dy * dy + dz * dz)).append('m');
            }
            RenderUtils.drawChip(context, mc.textRenderer, label.toString(), sc[0], sc[1] + 3, col);
        } catch (Throwable ignored) {
        }
    }
}
