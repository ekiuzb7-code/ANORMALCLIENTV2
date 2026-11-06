package com.anormal.client.module.impl.spectre;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;

/**
 * Spectre port: "Auto Reconnect" — reconnects to the last server after an unexpected disconnect.
 */
public final class SpectreAutoReconnect extends Module {
    public final NumberSetting delay = addSetting(new NumberSetting("Delay", "Seconds before reconnecting", 5, 3, 60, 1));

    private ServerInfo lastServer;
    private boolean seenDisconnected;
    private int ticksLeft;

    public SpectreAutoReconnect() {
        super("SpectreAutoReconnect", "Spectre port: reconnects to the last server after an unexpected disconnect.", Category.SPECTRE);
    }

    @Override
    public void onTickAlways() {
        // Track the current server while in game (never for singleplayer/LAN/Realms)
        if (mc.player != null && mc.world != null) {
            lastServer = mc.getCurrentServerEntry();
            return;
        }

        if (lastServer == null) return;
        if (mc.currentScreen instanceof TitleScreen || mc.currentScreen instanceof MultiplayerScreen) {
            lastServer = null;
            return;
        }
        if (mc.currentScreen instanceof DisconnectedScreen) {
            if (!seenDisconnected) {
                seenDisconnected = true;
                ticksLeft = Math.round((float) delay.getAsDouble() * 20.0f);
            }                if (ticksLeft > 0) {
                    ticksLeft--;
                    if (ticksLeft == 0) {
                        ServerInfo server = lastServer;
                        lastServer = null;
                        seenDisconnected = false;
                        // Yarn 1.21.11: ConnectScreen.connect(Screen, MinecraftClient,
                        //   ServerAddress, ServerInfo, boolean, CookieStorage)
                        ConnectScreen.connect(mc.currentScreen, mc,
                                ServerAddress.parse(server.address), server, false, null);
                    }
                }
        } else {
            seenDisconnected = false;
        }
    }
}
