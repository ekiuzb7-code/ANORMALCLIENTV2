package com.anormal.client.module.impl.kuro;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.module.ModuleManager;

/**
 * kuro {@code FoundList} port: kuro'da bu StashFinder topgan joylar ro'yxatini
 * o'z GUI ekranida ko'rsatadi va bind tugmaga bog'lanadi. Anormal'da Screen
 * qatlami yo'q, shuning uchun bu modul ishga tushgach KuroStashFinder'ning
 * chatdagi topilmalar ro'yxatini ("list") so'raydi va bir zumda o'chadi —
 * bind tugma sifatida kuro'dagi kabi ishlaydi.
 *
 * <p>Bog'lanish nom orqali (reflection) qilinadi: KuroStashFinder hali port
 * qilinmagan bo'lsa ham compilyatsiya buzilmaydi, port qilinganda esa
 * avtomatik ishlay boshlaydi.</p>
 */
public class KuroFoundList extends Module {

    public KuroFoundList() { super("Found List", "Show the finds StashFinder has logged (via chat)", Category.KURO); }

    @Override
    public void onEnable() {
        Module stash = ModuleManager.getModuleByName("Stash Finder");
        if (stash != null) {
            try {
                stash.getClass().getMethod("listFinds").invoke(stash);
            } catch (ReflectiveOperationException | RuntimeException e) {
                say("Stash Finder has no list yet (it has found nothing, or it is not ported).");
            }
        } else {
            say("Stash Finder module is not available.");
        }
        setEnabled(false);                                     // switches itself straight back off, like kuro
    }

    private static void say(String s) {
        if (mc.player != null) mc.player.sendMessage(net.minecraft.text.Text.literal("[Found List] " + s), false);
    }
}
