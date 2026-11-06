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
 * Spectre port: "Head Cosmetic" — the shared head accessory layer.
 *
 * <p>Spectre keeps this as the abstract base for its hoods, crowns and helmets: palette shading,
 * the glow flag, the glint sweep (3.6 s cycle), the friend rule and the wear rule. This port keeps
 * those rules in one concrete module that wears a crown with a gem, glowing eyes and the glint.</p>
 */
public final class SpectreHeadCosmetic extends Module {
    private static final long GLINT_CYCLE = 3_600_000_000L;

    public final ModeSetting piece = addSetting(new ModeSetting("Piece", "Which head accessory", "Crown", "Crown", "Circlet", "Horns"));
    public final ModeSetting colorMode = addSetting(new ModeSetting("Color", "Theme or manual colours", "Theme", "Theme", "Manual"));
    public final ColorSetting metalColor = addSetting(new ColorSetting("Metal Color", "Metal tint", ColorUtils.rgba(230, 200, 90, 255)));
    public final ColorSetting gemColor = addSetting(new ColorSetting("Gem Color", "Gem tint", ColorUtils.rgba(120, 220, 255, 255)));
    public final NumberSetting size = addSetting(new NumberSetting("Size", "Accessory size", 1.0, 0.6, 1.6, 0.05));
    public final BooleanSetting glow = addSetting(new BooleanSetting("Glow", "Glow the gem", true));
    public final BooleanSetting glint = addSetting(new BooleanSetting("Glint", "Sweep a highlight", true));
    public final BooleanSetting onFriends = addSetting(new BooleanSetting("On Friends", "Also wear for friends", false));

    public SpectreHeadCosmetic() {
        super("SpectreHeadCosmetic", "Spectre port: the shared head accessory layer (crown, circlet, horns).", Category.SPECTRE);
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        var self = mc.player;
        if (self == null || mc.world == null || mc.options.hudHidden) return;
        long now = System.nanoTime();
        double phase = (now % GLINT_CYCLE) / (double) GLINT_CYCLE;
        double sweep = Math.sin(phase * Math.PI) * Math.sin(phase * Math.PI);

        int metal = colorMode.is("Manual") ? metalColor.getColor() : ColorUtils.rgba(230, 200, 90, 255);
        int gem = colorMode.is("Manual") ? gemColor.getColor() : ColorUtils.rgba(120, 220, 255, 255);

        for (PlayerEntity player : mc.world.getPlayers()) {
            String name = player.getGameProfile().name();
            boolean friend = name != null && Friends.isFriend(name);
            if (player != self && friend && !onFriends.isEnabled()) continue;
            if (player.isSpectator() || player.isInvisible()) continue;

            double s = 0.26 * size.getAsDouble();
            double x = player.getX();
            double z = player.getZ();
            double y = player.getY() + player.getHeight() - 0.12;
            int line = (metal & 0x00FFFFFF) | 0xEE000000;
            int face = (metal & 0x00FFFFFF) | 0x44000000;

            if (piece.is("Horns")) {
                var look = player.getRotationVec(1.0F);
                double sideX = -look.z * s;
                double sideZ = look.x * s;
                SpectreHoleEsp.drawBox(context, tickDelta, new Box(x + sideX - 0.04, y, z + sideZ - 0.04, x + sideX + 0.04, y + s * 1.4, z + sideZ + 0.04), line, face, true, true, 1);
                SpectreHoleEsp.drawBox(context, tickDelta, new Box(x - sideX - 0.04, y, z - sideZ - 0.04, x - sideX + 0.04, y + s * 1.4, z - sideZ + 0.04), line, face, true, true, 1);
            } else {
                SpectreHoleEsp.drawBox(context, tickDelta, new Box(x - s, y, z - s, x + s, y + s * 0.35, z + s), line, face, true, true, 1);
                if (piece.is("Crown")) {
                    for (int i = 0; i < 4; i++) {
                        double a = i * Math.PI / 2.0;
                        double px = x + Math.cos(a) * s * 0.85;
                        double pz = z + Math.sin(a) * s * 0.85;
                        SpectreHoleEsp.drawBox(context, tickDelta, new Box(px - 0.03, y + s * 0.35, pz - 0.03, px + 0.03, y + s * 0.8, pz + 0.03), line, 0, true, false, 1);
                    }
                }
            }

            if (glow.isEnabled()) {
                int alpha = (int) (140 + 115 * sweep);
                SpectreHoleEsp.drawBox(context, tickDelta, new Box(x - 0.04, y + s * 0.1, z - 0.04, x + 0.04, y + s * 0.1 + 0.08, z + 0.04),
                        (gem & 0x00FFFFFF) | (alpha << 24), 0, true, false, 1);
            }
            if (glint.isEnabled() && sweep > 0.05) {
                double offset = (phase * 2.0 % 1.0) * 2.0 - 1.0;
                SpectreHoleEsp.drawBox(context, tickDelta,
                        new Box(x - s, y + s * 0.35, z - s + (offset + 1.0) * s, x + s, y + s * 0.37, z - s + (offset + 1.0) * s + 0.02),
                        (gem & 0x00FFFFFF) | ((int) (150 * sweep) << 24), 0, true, false, 1);
            }
        }
    }
}
