package com.anormal.client.mixin;

import com.anormal.client.module.impl.spectre.SpectreNameProtect;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Spectre section: the chat layer of the Spectre Name Protect port.
 *
 * <p>Spectre rewrites names before the chat, tab list and nametag layers read them. This hook
 * rewrites the message text on its way into the chat only, and leaves every message untouched
 * while the module is off.</p>
 */
@Mixin(ChatHud.class)
public class SpectreNameProtectMixin {

    @ModifyVariable(method = "addMessage(Lnet/minecraft/text/Text;)V", at = @At("HEAD"), argsOnly = true, require = 0)
    private Text spectre$rename(Text message) {
        try {
            return SpectreNameProtect.rename(message);
        } catch (Throwable ignored) {
            return message;
        }
    }

    @ModifyVariable(
            method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V",
            at = @At("HEAD"), argsOnly = true, require = 0)
    private Text spectre$renameSigned(Text message) {
        try {
            return SpectreNameProtect.rename(message);
        } catch (Throwable ignored) {
            return message;
        }
    }
}
