package com.anormal.client.module.impl.legit;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.theme.ThemeManager;
import com.anormal.client.util.HudStyle;
import com.anormal.client.util.RenderUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Warns about players approaching from behind.
 *
 * <p>Modernised: a status header ("CLEAR" / "DANGER"), then one row per player with the side they
 * are on, their name and distance, all colour-coded by how close they are.</p>
 */
public class Rearview extends Module {
    public final NumberSetting posX = new NumberSetting("Pos X", "X Position on screen", 1750.0, 0.0, 1920.0, 1.0);
    public final NumberSetting posY = new NumberSetting("Pos Y", "Y Position on screen", 300.0, 0.0, 1080.0, 1.0);
    public final NumberSetting scale = new NumberSetting("Scale", "HUD scale (0.05 = tiny, 2.0 = huge)", 1.0, 0.05, 2.0, 0.01);
    public final NumberSetting range = new NumberSetting("Range", "Detection range", 40.0, 10.0, 150.0, 5.0);

    public Rearview() {
        super("Rearview", "Warns about players approaching from behind you", Category.LEGIT);
        addSetting(posX);
        addSetting(posY);
        addSetting(scale);
        addSetting(range);
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.player == null || mc.world == null || mc.textRenderer == null) return;
        try {
            Vec3d look = mc.player.getRotationVec(1.0f);
            List<PlayerEntity> behind = new ArrayList<>();
            List<Double> distances = new ArrayList<>();

            for (Entity e : mc.world.getEntities()) {
                if (!(e instanceof PlayerEntity p) || p == mc.player || !p.isAlive()) continue;
                double dist = mc.player.distanceTo(p);
                if (dist > range.getValue()) continue;
                Vec3d to = new Vec3d(p.getX() - mc.player.getX(), 0, p.getZ() - mc.player.getZ()).normalize();
                double dot = look.x * to.x + look.z * to.z;
                if (dot < -0.3) {
                    behind.add(p);
                    distances.add(dist);
                }
            }

            // Nearest threat first.
            List<Integer> order = new ArrayList<>();
            for (int i = 0; i < behind.size(); i++) order.add(i);
            order.sort(Comparator.comparingDouble(distances::get));

            boolean danger = !behind.isEmpty();
            int accent = danger ? 0xFFFF5566 : ThemeManager.getAccentColor();
            int w = 214;
            int rows = Math.min(6, behind.size());
            int rowH = 16;
            int h = 24 + Math.max(1, rows) * rowH + 4;
            int[] at = HudStyle.place(posX.getValue().intValue(), posY.getValue().intValue(), w, h);
            int x = at[0], y = at[1];

            HudStyle.panel(context, x, y, w, h, accent);
            HudStyle.title(context, mc.textRenderer, "Behind", x + 10, y + 7);

            String status = danger ? behind.size() + " tracking" : "clear";
            int sw = mc.textRenderer.getWidth(status);
            RenderUtils.drawText(context, mc.textRenderer, status, x + w - 10 - sw, y + 7,
                    danger ? 0xFFFF7A88 : 0xFF4ADE80, true);

            if (!danger) {
                RenderUtils.drawText(context, mc.textRenderer, "nothing behind you", x + 10, y + 24,
                        0xFF5E6472, false);
                return;
            }

            int ly = y + 24;
            for (int i = 0; i < rows; i++) {
                int idx = order.get(i);
                PlayerEntity p = behind.get(idx);
                double dist = distances.get(idx);

                Vec3d to = new Vec3d(p.getX() - mc.player.getX(), 0, p.getZ() - mc.player.getZ()).normalize();
                double yawRad = Math.toRadians(mc.player.getYaw());
                double relRight = to.x * (-Math.cos(yawRad)) + to.z * (-Math.sin(yawRad));
                String side = relRight >= 0 ? ">" : "<";

                int threatColor = dist <= 12 ? 0xFFF87171 : (dist <= 24 ? 0xFFFBBF24 : 0xFFC7CBD6);

                RenderUtils.fill(context, x + 10, ly + 5, x + 14, ly + 9, threatColor);
                RenderUtils.drawText(context, mc.textRenderer, side, x + 18, ly + 3, threatColor, true);

                String name = HudStyle.trim(mc.textRenderer,
                        com.anormal.client.module.impl.client.NameProtect.replaceName(p), w - 74);
                RenderUtils.drawText(context, mc.textRenderer, name, x + 26, ly + 3, 0xFFEDEFF5, false);

                String distText = (int) dist + "m";
                int dw = mc.textRenderer.getWidth(distText);
                RenderUtils.drawText(context, mc.textRenderer, distText, x + w - 10 - dw, ly + 3,
                        threatColor, true);
                ly += rowH;
            }
        } catch (Throwable ignored) {}
    }
}
