package com.anormal.client.module.impl.kuro;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.module.impl.client.Friends;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;

/**
 * Kuro Hitbox Expand birga bir port: boshqa entity'lar hitbox'ini FAQAT sizning
 * nishon olishingiz (crosshair / attack raycast) uchun kengaytiradi — yengil
 * chetga surilgan holda ham urib tashlashingiz mumkin. Bu EntityMixin orqali
 * Entity#getTargetingMargin bilan qo'llanadi; o'yin entity-pickingda shu qiymatni
 * ishlatadi: hech narsa boshqacha harakat qilmaydi, to'qnashmaydi, renderlanmaydi.
 * Server bariber reach'ingizni tekshiradi, shuning uchun Size bilan urish masofasi
 * uzaytamaydi (Reach boshqa modul), juda katta Size'lar anticheat bayroqlannadi.
 * Hitboxes moduli (keyingi to'lqin) yoqilgan bo'lsa kengaytirilgan qutini chizadi.
 */
public class KuroHitboxExpand extends Module {
    private static KuroHitboxExpand instance;

    public final NumberSetting size = new NumberSetting("Size (blocks)", "Har tomondan qo'shimcha margin", 0.3, 0.05, 5.0, 0.05);
    public final BooleanSetting players = new BooleanSetting("Players", "O'yinchilar hitboxini kengaytir", true);
    public final BooleanSetting mobs = new BooleanSetting("Mobs", "Moblar hitboxini kengaytir", false);
    public final BooleanSetting crystals = new BooleanSetting("End crystals", "Kristallar hitboxini kengaytir", true);
    public final BooleanSetting skipFriends = new BooleanSetting("Skip friends", "Do'stlarni kengaytirmaslik", true);

    public KuroHitboxExpand() {
        super("HitboxExpand", "Nishon hitboxlarini kengaytiradi (kuro port)", Category.KURO);
        instance = this;
        addSetting(size);
        addSetting(players);
        addSetting(mobs);
        addSetting(crystals);
        addSetting(skipFriends);
    }

    /** Shu entity hitbox'iga qo'shiladigan margin (har tomonda, bloklarda) yoki vanilla bo'lsa 0. */
    public static float margin(Entity e) {
        KuroHitboxExpand h = instance;
        if (h == null || !h.isEnabled() || e == null || e == net.minecraft.client.MinecraftClient.getInstance().player) return 0.0f;
        if (e instanceof PlayerEntity p) {
            if (!h.players.isEnabled() || p.isSpectator()) return 0.0f;
            if (h.skipFriends.isEnabled() && Friends.isFriend(p.getName().getString())) return 0.0f;
        } else if (e instanceof EndCrystalEntity) {
            if (!h.crystals.isEnabled()) return 0.0f;
        } else if (e instanceof LivingEntity && !(e instanceof ArmorStandEntity)) {
            if (!h.mobs.isEnabled()) return 0.0f;
        } else return 0.0f;
        return h.size.getValue().floatValue();
    }
}
