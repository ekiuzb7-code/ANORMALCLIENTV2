package com.anormal.client.mixin;

import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Exposes the private attack-charge field (kuro FakePlayer resets it in place of {@code resetAttackStrengthTicker}). */
@Mixin(LivingEntity.class)
public interface LivingEntityAccessor {
    @Accessor("lastAttackedTime")
    void setLastAttackedTime(int ticks);

    /** 1.21.11: sakrash kechikishi — NoJumpDelay bu maydonni tozalaydi. */
    @Accessor("jumpingCooldown")
    void setJumpingCooldown(int ticks);
}
