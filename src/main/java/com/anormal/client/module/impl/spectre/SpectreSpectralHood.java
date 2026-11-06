package com.anormal.client.module.impl.spectre;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.module.impl.client.Friends;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.ColorUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;

/**
 * Spectre port: "Spectral Hood" — a hooded cosmetic worn by your player.
 *
 * <p>Spectre renders a layered hood mesh with cloth folds, an inlay, glowing eyes and a glint.
 * This port keeps the style (Hood / Cowl / Veil), cloth detail, the spectral pendant, size,
 * colour mode and the cloth / inlay / eye colours, eye glow, glint and friend visibility, and
 * draws the hood as layered boxes around the head.</p>
 */
public final class SpectreSpectralHood extends Module {
    public final ModeSetting style = addSetting(new ModeSetting("Style", "Hood shape", "Hood", "Hood", "Cowl", "Veil"));
    public final NumberSetting clothDetail = addSetting(new NumberSetting("Cloth Detail", "Number of cloth folds", 3.0, 1.0, 8.0, 1.0));
    public final BooleanSetting pendant = addSetting(new BooleanSetting("Spectral Pendant", "Add the pendant", true));
    public final NumberSetting size = addSetting(new NumberSetting("Size", "Hood scale", 1.0, 0.6, 1.6, 0.05));
    public final ModeSetting colorMode = addSetting(new ModeSetting("Color", "Theme or manual colours", "Theme", "Theme", "Manual"));
    public final ColorSetting clothColor = addSetting(new ColorSetting("Cloth Color", "Cloth colour", ColorUtils.rgba(70, 90, 140, 255)));
    public final ColorSetting inlayColor = addSetting(new ColorSetting("Inlay Color", "Inlay colour", ColorUtils.rgba(150, 210, 255, 255)));
    public final ColorSetting eyeColor = addSetting(new ColorSetting("Eye Color", "Eye glow colour", ColorUtils.rgba(180, 240, 255, 255)));
    public final BooleanSetting eyeGlow = addSetting(new BooleanSetting("Eye Glow", "Glow the eyes", true));
    public final BooleanSetting glint = addSetting(new BooleanSetting("Glint", "Add a moving glint", true));
    public final BooleanSetting onFriends = addSetting(new BooleanSetting("On Friends", "Also hood friends", true));

    public SpectreSpectralHood() {
        super("SpectreSpectralHood", "Spectre port: a hooded cosmetic worn by your player.", Category.SPECTRE);
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        var self = mc.player;
        if (self == null || mc.world == null || mc.options.hudHidden) return;

        int cloth = colorMode.is("Manual") ? clothColor.getColor() : ColorUtils.rgba(70, 90, 140, 255);
        int inlay = colorMode.is("Manual") ? inlayColor.getColor() : ColorUtils.rgba(150, 210, 255, 255);
        int eye = colorMode.is("Manual") ? eyeColor.getColor() : ColorUtils.rgba(180, 240, 255, 255);
        long now = System.nanoTime();
        double glintPhase = (now % 3_000_000_000L) / 3.0E9;

        for (PlayerEntity player : mc.world.getPlayers()) {
            boolean friend = false;
            String name = player.getGameProfile().name();
            if (name != null) friend = Friends.isFriend(name);
            if (player != self && friend && !onFriends.isEnabled()) continue;
            if (player.isInvisible() || player.isSpectator()) continue;

            double x = player.getX();
            double y = player.getY() + player.getHeight() - 0.52 * size.getAsDouble();
            double z = player.getZ();
            double s = 0.32 * size.getAsDouble();
            int head = (cloth & 0x00FFFFFF) | 0xCC000000;
            Box shell = new Box(x - s, y - s * 0.9, z - s, x + s, y + s * 0.9, z + s);
            SpectreHoleEsp.drawBox(context, tickDelta, shell, head, (cloth & 0x00FFFFFF) | 0x44000000, true, true, 1);

            int folds = (int) clothDetail.getAsDouble();
            for (int i = 0; i < folds; i++) {
                double f = (i + 1.0) / (folds + 1.0);
                double lift = -s * 0.9 + 2.0 * s * 0.9 * f;
                double out = s + 0.02 + 0.01 * i;
                SpectreHoleEsp.drawBox(context, tickDelta,
                        new Box(x - out, y - s * 0.9 + lift, z - out, x + out, y - s * 0.9 + lift + 0.01, z + out),
                        (inlay & 0x00FFFFFF) | 0x66000000, 0, true, false, 1);
            }

            if (style.is("Cowl")) {
                SpectreHoleEsp.drawBox(context, tickDelta, new Box(x - s * 1.1, y - s * 1.2, z - s * 0.4, x + s * 1.1, y + s * 1.0, z + s * 1.3),
                        head, 0, true, false, 1);
            } else if (style.is("Veil")) {
                SpectreHoleEsp.drawBox(context, tickDelta, new Box(x - s, y - s * 1.4, z + s * 0.5, x + s, y + s * 0.4, z + s * 1.15),
                        (cloth & 0x00FFFFFF) | 0x88000000, (cloth & 0x00FFFFFF) | 0x33000000, true, true, 1);
            }

            if (eyeGlow.isEnabled()) {
                var look = player.getRotationVec(1.0F);
                double ex = x + look.x * s * 0.95;
                double ez = z + look.z * s * 0.95;
                double ey = y + s * 0.1;
                double sideX = -look.z * s * 0.35;
                double sideZ = look.x * s * 0.35;
                SpectreHoleEsp.drawBox(context, tickDelta, new Box(ex + sideX - 0.03, ey - 0.02, ez + sideZ - 0.03, ex + sideX + 0.03, ey + 0.02, ez + sideZ + 0.03), eye, 0, true, false, 1);
                SpectreHoleEsp.drawBox(context, tickDelta, new Box(ex - sideX - 0.03, ey - 0.02, ez - sideZ - 0.03, ex - sideX + 0.03, ey + 0.02, ez - sideZ + 0.03), eye, 0, true, false, 1);
            }
            if (pendant.isEnabled()) {
                double py = y - s * 1.6;
                SpectreHoleEsp.drawBox(context, tickDelta, new Box(x - 0.05, py, z - 0.05, x + 0.05, py + 0.09, z + 0.05), (inlay & 0x00FFFFFF) | 0xCC000000, 0, true, false, 1);
            }
            if (glint.isEnabled()) {
                double offset = (glintPhase * 2.0 % 1.0) * 2.0 - 1.0;
                double gy = y + offset * s * 0.9;
                SpectreHoleEsp.drawBox(context, tickDelta, new Box(x - s, gy, z - s, x + s, gy + 0.015, z + s),
                        (inlay & 0x00FFFFFF) | 0x55FFFFFF, 0, true, false, 1);
            }
        }
    }
}
