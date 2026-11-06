package com.anormal.client.module.impl.spectre;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.module.ModuleManager;
import com.anormal.client.module.impl.client.Friends;
import com.anormal.client.setting.BooleanSetting;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;

/**
 * Spectre port: "Friend Guard" — stops you from hitting players on your friends list.
 *
 * <p>Spectre intercepts the attack before it is sent and shows an action-bar hint. This port
 * keeps the interception (wired from {@code ClientPlayerInteractionManagerMixin}) and the
 * 1.5 second hint cooldown, using the project's own friends list.</p>
 */
public final class SpectreFriendGuard extends Module {
    private static final long HINT_COOLDOWN_MS = 1500L;

    public final BooleanSetting notify = addSetting(new BooleanSetting("Notify", "Show a hint when a hit is held back", true));

    private static SpectreFriendGuard instance;
    private long lastHintAt;

    public SpectreFriendGuard() {
        super("SpectreFriendGuard", "Spectre port: stops you from hitting players on your friends list.", Category.SPECTRE);
        instance = this;
    }

    @Override
    public void onEnable() {
        lastHintAt = 0L;
    }

    @Override
    public void onDisable() {
        lastHintAt = 0L;
    }

    /** True when the attack must be cancelled. */
    public static boolean intercepts(Entity target) {
        SpectreFriendGuard guard = instance;
        var client = mc();
        if (guard == null || !guard.isEnabled() || client == null || client.player == null) return false;
        if (!(target instanceof PlayerEntity friend) || friend == client.player) return false;
        String name = friend.getGameProfile().name();
        if (name == null || !Friends.isFriend(name)) return false;
        guard.hint(name);
        return true;
    }

    private static net.minecraft.client.MinecraftClient mc() {
        return net.minecraft.client.MinecraftClient.getInstance();
    }

    private void hint(String name) {
        if (!notify.isEnabled()) return;
        long now = System.currentTimeMillis();
        if (now - lastHintAt < HINT_COOLDOWN_MS) return;
        lastHintAt = now;
        var client = mc();
        if (client != null && client.inGameHud != null) {
            client.inGameHud.setOverlayMessage(
                    Text.literal("[SpectreFriendGuard] Held back your hit on " + name), false);
        }
    }
}
