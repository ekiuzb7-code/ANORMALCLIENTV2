package com.anormal.client.module.impl.inventory;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.util.InventoryClicks;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.EquippableComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.SlotActionType;

public class InventoryManager extends Module {
    public final ModeSetting activation = new ModeSetting("Activation", "Toggle runs always, On Key bursts after press", "Toggle", "Toggle", "On Key");
    public final BooleanSetting openInventory = new BooleanSetting("Open Inventory", "Acts on its own without manual inventory", true);
    public final BooleanSetting combatCheck = new BooleanSetting("Combat Check", "Skips managing while in danger", true);
    public final NumberSetting clickDelay = new NumberSetting("Click Delay", "Ticks between inventory clicks", 2.0, 0.0, 20.0, 1.0);
    public final ModeSetting presets = new ModeSetting("Inventory Presets", "Loadout the manager maintains", "Balanced", "Balanced", "Rusher", "Fighter");

    private static final String[] BALANCED = {"_sword", "ender_pearl", "golden_apple", "splash_potion", "_pickaxe", "water_bucket", "cobweb", "torch", "mushroom_stew"};
    private static final String[] RUSHER = {"_pickaxe", "torch", "_sword", "ender_pearl", "golden_apple", "water_bucket", "cobweb", "splash_potion", "mushroom_stew"};
    private static final String[] FIGHTER = {"_sword", "mace", "ender_pearl", "golden_apple", "splash_potion", "_pickaxe", "water_bucket", "cobweb", "torch"};

    private int cooldown = 0;
    private int burst = 0;
    /** Cursor swap spread over ticks so survival servers accept it. */
    private final InventoryClicks.SlotMover mover = new InventoryClicks.SlotMover();

    public InventoryManager() {
        super("InventoryManager", "Intelligent inventory manager combining cleaner, auto armor and hotbar sorting", Category.INVENTORY);
        addSetting(activation);
        addSetting(openInventory);
        addSetting(combatCheck);
        addSetting(clickDelay);
        addSetting(presets);
    }

    @Override
    public void onEnable() {
        burst = 100;
        cooldown = 0;
    }

    @Override
    public void onDisable() {
        mover.cancel();
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;
        if (mover.isActive()) {
            if (!mover.tick()) return;
            cooldown = Math.max(1, clickDelay.getValue().intValue());
            return;
        }
        // The player handler is only clickable while no container screen is open.
        if (!InventoryClicks.playerHandlerOpen()) return;
        if (combatCheck.isEnabled() && (mc.player.hurtTime > 0 || mc.player.getAttacker() != null)) return;
        if (activation.is("On Key")) {
            if (burst <= 0) return;
            burst--;
        } else if (!openInventory.isEnabled() && !(mc.currentScreen instanceof InventoryScreen)) {
            return;
        }
        if (cooldown > 0) {
            cooldown--;
            return;
        }
        if (equipArmor() || sortHotbar() || dropJunk()) {
            cooldown = clickDelay.getValue().intValue();
        }
    }

    private boolean equipArmor() {
        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        for (EquipmentSlot slot : slots) {
            int worn = score(mc.player.getEquippedStack(slot));
            int best = -1;
            int bestScore = worn;
            for (int i = 9; i < 36; i++) {
                ItemStack s = mc.player.getInventory().getStack(i);
                EquippableComponent eq = s.get(DataComponentTypes.EQUIPPABLE);
                if (eq == null || eq.slot() != slot) continue;
                int sc = score(s);
                if (sc > bestScore) {
                    bestScore = sc;
                    best = i;
                }
            }
            if (best != -1) {
                // Shift-click refuses to replace worn armour; a cursor swap works on both cases.
                mover.begin(best, armorSlotId(slot));
                return true;
            }
        }
        return false;
    }

    private boolean sortHotbar() {
        String[] want = presets.is("Rusher") ? RUSHER : presets.is("Fighter") ? FIGHTER : BALANCED;
        for (int hot = 0; hot < 9; hot++) {
            ItemStack cur = mc.player.getInventory().getStack(hot);
            if (matches(cur, want[hot])) continue;
            for (int i = 9; i < 36; i++) {
                ItemStack s = mc.player.getInventory().getStack(i);
                if (!matches(s, want[hot])) continue;
                // Do not steal an item another hotbar slot is still waiting for - that is what made
                // two slots ping-pong the same stack every tick.
                if (neededByOtherSlot(s, hot, want)) continue;
                // slot i is a screen slot (main inventory 9..35), button hot is the hotbar index.
                if (!InventoryClicks.playerClick(i, hot, SlotActionType.SWAP)) return false;
                return true;
            }
        }
        return false;
    }

    private boolean matches(ItemStack stack, String token) {
        if (stack == null || stack.isEmpty()) return false;
        return Registries.ITEM.getId(stack.getItem()).getPath().contains(token);
    }

    private boolean neededByOtherSlot(ItemStack stack, int hot, String[] want) {
        for (int j = 0; j < 9; j++) {
            if (j == hot) continue;
            if (matches(stack, want[j]) && !matches(mc.player.getInventory().getStack(j), want[j])) return true;
        }
        return false;
    }

    /** Player screen handler slot id of an armour slot (head 5, chest 6, legs 7, feet 8). */
    private static int armorSlotId(EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> 5;
            case CHEST -> 6;
            case LEGS -> 7;
            default -> 8;
        };
    }

    private boolean dropJunk() {
        for (int i = 9; i < 36; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (s.isEmpty()) continue;
            String p = Registries.ITEM.getId(s.getItem()).getPath();
            if (p.equals("stick") || p.equals("string") || p.equals("flint") || p.equals("feather") || p.equals("glass_bottle")) {
                if (InventoryClicks.playerClick(i, 1, SlotActionType.THROW)) return true;
            }
        }
        return false;
    }

    private static int score(ItemStack s) {
        if (s.isEmpty()) return -1;
        String p = Registries.ITEM.getId(s.getItem()).getPath();
        return p.startsWith("netherite_") ? 6 : p.startsWith("diamond_") ? 5
                : p.startsWith("iron_") || p.startsWith("chainmail_") ? 4
                : p.startsWith("golden_") ? 3 : p.startsWith("turtle_") ? 2 : 1;
    }
}
