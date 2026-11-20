package com.anormal.client.mixin;

import net.minecraft.client.sound.EntityTrackingSoundInstance;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the entity behind an {@link EntityTrackingSoundInstance}.
 *
 * <p>Sound Detector needs the source of a sound when the client actually knows one (player
 * footsteps, mob sounds). The field is only reachable through this accessor, which Mixin remaps
 * together with the class, so it does not depend on runtime field names.</p>
 */
@Mixin(EntityTrackingSoundInstance.class)
public interface EntityTrackingSoundInstanceAccessor {

    @Accessor("entity")
    Entity anormal$trackedEntity();
}
