package com.anormal.client.mixin;

import com.anormal.client.util.ChatFeed;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Feeds every line added to chat into {@link ChatFeed} so client modules can read server
 * messages (cooldown text, auction listings) without touching packets.
 *
 * <p>{@code require = 0}: if the method signature changes the mixin silently disables itself
 * instead of crashing the client; the dependent modules then simply never see a line.</p>
 */
@Mixin(ChatHud.class)
public abstract class ChatFeedMixin {

    @Inject(method = "addMessage(Lnet/minecraft/text/Text;)V", at = @At("HEAD"), require = 0)
    private void anormal$feedChat(Text message, CallbackInfo ci) {
        try {
            if (message != null) ChatFeed.onMessage(message.getString());
        } catch (Throwable ignored) {
        }
    }
}
