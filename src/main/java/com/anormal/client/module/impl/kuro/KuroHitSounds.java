package com.anormal.client.module.impl.kuro;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.module.ModuleManager;
import com.anormal.client.mixin.ClientPlayerInteractionManagerMixin;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;

import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Kuro Hit Sounds port (dev.kuro.client.module.impl.HitSounds'dan Anormal'da).
 *
 * <p>Biror narsaga urilgan paytda ovoz chiqaradi. Sof audio javob: hech narsa
 * serverga yuborilmaydi va hujumning o'zi o'zgarmaydi.</p>
 *
 * <p>"Faqat zarar yetganda" yoqilgan payt ovoz kutadi: server zararni qabul
 * qilganini (hurt timer sakrashini) ko'rgach chiqadi — zarar yutilgan, bloklangan
 * yoki yetib bormagan urilishlar tinch qoladi. Killa bo'lsa uchun boshqacha chal.</p>
 */
public class KuroHitSounds extends Module {
    private static final String[] NAMES = {"Skeet", "Pop", "Click", "Bell", "Orb"};
    private static final SoundEvent[] SOUNDS;
    private static final SoundEvent KILL;
    /** Uch qadam ichida server javob berishi mumkin (yuqori pingni qamrab oladi, bog'liq shicalar yo'q). */
    private static final int WINDOW_TICKS = 10;
    private static final int MAX_PENDING = 8;
    private static KuroHitSounds instance;

    static {
        // kuro'ning ovoz fayllari (kuro:hit_*) portda yo'q — o'rniga javob sifatida
        // xuddi shu ohangdagi vanilla ovozlari ishlatiladi, shuning uchun modul
        // darhol, resurslarsiz ham ovoz chiqaradi. Orb allaqachon vanilla edi.
        SOUNDS = new SoundEvent[] {
                SoundEvents.ENTITY_ARROW_HIT_PLAYER,   // Skeet - toza "crack"
                SoundEvents.ENTITY_CHICKEN_EGG,        // Pop - halqa pop
                SoundEvents.ENTITY_ITEM_PICKUP,        // Click - qisqa klik
                SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, // Bell - yuqori chime
                SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP  // Orb (kuro'dagi kabi vanilla)
        };
        KILL = SoundEvents.ENTITY_PLAYER_LEVELUP;
    }

    private final NumberSetting sound = new NumberSetting("Sound", "Which chime to play", 1.0, 1.0, NAMES.length, 1.0);
    private final NumberSetting volume = new NumberSetting("Volume", "How loud", 0.8, 0.1, 1.0, 0.05);
    private final NumberSetting pitch = new NumberSetting("Pitch", "Playback pitch", 1.0, 0.5, 2.0, 0.05);
    private final BooleanSetting variation = new BooleanSetting("Pitch variation", "Slight random pitch per hit", true);
    private final BooleanSetting confirm = new BooleanSetting("Only when the hit lands", "Play only after damage is accepted", true);
    private final BooleanSetting killSound = new BooleanSetting("Kill sound", "Different chime for a killing hit", true);
    private final BooleanSetting playersOnly = new BooleanSetting("Players only", "Ignore non-player entities", false);

    private static final class Pending {
        final int id;
        int ticks = WINDOW_TICKS;
        int lastHurt;
        Pending(int id, int hurt) { this.id = id; this.lastHurt = hurt; }
    }

    private final List<Pending> pending = new ArrayList<>();

    public KuroHitSounds() {
        super("Hit Sounds", "Sound when you hit something", Category.KURO);
        instance = this;
        addSetting(sound);
        addSetting(volume);
        addSetting(pitch);
        addSetting(variation);
        addSetting(confirm);
        addSetting(killSound);
        addSetting(playersOnly);
    }

    private static SoundEvent event(String name) {
        return SoundEvent.of(Identifier.tryParse("kuro:" + name));   // hozircha ishlatilmaydi (aktivlarsiz)
    }

    /** ClientPlayerInteractionManagerMixin har hujumda shu yerda xabar beradi. */
    public static void onAttack(Entity target) {
        KuroHitSounds m = instance;
        if (m != null && m.isEnabled()) m.attacked(target);
    }

    private void attacked(Entity e) {
        if (mc.player == null || e == mc.player || !(e instanceof LivingEntity le) || !le.isAlive()) return;
        if (playersOnly.isEnabled() && !(e instanceof PlayerEntity)) return;
        if (!confirm.isEnabled()) {
            play(false);
            return;
        }
        for (Pending p : pending) {
            if (p.id == le.getId()) { p.ticks = WINDOW_TICKS; return; }
        }
        if (pending.size() >= MAX_PENDING) pending.remove(0);
        pending.add(new Pending(le.getId(), le.hurtTime));
    }

    @Override
    public void onTick() {
        if (pending.isEmpty()) return;
        if (mc.world == null) { pending.clear(); return; }
        for (Iterator<Pending> it = pending.iterator(); it.hasNext(); ) {
            Pending p = it.next();
            Entity e = mc.world.getEntityById(p.id);
            if (!(e instanceof LivingEntity le) || (le.isRemoved() && le.isAlive())) { it.remove(); continue; }
            boolean landed = le.hurtTime > p.lastHurt;
            boolean dead = !le.isAlive();
            p.lastHurt = le.hurtTime;
            if (landed || dead) {
                play(dead);
                it.remove();
            } else if (--p.ticks <= 0) {
                it.remove();
            }
        }
    }

    /** Hish (hurtsiz qabul qilingan zarar) yoki o'lik: hech narsa interaksiya bilan bog'liq emas. */
    @Override public void onDisable() { pending.clear(); }

    private void play(boolean kill) {
        if (mc.player == null) return;
        SoundEvent ev = kill && killSound.isEnabled() ? KILL : SOUNDS[Math.max(0, Math.min(SOUNDS.length - 1, (int) sound.getAsDouble() - 1))];
        float p = (float) pitch.getAsDouble();
        if (variation.isEnabled()) p *= 0.94f + ThreadLocalRandom.current().nextFloat() * 0.12f;
        mc.getSoundManager().play(PositionedSoundInstance.ui(ev, p, (float) volume.getAsDouble()));
    }
}
