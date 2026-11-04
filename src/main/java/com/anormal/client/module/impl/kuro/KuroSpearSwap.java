package com.anormal.client.module.impl.kuro;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.module.impl.kuro.KuroAttackPackets;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.kuro.KuroBlockPlacer;

import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.ItemTags;

/**
 * Ported from kuro (dev.kuro.client.module.impl.SpearSwap).
 *
 * <p>Spear swap (attribute swap): the instant you attack, the spear from your hotbar is selected
 * so the jab / lunge is made with it, then your original slot comes back. That way you keep
 * holding something else (sword, totem, rocket...) and never have to wait for the spear to be
 * raised / wound up before you dash.</p>
 *
 * <p>kuro drives this from the AttackEntityCallback; on the Anormal compile classpath that event
 * does not resolve its Minecraft types, so the attack moment is detected the same way the other
 * kuro ports detect it (attack held + entity under the crosshair + handSwinging edge), and the
 * hit itself goes through {@link KuroAttackPackets}, which syncs the selected slot to the server
 * first.</p>
 */
public class KuroSpearSwap extends Module {
    private final BooleanSetting lungeOnly = new BooleanSetting("Lunge spears only",
            "Only swap to a spear with the Lunge enchantment", false);
    private final BooleanSetting inAir = new BooleanSetting("Swap on air clicks",
            "Also swap when clicking empty air (needed for the dash)", true);
    private final BooleanSetting fromAny = new BooleanSetting("From any item",
            "Off = only from a sword", true);
    private final NumberSetting delay = new NumberSetting("Swap back",
            "Ticks after the click ends before restoring", 2.0, 1.0, 10.0, 1.0);

    private int previous = -1, restoreIn;
    private boolean wasSwinging;

    public KuroSpearSwap() {
        super("SpearSwap", "Swaps to a spear on each attack and back again (kuro port)", Category.KURO);
        addSetting(lungeOnly);
        addSetting(inAir);
        addSetting(fromAny);
        addSetting(delay);
    }

    @Override
    public void onEnable() { previous = -1; restoreIn = 0; wasSwinging = mc.player != null && mc.player.handSwinging; }

    @Override
    public void onDisable() {
        if (previous >= 0 && mc.player != null) KuroBlockPlacer.selectForce(previous);
        previous = -1;
    }

    static boolean isSpear(ItemStack s) {
        return !s.isEmpty() && Registries.ITEM.getId(s.getItem()).getPath().endsWith("_spear");
    }

    private static boolean hasLunge(ItemStack s) {
        ItemEnchantmentsComponent enc = s.getEnchantments();
        for (var e : enc.getEnchantments()) {
            if (e.matchesKey(Enchantments.LUNGE)) return true;
        }
        return false;
    }

    /** Best spear in the hotbar: a lunge one first, then any. */
    private int findSpear() {
        int slot = KuroBlockPlacer.findSlot(s -> isSpear(s) && hasLunge(s));
        if (slot < 0 && !lungeOnly.isEnabled()) slot = KuroBlockPlacer.findSlot(KuroSpearSwap::isSpear);
        return slot;
    }

    private void swapToSpear() {
        if (mc.player == null || previous >= 0) { if (previous >= 0) restoreIn = delay.getValue().intValue(); return; }
        ItemStack held = mc.player.getMainHandStack();
        if (isSpear(held)) return;                                          // already holding one
        if (!fromAny.isEnabled() && !held.isIn(ItemTags.SWORDS)) return;
        int slot = findSpear();
        if (slot < 0) return;
        int cur = KuroBlockPlacer.selected();
        if (!KuroBlockPlacer.select(slot)) return;                          // one swap per tick; retried next tick
        previous = cur;
        restoreIn = delay.getValue().intValue();
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;
        boolean mining = mc.crosshairTarget != null
                && mc.crosshairTarget.getType() == net.minecraft.util.hit.HitResult.Type.BLOCK;
        boolean clicking = mc.currentScreen == null && !mining && mc.options.attackKey.isPressed();

        // Attack moment (the swing edge stands in for kuro's AttackEntityCallback)
        boolean attacking = clicking && mc.crosshairTarget != null
                && mc.crosshairTarget.getType() == net.minecraft.util.hit.HitResult.Type.ENTITY;
        boolean swingEdge = attacking && mc.player.handSwinging && !wasSwinging;
        wasSwinging = mc.player.handSwinging;
        if (swingEdge) {
            if (previous >= 0 && restoreIn > 0) restoreIn = delay.getValue().intValue();   // continuous attack stream
            KuroAttackPackets.hit(((net.minecraft.util.hit.EntityHitResult) mc.crosshairTarget).getEntity());
            if (previous < 0) {
                swapToSpear();
                if (previous >= 0) KuroAttackPackets.hit(((net.minecraft.util.hit.EntityHitResult) mc.crosshairTarget).getEntity());
            }
        }

        if (clicking && inAir.isEnabled()) {
            swapToSpear();
            if (previous >= 0) restoreIn = delay.getValue().intValue();
        } else if (previous >= 0 && --restoreIn <= 0) restoreNow();
    }

    private void restoreNow() {
        if (previous >= 0 && mc.player != null && !KuroBlockPlacer.select(previous)) return;   // retried next tick
        previous = -1;
    }
}
