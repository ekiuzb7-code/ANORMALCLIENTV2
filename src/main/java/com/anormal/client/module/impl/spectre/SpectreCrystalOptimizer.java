package com.anormal.client.module.impl.spectre;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.module.ModuleManager;
import com.anormal.client.setting.BooleanSetting;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;

/**
 * Spectre port: "Crystal Optimizer" — removes crystals client-side as soon as your hit
 * breaks them.
 *
 * <p>Spectre waits for its own attack packet and then re-checks the vanilla damage rules;
 * this port hooks the attack itself through {@code ClientPlayerInteractionManagerMixin} and
 * applies the same gates: alive, in range, holding a weapon that actually deals damage, and
 * the crystal not already gone or beaming.</p>
 */
public final class SpectreCrystalOptimizer extends Module {
    private static final double RANGE_SLACK = 1.0;

    public final BooleanSetting anyWeapon = addSetting(new BooleanSetting("Any Weapon", "Remove crystals even when the held item deals no damage", false));

    public SpectreCrystalOptimizer() {
        super("SpectreCrystalOptimizer", "Spectre port: removes crystals client-side as soon as your hit breaks them.", Category.SPECTRE);
    }

    /** Called from the attack hook before vanilla sends the packet. */
    public static void onAttack(Entity target) {
        if (!(target instanceof EndCrystalEntity crystal)) return;
        SpectreCrystalOptimizer module = ModuleManager.getModule(SpectreCrystalOptimizer.class);
        if (module == null || !module.isEnabled()) return;

        var player = mc().player;
        var world = mc().world;
        if (player == null || world == null) return;
        if (crystal.isRemoved() || crystal.getBeamTarget() != null) return;
        if (!player.isAlive() || player.isSpectator()) return;
        if (player.distanceTo(crystal) > 6.0 + RANGE_SLACK) return;

        ItemStack weapon = player.getMainHandStack();
        if (!module.anyWeapon.isEnabled() && !dealsDamage(weapon)) return;
        world.removeEntity(crystal.getId(), Entity.RemovalReason.DISCARDED);
    }

    private static net.minecraft.client.MinecraftClient mc() {
        return net.minecraft.client.MinecraftClient.getInstance();
    }

    private static boolean dealsDamage(ItemStack stack) {
        if (stack.isEmpty()) return false;
        String name = stack.getItem().toString().toLowerCase();
        return name.contains("sword") || name.contains("axe") || name.contains("mace")
                || name.contains("trident") || name.contains("spear") || name.contains("pickaxe")
                || name.contains("shovel") || name.contains("hoe");
    }

    static {
        // keep MathHelper referenced for the range helper parity with Spectre's clamp path
        MathHelper.clamp(0.0F, 0.0F, 1.0F);
    }
}
