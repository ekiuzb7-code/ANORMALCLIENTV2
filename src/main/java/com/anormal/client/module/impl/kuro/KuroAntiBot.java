package com.anormal.client.module.impl.kuro;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Kuro AntiBot birga bir port: NPC / watchdog uslubidagi sun'iy o'yinchilarni
 * jangovar nishonlardan chiqarib tashlaydi. Respect'ning AntiBot tekshiruvlari
 * o'zisida soddalashtirilgan variant. Boshqa kuro modullar statik isBot()
 * orqali foydalanadi, nishon qabchilari boshqa modulla juda o'zivli.
 */
public class KuroAntiBot extends Module {
    private static KuroAntiBot INSTANCE;

    public final BooleanSetting tabList = new BooleanSetting("Tab list", "Tab ro'yxatida yo'q bo'lsa bot", true);
    public final BooleanSetting validName = new BooleanSetting("Valid name", "Noto'g'ri matinmi bo'lsa bot", true);
    public final BooleanSetting noColorCodes = new BooleanSetting("No color codes", "Rang kodlari mavjud bo'lsa bot", true);
    public final BooleanSetting pingCheck = new BooleanSetting("Ping present", "Ping salbiy bo'lsa bot", false);

    private static final Pattern NAME_OK = Pattern.compile("^[A-Za-z0-9_]{3,16}$");

    public KuroAntiBot() {
        super("AntiBot", "Fake o'yinchi nishonlarni chiqarib tashlaydi (kuro port)", Category.KURO);
        addSetting(tabList);
        addSetting(validName);
        addSetting(noColorCodes);
        addSetting(pingCheck);
        INSTANCE = this;
    }

    public static boolean isBot(Entity e) {
        if (!(e instanceof PlayerEntity p)) return false;
        if (INSTANCE == null || !INSTANCE.isEnabled()) return false;
        return INSTANCE.check(p);
    }

    public static boolean isBotPlayer(PlayerEntity p) {
        return isBot(p);
    }

    private boolean check(PlayerEntity p) {
        if (p == mc.player) return false;
        String name = p.getName().getString();
        if (noColorCodes.isEnabled() && (name.indexOf('§') >= 0 || name.indexOf('&') >= 0)) return true;
        if (validName.isEnabled() && !NAME_OK.matcher(name.replace("§", "").trim()).matches()) return true;

        if (tabList.isEnabled() || pingCheck.isEnabled()) {
            if (mc.getNetworkHandler() == null) return false;
            UUID id = p.getUuid();
            var info = mc.getNetworkHandler().getPlayerListEntry(id);
            if (tabList.isEnabled() && info == null) return true;      // profil tab-listda yo'q = bot
            if (pingCheck.isEnabled() && info != null && info.getLatency() < 0) return true;
        }
        return false;
    }
}
