package com.anormal.client.mixin;

import com.anormal.client.module.impl.kuro.KuroTotemPops;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Totem Pops: entity status paketlarini (35 = totem, 3 = o'lim) KuroTotemPops'ga uzatadi.
 * HEAD'da, chunki o'yin entity'ni paket ishlovidan keyin olib tashlashi mumkin.
 */
@Mixin(ClientPlayNetworkHandler.class)
public abstract class KuroTotemPopsMixin {
    @Inject(method = "onEntityStatus", at = @At("HEAD"), require = 0)
    private void anormal$entityStatus(EntityStatusS2CPacket packet, CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return;
        try {
            KuroTotemPops.onStatus(packet.getEntity(mc.world), packet.getStatus());
        } catch (Throwable ignored) {
        }
    }
}
