package com.anormal.client.module.impl.spectre;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;

import java.util.Set;

/**
 * Spectre port: "X-Ray" — only ore blocks stay visible.
 *
 * <p>Spectre swaps the chunk layer with an ore-only pass. This port keeps the opacity and
 * exposed-only rules and exposes the block test, which the client's existing block render hook
 * consults additively. Both X-rays never fight: Spectre's test runs first and answers only for
 * its own ore set.</p>
 */
public final class SpectreXray extends Module {
    private static final Set<Block> VISIBLE = Set.of(
            Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE,
            Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE,
            Blocks.COPPER_ORE, Blocks.DEEPSLATE_COPPER_ORE,
            Blocks.GOLD_ORE, Blocks.DEEPSLATE_GOLD_ORE, Blocks.NETHER_GOLD_ORE,
            Blocks.REDSTONE_ORE, Blocks.DEEPSLATE_REDSTONE_ORE,
            Blocks.LAPIS_ORE, Blocks.DEEPSLATE_LAPIS_ORE,
            Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE,
            Blocks.EMERALD_ORE, Blocks.DEEPSLATE_EMERALD_ORE,
            Blocks.ANCIENT_DEBRIS, Blocks.NETHER_QUARTZ_ORE,
            Blocks.CHEST, Blocks.TRAPPED_CHEST, Blocks.BARREL, Blocks.SPAWNER,
            Blocks.ENDER_CHEST, Blocks.WATER, Blocks.LAVA
    );

    private static final Set<Block> STONE_FAMILY = Set.of(
            Blocks.STONE, Blocks.DEEPSLATE, Blocks.TUFF, Blocks.ANDESITE, Blocks.DIORITE, Blocks.GRANITE,
            Blocks.CALCITE, Blocks.DRIPSTONE_BLOCK, Blocks.NETHERRACK, Blocks.BASALT, Blocks.BLACKSTONE,
            Blocks.END_STONE, Blocks.SMOOTH_BASALT, Blocks.TUFF_BRICKS
    );
    private static final Set<Block> SOIL_FAMILY = Set.of(
            Blocks.DIRT, Blocks.GRASS_BLOCK, Blocks.SAND, Blocks.GRAVEL, Blocks.CLAY, Blocks.SNOW,
            Blocks.SNOW_BLOCK, Blocks.SOUL_SAND, Blocks.SOUL_SOIL, Blocks.PODZOL, Blocks.MYCELIUM,
            Blocks.PACKED_ICE, Blocks.ICE
    );

    public final NumberSetting opacity = addSetting(new NumberSetting("Opacity", "How much of the world stays visible", 30.0, 0.0, 100.0, 5.0));
    public final BooleanSetting exposedOnly = addSetting(new BooleanSetting("Exposed Only", "Hide ores that are fully buried", false));

    public SpectreXray() {
        super("SpectreXray", "Spectre port: only ore blocks stay visible.", Category.SPECTRE);
    }

    /** True when this state must be hidden by the block render hook. */
    public static boolean hidesBlock(BlockState state) {
        SpectreXray module = com.anormal.client.module.ModuleManager.getModule(SpectreXray.class);
        if (module == null || !module.isEnabled()) return false;
        if (state.isAir()) return false;
        Block block = state.getBlock();
        if (VISIBLE.contains(block)) return false;
        // Opacity is the share of the surrounding world that stays visible: each step keeps a
        // whole block family, so the slider changes what you see instead of being a switch.
        double visible = module.opacity.getAsDouble();
        if (visible >= 100.0) return false;
        if (visible >= 75.0 && (state.isIn(net.minecraft.registry.tag.BlockTags.LOGS)
                || state.isIn(net.minecraft.registry.tag.BlockTags.LEAVES)
                || state.isIn(net.minecraft.registry.tag.BlockTags.PLANKS))) return false;
        if (visible >= 50.0 && SOIL_FAMILY.contains(block)) return false;
        if (visible >= 25.0 && STONE_FAMILY.contains(block)) return false;
        return true;
    }

    /** Convenience for callers that do have the position. */
    public static boolean hidesBlock(BlockState state, BlockPos pos) {
        SpectreXray module = com.anormal.client.module.ModuleManager.getModule(SpectreXray.class);
        if (module == null || !module.isEnabled()) return false;
        if (state.isAir() || VISIBLE.contains(state.getBlock())) return false;
        if (module.exposedOnly.isEnabled()) {
            var world = net.minecraft.client.MinecraftClient.getInstance().world;
            if (world != null) {
                boolean exposed = world.getBlockState(pos.up()).isAir() || world.getBlockState(pos.down()).isAir()
                        || world.getBlockState(pos.north()).isAir() || world.getBlockState(pos.south()).isAir()
                        || world.getBlockState(pos.east()).isAir() || world.getBlockState(pos.west()).isAir();
                // Fully buried blocks are the ones the flag hides; exposed ones stay visible.
                return !exposed;
            }
        }
        return true;
    }
}
