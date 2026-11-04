package com.anormal.client.module.impl.kuro;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.module.ModuleManager;
import com.anormal.client.module.impl.kuro.KuroAttackPackets;
import com.anormal.client.module.impl.world.Freecam;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.kuro.KuroSwapLimit;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;

/**
 * Ported from kuro (dev.kuro.client.module.impl.AutoShieldBreak).
 *
 * <p>When your crosshair is on a player who is blocking with a shield (and facing you), switches
 * to an axe from your hotbar and hits them so the shield gets disabled, then switches back once
 * they stop blocking. {@link KuroAutoWeapon}'s ShieldCheck backs off while this is active.</p>
 */
public class KuroAutoShieldBreak extends Module {
    private final BooleanSetting autoAttack = new BooleanSetting("Auto attack",
            "Swing at the blocker automatically", true);
    private final BooleanSetting switchBack = new BooleanSetting("Switch back",
            "Restore the previous slot when the target stops blocking", true);
    private final NumberSetting delay = new NumberSetting("Delay (ticks)",
            "Attack cooldown between axe swings", 0.0, 0.0, 10.0, 1.0);
    private int prevSlot = -1, wait;

    private static KuroAutoShieldBreak instance;
    /** True while the module is switched on (KuroTriggerBot's shield check backs off then). */
    public static boolean active() { return instance != null && instance.isEnabled(); }

    public KuroAutoShieldBreak() {
        super("AutoShieldBreak", "Axe-blocks a shielding player to disable the shield (kuro port)", Category.KURO);
        instance = this;
        addSetting(autoAttack);
        addSetting(switchBack);
        addSetting(delay);
    }

    @Override
    public void onEnable() { prevSlot = -1; wait = 0; }

    @Override
    public void onDisable() {
        if (prevSlot >= 0 && switchBack.isEnabled() && mc.player != null) KuroSwapLimit.selectNow(prevSlot);
        prevSlot = -1;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;
        Freecam freecam = ModuleManager.getModule(Freecam.class);
        boolean freecamActive = freecam != null && freecam.isEnabled();
        if (mc.currentScreen != null || freecamActive || mc.player.isSpectator()) { restore(); return; }

        PlayerEntity target = null;
        if (mc.crosshairTarget instanceof EntityHitResult hit && hit.getType() == HitResult.Type.ENTITY
                && hit.getEntity() instanceof PlayerEntity p && p != mc.player && p.isAlive() && !p.isSpectator()
                && p.isBlocking() && facingUs(p)) target = p;

        if (target == null) { wait = 0; restore(); return; }

        var inv = mc.player.getInventory();
        if (!isAxe(inv.getStack(inv.getSelectedSlot()))) {
            int axe = -1;
            for (int i = 0; i < 9; i++) if (isAxe(inv.getStack(i))) { axe = i; break; }
            if (axe < 0) return;                                    // no axe in the hotbar
            int cur = inv.getSelectedSlot();
            if (!KuroSwapLimit.select(axe)) return;                 // one swap per tick: try again next tick
            if (prevSlot < 0) prevSlot = cur;
        }
        if (!autoAttack.isEnabled()) return;
        if (++wait <= delay.getValue().intValue()) return;
        wait = 0;
        KuroAttackPackets.hit(target);                              // syncs the selected slot to the server first
    }

    /** A shield only blocks hits that come from in front of its holder. */
    private boolean facingUs(PlayerEntity p) {
        Vec3d to = mc.player.getEyePos().subtract(p.getEyePos()).normalize();
        return p.getRotationVec(1.0f).dotProduct(to) > 0.0;
    }

    private static boolean isAxe(ItemStack s) { return !s.isEmpty() && s.isIn(ItemTags.AXES); }

    private void restore() {
        if (prevSlot >= 0 && switchBack.isEnabled() && mc.player != null && !KuroSwapLimit.select(prevSlot)) return;
        prevSlot = -1;
    }
}
