package com.anormal.client.module.impl.kuro;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.module.ModuleManager;
import com.anormal.client.module.impl.client.AntiBot;
import com.anormal.client.module.impl.client.Friends;
import com.anormal.client.module.impl.world.Freecam;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;

/**
 * Kuro KillAura port (dev.kuro.client.module.impl.KillAura'dan Anormal'da).
 *
 * Siloh to'langanda range ichidagi eng yaqin to'g'ri targetga hujum qiladi.
 * Auto Weapon (taga mos siloh / shield axe / mace) va AttackPackets (paket tartibi)
 * orqali ishlaydi.
 */
public class KuroKillAura extends Module {
    private final NumberSetting range = new NumberSetting("Range", "Max attack distance", 3.5, 1.0, 6.0, 0.1);
    private final BooleanSetting players = new BooleanSetting("Players", "Attack other players", true);
    private final BooleanSetting mobs = new BooleanSetting("Other mobs", "Attack other living entities", false);
    private final NumberSetting cooldown = new NumberSetting("Min charge", "Wait for the weapon to recharge", 0.95, 0.1, 1.0, 0.05);

    public KuroKillAura() {
        super("Kill Aura", "Attacks the nearest valid target (kuro port)", Category.KURO);
        addSetting(range);
        addSetting(players);
        addSetting(mobs);
        addSetting(cooldown);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;
        if (mc.currentScreen != null || freecamActive() || mc.player.isUsingItem() || mc.player.isSpectator()) return;

        Entity best = null;
        double bestD = range.getValue();
        for (Entity e : mc.world.getEntities()) {
            if (!valid(e)) continue;
            double d = mc.player.distanceTo(e);
            if (d <= bestD) { bestD = d; best = e; }
        }
        if (best == null) return;

        KuroAutoWeapon.onTarget(best);      // bu targetga mos siloh; hujum bir tick keyin chiqadi

        // tutgan item optimizing: shield ustidagi axe / mace smash kutmaydi (Auto Weapon hal qiladi)
        if (!KuroAutoWeapon.bypass(best) && KuroAutoWeapon.progress() < cooldown.getValue()) return;
        if (!KuroAutoWeapon.readyToAttack()) return;    // bu tickda slot almashtirildi: hujum keyingi tick

        KuroAttackPackets.hit(best);        // swing + attack; keyingi harakat paketidan oldin jo'nadi
    }

    private boolean valid(Entity e) {
        if (e == mc.player || !e.isAlive()) return false;
        if (e instanceof PlayerEntity p) return players.isEnabled() && !p.isSpectator() && !Friends.isFriend(p.getGameProfile().name()) && !AntiBot.isBot(p);
        return e instanceof LivingEntity && mobs.isEnabled();
    }

    private boolean freecamActive() {
        Freecam fc = ModuleManager.getModule(Freecam.class);
        return fc != null && fc.isEnabled();
    }
}
