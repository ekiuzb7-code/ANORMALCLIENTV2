package com.anormal.client.module.impl.kuro;

import com.anormal.client.util.kuro.KuroBlockPlacer;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.ItemTags;

/**
 * Kuro SpearDtapHandler port (dev.kuro.client.module.impl.automace.SpearDtapHandler'dan Anormal'da).
 *
 * <p>Nayza right-click zaryadi bitganda bir marta mace bilan qo'shimcha zarar,
 * dtap uslubida: nayzani tashlab mose olib bir-ikki marta mace zaryadlanmoqda,
 * so'ng asl sig'eriga qaytadi.</p>
 */
public class KuroSpearDtapHandler {

    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static final long DTAP_SELF_FEEDBACK_GUARD_MS = 300L;

    private final KuroAutoMace parent;
    private final KuroShieldBreakHandler shieldBreakHandler;
    private final KuroAutoMaceTargeting targeting;

    private boolean dtapArmed = false;
    private int dtapOriginalSlot = -1;
    private int dtapSwapBackTick = 0;
    private long dtapNoArmUntil = 0L;

    public KuroSpearDtapHandler(KuroAutoMace parent, KuroShieldBreakHandler shieldBreakHandler, KuroAutoMaceTargeting targeting) {
        this.parent = parent;
        this.shieldBreakHandler = shieldBreakHandler;
        this.targeting = targeting;
    }

    public void onTickPre() {
        if (dtapSwapBackTick > 0) {
            dtapSwapBackTick--;
            if (dtapSwapBackTick == 0 && dtapOriginalSlot != -1) {
                if (KuroBlockPlacer.select(dtapOriginalSlot)) dtapOriginalSlot = -1;
                else dtapSwapBackTick = 1;
            }
        }

        if (dtapArmed) {
            dtapArmed = false;
            executeDtapMace();
            return;
        }

        if (!parent.getSpearDtap().isEnabled()) return;
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;
        if (dtapSwapBackTick > 0) return;

        if (!parent.getRocketDiveHandler().idle() || parent.isMaceHit() || shieldBreakHandler.getSlamState() != 0) return;

        if (System.currentTimeMillis() < dtapNoArmUntil) return;

        boolean holdsSpear = isSpear(mc.player.getMainHandStack()) || isSpear(mc.player.getOffHandStack());
        if (!holdsSpear) return;
        if (!mc.player.isUsingItem()) return;

        dtapOriginalSlot = KuroBlockPlacer.selected();
        dtapArmed = true;
    }

    private static boolean isSpear(ItemStack stack) {
        return stack != null && stack.getItem().getRegistryEntry().isIn(ItemTags.SPEARS);
    }

    private void executeDtapMace() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null || dtapOriginalSlot == -1) {
            dtapOriginalSlot = -1;
            return;
        }
        int maceSlot = findDtapMaceSlot();
        Entity target = maceSlot == -1 ? null : findDtapTarget();
        if (target == null) { dtapOriginalSlot = -1; return; }

        if (!KuroBlockPlacer.select(maceSlot)) { dtapOriginalSlot = -1; return; }
        if (!parent.executeAttack(target, false)) { dtapSwapBackTick = 1; return; }
        dtapNoArmUntil = System.currentTimeMillis() + DTAP_SELF_FEEDBACK_GUARD_MS;
        dtapSwapBackTick = 1;
    }

    private int findDtapMaceSlot() {
        boolean useDensity = mc.player.fallDistance >= parent.getDensityThreshold().getValue();
        int primary = useDensity ? shieldBreakHandler.findEnchantedMaceSlot("density") : shieldBreakHandler.findEnchantedMaceSlot("breach");
        int secondary = useDensity ? shieldBreakHandler.findEnchantedMaceSlot("breach") : shieldBreakHandler.findEnchantedMaceSlot("density");
        if (primary != -1) return primary;
        if (secondary != -1) return secondary;
        return shieldBreakHandler.findAnyMaceSlot();
    }

    private Entity findDtapTarget() {
        Entity cross = mc.targetedEntity;
        if (targeting.isValidTarget(cross)) return cross;
        return targeting.findOptimalTarget(parent.reach());
    }

    public void reset() {
        dtapArmed = false;
        dtapOriginalSlot = -1;
        dtapSwapBackTick = 0;
        dtapNoArmUntil = 0L;
    }

    public void onDisable() {
        if (dtapSwapBackTick > 0 && dtapOriginalSlot != -1 && mc.player != null) {
            KuroBlockPlacer.selectForce(dtapOriginalSlot);
        }
        reset();
    }
}
