package com.anormal.client.module.impl.spectre;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.module.ModuleManager;
import com.anormal.client.setting.BooleanSetting;

/**
 * Spectre port: "No Render" — hides selected overlays, screen effects and entity renders.
 *
 * <p>Spectre is a pure accessor module: its mixins ask these static flags whether a given
 * overlay may be drawn. The same accessors are ported here; the weather one is applied
 * directly (rain gradient is a client value), the rest are read by the additive hooks as
 * they are wired one by one.</p>
 */
public final class SpectreNoRender extends Module {
    public final BooleanSetting fireOverlay = addSetting(new BooleanSetting("Fire Overlay", "Hide the burning overlay", true));
    public final BooleanSetting totemAnimation = addSetting(new BooleanSetting("Totem Animation", "Hide the totem pop overlay", true));
    public final BooleanSetting pumpkinPowderSnow = addSetting(new BooleanSetting("Pumpkin & Powder Snow", "Hide pumpkin and powder-snow overlays", true));
    public final BooleanSetting nauseaPortal = addSetting(new BooleanSetting("Nausea & Portal", "Hide nausea and portal distortion", true));
    public final BooleanSetting blindnessDarkness = addSetting(new BooleanSetting("Blindness & Darkness", "Hide blindness and darkness", true));
    public final BooleanSetting spawnerMob = addSetting(new BooleanSetting("Spawner Mob", "Hide the spinning mob inside spawners", true));
    public final BooleanSetting fallingBlocks = addSetting(new BooleanSetting("Falling Blocks", "Hide falling blocks", false));
    public final BooleanSetting weather = addSetting(new BooleanSetting("Weather", "Hide rain and snow", true));
    public final BooleanSetting lavaFog = addSetting(new BooleanSetting("Lava Fog", "Hide the lava fog", true));
    public final BooleanSetting entityFire = addSetting(new BooleanSetting("Entity Fire", "Hide the fire on entities", false));

    public SpectreNoRender() {
        super("SpectreNoRender", "Spectre port: hides selected overlays, screen effects and entity renders.", Category.SPECTRE);
    }

    @Override
    public void onTick() {
        if (weather.isEnabled() && mc.world != null) {
            mc.world.setRainGradient(0.0F);
            mc.world.setThunderGradient(0.0F);
        }
    }

    private static SpectreNoRender module() {
        SpectreNoRender module = ModuleManager.getModule(SpectreNoRender.class);
        return module != null && module.isEnabled() ? module : null;
    }

    public static boolean hideFireOverlay() {
        SpectreNoRender m = module();
        return m != null && m.fireOverlay.isEnabled();
    }

    public static boolean hideTotemAnimation() {
        SpectreNoRender m = module();
        return m != null && m.totemAnimation.isEnabled();
    }

    public static boolean hidePumpkinPowderSnow() {
        SpectreNoRender m = module();
        return m != null && m.pumpkinPowderSnow.isEnabled();
    }

    public static boolean hideNauseaPortal() {
        SpectreNoRender m = module();
        return m != null && m.nauseaPortal.isEnabled();
    }

    public static boolean hideBlindnessDarkness() {
        SpectreNoRender m = module();
        return m != null && m.blindnessDarkness.isEnabled();
    }

    public static boolean hideSpawnerMob() {
        SpectreNoRender m = module();
        return m != null && m.spawnerMob.isEnabled();
    }

    public static boolean hideFallingBlocks() {
        SpectreNoRender m = module();
        return m != null && m.fallingBlocks.isEnabled();
    }

    public static boolean hideWeather() {
        SpectreNoRender m = module();
        return m != null && m.weather.isEnabled();
    }

    public static boolean hideLavaFog() {
        SpectreNoRender m = module();
        return m != null && m.lavaFog.isEnabled();
    }

    public static boolean hideEntityFire() {
        SpectreNoRender m = module();
        return m != null && m.entityFire.isEnabled();
    }
}
