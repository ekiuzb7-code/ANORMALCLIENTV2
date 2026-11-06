package com.anormal.client.module.impl.spectre;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.lucid.StringSetting;
import net.minecraft.text.Text;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Spectre port: "Skin Change" — wears another player's skin by name.
 *
 * <p>Spectre patches the avatar render state with the resolved skin texture. This port keeps the
 * name resolution (Mojang profile lookup, cached, off the render thread) and the self / others
 * split; the texture it resolves is exposed to the skin layer so a hook can wear it.</p>
 */
public final class SpectreSkinChange extends Module {
    public final StringSetting selfPlayer = addSetting(new StringSetting("Self Player", "Skin to wear yourself", ""));
    public final StringSetting othersPlayer = addSetting(new StringSetting("Others Player", "Skin worn by other players", ""));
    public final BooleanSetting resolveUuid = addSetting(new BooleanSetting("Resolve UUID", "Look the name up on Mojang", true));

    private static final Map<String, String> SKINS = new ConcurrentHashMap<>();
    private static SpectreSkinChange active;

    public SpectreSkinChange() {
        super("SpectreSkinChange", "Spectre port: wears another player's skin by name.", Category.SPECTRE);
    }

    @Override
    public void onEnable() {
        active = this;
        fetch(selfPlayer.getValue());
        fetch(othersPlayer.getValue());
    }

    @Override
    public void onDisable() {
        if (active == this) active = null;
    }

    @Override
    public void onTick() {
        fetch(selfPlayer.getValue());
        fetch(othersPlayer.getValue());
    }

    /**
     * Skin the given player should wear, taken from the tab list of the target name so no
     * texture download is needed. Returns null when nothing is overridden.
     */
    public static net.minecraft.entity.player.SkinTextures skinFor(
            net.minecraft.client.network.AbstractClientPlayerEntity player) {
        SpectreSkinChange module = active;
        if (module == null || !module.isEnabled()) return null;
        var client = net.minecraft.client.MinecraftClient.getInstance();
        boolean self = player == client.player;
        String target = (self ? module.selfPlayer : module.othersPlayer).getValue();
        if (target == null || target.isBlank()) return null;
        var handler = client.getNetworkHandler();
        if (handler == null) return null;
        var entry = handler.getPlayerListEntry(target.trim());
        return entry == null ? null : entry.getSkinTextures();
    }

    /** Skin texture id for a player name, or null when nothing was resolved. */
    public static String skinFor(String name) {
        return name == null ? null : SKINS.get(name.toLowerCase(java.util.Locale.ROOT));
    }

    public static boolean changesSelf() {
        return active != null && !active.selfPlayer.getValue().isBlank();
    }

    private void fetch(String name) {
        if (name == null || name.isBlank() || !resolveUuid.isEnabled()) return;
        String key = name.toLowerCase(java.util.Locale.ROOT);
        if (SKINS.containsKey(key)) return;
        SKINS.put(key, "");
        Thread thread = new Thread(() -> {
            String texture = resolve(name.trim());
            if (texture != null) SKINS.put(key, texture);
        }, "spectre-skin-" + key);
        thread.setDaemon(true);
        thread.start();
    }

    private static String resolve(String name) {
        try {
            HttpURLConnection request = (HttpURLConnection) new URL(
                    "https://api.mojang.com/users/profiles/minecraft/" + java.net.URLEncoder.encode(name, "UTF-8")).openConnection();
            request.setConnectTimeout(4000);
            request.setReadTimeout(4000);
            request.setRequestProperty("User-Agent", "AnormalClient/1.0");
            if (request.getResponseCode() != 200) return null;
            String body = new String(request.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            int id = body.indexOf("\"id\"");
            if (id < 0) return null;
            int start = body.indexOf('"', body.indexOf(':', id) + 1);
            int end = body.indexOf('"', start + 1);
            if (start < 0 || end <= start) return null;
            return body.substring(start + 1, end);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** Message shown in chat when a lookup fails, so the user knows why nothing changed. */
    public static Text notResolved() {
        return Text.literal("[SpectreSkinChange] Skin lookup failed; keeping the current skin.");
    }
}
