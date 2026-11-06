package com.anormal.client.mixin;

import com.anormal.client.module.ModuleManager;
import com.anormal.client.module.impl.world.FastBreak;
import com.anormal.client.module.impl.combat.TargetLock;
import com.anormal.client.module.impl.world.HitSwap;
import com.anormal.client.module.impl.world.InstantMine;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Removes the vanilla delay between broken blocks ("Fast" mode of InstantMine).
 *
 * <p>Vanilla sets {@code blockBreakingCooldown} after each breaking tick, which
 * is what makes you wait ~5 ticks per block even with a good tool. Zeroing it
 * after every call makes mining run at the tool's real speed.</p>
 */
@Mixin(ClientPlayerInteractionManager.class)
public class ClientPlayerInteractionManagerMixin {

    @Shadow
    private int blockBreakingCooldown;

    /**
     * HitSwap has to switch the slot BEFORE the attack packet leaves the client.
     *
     * <p>The old implementation did it from {@code onTick}, which runs after the click was already
     * processed - so the hit always went out with the previous weapon. This hook runs at the head
     * of the attack itself, which is the only place where the swap can still affect the packet.</p>
     */
    @Inject(method = "attackEntity", at = @At("HEAD"), require = 0)
    private void anormal$hitSwap(PlayerEntity player, Entity target, CallbackInfo ci) {
        try {
            // kuro FakePlayer: hitting the client-side dummy is fully simulated client-side.
            if (com.anormal.client.module.impl.kuro.KuroFakePlayer.onAttack(target)) ci.cancel();

            HitSwap hitSwap = ModuleManager.getModule(HitSwap.class);
            if (hitSwap != null && hitSwap.isEnabled()) hitSwap.beforeAttack(target);

            // Spectre section: hit particles on every landed attack.
            com.anormal.client.module.impl.spectre.SpectreHitParticles.onAttack(target);

            // Spectre section: W Tap Recode arms its tap on every landed attack.
            com.anormal.client.module.impl.spectre.SpectreWTapRecode.onAttack();
            // Spectre section: Sprint's Unsprint On Hit reacts to the same hit.
            com.anormal.client.module.impl.spectre.SpectreSprint.onAttack();
            com.anormal.client.module.impl.spectre.SpectreTargetEsp.onAttack(target);
            com.anormal.client.module.impl.spectre.SpectreGlowEsp.onAttack(target);

            // Spectre section: Crystal Optimizer removes the crystal, Mace swaps for a smash hit.
            com.anormal.client.module.impl.spectre.SpectreCrystalOptimizer.onAttack(target);
            com.anormal.client.module.impl.spectre.SpectreMace spectreMace =
                    ModuleManager.getModule(com.anormal.client.module.impl.spectre.SpectreMace.class);
            if (spectreMace != null && spectreMace.isEnabled()) spectreMace.beforeAttack(player, target);

            // A manual attack is the natural way to choose a lock target.
            TargetLock targetLock = ModuleManager.getModule(TargetLock.class);
            if (targetLock != null && targetLock.isEnabled()) targetLock.lockOn(target);
        } catch (Throwable ignored) {
        }
    }

    @Inject(method = "updateBlockBreakingProgress", at = @At("RETURN"), require = 0)
    private void anormal$removeBreakingCooldown(BlockPos pos, Direction direction,
                                                CallbackInfoReturnable<Boolean> cir) {
        try {
            InstantMine instantMine = ModuleManager.getModule(InstantMine.class);
            if (instantMine != null && instantMine.isFast()) {
                blockBreakingCooldown = 0;
                return;
            }
            // FastBreak uses the same cooldown removal; its early finish is sent from the module.
            FastBreak fastBreak = ModuleManager.getModule(FastBreak.class);
            if (fastBreak != null && fastBreak.isEnabled()) {
                blockBreakingCooldown = 0;
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * kuro {@code AutoTunnel}: vanilla har tickda hujum tugmasi bosilmagan bo'lsa
     * blok qazishni bekor qiladi; Auto Tunnel progressini saqlaydi.
     */
    @Inject(method = "cancelBlockBreaking", at = @At("HEAD"), cancellable = true, require = 0)
    private void kuro$keepBreaking(CallbackInfo ci) {
        try {
            if (com.anormal.client.module.impl.kuro.KuroAutoTunnel.breaking()) ci.cancel();
        } catch (Throwable ignored) {
        }
    }

    /**
     * Spectre section: Friend Guard. Hitting a friend is cancelled before the packet leaves,
     * which is the same interception point Spectre uses.
     */
    @Inject(method = "attackEntity", at = @At("HEAD"), cancellable = true, require = 0)
    private void spectre$friendGuard(PlayerEntity player, Entity target, CallbackInfo ci) {
        try {
            if (com.anormal.client.module.impl.spectre.SpectreFriendGuard.intercepts(target)) ci.cancel();
        } catch (Throwable ignored) {
        }
    }
}
