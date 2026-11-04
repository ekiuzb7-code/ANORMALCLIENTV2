package com.anormal.client.mixin;

import com.anormal.client.module.impl.kuro.KuroChatMentions;
import com.anormal.client.module.impl.kuro.KuroChatTimestamps;
import com.anormal.client.module.impl.kuro.KuroUsernameHider;

import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.text.Text;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * kuro'dagi ChatComponentMixin porti (Yarn analogiga moslashgan): ChatHud'ga
 * qo'shilib bosqichida har bir xabar (o'yinchi, sistemi va boshqalarning)
 * Mentions sozlamalarini o'z ichiga oladi va Timestamps bilanda stamp'lanadi.
 * Bir marta stamp'lanadi: ko'rsatish bosqichida emas, qo'shilish paytida.
 */
@Mixin(ChatHud.class)
public abstract class KuroChatHudMixin {

    @ModifyVariable(
            method = "addMessage(Lnet/minecraft/text/Text;)V",
            at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0)
    private Text kuro$processMessage(Text message) {
        if (KuroChatMentions.isAlerting()) return message;
        return KuroChatTimestamps.stamp(KuroChatMentions.process(message));
    }
}