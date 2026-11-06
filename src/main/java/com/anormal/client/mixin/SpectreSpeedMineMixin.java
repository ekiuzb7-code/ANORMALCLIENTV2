package com.anormal.client.mixin;

import com.anormal.client.module.impl.spectre.SpectreSpeedMine;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Spectre-section mining hook (Spectre {@code MixinSpeedMineMultiPlayerGameMode} equivalent).
 *
 * <p>Vanilla sets {@code blockBreakingCooldown} to 5 after every finished block and
 * early-returns from {@code updateBlockBreakingProgress} while it is positive. Spectre
 * zeroes that gate ({@code dropsDelay}) for blocks the module affects, so the next block
 * can be started immediately; {@code Keep Instant Delay} leaves the gate alone for blocks
 * that already break in a single tick.</p>
 */
@Mixin(ClientPlayerInteractionManager.class)
public abstract class SpectreSpeedMineMixin {

    @Shadow
    private int blockBreakingCooldown;

    @Inject(method = "updateBlockBreakingProgress", at = @At("HEAD"))
    private void anormal$spectreSkipBreakDelay(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        try {
            if (blockBreakingCooldown <= 0) return;
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null || mc.player.isCreative()) return;
            if (SpectreSpeedMine.dropsDelayAt(pos)) {
                blockBreakingCooldown = 0;
            }
        } catch (Throwable ignored) {
        }
    }
}
