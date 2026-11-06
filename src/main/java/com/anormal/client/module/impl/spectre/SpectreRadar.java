package com.anormal.client.module.impl.spectre;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.ColorUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;

/**
 * Spectre port: "Radar" — a round radar of the players around you.
 *
 * <p>Spectre draws it through its HUD overlay provider; this port draws the same circle,
 * ring and player dots straight into the Anormal HUD pass, with Rotate following your yaw.</p>
 */
public final class SpectreRadar extends Module {
    public final NumberSetting range = addSetting(new NumberSetting("Range", "Radar range in blocks", 48.0, 8.0, 256.0, 1.0));
    public final NumberSetting size = addSetting(new NumberSetting("Size", "Radar diameter", 150.0, 80.0, 320.0, 1.0));
    public final BooleanSetting rotate = addSetting(new BooleanSetting("Rotate", "Rotate with your yaw", true));
    public final ColorSetting dotColor = addSetting(new ColorSetting("Player Color", "Colour of the player dots", ColorUtils.rgba(255, 98, 210, 255)));

    public SpectreRadar() {
        super("SpectreRadar", "Spectre port: a round radar of the players around you.", Category.SPECTRE);
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        var player = mc.player;
        if (player == null || mc.world == null || mc.options.hudHidden) return;
        if (mc.currentScreen != null && !(mc.currentScreen instanceof net.minecraft.client.gui.screen.ChatScreen)) return;

        float diameter = size.getValue().floatValue();
        float radius = diameter * 0.5F;
        float cx = 12.0F + radius;
        float cy = 12.0F + radius;

        // backdrop + outer ring
        context.fill((int) (cx - radius), (int) (cy - radius), (int) (cx + radius), (int) (cy + radius), 0x66000000);
        drawRing(context, cx, cy, radius, 0x55FFFFFF);

        int dot = dotColor.getColor();
        double rangeSq = range.getAsDouble() * range.getAsDouble();
        float yawRad = rotate.isEnabled() ? (float) Math.toRadians(player.getYaw()) : 0.0F;

        for (PlayerEntity other : mc.world.getPlayers()) {
            if (other == player || other.isRemoved() || other.isSpectator()) continue;
            double dx = other.getX() - player.getX();
            double dz = other.getZ() - player.getZ();
            if (dx * dx + dz * dz > rangeSq) continue;

            float relX = (float) dx / range.getValue().floatValue() * radius;
            float relZ = (float) dz / range.getValue().floatValue() * radius;
            if (rotate.isEnabled()) {
                float cos = MathHelper.cos(yawRad);
                float sin = MathHelper.sin(yawRad);
                float rx = relX * cos - relZ * sin;
                float rz = relX * sin + relZ * cos;
                relX = rx;
                relZ = rz;
            }
            int px = (int) (cx + relX);
            int py = (int) (cy + relZ);
            context.fill(px - 1, py - 1, px + 2, py + 2, dot);
        }

        // own position marker
        context.fill((int) cx - 1, (int) cy - 1, (int) cx + 2, (int) cy + 2, 0xFFFFFFFF);
    }

    private void drawRing(DrawContext context, float cx, float cy, float radius, int color) {
        int segments = 64;
        for (int i = 0; i < segments; i++) {
            double a0 = (Math.PI * 2.0 * i) / segments;
            double a1 = (Math.PI * 2.0 * (i + 1)) / segments;
            int x0 = (int) (cx + Math.cos(a0) * radius);
            int y0 = (int) (cy + Math.sin(a0) * radius);
            int x1 = (int) (cx + Math.cos(a1) * radius);
            int y1 = (int) (cy + Math.sin(a1) * radius);
            context.fill(Math.min(x0, x1), Math.min(y0, y1), Math.max(x0, x1) + 1, Math.max(y0, y1) + 1, color);
        }
    }
}
