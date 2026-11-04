package com.anormal.client.module.impl.kuro;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.module.ModuleManager;
import com.anormal.client.module.impl.kuro.KuroAutoWeapon;
import com.anormal.client.module.impl.world.Freecam;
import com.anormal.client.module.impl.client.Friends;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.module.impl.kuro.KuroAttackPackets;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;

import org.lwjgl.glfw.GLFW;

/**
 * Kuro Auto Clicker port (dev.kuro.client.module.impl.AutoClicker'dan Anormal'da).
 *
 * <p>Chap sichqoncha tugmasini tutilganda va crosshair nishonda bo'lsa esa
 * CPS bilan barqaror hujum qiladi. Kliklar tick bo'ylab teng taratiladi;
 * tickda eng ko'pi bilan bitta. Bloklarni buzish o'yinga qoladi: u faqat
 * crosshair entity bo'lganda ishlaydi.</p>
 */
public class KuroAutoClicker extends Module {
    private final NumberSetting cps = new NumberSetting("CPS", "Clicks per second", 10.0, 1.0, 20.0, 1.0);
    private final NumberSetting minCharge = new NumberSetting("Min charge", "Attack charge to wait before clicking (0 = off)", 0.0, 0.0, 1.0, 0.05);
    private final BooleanSetting players = new BooleanSetting("Players", "Click players", true);
    private final BooleanSetting mobs = new BooleanSetting("Other mobs", "Click other living entities", true);
    private final BooleanSetting pauseUsing = new BooleanSetting("Pause while using item", true);

    private double acc;

    public KuroAutoClicker() {
        super("Auto Clicker", "Attack while you hold the left button and the crosshair is on a target", Category.KURO);
        addSetting(cps);
        addSetting(minCharge);
        addSetting(players);
        addSetting(mobs);
        addSetting(pauseUsing);
    }

    @Override public void onEnable() { acc = 0; }

    private boolean mouseDown() {
        return mc.getWindow() != null
                && GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;
        Freecam freecam = ModuleManager.getModule(Freecam.class);
        if (mc.currentScreen != null || (freecam != null && freecam.isEnabled()) || mc.player.isSpectator()) { acc = 0; return; }
        if (pauseUsing.isEnabled() && mc.player.isUsingItem()) { acc = 0; return; }
        if (!mouseDown()) { acc = 0; return; }
        if (!(mc.crosshairTarget instanceof EntityHitResult hit) || hit.getType() != HitResult.Type.ENTITY) {
            acc = Math.min(acc, 1.0);
            return;
        }
        Entity target = hit.getEntity();
        if (!valid(target)) return;

        KuroAutoWeapon.onTarget(target);
        acc += cps.getValue() / 20.0;
        if (acc < 1.0) return;
        if (minCharge.getValue() > 0.0 && !KuroAutoWeapon.bypass(target)
                && KuroAutoWeapon.progress() < minCharge.getValue()) return;
        if (!KuroAutoWeapon.readyToAttack()) return;
        acc -= 1.0;

        KuroAttackPackets.hit(target);
    }

    private boolean valid(Entity e) {
        if (e == mc.player || !e.isAlive()) return false;
        if (e instanceof PlayerEntity p) return players.isEnabled() && !p.isSpectator() && !Friends.isFriend(p.getGameProfile().name());
        return e instanceof LivingEntity && mobs.isEnabled();
    }
}
