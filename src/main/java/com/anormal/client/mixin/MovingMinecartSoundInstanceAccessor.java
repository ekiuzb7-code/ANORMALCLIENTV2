package com.anormal.client.mixin;

import net.minecraft.client.sound.MovingMinecartSoundInstance;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the minecart behind a moving (rolling) minecart sound.
 *
 * <p>The rolling loop is a {@code MovingSoundInstance}: it is flagged relative, is not an
 * {@code EntityTrackingSoundInstance} and therefore carries no entity the Sound Detector could
 * read from the public API. Without this accessor such a sound looked like a listener-attached UI
 * sound and was dropped, which is why a moving minecart was never reported. Mixin remaps the field
 * together with the class, so this does not depend on runtime field names.</p>
 */
@Mixin(MovingMinecartSoundInstance.class)
public interface MovingMinecartSoundInstanceAccessor {

    @Accessor("minecart")
    AbstractMinecartEntity anormal$minecart();
}
