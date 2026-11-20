package com.anormal.client.mixin;

import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Reads the chat input text so completion evidence is only used for the command being typed. */
@Mixin(ChatScreen.class)
public interface ChatScreenAccessor {

    @Accessor("chatField")
    TextFieldWidget anormal$chatField();
}
