package com.anormal.client.module.impl.kuro;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;

import net.minecraft.client.option.KeyBinding;
import net.minecraft.util.math.MathHelper;

/**
 * kuro {@code MacroRecord} port.
 *
 * <p>Bind qilingan tugma: bosilsa kalit bosishlarini, sichqoncha tugmalari va
 * kamera burilishini yozishni boshlaydi; yana bosilsa to'xtatadi. Yozish
 * oldingi makroni almashtiradi va "Macro" moduli tomonidan o'ynatiladi.</p>
 */
public class KuroMacroRecord extends Module {
    private static final int MAX_TICKS = 20 * 60 * 5;       // 5 minutes
    private float lastYaw, lastPitch;

    public KuroMacroRecord() { super("Macro Record", "Records keys and camera turns into a macro", Category.KURO); }

    @Override
    public void onEnable() {
        if (mc.player == null) { setEnabled(false); return; }
        KuroMacro m = KuroMacro.get();
        if (m != null && m.isEnabled()) { KuroMacro.say("Stop the macro first."); setEnabled(false); return; }
        KuroMacro.frames.clear();
        KuroMacro.recording = true;
        lastYaw = mc.player.getYaw();
        lastPitch = mc.player.getPitch();
        KuroMacro.say("Recording... press the key again to stop.");
    }

    @Override
    public void onDisable() {
        if (!KuroMacro.recording) return;
        KuroMacro.recording = false;
        var f = KuroMacro.frames;
        while (!f.isEmpty() && f.get(f.size() - 1).mask() == 0
                && Math.abs(f.get(f.size() - 1).dyaw()) < 0.01f && Math.abs(f.get(f.size() - 1).dpitch()) < 0.01f)
            f.remove(f.size() - 1);                         // trim the idle ticks around pressing the stop key
        KuroMacro.save();
        KuroMacro.say("Saved " + f.size() + " ticks (" + String.format("%.1f", f.size() / 20.0) + "s).");
    }

    @Override
    public void onPreTick() {
        if (mc.player == null || mc.world == null) { setEnabled(false); return; }
        if (KuroMacro.frames.size() >= MAX_TICKS) { KuroMacro.say("Reached the 5 minute limit."); setEnabled(false); return; }

        int mask = 0;
        float dyaw = 0, dpitch = 0;
        if (mc.currentScreen == null) {
            KeyBinding[] keys = KuroMacro.keys();
            for (int i = 0; i < keys.length; i++) if (keys[i].isPressed()) mask |= 1 << i;
            dyaw = MathHelper.wrapDegrees(mc.player.getYaw() - lastYaw);
            dpitch = mc.player.getPitch() - lastPitch;
        }
        lastYaw = mc.player.getYaw();
        lastPitch = mc.player.getPitch();
        KuroMacro.frames.add(new KuroMacro.Frame(mask, dyaw, dpitch));
    }
}
