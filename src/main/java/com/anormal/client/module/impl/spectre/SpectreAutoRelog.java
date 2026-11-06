package com.anormal.client.module.impl.spectre;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.world.World;

/**
 * Spectre port: "Auto Relog" — relogs once when you go down to deepslate level.
 */
public final class SpectreAutoRelog extends Module {
    private static final int REARM_TICKS = 40;

    public final NumberSetting yLevel = addSetting(new NumberSetting("Y Level", "Y that triggers the relog", 0, -60, 16, 1));
    public final NumberSetting delay = addSetting(new NumberSetting("Delay", "Seconds before rejoining", 3, 1, 30, 0.5));

    private net.minecraft.client.world.ClientWorld seenLevel;
    private net.minecraft.client.network.ClientPlayerEntity seenPlayer;
    private int aboveTicks;
    private ServerInfo rejoin;
    private boolean disconnected;
    private int rejoinTicks;

    public SpectreAutoRelog() {
        super("SpectreAutoRelog", "Spectre port: relogs once when you go down to deepslate level.", Category.SPECTRE);
    }

    @Override
    public void onDisable() {
        forget();
    }

    private void forget() {
        seenLevel = null;
        seenPlayer = null;
        aboveTicks = 0;
        rejoin = null;
        disconnected = false;
    }

    @Override
    public void onTick() {
        if (rejoin != null) {
            waitToRejoin();
            return;
        }
        if (mc.world == null) {
            aboveTicks = 0;
        }

        var player = mc.player;
        var level = mc.world;
        if (player == null || level == null) {
            aboveTicks = 0;
            return;
        }

        if (level != seenLevel || player != seenPlayer) {
            seenLevel = level;
            seenPlayer = player;
            aboveTicks = 0;
        }

        if (level.getRegistryKey().getValue().equals(World.OVERWORLD.getValue())) {
            double y = player.getY();
            double line = yLevel.getAsDouble();
            if (y >= line + 4.0) {
                if (aboveTicks < REARM_TICKS) aboveTicks++;
            } else if (y < line) {
                boolean armed = aboveTicks >= REARM_TICKS;
                aboveTicks = 0;
                if (armed) relog(y);
            }
        } else {
            aboveTicks = 0;
        }
    }

    private void relog(double y) {
        ServerInfo server = mc.getCurrentServerEntry();
        if (server == null) return;
        rejoin = server;
        disconnected = false;
        rejoinTicks = Math.round((float) delay.getAsDouble() * 20.0f);
        ServerInfo tracked = server;
        mc.execute(() -> {
            if (rejoin == tracked && mc.world != null) {
                disconnectToWorld("SpectreAutoRelog: reached Y " + (int) Math.floor(y));
                disconnected = true;
            }
        });
    }

    private void waitToRejoin() {
        if (disconnected) {
            if (mc.world == null) {
                if (--rejoinTicks <= 0) {
                    ServerInfo server = rejoin;
                    var parent = mc.currentScreen;
                    rejoin = null;
                    mc.execute(() -> {
                        if (mc.world == null) connect(parent, server);
                    });
                }
            } else {
                rejoin = null;
            }
        }
    }

    private void connect(net.minecraft.client.gui.screen.Screen parent, ServerInfo server) {
        mc.setScreen(parent);
        try {
            ConnectScreen.connect(parent, mc, ServerAddress.parse(server.address), server, false, null);
        } catch (Throwable ignored) {
        }
    }

    private static void disconnectToWorld(String reason) {
        // Same clean leave Anormal's AutoDisconnect uses (drops into the server list flow)
        try {
            mc.disconnect(net.minecraft.text.Text.literal(reason));
        } catch (Throwable ignored) {
        }
    }
}
