package com.anormal.client.module.impl.kuro;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;

import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * kuro {@code Snowflakes} port: ekranda yoz yog'adigan qor zarralari — sof
 * ko'rgazmali. kuro'da bu faqat klient menyusi ochiq bo'lganda menyu ekranidan
 * chaqiriladi; Anormal'da menyu ekraniga chizish hooki yo'q, shuning uchun bu
 * portda o'yin HUD'sida ham yog'adi (modul yoqilgan vaqgina). Amounda modil
 * miqdori, tezligi va katta zarrachalar sozlamalari kurodagi kabi saqlandi.
 */
public class KuroSnowflakes extends Module {
    private static KuroSnowflakes instance;

    private final NumberSetting amount = new NumberSetting("Amount", "Snowflakes on screen", 150.0, 20.0, 500.0, 10.0);
    private final NumberSetting speed = new NumberSetting("Speed", "Fall speed multiplier", 1.0, 0.3, 3.0, 0.1);
    private final BooleanSetting big = new BooleanSetting("Big flakes", "Occasional larger flakes", true);

    private static final class Flake { double x, y, vy, phase, drift; int size; int alpha; }

    private final List<Flake> flakes = new ArrayList<>();
    private final Random rng = new Random();
    private long last;

    public KuroSnowflakes() {
        super("Snowflakes", "Snow drifting down the screen (cosmetic)", Category.KURO);
        instance = this;
        addSetting(amount);
        addSetting(speed);
        addSetting(big);
    }

    /** Menyu ekranlari qor chizishni xohlasa, har frame bu metodni chaqiradi (kuro'dagi statik API bilan bir xil). */
    public static void draw(DrawContext g, int w, int h) {
        if (instance != null && instance.isEnabled() && w > 0 && h > 0) instance.render(g, w, h);
    }

    private Flake spawn(int w, int h, boolean anywhere) {
        Flake f = new Flake();
        f.x = rng.nextDouble() * w;
        f.y = anywhere ? rng.nextDouble() * h : -rng.nextDouble() * 20;
        f.vy = 12 + rng.nextDouble() * 28;               // pixels per second
        f.phase = rng.nextDouble() * Math.PI * 2;
        f.drift = 6 + rng.nextDouble() * 14;
        f.size = big.isEnabled() && rng.nextInt(6) == 0 ? 3 : rng.nextInt(3) == 0 ? 2 : 1;
        f.alpha = 110 + rng.nextInt(130);
        return f;
    }

    private void render(DrawContext g, int w, int h) {
        long now = System.nanoTime();
        double dt = last == 0 ? 0 : Math.min(0.1, (now - last) / 1e9);   // clamp so a pause doesn't teleport the snow
        last = now;

        int want = amount.getValue().intValue();
        while (flakes.size() < want) flakes.add(spawn(w, h, true));
        while (flakes.size() > want) flakes.remove(flakes.size() - 1);

        double t = now / 1e9;
        for (Flake f : flakes) {
            f.y += f.vy * speed.getValue() * dt;
            f.x += Math.sin(t * 0.9 + f.phase) * f.drift * dt;
            if (f.y > h + 4 || f.x < -6 || f.x > w + 6) {
                Flake n = spawn(w, h, false);
                f.x = n.x; f.y = n.y; f.vy = n.vy; f.phase = n.phase; f.drift = n.drift; f.size = n.size; f.alpha = n.alpha;
            }
            int px = (int) f.x, py = (int) f.y, c = (f.alpha << 24) | 0xFFFFFF;
            int s = f.size + 1;
            // kuro: ShaderUi.shape yumshoq doira chizadi; Anormal'da oddiy fill ishlatiladi
            g.fill(px, py, px + s, py + s, c);
        }
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.getWindow() == null) return;
        render(context, mc.getWindow().getScaledWidth(), mc.getWindow().getScaledHeight());
    }
}
