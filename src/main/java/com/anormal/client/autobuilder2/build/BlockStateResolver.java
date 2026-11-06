package com.anormal.client.autobuilder2.build;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;

import java.util.Map;
import java.util.Optional;

/**
 * Turns a schematic entry (a block name plus its stored properties) into the real
 * {@link BlockState} the builder should place.
 *
 * <p>Properties are applied one by one on top of the block's default state. Anything the running
 * game does not recognise — a property renamed by a version bump, a value that no longer exists,
 * a block from a mod that is not installed — is skipped rather than throwing, so a mostly-good
 * schematic still builds.
 */
public final class BlockStateResolver {
    private BlockStateResolver() {
    }

    /** Resolves a block state, or {@code null} when the id is unknown to this game version. */
    public static BlockState resolve(String blockId, Map<String, String> properties) {
        Identifier identifier = Identifier.tryParse(blockId);
        if (identifier == null || !Registries.BLOCK.containsId(identifier)) {
            return null;
        }
        Block block = Registries.BLOCK.get(identifier);
        BlockState state = block.getDefaultState();
        if (properties.isEmpty()) {
            return state;
        }
        for (Map.Entry<String, String> entry : properties.entrySet()) {
            Property<?> property = block.getStateManager().getProperty(entry.getKey());
            if (property != null) {
                state = apply(state, property, entry.getValue());
            }
        }
        return state;
    }

    /** True when the schematic state can be represented exactly by this game version. */
    public static boolean matches(BlockState actual, BlockState wanted) {
        return wanted != null && actual.equals(wanted);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static BlockState apply(BlockState state, Property<?> property, String value) {
        Optional<?> parsed = property.parse(value);
        if (parsed.isEmpty()) {
            return state;
        }
        // The property and value come from the same block's state manager, so the raw cast is sound.
        return (BlockState) state.with((Property) property, (Comparable) parsed.get());
    }
}
