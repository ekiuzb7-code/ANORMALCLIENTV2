package com.anormal.client.mixin;

import com.anormal.client.module.ModuleManager;
import com.anormal.client.module.impl.combat.BlockHit;
import com.anormal.client.module.impl.combat.Criticals;
import com.anormal.client.module.impl.combat.HitFlick;
import com.anormal.client.module.impl.combat.HitSelect;
import com.anormal.client.module.impl.combat.WTap;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The single choke point for every attack, whether it comes from a vanilla
 * click or from our own KillAura.
 *
 * <p>Five combat modules need to run at exactly this moment, so they are all
 * driven from here instead of from each module's own tick:</p>
 * <ul>
 *   <li>{@code HitSelect} - may hold the attack back for better timing, so it
 *       runs first and can cancel.</li>
 *   <li>{@code Criticals} - has to fake the fall <i>before</i> the server
 *       processes the hit, otherwise the crit does not count.</li>
 *   <li>{@code WTap}, {@code HitFlick} and {@code BlockHit} - all react
 *       <i>after</i> the hit has landed.</li>
 * </ul>
 */
@Mixin(ClientPlayerInteractionManager.class)
public class InteractionManagerMixin {

    @Inject(method = "attackEntity", at = @At("HEAD"), cancellable = true, require = 0)
    private void anormal$beforeAttack(PlayerEntity player, Entity target, CallbackInfo ci) {
        try {
            if (player == null || target == null) return;

            HitSelect hitSelect = ModuleManager.getModule(HitSelect.class);
            if (hitSelect != null && hitSelect.isEnabled() && !hitSelect.shouldAttack()) {
                ci.cancel();
                return;
            }

            Criticals criticals = ModuleManager.getModule(Criticals.class);
            if (criticals != null && criticals.isEnabled()) {
                criticals.doCrit();
            }
        } catch (Throwable ignored) {
        }
    }

    @Inject(method = "attackEntity", at = @At("RETURN"), require = 0)
    private void anormal$afterAttack(PlayerEntity player, Entity target, CallbackInfo ci) {
        try {
            if (player == null || target == null) return;

            WTap wTap = ModuleManager.getModule(WTap.class);
            if (wTap != null && wTap.isEnabled()) wTap.onAttack();

            HitFlick hitFlick = ModuleManager.getModule(HitFlick.class);
            if (hitFlick != null && hitFlick.isEnabled()) hitFlick.onAttack();

            BlockHit blockHit = ModuleManager.getModule(BlockHit.class);
            if (blockHit != null && blockHit.isEnabled()) blockHit.onAttack();

            com.anormal.client.module.impl.render.Effects effects = ModuleManager.getModule(com.anormal.client.module.impl.render.Effects.class);
            if (effects != null && effects.isEnabled()) effects.onAttack(target);

            // ReachDisplay: eye-to-hitbox distance of the attack that was just sent. This is the
            // single choke point (vanilla click and auras both go through here), and without a
            // caller the display had nothing to show and never rendered at all.
            try {
                net.minecraft.util.math.Vec3d eye = player.getEyePos();
                net.minecraft.util.math.Box bb = target.getBoundingBox();
                double dx = Math.max(bb.minX - eye.x, Math.max(0.0, eye.x - bb.maxX));
                double dy = Math.max(bb.minY - eye.y, Math.max(0.0, eye.y - bb.maxY));
                double dz = Math.max(bb.minZ - eye.z, Math.max(0.0, eye.z - bb.maxZ));
                com.anormal.client.module.impl.legit.ReachDisplay.updateReach(
                        Math.sqrt(dx * dx + dy * dy + dz * dz));
            } catch (Throwable ignored) {
            }

            // Kuro HitSounds: needs the hit moment to wait for the server's hurt confirmation.
            com.anormal.client.module.impl.kuro.KuroHitSounds.onAttack(target);

            if (target instanceof LivingEntity living) {
                com.anormal.client.module.impl.combat.BackTrackV2 backTrackV2 = ModuleManager.getModule(com.anormal.client.module.impl.combat.BackTrackV2.class);
                if (backTrackV2 != null && backTrackV2.isEnabled()) backTrackV2.onAttack(living);

                com.anormal.client.module.impl.world.BackTrack backTrack = ModuleManager.getModule(com.anormal.client.module.impl.world.BackTrack.class);
                if (backTrack != null && backTrack.isEnabled()) backTrack.onAttack(living);

                com.anormal.client.module.impl.world.FakeLag fakeLag = ModuleManager.getModule(com.anormal.client.module.impl.world.FakeLag.class);
                if (fakeLag != null && fakeLag.isEnabled()) fakeLag.onAttack(living);
            }
        } catch (Throwable ignored) {
        }
    }
}
