package com.anormal.client.gui;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.module.ModuleManager;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.theme.ThemeManager;
import com.anormal.client.util.ColorUtils;
import com.anormal.client.util.RenderUtils;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.KeyInput;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public class HudEditorScreen extends Screen {
    private final Screen parentScreen;
    private HudElement draggingElement = null;
    private int dragOffsetX = 0;
    private int dragOffsetY = 0;

    public static class HudElement {
        public final String name;
        public final Module module;
        public final NumberSetting posX;
        public final NumberSetting posY;
        public int width;
        public int height;

        public HudElement(String name, Module module, NumberSetting posX, NumberSetting posY, int width, int height) {
            this.name = name;
            this.module = module;
            this.posX = posX;
            this.posY = posY;
            this.width = width;
            this.height = height;
        }

        public int getX() { return posX.getValue().intValue(); }
        public int getY() { return posY.getValue().intValue(); }

        public void setPos(int x, int y) {
            posX.setValue((double) x);
            posY.setValue((double) y);
        }

        public boolean isHovered(int mouseX, int mouseY) {
            int x = getX(), y = getY();
            return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
        }
    }

    private final List<HudElement> elements = new ArrayList<>();

    public HudEditorScreen(Screen parentScreen) {
        super(Text.literal("HUD Editor"));
        this.parentScreen = parentScreen;
    }

    /** Finds posX/posY NumberSettings on any module via reflection. Null if not movable. */
    public static NumberSetting[] findPosSettings(Module module) {
        NumberSetting px = null, py = null;
        for (Field f : module.getClass().getFields()) {
            if (f.getType() == NumberSetting.class) {
                try {
                    NumberSetting s = (NumberSetting) f.get(module);
                    if (s == null) continue;
                    String n = s.getName().toLowerCase();
                    if (n.contains("pos") && n.contains("x")) px = s;
                    else if (n.contains("pos") && n.contains("y")) py = s;
                } catch (Throwable ignored) {}
            }
        }
        if (px == null || py == null) return null;
        return new NumberSetting[]{px, py};
    }

    private static int[] defaultSize(Module module) {
        return switch (module.getName()) {
            case "Keystrokes" -> new int[]{70, 110};
            case "Radar" -> new int[]{74, 74};
            case "TargetInfo" -> new int[]{124, 40};
            case "InventoryOverlay" -> new int[]{168, 64};
            case "ArmorStatus" -> new int[]{60, 76};
            case "PotionStatus" -> new int[]{150, 60};
            case "Compass" -> new int[]{104, 18};
            case "ReachDisplay" -> new int[]{130, 16};
            case "DuelInfo" -> new int[]{130, 38};
            case "PartyOverlay" -> new int[]{150, 80};
            case "Rearview" -> new int[]{130, 36};
            case "Scoreboard" -> new int[]{130, 100};
            default -> new int[]{130, 18};
        };
    }

    @Override
    protected void init() {
        elements.clear();
        // Auto-discover EVERY movable overlay: any LEGIT module with posX/posY
        for (Module m : ModuleManager.getModulesByCategory(Category.LEGIT)) {
            NumberSetting[] pos = findPosSettings(m);
            if (pos == null) continue;
            int[] size = defaultSize(m);
            elements.add(new HudElement(m.getName(), m, pos[0], pos[1], size[0], size[1]));
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        RenderUtils.fill(context, 0, 0, width, height, ColorUtils.rgba(0, 0, 0, 160));

        String banner = "§6[HUD EDITOR] §fDrag = move  |  Right-click = on/off  |  §e[ESC]§f back";
        int bw = textRenderer.getWidth(banner);
        RenderUtils.fill(context, (width - bw) / 2 - 10, 10, (width + bw) / 2 + 10, 26, ColorUtils.rgba(15, 15, 20, 220));
        RenderUtils.drawBorder(context, (width - bw) / 2 - 10, 10, (width + bw) / 2 + 10, 26, 1, ThemeManager.getAccentColor());
        RenderUtils.drawText(context, textRenderer, banner, (width - bw) / 2, 15, 0xFFFFFFFF, true);

        // Drag follows mouse, clamped to screen
        if (draggingElement != null) {
            int nx = Math.max(0, Math.min(width - draggingElement.width, mouseX - dragOffsetX));
            int ny = Math.max(0, Math.min(height - draggingElement.height, mouseY - dragOffsetY));
            draggingElement.setPos(nx, ny);
        }

        // Live preview: draw each overlay at its real position, then selection frame on top
        for (HudElement el : elements) {
            boolean wasEnabled = el.module.isEnabled();
            try {
                el.module.setEnabled(true);
                el.module.onRender2D(context, delta);
            } catch (Throwable ignored) {
            } finally {
                if (!wasEnabled) el.module.setEnabled(false);
            }

            boolean hovered = el.isHovered(mouseX, mouseY) || el == draggingElement;
            int x = el.getX(), y = el.getY();
            if (!wasEnabled) {
                RenderUtils.fill(context, x, y, x + el.width, y + el.height, ColorUtils.rgba(60, 60, 60, 80));
            }
            int border = !wasEnabled ? ColorUtils.rgba(120, 120, 120, 180)
                    : (hovered ? ThemeManager.getAccentColor() : ColorUtils.rgba(100, 110, 130, 180));
            RenderUtils.drawBorder(context, x, y, x + el.width, y + el.height, 1, border);
            String label = (wasEnabled ? "§a● " : "§c○ ") + "§e" + el.name;
            RenderUtils.drawText(context, textRenderer, label, x + 4, y - 10 < 28 ? y + 4 : y - 10, 0xFFFFFFFF, true);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        return handleEditorClick(click.x(), click.y(), click.button());
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return handleEditorClick(mouseX, mouseY, button);
    }

    private boolean handleEditorClick(double mouseX, double mouseY, int button) {
        for (HudElement el : elements) {
            if (el.isHovered((int) mouseX, (int) mouseY)) {
                if (button == 0) {
                    draggingElement = el;
                    dragOffsetX = (int) mouseX - el.getX();
                    dragOffsetY = (int) mouseY - el.getY();
                    return true;
                } else if (button == 1) {
                    el.module.toggle();
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (click.button() == 0) {
            draggingElement = null;
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
            if (client != null) {
                client.setScreen(parentScreen);
            }
            return true;
        }
        return super.keyPressed(input);
    }
}
