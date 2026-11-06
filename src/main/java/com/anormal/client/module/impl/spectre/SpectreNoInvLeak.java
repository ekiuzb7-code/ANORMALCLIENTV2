package com.anormal.client.module.impl.spectre;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.ColorUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.Box;

import java.util.ArrayList;
import java.util.List;

/**
 * Spectre port: "No Inv Leak" — hides your items on screen for screen shares.
 *
 * <p>Spectre rewrites the slot stacks before they reach the screen, so the real inventory never
 * appears in a capture. This port keeps the loadout (totem / random / empty), the fill-empty and
 * HUD rules and the "Stream Only" idea, and paints the masked placeholder stacks over the hotbar
 * and armour slots itself; the static flags are exposed for other screens to consult.</p>
 */
public final class SpectreNoInvLeak extends Module {
    public final ModeSetting style = addSetting(new ModeSetting("Style", "What the slots show", "Totem", "Totem", "Random", "Empty"));
    public final BooleanSetting fillEmpty = addSetting(new BooleanSetting("Fill Empty Slots", "Also mask empty slots", true));
    public final BooleanSetting streamOnly = addSetting(new BooleanSetting("Stream Only", "Only mask while a capture overlay is up", false));
    public final BooleanSetting borderless = addSetting(new BooleanSetting("Borderless Fullscreen", "Prefer borderless capture", true));
    public final BooleanSetting tooltips = addSetting(new BooleanSetting("Hide Tooltips", "Never show item tooltips", true));
    public final BooleanSetting itemName = addSetting(new BooleanSetting("Hide Item Name", "Hide the held item name", true));
    public final BooleanSetting selectedSlot = addSetting(new BooleanSetting("Hide Selected Slot", "Hide the selected slot frame", true));
    public final BooleanSetting hud = addSetting(new BooleanSetting("Spectre HUD", "Mask the HUD icons", true));

    private static final Item[] RANDOM_POOL = {
            Items.TOTEM_OF_UNDYING, Items.END_CRYSTAL, Items.OBSIDIAN, Items.GOLDEN_APPLE,
            Items.EXPERIENCE_BOTTLE, Items.ENDER_PEARL, Items.RESPAWN_ANCHOR, Items.GLOWSTONE
    };

    private static SpectreNoInvLeak active;

    public SpectreNoInvLeak() {
        super("SpectreNoInvLeak", "Spectre port: hides your items on screen for screen shares.", Category.SPECTRE);
    }

    @Override
    public void onEnable() {
        active = this;
    }

    @Override
    public void onDisable() {
        if (active == this) active = null;
    }

    /** The masked stack for a container slot, used by screens that want to consult the mask. */
    public static ItemStack masked(int slot, ItemStack original) {
        SpectreNoInvLeak module = active;
        if (module == null || original == null) return original;
        if (original.isEmpty() && !module.fillEmpty.isEnabled()) return original;
        return module.fake(slot);
    }

    public static boolean hidesTooltips() {
        return active != null && active.tooltips.isEnabled();
    }

    public static boolean hidesItemName() {
        return active != null && active.itemName.isEnabled();
    }

    public static boolean hidesSelectedSlot() {
        return active != null && active.selectedSlot.isEnabled();
    }

    public static boolean fillsEmpty() {
        SpectreNoInvLeak module = active;
        return module != null && module.fillEmpty.isEnabled() && !module.style.is("Empty");
    }

    private ItemStack fake(int slot) {
        if (style.is("Empty")) return ItemStack.EMPTY;
        if (style.is("Random")) {
            Item item = RANDOM_POOL[Math.floorMod(slot * 31 + 7, RANDOM_POOL.length)];
            return new ItemStack(item);
        }
        return new ItemStack(Items.TOTEM_OF_UNDYING);
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        var player = mc.player;
        if (player == null || !hud.isEnabled() || mc.options.hudHidden) return;
        if (streamOnly.isEnabled()) {
            // Borderless mode assumes the stream captures a fullscreen window, so the mask is only
            // needed there; with it off the mask follows the Stream Only switch everywhere.
            if (borderless.isEnabled() && !mc.getWindow().isFullscreen()) return;
        }

        int width = mc.getWindow().getScaledWidth();
        int height = mc.getWindow().getScaledHeight();
        int cell = 20;
        int hotbarWidth = cell * 9;
        int left = (width - hotbarWidth) / 2;
        int top = height - 22;
        int selected = player.getInventory().getSelectedSlot();

        List<Box> unused = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            if (!fillEmpty.isEnabled() && player.getInventory().getStack(i).isEmpty()) continue;
            if (selectedSlot.isEnabled() && i == selected) {
                // Hide the selection frame by covering the slot completely.
                context.fill(left + i * cell - 1, top - 1, left + i * cell + cell - 1, top + cell - 1, ColorUtils.rgba(0, 0, 0, 255));
            }
            context.fill(left + i * cell, top, left + i * cell + cell - 2, top + cell - 1, ColorUtils.rgba(28, 28, 34, 235));
        }
        unused.clear();
    }
}
