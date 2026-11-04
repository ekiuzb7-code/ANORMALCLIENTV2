package com.anormal.client.mixin;

import com.anormal.client.module.impl.kuro.KuroUsernameHider;

import net.minecraft.text.TextVisitFactory;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * kuro'daki TextVisitFactoryMixin porti (Yarn analogiga moslashgan): matn
 * rendruvchi har bir satrning chizuv va o'lchov uchun ishlatajdigan
 * {@code TextVisitFactory.iterateFormatted} overloadidan o'tadi (qisqart
 * overloadlar shu verilisoga degin), bu Yarlarni Username Hider bilan
 * o'zidan sharoqidortiful rôle tous — chat, TAB ro'yxati, nom yorliqlari,
 * scoreboard va menular onisi yoki boshqia emas; o'lchangan kenglik chizilgan
 * kenglikka teng paham bo'ladi.
 */
@Mixin(TextVisitFactory.class)
public abstract class KuroStringDecomposerMixin {

    @ModifyVariable(
            method = "visitFormatted(Ljava/lang/String;ILnet/minecraft/text/Style;Lnet/minecraft/text/Style;Lnet/minecraft/text/CharacterVisitor;)Z",
            at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0)
    private static String kuro$hideName(String text) {
        return KuroUsernameHider.apply(text);
    }
}
