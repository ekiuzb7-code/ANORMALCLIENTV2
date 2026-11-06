package com.anormal.client.module.impl.spectre;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.module.impl.client.Friends;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.ColorUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;

/**
 * Spectre port: "3D Character" — wears a small character modelled around your player.
 *
 * <p>Spectre loads a modelled character mesh from its characters folder. This port keeps the
 * character choice, the detail level (how many body parts are drawn), held items and the friend
 * toggle, and builds the character from boxes with the same palette shading.</p>
 */
public final class SpectreCharacter extends Module {
    public final ModeSetting character = addSetting(new ModeSetting("Character", "Which character to wear",
            "Reaper", "Reaper", "Knight", "Fox", "Wisp", "Golem"));
    public final ModeSetting detail = addSetting(new ModeSetting("Detail", "How detailed the model is", "Medium", "Low", "Medium", "High"));
    public final BooleanSetting heldItems = addSetting(new BooleanSetting("Held Items", "Draw the held item", false));
    public final BooleanSetting onFriends = addSetting(new BooleanSetting("On Friends", "Also wear it for friends", false));
    public final NumberSetting size = addSetting(new NumberSetting("Size", "Character scale", 1.0, 0.6, 1.6, 0.05));
    public final BooleanSetting glow = addSetting(new BooleanSetting("Glow", "Glow the eyes", true));

    public SpectreCharacter() {
        super("SpectreCharacter", "Spectre port: wears a small character modelled around your player.", Category.SPECTRE);
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        var self = mc.player;
        if (self == null || mc.world == null || mc.options.hudHidden) return;
        long now = System.nanoTime();
        double pulse = 0.85 + 0.15 * Math.sin(now / 4.0E8);

        for (PlayerEntity player : mc.world.getPlayers()) {
            String name = player.getGameProfile().name();
            boolean friend = name != null && Friends.isFriend(name);
            if (player != self && friend && !onFriends.isEnabled()) continue;
            if (player.isSpectator() || player.isInvisible()) continue;

            int[] palette = palette(character.getValue());
            double s = 0.22 * size.getAsDouble();
            double baseY = player.getY();
            double x = player.getX();
            double z = player.getZ();
            var look = player.getRotationVec(1.0F);

            int body = palette[0];
            int trim = palette[1];
            int eyes = palette[2];

            draw(context, tickDelta, x, baseY + s * 4.4, z, s * 1.6, s * 2.2, body);
            if ("Low".equals(detail.getValue())) continue;
            draw(context, tickDelta, x + look.z * s * 0.4, baseY + s * 6.0, z - look.x * s * 0.4, s * 1.1, s * 1.0, trim);
            if ("High".equals(detail.getValue())) {
                draw(context, tickDelta, x - s * 1.2, baseY + s * 3.4, z, s * 0.55, s * 2.0, trim);
                draw(context, tickDelta, x + s * 1.2, baseY + s * 3.4, z, s * 0.55, s * 2.0, trim);
                draw(context, tickDelta, x - s * 0.6, baseY + s * 1.0, z, s * 0.5, s * 1.8, body);
                draw(context, tickDelta, x + s * 0.6, baseY + s * 1.0, z, s * 0.5, s * 1.8, body);
            }
            if (glow.isEnabled()) {
                double ex = x + look.x * s * 0.9;
                double ez = z + look.z * s * 0.9;
                double ey = baseY + s * 6.1;
                SpectreHoleEsp.drawBox(context, tickDelta, new Box(ex - 0.03, ey - 0.02, ez - 0.03, ex + 0.03, ey + 0.02, ez + 0.03),
                        (eyes & 0x00FFFFFF) | ((int) (200 * pulse) << 24), 0, true, false, 1);
            }
            if (heldItems.isEnabled()) {
                SpectreHoleEsp.drawBox(context, tickDelta,
                        new Box(x + look.z * s * 1.6 - 0.06, baseY + s * 3.2, z - look.x * s * 1.6 - 0.06,
                                x + look.z * s * 1.6 + 0.06, baseY + s * 3.2 + 0.5, z - look.x * s * 1.6 + 0.06),
                        (trim & 0x00FFFFFF) | 0xAA000000, 0, true, false, 1);
            }
        }
    }

    private static void draw(DrawContext context, float tickDelta, double x, double y, double z, double halfX, double halfY, int color) {
        SpectreHoleEsp.drawBox(context, tickDelta, new Box(x - halfX, y - halfY * 0.5, z - halfX, x + halfX, y + halfY * 0.5, z + halfX),
                (color & 0x00FFFFFF) | 0xDD000000, (color & 0x00FFFFFF) | 0x44000000, true, true, 1);
    }

    private static int[] palette(String name) {
        return switch (name) {
            case "Knight" -> new int[]{ColorUtils.rgba(120, 130, 150, 255), ColorUtils.rgba(230, 200, 90, 255), ColorUtils.rgba(120, 220, 255, 255)};
            case "Fox" -> new int[]{ColorUtils.rgba(220, 120, 50, 255), ColorUtils.rgba(245, 235, 225, 255), ColorUtils.rgba(255, 210, 90, 255)};
            case "Wisp" -> new int[]{ColorUtils.rgba(90, 200, 210, 255), ColorUtils.rgba(180, 240, 255, 255), ColorUtils.rgba(220, 255, 255, 255)};
            case "Golem" -> new int[]{ColorUtils.rgba(140, 145, 135, 255), ColorUtils.rgba(110, 160, 120, 255), ColorUtils.rgba(255, 190, 120, 255)};
            default -> new int[]{ColorUtils.rgba(40, 35, 55, 255), ColorUtils.rgba(120, 90, 200, 255), ColorUtils.rgba(190, 140, 255, 255)};
        };
    }
}
