package com.anormal.client.module.impl.kuro;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;

import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * kuro {@code ChatTimestamps} port: har bir chat qatorining boshiga haqiqiy
 * vaqt prefiksi qo‘shadi — "[14:32:07]". Faqat ko‘rsatish uchun: hech nima
 * yuborilmaydi va server jurnalida o‘zgarmaydi.
 *
 * <p>Anormaldagi farqlar: kuro'daki ChatComponentMixin o‘rniga
 * {@link com.anormal.client.mixin.KuroChatHudMixin} {@code ChatHud.addMessage}
 * uchun ishlaydi; Anormaldagi chat HUD'a yozilgan xabarlar ham ushlanadi.</p>
 */
public class KuroChatTimestamps extends Module {
    private static final DateTimeFormatter T24 = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter T24S = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter T12 = DateTimeFormatter.ofPattern("h:mm a");
    private static final DateTimeFormatter T12S = DateTimeFormatter.ofPattern("h:mm:ss a");
    private static KuroChatTimestamps instance;

    private final BooleanSetting clock24 = new BooleanSetting("24h Clock", "24 soatlik format", true);
    private final BooleanSetting seconds = new BooleanSetting("Seconds", "Sekundlar ham", true);

    public KuroChatTimestamps() {
        super("Chat Timestamps", "Prefixes every chat line with the real-world time (kuro port)", Category.KURO);
        instance = this;
        addSetting(clock24);
        addSetting(seconds);
    }

    /** ChatHud'ga qo'shilib bosqichida stamp'laydigan yordamchi (mixin ishlaydi). */
    public static Text stamp(Text message) {
        KuroChatTimestamps t = instance;
        if (t == null || !t.isEnabled()) return message;
        DateTimeFormatter f = t.clock24.isEnabled() ? (t.seconds.isEnabled() ? T24S : T24) : (t.seconds.isEnabled() ? T12S : T12);
        // bo'sh otaq element: xabarning o'z ranglari saqlanadi
        MutableText out = Text.empty()
                .append(Text.literal("[" + LocalTime.now().format(f) + "] ").formatted(Formatting.GRAY))
                .append(message);
        return out;
    }
}
