package com.anormal.client.mixin;

import com.anormal.client.util.sound.SoundEventRegistry;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.WorldEventS2CPacket;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Third capture point for Sound Detector: block world events.
 *
 * <p>Breaking a block is not delivered as a {@code PlaySoundS2CPacket}. The server sends a
 * {@code WorldEventS2CPacket} with event id 2001 (block broken) and the client plays the break
 * sound locally from the block's sound group. Neither the mixer hook nor the sound-packet hook
 * can see the id in that case, so a block being broken by another player stayed invisible to the
 * detector. Reading the event here recovers the exact sound id from the block state.</p>
 */
@Mixin(ClientPlayNetworkHandler.class)
public class WorldEventSoundMixin {

    /** {@code net.minecraft.world.WorldEvents.BLOCK_BROKEN} */
    private static final int BLOCK_BROKEN = 2001;

    @Inject(method = "onWorldEvent", at = @At("HEAD"), require = 0)
    private void anormal$worldEventSound(WorldEventS2CPacket packet, CallbackInfo ci) {
        if (packet == null || packet.getEventId() != BLOCK_BROKEN) return;
        try {
            BlockPos pos = packet.getPos();
            BlockState state = Block.getStateFromRawId(packet.getData());
            if (pos == null || state == null || state.isAir()) return;
            BlockSoundGroup group = state.getSoundGroup();
            SoundEvent sound = group.getBreakSound();
            if (sound == null) return;
            Identifier id = sound.id();
            if (id == null) return;
            SoundEventRegistry.capturePacket(id, SoundCategory.BLOCKS,
                    pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    group.getVolume(), group.getPitch(), null);
        } catch (Throwable ignored) {
        }
    }
}
