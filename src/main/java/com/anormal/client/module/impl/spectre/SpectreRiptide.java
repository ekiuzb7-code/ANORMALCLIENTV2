package com.anormal.client.module.impl.spectre;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import org.lwjgl.glfw.GLFW;

/**
 * Spectre port: "Riptide" — uses a riptide trident while you hold right-click.
 *
 * <p>GrimRiptide port along: the client only starts the use cycle when a Grim-style
 * server accepts it (you must be in water/rain, or touching a fluid block), and the
 * release waits for the minimum charge before repeating (>= 9 tick cadence).</p>
 */
public final class SpectreRiptide extends Module {
    public final NumberSetting minTicks = addSetting(new NumberSetting("Min Ticks",
            "Minimum ticks between two use cycles", 9.0, 1.0, 40.0, 1.0));
    public final NumberSetting chargeTicks = addSetting(new NumberSetting("Charge Ticks",
            "Ticks the riptide has to charge before it is released", 10.0, 1.0, 40.0, 1.0));

    private int releasedTick = -100;

    public SpectreRiptide() {
        super("SpectreRiptide", "Spectre port: uses a riptide trident while you hold right-click.", Category.SPECTRE);
    }

    @Override
    public void onTick() {
        var player = mc.player;
        if (player == null || mc.interactionManager == null || mc.currentScreen != null) return;
        if (GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), 1) != GLFW.GLFW_PRESS) return;
        if (!GrimRiptideClientAccepts()) return;

        Hand hand = hand(player);
        if (hand == null) return;

        if (!player.isUsingItem()) {
            if (player.age - releasedTick >= minTicks()) {
                mc.interactionManager.interactItem(player, hand);
            }
        } else if (isRiptide(player.getActiveItem())) {
            if (player.getItemUseTime() >= chargeTicks()) {
                if (player.age - releasedTick >= minTicks()) {
                    mc.interactionManager.stopUsingItem(player);
                    releasedTick = player.age;
                }
            }
        }
    }

    private int minTicks() {
        return (int) minTicks.getAsDouble();
    }

    /** Spectre: {@code max(9, TridentUtilModule.minChargeTicks(chargeTicks))}. */
    private int chargeTicks() {
        return Math.max(9, SpectreTridentUtil.minChargeTicks((int) chargeTicks.getAsDouble()));
    }

    private static Hand hand(net.minecraft.client.network.ClientPlayerEntity player) {
        if (isRiptide(player.getMainHandStack())) return Hand.MAIN_HAND;
        return isRiptide(player.getOffHandStack()) ? Hand.OFF_HAND : null;
    }

    /** Port of Spectre's GrimRiptide.clientAccepts(). */
    private static boolean GrimRiptideClientAccepts() {
        var player = mc.player;
        if (player == null || mc.world == null) return false;
        if (player.isTouchingWaterOrRain() || mc.world.isRaining()) return true;

        Box box = player.getBoundingBox().expand(0.1);
        BlockPos min = BlockPos.ofFloored(box.minX, box.minY, box.minZ);
        BlockPos max = BlockPos.ofFloored(box.maxX, box.maxY, box.maxZ);
        for (BlockPos pos : BlockPos.iterate(min, max)) {
            if (mc.world.getFluidState(pos).isIn(FluidTags.WATER)) return true;
        }
        return false;
    }

    /** Port of Spectre's GrimRiptide.isRiptide(). */
    public static boolean isRiptide(ItemStack stack) {
        if (stack == null || !stack.isOf(Items.TRIDENT)) return false;
        try {
            net.minecraft.registry.Registry<net.minecraft.enchantment.Enchantment> reg =
                    mc.world.getRegistryManager().getOrThrow(net.minecraft.registry.RegistryKeys.ENCHANTMENT);
            net.minecraft.registry.entry.RegistryEntry<net.minecraft.enchantment.Enchantment> riptide =
                    reg.getOrThrow(Enchantments.RIPTIDE);
            return EnchantmentHelper.getLevel(riptide, stack) > 0;
        } catch (Throwable t) {
            return false;
        }
    }
}
