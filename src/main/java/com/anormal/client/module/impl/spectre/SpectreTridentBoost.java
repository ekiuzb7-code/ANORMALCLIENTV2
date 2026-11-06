package com.anormal.client.module.impl.spectre;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

/**
 * Spectre port: "Trident Boost" — boosts you when using riptide with a trident.
 */
public final class SpectreTridentBoost extends Module {
    public final NumberSetting boost = addSetting(new NumberSetting("Boost", "Riptide launch multiplier", 2.0, 0.1, 10.0, 0.1));
    public final BooleanSetting outOfWater = addSetting(new BooleanSetting("Out Of Water", "Allow riptide dashes on dry land", true));

    public SpectreTridentBoost() {
        super("SpectreTridentBoost", "Spectre port: boosts you when using riptide with a trident.", Category.SPECTRE);
    }

    /** Called from SpectreTridentMixin.onStoppedUsing TAIL — scale the launch velocity. */
    public static void boostRiptideLaunch(World world, net.minecraft.entity.LivingEntity user) {
        SpectreTridentBoost m = com.anormal.client.module.ModuleManager.getModule(SpectreTridentBoost.class);
        if (m == null || !m.isEnabled() || world == null || !world.isClient()) return;
        if (user == null || !(mc.player == user)) return;
        var velocity = user.getVelocity();
        if (velocity.horizontalLength() <= 0.01) return;
        double factor = m.boost.getAsDouble();
        user.setVelocity(velocity.x * factor, velocity.y * factor, velocity.z * factor);
    }

    /** Called from SpectreTridentMixin.use HEAD — bypass the dry-land refusal. */
    public static boolean forceStart(World world, net.minecraft.entity.player.PlayerEntity user, net.minecraft.util.Hand hand) {
        SpectreTridentBoost m = com.anormal.client.module.ModuleManager.getModule(SpectreTridentBoost.class);
        if (m == null || !m.isEnabled()) return false;
        // Spectre has the same gate on both modules: either one may allow dry-land riptide.
        if (!m.outOfWater.isEnabled() && !SpectreTridentUtil.allowOutOfWater()) return false;
        if (world == null || !world.isClient() || user == null || mc.player != user) return false;
        // only force-start when vanilla would refuse: riptide + dry land + no rain
        if (user.isTouchingWaterOrRain() || world.isRaining()) return false;
        ItemStack stack = user.getStackInHand(hand);
        if (!SpectreRiptide.isRiptide(stack)) return false;
        stack.set(DataComponentTypes.DAMAGE, (stack.get(DataComponentTypes.DAMAGE) == null ? 0
                : stack.get(DataComponentTypes.DAMAGE)) + 1);
        user.setCurrentHand(hand);
        return true;
    }
}
