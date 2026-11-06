package com.anormal.client.module.impl.spectre;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.NumberSetting;

/**
 * Spectre port: "Home Meta" — re-sets a home, RTPs away, then teleports back home.
 */
public final class SpectreHomeMeta extends Module {
    private static final double TELEPORT_DISTANCE_SQ = 25.0;

    public final NumberSetting homeNumber = addSetting(new NumberSetting("Home Number", "Which /home slot to work with", 3, 1, 10, 1));
    public final NumberSetting delay = addSetting(new NumberSetting("Delay (s)", "Seconds between command hops", 0.5, 0.0, 10.0, 0.5));
    public final NumberSetting rtpTimeout = addSetting(new NumberSetting("RTP Timeout (s)", "Max seconds to wait for the RTP teleport", 10, 1, 30, 1));

    private int step;
    private long nextAtMs;
    private long rtpDeadlineMs;
    private net.minecraft.util.math.Vec3d rtpStart;
    private int home;

    public SpectreHomeMeta() {
        super("SpectreHomeMeta", "Spectre port: re-sets a home, RTPs away, then teleports back home.", Category.SPECTRE);
    }

    @Override
    public void onEnable() {
        if (mc.player == null) {
            setEnabled(false);
            return;
        }
        home = (int) homeNumber.getAsDouble();
        step = 0; // DELHOME
        nextAtMs = 0L;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.getNetworkHandler() == null) {
            setEnabled(false);
            return;
        }
        long now = System.currentTimeMillis();
        if (now < nextAtMs) return;

        long delayMs = (long) (delay.getAsDouble() * 1000.0);
        switch (step) {
            case 0 -> {
                command("delhome " + home);
                step = 1;
                nextAtMs = now + delayMs;
            }
            case 1 -> {
                command("sethome " + home);
                step = 2;
                nextAtMs = now + delayMs;
            }
            case 2 -> {
                rtpStart = new net.minecraft.util.math.Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
                command("rtp");
                rtpDeadlineMs = now + (long) (rtpTimeout.getAsDouble() * 1000.0);
                step = 3;
            }
            case 3 -> {
                boolean moved = rtpStart != null
                        && mc.player.squaredDistanceTo(rtpStart) > TELEPORT_DISTANCE_SQ;
                if (moved || now >= rtpDeadlineMs) {
                    step = 4;
                    nextAtMs = now + delayMs;
                }
            }
            default -> {
                command("home " + home);
                setEnabled(false);
            }
        }
    }

    private void command(String command) {
        var handler = mc.getNetworkHandler();
        if (handler == null) return;
        handler.sendChatCommand(command);
    }
}
