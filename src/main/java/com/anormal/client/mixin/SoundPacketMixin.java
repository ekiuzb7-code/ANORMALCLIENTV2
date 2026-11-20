package com.anormal.client.mixin;

import com.anormal.client.util.sound.SoundEventRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.s2c.play.PlaySoundFromEntityS2CPacket;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Second, independent capture point for Sound Detector: the sound packets themselves.
 *
 * <p>The mixer hook only sees a sound if the audio engine accepted it. Reading the packets means a
 * sound is still detected when the master volume / a category is at zero, when the sound system is
 * paused or when the sound is too far away to be audible. {@code RETURN} is used because the
 * handler first runs on the network thread and re-queues itself onto the client thread; only the
 * client-thread run reaches {@code RETURN}, so entity lookups here are safe.</p>
 */
@Mixin(ClientPlayNetworkHandler.class)
public class SoundPacketMixin {

    @Inject(method = "onPlaySound", at = @At("RETURN"), require = 0)
    private void anormal$packetSound(PlaySoundS2CPacket packet, CallbackInfo ci) {
        try {
            Identifier id = packet.getSound().getKey().map(k -> k.getValue()).orElse(null);
            if (id == null) id = packet.getSound().value().id();
            SoundEventRegistry.capturePacket(id, packet.getCategory(), packet.getX(), packet.getY(),
                    packet.getZ(), packet.getVolume(), packet.getPitch(), null);
        } catch (Throwable ignored) {
        }
    }

    @Inject(method = "onPlaySoundFromEntity", at = @At("RETURN"), require = 0)
    private void anormal$packetEntitySound(PlaySoundFromEntityS2CPacket packet, CallbackInfo ci) {
        try {
            var world = MinecraftClient.getInstance().world;
            if (world == null) return;
            Entity entity = world.getEntityById(packet.getEntityId());
            if (entity == null) return;
            Identifier id = packet.getSound().getKey().map(k -> k.getValue()).orElse(null);
            if (id == null) id = packet.getSound().value().id();
            SoundEventRegistry.capturePacket(id, packet.getCategory(), entity.getX(), entity.getY(),
                    entity.getZ(), packet.getVolume(), packet.getPitch(), entity);
        } catch (Throwable ignored) {
        }
    }
}
