package com.anormal.client.module.impl.kuro;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * kuro {@code UsernameHider} port (faqat klient tomonda): yoqilganda o‘z
 * foydalanuvchi nomingiz ekrandagi har bir matnda (chat, TAB ro‘yxati, nom
 * yorliqlari, scoreboard, menular, belgilar) soxta nom bilan almashtiriladi.
 * Server va boshqa o‘yinchilar real ismingizni ko‘radi.
 *
 * <p>Anormaldagi farqlar: kuro'daki Setting.Text/Button o‘rniga soxta nom
 * {@code anormal-fakename.txt} faylidan o'qiladi (config papkasida, modulni
 * yoqishda qayta o'qiladi) va chatga hozirgi soxta nom ko'rsatiladi.
 * Qo'shimcha "Show fake name" sozlamasi chatga yozadi.</p>
 */
public class KuroUsernameHider extends Module {
    private static final String DEFAULT_NAME = "Player";
    private static KuroUsernameHider instance;

    private String fake = DEFAULT_NAME;
    private String cachedReal = "";
    private Pattern pattern;

    private final BooleanSetting showFake = new BooleanSetting("Show fake name", "Yoqilishda hozirgi soxta nomni chatga yozadi", true);

    public KuroUsernameHider() {
        super("Username Hider", "Replaces your own name with a fake name in all drawn text (kuro port, client-side)", Category.KURO);
        instance = this;
        addSetting(showFake);
        load();
    }

    private static Path file() { return FabricLoader.getInstance().getConfigDir().resolve("anormal-fakename.txt"); }

    @Override
    public void onEnable() {
        load();
        if (showFake.isEnabled() && mc.player != null)
            mc.player.sendMessage(Text.literal("§7Name shown as: §f" + fake), false);
    }

    private void load() {
        try {
            Path p = file();
            if (Files.exists(p)) {
                String s = Files.readString(p).trim();
                if (!s.isEmpty() && s.length() <= 32) fake = s;
            }
        } catch (Exception ignored) {}
    }

    /** StringDecomposer mixinida chaqiriladi: har chizilayotgan/matn o'lchaydigan satr. */
    public static String apply(String text) {
        KuroUsernameHider h = instance;
        if (h == null || !h.isEnabled() || text == null || text.isEmpty()) return text;
        return h.replace(text);
    }

    private String replace(String text) {
        String real;
        try { real = MinecraftClient.getInstance().getSession().getUsername(); } catch (Exception e) { return text; }
        if (real == null || real.isEmpty() || text.length() < real.length()) return text;
        if (!real.equals(cachedReal)) { cachedReal = real; pattern = Pattern.compile(Pattern.quote(real), Pattern.CASE_INSENSITIVE); }
        Matcher m = pattern.matcher(text);
        return m.find() ? m.replaceAll(Matcher.quoteReplacement(fake)) : text;
    }
}
