package com.anormal.client.mixin;

import com.anormal.client.util.staff.MsgCompletionObserver;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.CommandSuggestionsS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Passive hook: only observes suggestions the server already sent in response to the user's own
 * tab completion. It never writes to the connection and never requests anything.
 */
@Mixin(ClientPlayNetworkHandler.class)
public class StaffCompletionMixin {

    @Inject(method = "onCommandSuggestions", at = @At("TAIL"))
    private void anormal$captureSuggestions(CommandSuggestionsS2CPacket packet, CallbackInfo ci) {
        MsgCompletionObserver.inspect(packet);
    }
}
