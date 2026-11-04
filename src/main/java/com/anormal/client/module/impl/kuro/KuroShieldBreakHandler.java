package com.anormal.client.module.impl.kuro;

import com.anormal.client.util.kuro.KuroBlockPlacer;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import java.util.Locale;

/**
 * Kuro ShieldBreakHandler port (dev.kuro.client.module.impl.automace.ShieldBreakHandler'dan Anormal'da).
 *
 * <p>Qalqonni buzish, tick'da bir qadam o'yinchiga o'xshash: bol'tni olib keladi,
 * qalqonni bir marta vazbagantirib oladi, so'ng mase olib ikkinchi marta Zoq
 * omat. Hozirgi tick'da bajarilolmaydigan narsa (swap rad etildi, hujum to'liq
 * zaryadlanmagan, nishonga qaralmagan) keyingi tickni kutadi; hech narsa tez
 * yuborilmaydi. Step-faqat degan usulda tick qaytadi.</p>
 */
public class KuroShieldBreakHandler {

    private static final MinecraftClient mc = MinecraftClient.getInstance();

    private final KuroAutoMace parent;
    private int slamState = 0;
    private long slamTimer = 0L;

    public KuroShieldBreakHandler(KuroAutoMace parent) {
        this.parent = parent;
    }

    public int getSlamState() { return slamState; }
    public void setSlamState(int state) { this.slamState = state; }
    public void reset() { this.slamState = 0; this.slamTimer = 0L; }

    private static final long SLAM_TIMEOUT_MS = 1500L;

    /**
     * Qalqonni alarmi bilan birga to'liq combo boshlash. True qaytarsa handler bu tickni egallagan deyman.
     */
    public boolean handleShieldBreak(Entity target, double fallDist) {
        if (mc.interactionManager == null || mc.player == null) return false;

        boolean targetBlocking = target instanceof LivingEntity && ((LivingEntity) target).isBlocking();
        KuroAutoMace.ShieldMode mode = parent.getShieldMode();

        if (slamState == 1 && System.currentTimeMillis() - slamTimer > SLAM_TIMEOUT_MS) slamState = 0;

        if ((targetBlocking || slamState > 0) && mode == KuroAutoMace.ShieldMode.STUN_SLAM) {
            int axeSlot = findAxeSlot();
            if (axeSlot != -1) {
                if (slamState == 0) {
                    if (parent.getSavedSlot() == -1) parent.setSavedSlot(KuroBlockPlacer.selected());
                    if (!KuroBlockPlacer.select(axeSlot)) return true;
                    if (!parent.executeAttack(target)) return true;

                    slamState = 1;
                    slamTimer = System.currentTimeMillis();
                    if (parent.getFastStunSlam().isEnabled()) switchToAppropriateMace(fallDist);
                    return true;
                } else if (slamState == 1) {
                    if (!hasMace()) {
                        boolean due = System.currentTimeMillis() - slamTimer >= Math.max(50, (long) parent.getStunSlamDelay().getAsDouble());
                        if (parent.getFastStunSlam().isEnabled() || due) switchToAppropriateMace(fallDist);
                        return true;
                    }
                    if (System.currentTimeMillis() - slamTimer >= Math.max(50, (long) parent.getStunSlamDelay().getAsDouble())
                            && parent.executeAttack(target)) {
                        slamState = 2;
                        parent.registerMaceHit();
                    }
                    return true;
                } else {
                    return true;
                }
            } else {
                slamState = 0;
            }
        }

        if (targetBlocking && mode == KuroAutoMace.ShieldMode.DRAIN) {
            if (parent.getSavedSlot() == -1) parent.setSavedSlot(KuroBlockPlacer.selected());
            if (!hasMace() && !switchToAppropriateMace(fallDist)) return true;
            if (hasMace() && parent.executeAttack(target)) parent.registerMaceHit();
            return true;
        }

        return false;
    }

    public void abortStaleSlam() {
        if (slamState == 1) slamState = 0;
    }

    public int findAxeSlot() {
        if (mc.player == null) return -1;
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).getItem() instanceof AxeItem) return i;
        }
        return -1;
    }

    /** To'g'ri mase olib keladi. True qaytarsa yozilgan. */
    public boolean switchToAppropriateMace(double fallDistance) {
        boolean useDensity = fallDistance >= parent.getDensityThreshold().getValue();
        int primarySlot = useDensity ? findEnchantedMaceSlot("density") : findEnchantedMaceSlot("breach");
        int secondarySlot = useDensity ? findEnchantedMaceSlot("breach") : findEnchantedMaceSlot("density");

        int targetSlot = primarySlot != -1 ? primarySlot : (secondarySlot != -1 ? secondarySlot : findAnyMaceSlot());

        return targetSlot != -1 && KuroBlockPlacer.select(targetSlot);
    }

    /** bos berilgan enchantment nomi bilan mase slotini qidiradi (vanilla mase enchantment birligida). */
    public int findEnchantedMaceSlot(String enchantKey) {
        if (mc.player == null) return -1;
        String key = enchantKey.toLowerCase(Locale.ROOT);
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty() || stack.getItem() != Items.MACE) continue;
            for (var entry : stack.getEnchantments().getEnchantmentEntries()) {
                String name = entry.getKey().getIdAsString().toLowerCase(Locale.ROOT);
                if (name.contains(key)) return i;
            }
        }
        return -1;
    }

    public int findAnyMaceSlot() {
        if (mc.player == null) return -1;
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).getItem() == Items.MACE) return i;
        }
        return -1;
    }

    public boolean hasMace() {
        if (mc.player == null) return false;
        ItemStack held = mc.player.getInventory().getStack(KuroBlockPlacer.selected());
        return !held.isEmpty() && held.isOf(Items.MACE);
    }
}
