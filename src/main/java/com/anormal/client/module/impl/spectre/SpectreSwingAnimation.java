package com.anormal.client.module.impl.spectre;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.module.ModuleManager;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.HostileEntity;

/**
 * Spectre port: "Swing Animation" — changes your swing animation.
 *
 * <p>Spectre poses the first-person hand with six Egyptian styles and scales the swing
 * duration by Tempo. This port keeps the swing controller (Tempo, Offhand Too, Combat Only)
 * which is what {@code SpectreSwingMixin} reads from the hand-swing duration; the glyph
 * poses need a first-person pose hook that is not ported yet.</p>
 */
public final class SpectreSwingAnimation extends Module {
    private static SpectreSwingAnimation instance;

    public final NumberSetting tempo = addSetting(new NumberSetting("Tempo", "Swing speed multiplier", 1.0, 0.4, 2.5, 0.05));
    public final BooleanSetting offhand = addSetting(new BooleanSetting("Offhand Too", "Also change the offhand swing", true));
    public final BooleanSetting combatOnly = addSetting(new BooleanSetting("Combat Only", "Only while fighting", false));

    private boolean styled;

    public SpectreSwingAnimation() {
        super("SpectreSwingAnimation", "Spectre port: changes the speed of your swing animation.", Category.SPECTRE);
        instance = this;
    }

    @Override
    public void onEnable() {
        latch();
    }

    public void latch() {
        styled = !combatOnly.isEnabled() || inCombat();
    }

    @Override
    public void onTick() {
        // "Combat Only" follows the crosshair, so the latch has to be re-evaluated.
        latch();
    }

    private static boolean inCombat() {
        Entity looked = mc2().targetedEntity;
        return looked instanceof HostileEntity || looked instanceof net.minecraft.entity.player.PlayerEntity;
    }

    /** Swing duration for a given vanilla duration. */
    public static int swingTicks(int ticks) {
        SpectreSwingAnimation module = instance;
        if (module == null || !module.isEnabled()) return ticks;
        if (module.combatOnly.isEnabled() && !module.styled) return ticks;
        return Math.max(1, Math.round(ticks / module.tempo.getValue().floatValue()));
    }

    public static boolean covers(boolean mainHand) {
        SpectreSwingAnimation module = instance;
        if (module == null || !module.isEnabled()) return false;
        if (module.combatOnly.isEnabled() && !module.styled) return false;
        return mainHand || module.offhand.isEnabled();
    }

    private static net.minecraft.client.MinecraftClient mc2() {
        return net.minecraft.client.MinecraftClient.getInstance();
    }
}
