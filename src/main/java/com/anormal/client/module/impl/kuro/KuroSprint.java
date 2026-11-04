package com.anormal.client.module.impl.kuro;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.util.kuro.KuroFreecam;

import net.minecraft.entity.effect.StatusEffects;

/**
 * Ported from kuro (dev.kuro.client.module.impl.Sprint).
 *
 * <p>Holds sprint whenever you are allowed to sprint: a movement key is held, you are not sneaking,
 * not gliding, not in water and your hunger is above 6.</p>
 */
public class KuroSprint extends Module {
    private final BooleanSetting cancelInvis = new BooleanSetting("Cancel invis",
            "Do not sprint while you have Invisibility", false);
    private final BooleanSetting omni = new BooleanSetting("Omni (any direction)",
            "Also sprint while strafing or walking backwards", false);

    public KuroSprint() {
        super("Sprint", "Holds sprint whenever you are allowed to (kuro port)", Category.KURO);
        addSetting(cancelInvis);
        addSetting(omni);
    }

    @Override
    public void onDisable() {
        if (mc.options != null) mc.options.sprintKey.setPressed(false);
    }

    @Override
    public void onPreTick() {
        if (mc.player == null || mc.options == null || mc.currentScreen != null) return;
        if (mc.player.isSpectator() || KuroFreecam.active()) return;
        if (cancelInvis.isEnabled() && mc.player.hasStatusEffect(StatusEffects.INVISIBILITY)) return;

        boolean forward = mc.options.forwardKey.isPressed();
        boolean strafe = mc.options.leftKey.isPressed() || mc.options.rightKey.isPressed()
                || mc.options.backKey.isPressed();
        boolean moving = omni.isEnabled() ? (forward || strafe) : forward;

        boolean can = moving
                && !mc.player.isSneaking()
                && !mc.player.isGliding()
                && mc.player.getHungerManager().getFoodLevel() > 6
                && !mc.player.isTouchingWater();

        if (!can) return;
        if (!mc.options.sprintKey.isPressed()) mc.options.sprintKey.setPressed(true);
        if (!mc.player.isSprinting()) mc.player.setSprinting(true);
    }
}
