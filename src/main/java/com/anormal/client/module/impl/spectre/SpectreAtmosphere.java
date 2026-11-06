package com.anormal.client.module.impl.spectre;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.module.ModuleManager;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.util.ColorUtils;

/**
 * Spectre port: "Spectre Sky" — coloured skies, a night tint and a nether tint.
 *
 * <p>Spectre builds nebula and aurora geometry. This port keeps the colour grading part —
 * palette, gradient sky, night sky, nether colours and rain behaviour — and applies it
 * through {@code SpectreAtmosphereMixin} on the vanilla sky colour.</p>
 */
public final class SpectreAtmosphere extends Module {
    public final ModeSetting theme = addSetting(new ModeSetting("Sky palette", "Palette used for the sky tint", "Spectre",
            "Spectre", "Borealis", "Nebula", "Frost", "Crimson", "Custom"));
    public final ColorSetting nightColor = addSetting(new ColorSetting("Night Sky Color", "Night tint", ColorUtils.rgba(70, 90, 160, 255)));
    public final ColorSetting sunsetColor = addSetting(new ColorSetting("Sunset Color", "Sunset tint", ColorUtils.rgba(255, 150, 90, 255)));
    public final ColorSetting auroraColor = addSetting(new ColorSetting("Aurora Color", "Secondary tint", ColorUtils.rgba(90, 240, 200, 255)));
    public final BooleanSetting gradientSky = addSetting(new BooleanSetting("Gradient Sky", "Blend towards the horizon colour", true));
    public final BooleanSetting nightSky = addSetting(new BooleanSetting("Always Night Sky", "Keep the night tint even at noon", false));
    public final BooleanSetting keepInRain = addSetting(new BooleanSetting("Keep In Rain", "Keep the tint while raining", true));
    public final BooleanSetting netherColors = addSetting(new BooleanSetting("Nether Colors", "Tint the nether fog", true));

    public SpectreAtmosphere() {
        super("SpectreAtmosphere", "Spectre port: coloured skies, a night tint and a nether tint.", Category.SPECTRE);
    }

    private int themeColor() {
        return switch (theme.getValue()) {
            case "Borealis" -> 0x2E6BD6;
            case "Nebula" -> 0x8B3DD6;
            case "Frost" -> 0x8FD3FF;
            case "Crimson" -> 0xD6453D;
            case "Custom" -> nightColor.getColor();
            default -> 0x3ED0C8;
        };
    }

    /** Called from the sky-colour hook; returns the graded colour. */
    public static int graded(int original) {
        SpectreAtmosphere module = ModuleManager.getModule(SpectreAtmosphere.class);
        if (module == null || !module.isEnabled()) return original;
        return module.grade(original);
    }

    private int grade(int original) {
        var player = mc.player;
        if (player == null || mc.world == null) return original;
        if (!keepInRain.isEnabled() && mc.world.isRaining()) return original;
        if (mc.world.getRegistryKey().equals(net.minecraft.world.World.NETHER)) {
            // The nether tint has its own palette switch; when it is off the sky stays vanilla.
            return netherColors.isEnabled() ? netherFog(original) : original;
        }

        boolean night = nightSky.isEnabled() || mc.world.getTimeOfDay() % 24000L > 13000L;
        int tint = night ? themeColor() : sunsetColor.getColor();
        float amount = night ? 0.65F : 0.25F;
        if (!gradientSky.isEnabled()) amount *= 0.6F;

        int r = (int) (((original >> 16) & 0xFF) * (1.0F - amount) + ((tint >> 16) & 0xFF) * amount);
        int g = (int) (((original >> 8) & 0xFF) * (1.0F - amount) + ((tint >> 8) & 0xFF) * amount);
        int b = (int) ((original & 0xFF) * (1.0F - amount) + (tint & 0xFF) * amount);
        return (r << 16) | (g << 8) | b;
    }

    /** Nether fog tint, applied by the same hook family. */
    public static int netherFog(int original) {
        SpectreAtmosphere module = ModuleManager.getModule(SpectreAtmosphere.class);
        if (module == null || !module.isEnabled() || !module.netherColors.isEnabled()) return original;
        int accent = module.auroraColor.getColor();
        int r = (int) (((original >> 16) & 0xFF) * 0.65F + ((accent >> 16) & 0xFF) * 0.35F);
        int g = (int) (((original >> 8) & 0xFF) * 0.65F + ((accent >> 8) & 0xFF) * 0.35F);
        int b = (int) ((original & 0xFF) * 0.65F + (accent & 0xFF) * 0.35F);
        return (r << 16) | (g << 8) | b;
    }
}
