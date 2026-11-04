package com.anormal.client.mixin;

import com.anormal.client.util.kuro.KuroServerSync;
import net.minecraft.client.network.ClientCommonNetworkHandler;
import net.minecraft.network.packet.s2c.common.CommonPingS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hooks every server transaction (ping) so KuroServerSync can pace automation by what the
 * server has actually acknowledged. TAIL = game thread only (the network-thread pass is
 * thrown away before the end); the pong is already queued by then, and the sync only needs
 * to know a transaction cycle happened.
 */
@Mixin(ClientCommonNetworkHandler.class)
public abstract class KuroClientCommonNetworkHandlerMixin {
    @Inject(method = "onPing", at = @At("TAIL"), require = 0)
    private void anormal$transaction(CommonPingS2CPacket packet, CallbackInfo ci) {
        KuroServerSync.onTransaction();
    }
}
