package com.anormal.client.mixin;

import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Exposes the protected damage-tilt field (kuro FakePlayer sets it in place of {@code animateHurt}). */
@Mixin(PlayerEntity.class)
public interface PlayerEntityAccessor {
    @Accessor("damageTiltYaw")
    void setDamageTiltYaw(float yaw);
}
