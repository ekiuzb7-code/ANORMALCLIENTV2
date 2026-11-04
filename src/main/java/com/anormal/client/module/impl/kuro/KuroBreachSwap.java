package com.anormal.client.module.impl.kuro;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.kuro.KuroBlockPlacer;

import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.ItemTags;

/**
 * Ported from kuro (dev.kuro.client.module.impl.BreachSwap).
 *
 * <p>Breach swap: the moment you attack an entity while holding something else (a sword by
 * default), the hit is made with a Breach mace from your hotbar instead, then your original slot
 * is selected again. Detects the attack via the swing edge (kuro's AttackEntityCallback does not
 * resolve on the Anormal compile classpath) and lets {@link KuroAttackPackets} send the hit,
 * which syncs the selected slot first.</p>
 */
public class KuroBreachSwap extends Module {
    private final BooleanSetting breachOnly = new BooleanSetting("Breach only",
            "Only swap to a mace with the Breach enchantment (off = any mace)", true);
    private final BooleanSetting fromSword = new BooleanSetting("Only from sword",
            "Only trigger while holding a sword", true);
    private final NumberSetting delay = new NumberSetting("Swap back",
            "Ticks before your original slot comes back", 2.0, 1.0, 10.0, 1.0);

    private int previous = -1, restoreIn;
    private boolean wasSwinging;

    public KuroBreachSwap() {
        super("BreachSwap", "Hits with a Breach mace and swaps back (kuro port)", Category.KURO);
        addSetting(breachOnly);
        addSetting(fromSword);
        addSetting(delay);
    }

    @Override
    public void onEnable() { previous = -1; restoreIn = 0; wasSwinging = mc.player != null && mc.player.handSwinging; }

    @Override
    public void onDisable() {
        if (previous >= 0 && mc.player != null) KuroBlockPlacer.selectForce(previous);
        previous = -1;
    }

    private void swapToMace() {
        if (mc.player == null || previous >= 0) return;                    // already swapped for this hit
        ItemStack held = mc.player.getMainHandStack();
        if (held.isOf(Items.MACE)) return;
        if (fromSword.isEnabled() && !held.isIn(ItemTags.SWORDS)) return;

        int slot = KuroBlockPlacer.findSlot(s -> s.isOf(Items.MACE) && hasBreach(s));
        if (slot < 0 && !breachOnly.isEnabled()) slot = KuroBlockPlacer.findSlot(s -> s.isOf(Items.MACE));
        if (slot < 0) return;

        int cur = KuroBlockPlacer.selected();
        if (!KuroBlockPlacer.select(slot)) return;                         // one swap per tick; retried next tick
        previous = cur;
        restoreIn = delay.getValue().intValue();
    }

    private static boolean hasBreach(ItemStack s) {
        ItemEnchantmentsComponent enc = s.getEnchantments();
        for (var e : enc.getEnchantments()) {
            if (e.matchesKey(Enchantments.BREACH)) return true;
        }
        return false;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;
        boolean attacking = mc.currentScreen == null && mc.options.attackKey.isPressed()
                && mc.crosshairTarget != null
                && mc.crosshairTarget.getType() == net.minecraft.util.hit.HitResult.Type.ENTITY;
        boolean swingEdge = attacking && mc.player.handSwinging && !wasSwinging;
        wasSwinging = mc.player.handSwinging;
        if (swingEdge) {
            if (previous >= 0 && restoreIn > 0) restoreIn = delay.getValue().intValue();
            if (previous < 0) {
                swapToMace();
                if (previous >= 0) {
                    // the attack goes out one tick later through AttackPackets, with the mace in hand
                    KuroAttackPackets.hit(((net.minecraft.util.hit.EntityHitResult) mc.crosshairTarget).getEntity());
                    return;
                }
            }
        }
        if (previous >= 0 && --restoreIn <= 0) restoreNow();
    }

    private void restoreNow() {
        if (previous >= 0 && mc.player != null && !KuroBlockPlacer.select(previous)) return;   // retried next tick
        previous = -1;
    }
}
