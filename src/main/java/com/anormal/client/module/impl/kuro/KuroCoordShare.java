package com.anormal.client.module.impl.kuro;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ModeSetting;

import net.minecraft.text.Text;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;

/**
 * kuro {@code CoordShare} port: faollashganda koordinatalarni buferga nusxalaydi
 * va/(chatga) yuboradi ("hech qachon yonganda qolmaydi"). Kuro'daki
 * {@code .coords} buyruq tizimi bu portda yo'q — modul kalitga bog'lanadi.
 *
 * <p>Anormaldagi farqlar: kuro'daki {@code Notifications.info(...)} tomatiga
 * o'rniga natija oddiy chat xabari sifatida ko'rsatiladi ({@code mc.player.sendMessage});
 * kuro {@code Setting.Num.labelled("Copy","Chat","Copy + Chat")} rejimi bu yerda
 * ModeSetting satrlari bilan berilgan.</p>
 */
public class KuroCoordShare extends Module {
    private final ModeSetting action = new ModeSetting("Action", "Buferga nusxalash / chat / ikkalasi",
            "Copy", "Copy", "Chat", "Copy + Chat");
    private final BooleanSetting dimension = new BooleanSetting("Dimension", "Dunyo nomini qo'shadi", true);
    private final BooleanSetting other = new BooleanSetting("Nether / Overworld equivalent", "Konvert qilingan koordinata ham", false);

    public KuroCoordShare() {
        super("Coord Share", "Copies your coordinates to the clipboard and/or posts them in chat (kuro port)", Category.KURO);
        addSetting(action);
        addSetting(dimension);
        addSetting(other);
    }

    @Override
    public void onEnable() {
        share(false);
        setEnabled(false);
    }

    /** {@code forceChat} — Action sozlamasidan qat'i nazar chatga yuboradi. */
    public void share(boolean forceChat) {
        if (mc.player == null || mc.world == null) return;
        String text = format();
        String m = action.getValue();
        boolean copy = !forceChat && (m.equals("Copy") || m.equals("Copy + Chat")) || forceChat && m.equals("Copy + Chat");
        boolean chat = forceChat || m.equals("Chat") || m.equals("Copy + Chat");
        if (copy) mc.keyboard.setClipboard(text);
        if (chat) mc.player.networkHandler.sendChatMessage(text);

        String verb = copy && chat ? "Copied and sent" : chat ? "Sent to chat" : "Copied";
        if (mc.inGameHud != null) mc.inGameHud.getChatHud().addMessage(Text.literal("§7" + verb + ": §f" + text));
    }

    private String format() {
        int x = (int) Math.floor(mc.player.getX()), y = (int) Math.floor(mc.player.getY()), z = (int) Math.floor(mc.player.getZ());
        StringBuilder sb = new StringBuilder("X: ").append(x).append(" Y: ").append(y).append(" Z: ").append(z);
        RegistryKey<net.minecraft.world.World> dim = mc.world.getRegistryKey();
        boolean nether = dim == World.NETHER, overworld = dim == World.OVERWORLD;
        if (dimension.isEnabled()) sb.append(" (").append(overworld ? "Overworld" : nether ? "Nether"
                : dim == World.END ? "End" : "Custom").append(")");
        if (other.isEnabled() && (overworld || nether)) {
            int ox = Math.floorDiv(x, overworld ? 8 : 1) * (nether ? 8 : 1), oz = Math.floorDiv(z, overworld ? 8 : 1) * (nether ? 8 : 1);
            sb.append(" [").append(overworld ? "Nether" : "Overworld").append(": ").append(ox).append(", ").append(oz).append("]");
        }
        return sb.toString();
    }
}
