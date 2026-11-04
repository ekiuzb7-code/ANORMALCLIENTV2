package com.anormal.client.mixin;

import com.anormal.client.module.impl.kuro.KuroFreeze;

import net.minecraft.client.network.ClientCommonNetworkHandler;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Freeze moduli ("Move locally" rejimi yoqilganda) barcha chiqarilayotgan
 * harakat paketlarini yutib yuboradi — server sizni yoqilgandagi joyda
 * ushlab turadi. {@code KuroFreeze.holding()} boshqaradi; {@code require = 0}
 * bilan xaritalash o‘zgarsa sof "ta'sir emas" kamayishiga o‘tadi.
 */
@Mixin(ClientCommonNetworkHandler.class)
public abstract class KuroFreezePacketMixin {

    @Inject(method = "sendPacket", at = @At("HEAD"), cancellable = true, require = 0)
    private void kuro$swallowMove(Packet<?> packet, CallbackInfo ci) {
        if (packet instanceof PlayerMoveC2SPacket && KuroFreeze.holding()) ci.cancel();
    }
}
