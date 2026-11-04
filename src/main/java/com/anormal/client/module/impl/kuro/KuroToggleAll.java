package com.anormal.client.module.impl.kuro;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.module.ModuleManager;
import com.anormal.client.util.NotificationManager;

import java.util.ArrayList;
import java.util.List;

/**
 * kuro {@code ToggleAll} port.
 *
 * <p>Bind qilingan tugma barcha yoqilgan kuro modulni o'chiradi va qaysilarini
 * o'chirgani eslab qoladi; yana bosilsa aynan shularni qayta yoqadi. Hech
 * narsa eslalmagan bo'lsa — ataylab yoqilgani ma'noli bo'lgan modullar
 * (Flight, Ding Dong Builder, Auto Walk...) bundan mustasno hammasini yoqadi.</p>
 *
 * <p>Moslash: kuro {@code KuroClient.modules.modules} bilan butun ro'yxatni
 * silaydi; Anormal'da shu o'rniga {@link Category#KURO} bo'limidagi modullar
 * silakanadi (Anormal'ning o'z modullari tegmalanmaydi). Kuro tomonidagi
 * SidebarUi / BlobUi / GlassUi / Freelook / WaypointAdd / CoordShare ushbu
 * stackga port qilinmagan, shuning uchun ularni istisno qilishga hojat yo'q.</p>
 */
public class KuroToggleAll extends Module {
    private final List<Module> remembered = new ArrayList<>();

    public KuroToggleAll() { super("Toggle All", "Turns every kuro module off, then back on", Category.KURO); }

    /** kuro: asosiy vosita bind qilingan tugma (toggle emas, start/pause sifatida). */
    @Override
    public boolean onKeyPressed() {
        toggle();
        return true;
    }

    @Override
    public void toggle() {
        int before = 0, after = 0;
        for (Module m : kuroModules()) if (m.isEnabled() && m != this) before++;
        NotificationManager.quiet = true;               // one summary toast instead of one per module
        try { toggleAll(); } finally { NotificationManager.quiet = false; }
        for (Module m : kuroModules()) if (m.isEnabled() && m != this) after++;
        if (after != before) NotificationManager.info("Toggle All", Math.abs(after - before) + " module"
                + (Math.abs(after - before) == 1 ? "" : "s") + (after > before ? " enabled" : " disabled"));
    }

    private void toggleAll() {
        List<Module> all = kuroModules();

        List<Module> active = new ArrayList<>();
        for (Module m : all) if (m != this && m.isEnabled()) active.add(m);

        if (!active.isEmpty()) {                       // something is on -> switch everything off
            remembered.clear();
            remembered.addAll(active);
            for (Module m : active) m.setEnabled(false);
            return;
        }

        if (!remembered.isEmpty()) {                   // all off -> bring back what was on
            for (Module m : remembered) m.setEnabled(true);
            remembered.clear();
            return;
        }

        for (Module m : all) {                         // nothing remembered -> enable everything sensible
            if (m == this || excluded(m)) continue;
            m.setEnabled(true);
        }
    }

    private static List<Module> kuroModules() {
        List<Module> out = new ArrayList<>();
        for (Module m : ModuleManager.getModules()) if (m.getCategory() == Category.KURO) out.add(m);
        return out;
    }

    /** kuro: faqat ataylab yoqilgani ma'noli bo'lgan modullar istisno qilinadi. */
    private static boolean excluded(Module m) {
        // To'liq ro'yxat qolgan kuro portlari yaratilgach kengaytiriladi.
        return m instanceof KuroFlight || m instanceof KuroAutoWalk || m instanceof KuroElytraSwap
                || m instanceof KuroBaritoneControl || m instanceof KuroAutoBuild || m instanceof KuroAutoMiner;
    }
}
