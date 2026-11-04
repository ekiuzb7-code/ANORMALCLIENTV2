package com.anormal.client.module.impl.kuro;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import org.lwjgl.glfw.GLFW;

/**
 * Slice matnini tahrirlash oynasi: matnni kiriting va Enter bos with uslubda
 * saqlanadi; ESC bekor qiladi. Kuro'da to'g'ridan-to'g'ri radial ekran ichida
 * edi; bu yerder oddiy Screen' bilan almashtirildi va saqlanganda radial
 * ekran yana ochiladi.
 */
public class KuroRadialEditScreen extends Screen {
    private final KuroRadialMenu module;
    private final int slot;
    private TextFieldWidget field;

    public KuroRadialEditScreen(KuroRadialMenu module, int slot) {
        super(Text.literal("Radial Menu — Slot " + (slot + 1)));
        this.module = module;
        this.slot = slot;
    }

    @Override
    protected void init() {
        field = new TextFieldWidget(client.textRenderer, width / 2 - 110, height / 2 - 12, 220, 20, Text.literal("Slot " + (slot + 1)));
        field.setMaxLength(256);
        field.setText(module.slot(slot));
        addDrawableChild(field);
        setInitialFocus(field);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0x88101018);
        context.drawCenteredTextWithShadow(client.textRenderer, "" , width / 2, height / 2 - 40, 0xFFE05AB8);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyInput key) {
        int keyCode = key.key();
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            module.setSlot(slot, field.getText());
            client.setScreen(new KuroRadialMenuScreen(module));    // menyuga qaytish
            return true;
        }
        return super.keyPressed(key);
    }

    @Override
    public void removed() {
        // Tahrirlash oynasi yopilganda menyu yana ochiladi (modul yondi emas)
        if (client.currentScreen == null && module.isEnabled()) client.setScreen(new KuroRadialMenuScreen(module));
        super.removed();
    }

    @Override
    public boolean shouldPause() { return false; }
}
