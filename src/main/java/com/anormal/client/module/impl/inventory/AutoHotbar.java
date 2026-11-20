package com.anormal.client.module.impl.inventory;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.InventoryClicks;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.SlotActionType;

public class AutoHotbar extends Module {
    public final ModeSetting activation = new ModeSetting("Activation", "Toggle sorts always, On Key sorts once per press", "Toggle", "Toggle", "On Key");
    public final BooleanSetting openInventory = new BooleanSetting("Open Inventory", "Opens inventory while reorganizing", false);
    public final NumberSetting delay = new NumberSetting("Delay", "Ticks between inventory clicks", 2.0, 0.0, 20.0, 1.0);
    public final ModeSetting hotbars = new ModeSetting("Hotbars", "Hotbar loadout to maintain", "Fighter", "Fighter", "Archer", "Potion");

    private static final String[] FIGHTER = {"_sword", "mace", "ender_pearl", "golden_apple", "splash_potion", "_pickaxe", "water_bucket", "cobweb", "torch"};
    private static final String[] ARCHER = {"bow", "arrow", "_sword", "ender_pearl", "golden_apple", "splash_potion", "_pickaxe", "water_bucket", "cobweb"};
    private static final String[] POTION = {"splash_potion", "mushroom_stew", "_sword", "ender_pearl", "golden_apple", "_pickaxe", "water_bucket", "cobweb", "bow"};

    private int cooldown = 0;
    /** "On Key" mode sorts one item per tick, then switches itself off. */
    private boolean onceMode = false;

    public AutoHotbar() {
        super("AutoHotbar", "Automatically organizes and slots hotbar items into preferred slots", Category.INVENTORY);
        addSetting(activation);
        addSetting(openInventory);
        addSetting(delay);
        addSetting(hotbars);
    }

    @Override
    public void onEnable() {
        cooldown = 0;
        onceMode = activation.is("On Key");
        if (!onceMode && mc.player != null && mc.currentScreen == null && openInventory.isEnabled()) {
            mc.setScreen(new InventoryScreen(mc.player));
        }
    }

    @Override
    public void onDisable() {
        onceMode = false;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) {
            setEnabled(false);
            return;
        }
        if (!onceMode && !activation.is("Toggle")) return;
        if (cooldown > 0) {
            cooldown--;
            return;
        }
        if (openInventory.isEnabled() && mc.currentScreen == null) {
            mc.setScreen(new InventoryScreen(mc.player));
            return;
        }
        // One click per tick, and only on the handler the server actually has open.
        if (!InventoryClicks.playerHandlerOpen()) return;
        if (sortOne()) {
            cooldown = Math.max(1, delay.getValue().intValue());
            return;
        }
        if (onceMode) {
            onceMode = false;
            setEnabled(false);
        }
    }

    private boolean sortOne() {
        String[] want = hotbars.is("Archer") ? ARCHER : hotbars.is("Potion") ? POTION : FIGHTER;
        for (int hot = 0; hot < 9; hot++) {
            ItemStack cur = mc.player.getInventory().getStack(hot);
            String frag = want[hot];
            if (!cur.isEmpty() && Registries.ITEM.getId(cur.getItem()).getPath().contains(frag)) continue;
            int src = findSource(frag, hot);
            if (src == -1) continue;
            if (!InventoryClicks.playerClick(src, hot, SlotActionType.SWAP)) return false;
            return true;
        }
        return false;
    }

    /** @return player-handler screen slot of a matching item, or -1 */
    private int findSource(String frag, int skipHot) {
        for (int i = 9; i < 36; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (!s.isEmpty() && Registries.ITEM.getId(s.getItem()).getPath().contains(frag)) return i;
        }
        for (int i = 0; i < 9; i++) {
            if (i == skipHot) continue;
            ItemStack s = mc.player.getInventory().getStack(i);
            if (!s.isEmpty() && Registries.ITEM.getId(s.getItem()).getPath().contains(frag)) return InventoryClicks.screenSlot(i);
        }
        return -1;
    }
}
