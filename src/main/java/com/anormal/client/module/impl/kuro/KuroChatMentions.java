package com.anormal.client.module.impl.kuro;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;

import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * kuro {@code ChatMentions} port: kimningdir o‘z ismingizni chatga yozganda
 * u bilan ajratib ko‘rsatadi, "ping" tovushini chiqaradi va haqiqiy
 * ko‘rinishidan eslatma yozadi. Faqat chat qatorlaridir ("<Ism> ...", "Ism: ...",
 * "Ism » ...") sender qismi topilsa, bu joyda o‘z ismingiz yoki sistem
 * qatorlarda ("Ism joined the game") e'tiborga olinmaydi. Faqat ko‘rsatish.
 */
public class KuroChatMentions extends Module {
    private static KuroChatMentions instance;
    private static String cachedName = "";
    private static Pattern cachedPattern;
    private static long lastPing;
    private static boolean alerting;

    private final com.anormal.client.setting.ColorSetting highlight =
            new com.anormal.client.setting.ColorSetting("Highlight Colour", "Ism ajrang ko‘rsatilish rangi", com.anormal.client.util.ColorUtils.rgba(230, 180, 70, 255));
    private final BooleanSetting bold = new BooleanSetting("Bold", "Qalin shrift", true);
    private final BooleanSetting sound = new BooleanSetting("Ping Sound", "Ping tovushi", true);
    private final BooleanSetting toast = new BooleanSetting("Toast", "Chat xabari bilan eslatma yozadi", true);

    public KuroChatMentions() {
        super("Chat Mentions", "Highlights your username in chat and pings you (kuro port)", Category.KURO);
        instance = this;
        addSetting(highlight);
        addSetting(bold);
        addSetting(sound);
        addSetting(toast);
    }

    /** Mixin cheklovi: eslatma paytida o‘zini qayta chaqirmaslik uchun. */
    public static boolean isAlerting() { return alerting; }

    private static Pattern pattern(String name) {
        if (!name.equals(cachedName) || cachedPattern == null) {
            cachedName = name;
            cachedPattern = Pattern.compile("(?i)(?<![A-Za-z0-9_])" + Pattern.quote(name) + "(?![A-Za-z0-9_])");
        }
        return cachedPattern;
    }

    /** "Kim dedi" qismning oxiri (birinchi 48 belgida "> : »" dan keyingi index) yoki chat qatoriga taalluqsizsa -1. */
    private static int senderEnd(String plain) {
        int lim = Math.min(plain.length(), 48);
        for (int i = 0; i < lim; i++) {
            char c = plain.charAt(i);
            if (c == '>' || c == ':' || c == '»') return i + 1;
        }
        return -1;
    }

    /** ChatHud'ga qo'shilgan har bir xabarni ko'rib chiqaradigan yordamchi (mixin ishlaydi). */
    public static Text process(Text msg) {
        KuroChatMentions m = instance;
        if (m == null || !m.isEnabled() || mc.player == null) return msg;
        String name = mc.player.getName().getString();
        if (name.isEmpty()) return msg;
        String plain = msg.getString();
        int zone = senderEnd(plain);
        if (zone < 0) return msg;
        Pattern pat = pattern(name);
        Matcher probe = pat.matcher(plain);
        boolean hit = false;
        while (probe.find()) if (probe.start() >= zone) { hit = true; break; }
        if (!hit) return msg;

        TextColor tc = TextColor.fromRgb(m.highlight.getValue());
        MutableText out = Text.empty();
        int[] offset = {0};
        msg.<Boolean>visit((style, s) -> {
            Matcher mm = pat.matcher(s);
            int last = 0;
            while (mm.find()) {
                if (offset[0] + mm.start() < zone) continue;
                if (mm.start() > last) out.append(Text.literal(s.substring(last, mm.start())).setStyle(style));
                Style mStyle = m.bold.isEnabled() ? style.withBold(true) : style;
                out.append(Text.literal(mm.group()).setStyle(mStyle.withColor(tc)));
                last = mm.end();
            }
            if (last < s.length()) out.append(Text.literal(s.substring(last)).setStyle(style));
            offset[0] += s.length();
            return Optional.empty();
        }, Style.EMPTY);

        m.alert(plain);
        return out;
    }

    private void alert(String plain) {
        long now = System.currentTimeMillis();
        if (now - lastPing < 1000) return;     // qisrik zanglariga faqat bitta ping
        lastPing = now;
        if (sound.isEnabled()) mc.getSoundManager().play(PositionedSoundInstance.ui(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.2f));
        if (toast.isEnabled()) {
            String short2 = plain.length() > 40 ? plain.substring(0, 40) + "..." : plain;
            alerting = true;
            try {
                mc.inGameHud.getChatHud().addMessage(Text.literal("§6[Mentioned] §f" + short2));
            } finally {
                alerting = false;
            }
        }
    }
}
