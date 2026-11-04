package com.anormal.client.module.impl.kuro;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.module.ModuleManager;
import com.anormal.client.module.impl.world.Freecam;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.kuro.KuroSwapLimit;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;

/**
 * Kuro Auto Dtap birga bir port: end kristalga har bir urush bir tickda bir necha
 * marta yuboriladi (Taps) — birinchi urush yo'q qilib chetga chiqilsa yoki
 * lag bo'lsa ham kristallar buziladi. O'z kliklaringiz uchun ham ishlaydi;
 * "Auto break" yonsa kursordiniz turgan kristalni o'zlikidan uradi.
 *
 * <p>kuro AttackEntityCallback'da aynan hujumdan avval ishlaydi; Anormal'da fabric
 * callback compile bilan kelmaydi, shuning uchun hujum payti KuroWTap'dagi
 * swing-edge usulida aniqlanadi (hujum belgilangan holda qo'l swing yangi
 * bo'lganda) va qo'shimcha urushlar AttackPackets orqali yuboriladi.</p>
 */
public class KuroAutoDtap extends Module {
    public final NumberSetting taps = new NumberSetting("Taps", "Har hujumda qo'shimcha urushlar soni", 2.0, 2.0, 4.0, 1.0);
    public final BooleanSetting autoBreak = new BooleanSetting("Auto break", "Nishon ustdagini o'zlikidan uradi", true);
    public final NumberSetting autoDelay = new NumberSetting("Auto delay (ticks)", "Avtomatik urushlar orasidagi delay", 0.0, 0.0, 10.0, 1.0);

    private boolean busy;   // o'zimizning qo'shimcha urushlarimiz boshqa urush chetda turmasligi uchun
    private int wait;
    private boolean wasSwinging;

    public KuroAutoDtap() {
        super("AutoDtap", "Kristalga qo'shimcha urushlar — double tap (kuro port)", Category.KURO);
        addSetting(taps);
        addSetting(autoBreak);
        addSetting(autoDelay);
    }

    @Override
    public void onEnable() {
        wait = 0;
        wasSwinging = mc.player != null && mc.player.handSwinging;
    }

    private EndCrystalEntity crystalUnderCrosshair() {
        Entity te = mc.targetedEntity;
        return te instanceof EndCrystalEntity c ? c : null;
    }

    private void extraTaps(EndCrystalEntity crystal) {
        if (mc.interactionManager == null) return;
        busy = true;
        try {
            for (int i = 1; i < taps.getValue().intValue(); i++) KuroAttackPackets.hit(crystal);   // har tap = swing + attack
        } finally {
            busy = false;
        }
    }

    @Override
    public void onTick() {
        if (!autoBreak.isEnabled()) {
            trackSwingEdge();
            return;
        }
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;
        Freecam freecam = ModuleManager.getModule(Freecam.class);
        boolean freecamActive = freecam != null && freecam.isEnabled();
        if (mc.currentScreen != null || freecamActive || mc.player.isSpectator()) { wait = 0; return; }

        EndCrystalEntity crystal = crystalUnderCrosshair();
        boolean manualAttack = mc.options.attackKey.isPressed() && crystal != null;
        boolean swingEdge = manualAttack && mc.player.handSwinging && !wasSwinging;
        if (swingEdge && !busy) extraTaps(crystal);

        if (crystal != null && crystal.isAlive()) {
            if (++wait <= autoDelay.getValue().intValue()) { trackSwingEdge(); return; }
            wait = 0;
            if (!busy) KuroAttackPackets.hit(crystal);   // qo'shimcha taplar swing-edge'da qo'yiladi
        } else {
            wait = 0;
        }
        trackSwingEdge();
    }

    private void trackSwingEdge() {
        if (mc.player != null) wasSwinging = mc.player.handSwinging;
    }
}
