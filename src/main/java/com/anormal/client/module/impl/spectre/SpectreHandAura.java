package com.anormal.client.module.impl.spectre;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.ColorUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.Box;

/**
 * Spectre port: "Hand Aura" — a glowing outline on your hand and held item.
 *
 * <p>Spectre captures the hand pass into an offscreen buffer and composes a rim glow. This port
 * keeps the colour mode, aura strength, reach, rim, sheen, breathe and sheen colour and draws
 * the aura as an animated shell around the held item position, which the box renderer can
 * express without an offscreen pass.</p>
 */
public final class SpectreHandAura extends Module {
    private static final long BREATH_NANOS = 3_200_000_000L;
    private static final long SHEEN_NANOS = 4_400_000_000L;

    public final ModeSetting colorMode = addSetting(new ModeSetting("Color", "Theme or manual colours", "Theme", "Theme", "Manual"));
    public final ColorSetting glowColor = addSetting(new ColorSetting("Glow Color", "Aura colour", ColorUtils.rgba(60, 210, 200, 255)));
    public final NumberSetting aura = addSetting(new NumberSetting("Aura", "Aura strength", 65.0, 0.0, 100.0, 5.0));
    public final NumberSetting reach = addSetting(new NumberSetting("Reach", "Aura size", 8.0, 2.0, 20.0, 1.0));
    public final NumberSetting rim = addSetting(new NumberSetting("Rim", "Edge brightness", 40.0, 0.0, 100.0, 5.0));
    public final NumberSetting sheen = addSetting(new NumberSetting("Sheen", "Moving highlight", 25.0, 0.0, 100.0, 5.0));
    public final ColorSetting sheenColor = addSetting(new ColorSetting("Sheen Color", "Highlight colour", ColorUtils.rgba(180, 255, 250, 255)));
    public final BooleanSetting breathe = addSetting(new BooleanSetting("Breathe", "Pulse the aura", true));

    public SpectreHandAura() {
        super("SpectreHandAura", "Spectre port: a glowing outline on your hand and held item.", Category.SPECTRE);
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        var player = mc.player;
        if (player == null || mc.options.hudHidden) return;
        double strength = aura.getAsDouble();
        if (strength <= 0.0) return;

        long now = System.nanoTime();
        float pulse = breathe.isEnabled() ? 0.65F + 0.35F * (float) (0.5 - 0.5 * Math.cos(now % BREATH_NANOS / (double) BREATH_NANOS * Math.PI * 2.0)) : 1.0F;
        float sheenPhase = (float) (now % SHEEN_NANOS / (double) SHEEN_NANOS);

        int glow = colorMode.is("Manual") ? glowColor.getColor() : ColorUtils.rgba(60, 210, 200, 255);
        int highlight = colorMode.is("Manual") ? sheenColor.getColor() : ColorUtils.rgba(180, 255, 250, 255);

        double reachValue = reach.getAsDouble() / 100.0 + 0.08;
        var look = player.getRotationVec(1.0F);
        float swing = player.getHandSwingProgress(tickDelta);
        double forward = 0.35 - 0.12 * Math.sin(swing * Math.PI);
        double side = player.getMainHandStack().isEmpty() ? 0.16 : 0.28;
        double rightX = look.z;
        double rightZ = -look.x;
        double cx = player.getX() + look.x * forward + rightX * side;
        double cy = player.getY() + player.getStandingEyeHeight() - 0.28 - 0.18 * Math.sin(swing * Math.PI);
        double cz = player.getZ() + look.z * forward + rightZ * side;

        int alpha = (int) Math.min(255, strength * 2.2 * pulse);
        double half = 0.16 + reachValue * 0.12;
        Box shell = new Box(cx - half, cy - half, cz - half, cx + half, cy + half, cz + half);
        SpectreHoleEsp.drawBox(context, tickDelta, shell, (glow & 0x00FFFFFF) | (alpha << 24), (glow & 0x00FFFFFF) | ((int) (alpha * 0.25) << 24), true, true, 1);
        if (rim.getAsDouble() > 0.0) {
            SpectreHoleEsp.drawBox(context, tickDelta, shell.expand(0.02 + rim.getAsDouble() / 100.0 * 0.06),
                    (glow & 0x00FFFFFF) | ((int) (alpha * 0.5) << 24), 0, true, false, 1);
        }
        if (sheen.getAsDouble() > 0.0) {
            double offset = Math.sin(sheenPhase * Math.PI * 2.0) * half;
            BlockBoxHint:
            {
                SpectreHoleEsp.drawBox(context, tickDelta,
                        new Box(cx - half * 0.25, cy - half + offset * 0.5, cz - half * 0.25,
                                cx + half * 0.25, cy - half + offset * 0.5 + 0.02, cz + half * 0.25),
                        (highlight & 0x00FFFFFF) | ((int) (alpha * sheen.getAsDouble() / 100.0) << 24), 0, true, false, 1);
            }
        }
    }
}
