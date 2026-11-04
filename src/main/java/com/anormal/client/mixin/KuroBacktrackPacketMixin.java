package com.anormal.client.mixin;

import com.anormal.client.module.impl.kuro.KuroBacktrack;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.EntityPositionS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityPositionSyncS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Ported one-to-one from kuro (mixin.ClientPlayNetworkHandlerMixin, Backtrack part).
 *
 * <p>Backtrack (Delay packets mode): the network-thread pass is left alone — each handler first
 * calls {@code NetworkThreadUtils.forceMainThread}, which schedules the packet onto the game
 * thread and throws {@code OffThreadException}; on the game thread Backtrack may take the packet
 * and give it back later ({@code KuroBacktrack.active()} is false on the network thread).</p>
 *
 * <p>require = 0: if the handler names ever change, the injection is skipped instead of
 * crashing the game.</p>
 */
@Mixin(ClientPlayNetworkHandler.class)
public abstract class KuroBacktrackPacketMixin {

    @Inject(method = "onEntity", at = @At("HEAD"), cancellable = true, require = 0)
    private void anormal$btMove(EntityS2CPacket packet, CallbackInfo ci) {
        ClientPlayNetworkHandler self = (ClientPlayNetworkHandler) (Object) this;
        net.minecraft.entity.Entity e = packet.getEntity(net.minecraft.client.MinecraftClient.getInstance().world);
        if (e != null && KuroBacktrack.intercept(self, packet, e.getId())) ci.cancel();
    }

    @Inject(method = "onEntityPosition", at = @At("HEAD"), cancellable = true, require = 0)
    private void anormal$btTeleport(EntityPositionS2CPacket packet, CallbackInfo ci) {
        if (KuroBacktrack.intercept((ClientPlayNetworkHandler) (Object) this, packet, packet.entityId()))
            ci.cancel();
    }

    @Inject(method = "onEntityPositionSync", at = @At("HEAD"), cancellable = true, require = 0)
    private void anormal$btSync(EntityPositionSyncS2CPacket packet, CallbackInfo ci) {
        if (KuroBacktrack.intercept((ClientPlayNetworkHandler) (Object) this, packet, packet.id()))
            ci.cancel();
    }
}
