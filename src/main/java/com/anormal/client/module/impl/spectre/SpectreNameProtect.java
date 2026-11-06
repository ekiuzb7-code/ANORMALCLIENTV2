package com.anormal.client.module.impl.spectre;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.lucid.StringSetting;
import net.minecraft.text.Text;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Spectre port: "Name Protect" — hides the names of you and everyone around you.
 *
 * <p>Spectre rewrites every name before the chat, tab list and nametag layers read it. This port
 * keeps the alias, the extra name list, the "Others" mode (keep / same label / numbered) and the
 * other label choice, and applies the mapping through the chat label hook; the same map is
 * exposed for the nametag layers.</p>
 */
public final class SpectreNameProtect extends Module {
    public final StringSetting alias = addSetting(new StringSetting("Your Alias", "The name shown for you", "You"));
    public final StringSetting extraNames = addSetting(new StringSetting("Extra Names", "Extra names, comma separated", ""));
    public final ModeSetting others = addSetting(new ModeSetting("Others", "How other players are shown", "Keep", "Keep", "Same Label", "Numbered"));
    public final ModeSetting othersLabel = addSetting(new ModeSetting("Others Label", "Label used for other players", "Player", "Player", "Anonymous", "Unknown"));
    public final BooleanSetting chat = addSetting(new BooleanSetting("Chat", "Apply inside chat messages", true));

    private static final Map<String, String> LABELS = new LinkedHashMap<>();
    private static final Map<String, String> EXTRA = new HashMap<>();
    private static SpectreNameProtect active;

    public SpectreNameProtect() {
        super("SpectreNameProtect", "Spectre port: hides the names of you and everyone around you.", Category.SPECTRE);
    }

    @Override
    public void onEnable() {
        active = this;
        rebuild();
    }

    @Override
    public void onDisable() {
        if (active == this) active = null;
        LABELS.clear();
        EXTRA.clear();
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;
        String own = mc.player.getGameProfile().name();
        if (own == null) return;
        String wanted = alias.getValue();
        if (!LABELS.containsKey(own) || !wanted.equals(LABELS.get(own))) {
            LABELS.put(own, wanted);
        }
        int index = 0;
        for (var player : mc.world.getPlayers()) {
            String name = player.getGameProfile().name();
            if (name == null || LABELS.containsKey(name)) continue;
            LABELS.put(name, label(name, index++));
        }
    }

    private void rebuild() {
        EXTRA.clear();
        String raw = extraNames.getValue();
        if (raw == null || raw.isBlank()) return;
        for (String part : raw.split(",")) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) continue;
            int split = trimmed.indexOf('=');
            if (split > 0) {
                EXTRA.put(trimmed.substring(0, split).trim(), trimmed.substring(split + 1).trim());
            }
        }
    }

    private String label(String name, int index) {
        String extra = EXTRA.get(name);
        if (extra != null) return extra;
        return switch (others.getValue()) {
            case "Same Label" -> othersLabel.getValue();
            case "Numbered" -> othersLabel.getValue() + " " + (index + 1);
            default -> name;
        };
    }

    /** Renamed text for the chat and label layers. */
    public static Text rename(Text text) {
        SpectreNameProtect module = active;
        if (module == null || !module.isEnabled() || !module.chat.isEnabled() || text == null) return text;
        String raw = text.getString();
        if (raw.isEmpty()) return text;
        String replaced = raw;
        for (Map.Entry<String, String> entry : LABELS.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) continue;
            if (!entry.getKey().equals(entry.getValue())) {
                replaced = replaced.replace(entry.getKey(), entry.getValue());
            }
        }
        return replaced.equals(raw) ? text : Text.literal(replaced);
    }

    /** Display name for the nametag layers. */
    public static String display(String name) {
        SpectreNameProtect module = active;
        if (module == null || name == null) return name;
        String mapped = LABELS.get(name);
        if (mapped == null) mapped = EXTRA.get(name);
        return mapped == null ? name : mapped;
    }
}
