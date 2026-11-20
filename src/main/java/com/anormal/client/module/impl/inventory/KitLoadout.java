package com.anormal.client.module.impl.inventory;

import com.anormal.client.lucid.StringSetting;
import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.KeybindSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.theme.ThemeManager;
import com.anormal.client.util.HudStyle;
import com.anormal.client.util.RenderUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

/**
 * Four saveable hotbar loadouts, applied with a key.
 *
 * <p>Saving stores the item id in every hotbar slot plus the slot you were holding. Applying
 * picks the saved slot when the same item is still there, otherwise it searches the hotbar for
 * that item, so a loadout survives items being moved around between deaths. Everything runs on
 * the client: the only packet sent is the vanilla {@code UpdateSelectedSlotC2SPacket} that a
 * hotbar key press would send anyway.</p>
 *
 * <p>The snapshot lives in a {@link StringSetting}, so loadouts survive a restart and can also
 * be typed in by hand (format {@code id|id|...|id#slot}).</p>
 */
public class KitLoadout extends Module {

    private static final int SLOTS = 9;

    public final ModeSetting active = new ModeSetting("Active Loadout",
            "Which loadout Save Key writes to and Load Key applies",
            "Loadout 1", "Loadout 1", "Loadout 2", "Loadout 3", "Loadout 4");
    public final KeybindSetting saveKey = new KeybindSetting("Save Key",
            "Saves the current hotbar into the active loadout", GLFW.GLFW_KEY_UNKNOWN);
    public final KeybindSetting loadKey = new KeybindSetting("Load Key",
            "Applies the active loadout", GLFW.GLFW_KEY_UNKNOWN);
    public final KeybindSetting cycleKey = new KeybindSetting("Cycle Key",
            "Switches to the next loadout (and applies it)", GLFW.GLFW_KEY_UNKNOWN);
    public final BooleanSetting strictSlots = new BooleanSetting("Strict Slots",
            "Only use the saved hotbar slot; never search other slots for the item", false);
    public final BooleanSetting chatFeedback = new BooleanSetting("Chat Feedback",
            "Print what was saved / applied in local chat", true);
    public final BooleanSetting showHud = new BooleanSetting("Show HUD", "Draw the loadout card", true);
    public final BooleanSetting showMissing = new BooleanSetting("Show Missing",
            "Mark saved items that are no longer in the hotbar", true);
    public final NumberSetting posX = new NumberSetting("Pos X", "X Position on screen", 6.0, 0.0, 1920.0, 1.0);
    public final NumberSetting posY = new NumberSetting("Pos Y", "Y Position on screen", 330.0, 0.0, 1080.0, 1.0);

    public final StringSetting data1 = new StringSetting("Loadout 1 Data",
            "Saved automatically by Save Key; format id|id|...|id#slot", "", 256);
    public final StringSetting data2 = new StringSetting("Loadout 2 Data",
            "Saved automatically by Save Key; format id|id|...|id#slot", "", 256);
    public final StringSetting data3 = new StringSetting("Loadout 3 Data",
            "Saved automatically by Save Key; format id|id|...|id#slot", "", 256);
    public final StringSetting data4 = new StringSetting("Loadout 4 Data",
            "Saved automatically by Save Key; format id|id|...|id#slot", "", 256);

    private boolean saveDown, loadDown, cycleDown;
    private String lastAction = "none";

    public KitLoadout() {
        super("KitLoadout", "Saves up to four hotbar kits and switches to the right slot with one key", Category.INVENTORY);
        addSetting(active);
        addSetting(saveKey);
        addSetting(loadKey);
        addSetting(cycleKey);
        addSetting(strictSlots);
        addSetting(chatFeedback);
        addSetting(showHud);
        addSetting(showMissing);
        addSetting(posX);
        addSetting(posY);
        addSetting(data1);
        addSetting(data2);
        addSetting(data3);
        addSetting(data4);
    }

    // ------------------------------------------------------------------
    //  Key polling
    // ------------------------------------------------------------------

    @Override
    public void onTickAlways() {
        if (mc.player == null || mc.getWindow() == null) return;
        boolean save = down(saveKey);
        boolean load = down(loadKey);
        boolean cycle = down(cycleKey);

        if (save && !saveDown) saveActive();
        if (load && !loadDown) applyActive();
        if (cycle && !cycleDown) {
            int index = activeIndex();
            index = (index + 1) % 4;
            active.setMode("Loadout " + (index + 1));
            applyActive();
        }
        saveDown = save;
        loadDown = load;
        cycleDown = cycle;
    }

    private boolean down(KeybindSetting setting) {
        if (setting == null) return false;
        int key = setting.getKey();
        if (key == GLFW.GLFW_KEY_UNKNOWN || key == 0) return false;
        try {
            long handle = mc.getWindow().getHandle();
            if (KeybindSetting.isMouseCode(key)) {
                return GLFW.glfwGetMouseButton(handle, KeybindSetting.mouseButton(key)) == GLFW.GLFW_PRESS;
            }
            return GLFW.glfwGetKey(handle, key) == GLFW.GLFW_PRESS;
        } catch (Throwable ignored) {
            return false;
        }
    }

    // ------------------------------------------------------------------
    //  Save / apply
    // ------------------------------------------------------------------

    private int activeIndex() {
        String mode = active.getValue();
        if (mode == null) return 0;
        if (mode.endsWith("2")) return 1;
        if (mode.endsWith("3")) return 2;
        if (mode.endsWith("4")) return 3;
        return 0;
    }

    private StringSetting dataFor(int index) {
        switch (index) {
            case 1: return data2;
            case 2: return data3;
            case 3: return data4;
            default: return data1;
        }
    }

    private void saveActive() {
        if (mc.player == null) return;
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < SLOTS; i++) {
            if (i > 0) builder.append('|');
            builder.append(itemId(i));
        }
        builder.append('#').append(mc.player.getInventory().getSelectedSlot());
        String text = builder.toString();
        dataFor(activeIndex()).set(text);
        lastAction = "saved " + active.getValue();
        feedback("Saved " + active.getValue() + " (" + filledSlots(text) + " items)");
    }

    private void applyActive() {
        if (mc.player == null) return;
        int index = activeIndex();
        String raw = dataFor(index).get();
        if (raw == null || raw.isBlank()) {
            feedback(active.getValue() + " is empty - press Save Key first");
            lastAction = "empty";
            return;
        }
        int hash = raw.lastIndexOf('#');
        int wantedSlot = -1;
        String body = raw;
        if (hash > 0) {
            body = raw.substring(0, hash);
            try {
                wantedSlot = Integer.parseInt(raw.substring(hash + 1).trim());
            } catch (Throwable ignored) {
            }
        }
        String[] ids = body.split("\\|", -1);
        String wantedId = (wantedSlot >= 0 && wantedSlot < ids.length) ? ids[wantedSlot] : "";

        if (wantedId == null || wantedId.isEmpty()) {
            feedback(active.getValue() + " has no primary item saved");
            lastAction = "no primary";
            return;
        }

        int target = -1;
        if (wantedSlot >= 0 && wantedSlot < SLOTS && wantedId.equals(itemId(wantedSlot))) {
            target = wantedSlot;
        }
        if (target < 0 && !strictSlots.isEnabled()) {
            for (int i = 0; i < SLOTS; i++) {
                if (wantedId.equals(itemId(i))) {
                    target = i;
                    break;
                }
            }
        }
        if (target < 0) {
            feedback(active.getValue() + ": " + shortName(wantedId) + " is not in the hotbar");
            lastAction = "missing";
            return;
        }
        select(target);
        lastAction = "applied " + active.getValue();
        feedback("Equipped " + active.getValue() + " (slot " + (target + 1) + ", "
                + shortName(wantedId) + ")");
    }

    private void select(int slot) {
        if (mc.player == null) return;
        try {
            if (mc.player.getInventory().getSelectedSlot() == slot) return;
            mc.player.getInventory().setSelectedSlot(slot);
            if (mc.player.networkHandler != null) {
                mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(slot));
            }
        } catch (Throwable ignored) {
        }
    }

    private String itemId(int slot) {
        try {
            ItemStack stack = mc.player.getInventory().getStack(slot);
            if (stack == null || stack.isEmpty()) return "";
            Identifier id = Registries.ITEM.getId(stack.getItem());
            return id == null ? "" : id.toString();
        } catch (Throwable t) {
            return "";
        }
    }

    private int filledSlots(String data) {
        int count = 0;
        String body = data;
        int hash = body.lastIndexOf('#');
        if (hash > 0) body = body.substring(0, hash);
        for (String id : body.split("\\|", -1)) {
            if (id != null && !id.isEmpty()) count++;
        }
        return count;
    }

    private String shortName(String id) {
        if (id == null || id.isEmpty()) return "empty";
        String name = id;
        int colon = name.indexOf(':');
        if (colon >= 0) name = name.substring(colon + 1);
        name = name.replace('_', ' ');
        if (name.length() > 14) name = name.substring(0, 14);
        return name;
    }

    private void feedback(String message) {
        if (!chatFeedback.isEnabled() || mc.inGameHud == null) return;
        try {
            mc.inGameHud.getChatHud().addMessage(Text.literal("[Kit] " + message));
        } catch (Throwable ignored) {
        }
    }

    // ------------------------------------------------------------------
    //  HUD
    // ------------------------------------------------------------------

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (!showHud.isEnabled() || mc.player == null || mc.textRenderer == null) return;
        int index = activeIndex();
        String raw = dataFor(index).get();
        String[] ids = new String[SLOTS];
        for (int i = 0; i < SLOTS; i++) ids[i] = "";
        int wantedSlot = -1;
        if (raw != null && !raw.isBlank()) {
            int hash = raw.lastIndexOf('#');
            String body = raw;
            if (hash > 0) {
                body = raw.substring(0, hash);
                try {
                    wantedSlot = Integer.parseInt(raw.substring(hash + 1).trim());
                } catch (Throwable ignored) {
                }
            }
            String[] parts = body.split("\\|", -1);
            for (int i = 0; i < SLOTS && i < parts.length; i++) ids[i] = parts[i];
        }

        int w = 196;
        int h = 54;
        int[] at = HudStyle.place(posX.getValue().intValue(), posY.getValue().intValue(), w, h);
        int x = at[0], y = at[1];
        int accent = ThemeManager.getAccentColor();

        HudStyle.panel(context, x, y, w, h, accent);
        HudStyle.title(context, mc.textRenderer, active.getValue(), x + 10, y + 7);
        String state = lastAction.isEmpty() ? "ready" : lastAction;
        int sw = mc.textRenderer.getWidth(state);
        RenderUtils.drawText(context, mc.textRenderer, state, x + w - 10 - sw, y + 7, 0xFF6E7482, false);

        int cell = 18;
        int startX = x + 10;
        int rowY = y + 20;
        for (int i = 0; i < SLOTS; i++) {
            int cx = startX + i * cell;
            boolean primary = i == wantedSlot;
            boolean saved = ids[i] != null && !ids[i].isEmpty();
            boolean present = saved && ids[i].equals(itemId(i));
            RenderUtils.fillRounded(context, cx, rowY, cell - 2, cell - 2, 3,
                    primary ? (accent & 0x00FFFFFF) | 0x66000000 : 0x33FFFFFF);
            if (saved) {
                ItemStack stack = stackOf(ids[i]);
                if (!stack.isEmpty()) {
                    try {
                        context.drawItem(stack, cx + 1, rowY + 1);
                    } catch (Throwable ignored) {
                    }
                }
                if (showMissing.isEnabled() && !present) {
                    RenderUtils.drawBorder(context, cx, rowY, cx + cell - 2, rowY + cell - 2, 1, 0xFFF87171);
                }
            }
            if (i == mc.player.getInventory().getSelectedSlot()) {
                RenderUtils.drawBorder(context, cx - 1, rowY - 1, cx + cell - 1, rowY + cell - 1, 1, 0xFF4ADE80);
            }
        }

        String hint = "Save Key = snapshot, Load Key = apply";
        RenderUtils.drawText(context, mc.textRenderer, hint, x + 10, y + 40, 0xFF5E6470, false);
    }

    private ItemStack stackOf(String id) {
        if (id == null || id.isEmpty()) return ItemStack.EMPTY;
        try {
            Identifier identifier = Identifier.tryParse(id);
            if (identifier == null || !Registries.ITEM.containsId(identifier)) return ItemStack.EMPTY;
            return new ItemStack(Registries.ITEM.get(identifier));
        } catch (Throwable t) {
            return ItemStack.EMPTY;
        }
    }
}
