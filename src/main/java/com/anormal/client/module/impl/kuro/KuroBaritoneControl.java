package com.anormal.client.module.impl.kuro;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.module.ModuleManager;
import com.anormal.client.module.impl.render.Waypoints;
import com.anormal.client.module.impl.world.XRay;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.player.PlayerEntity;

import java.util.StringJoiner;

/**
 * kuro {@code BaritoneControl} port: Baritone yo'l yo'naltirish modini
 * shartli hasta buyruqlari bilan boshqaramiz. Baritone alohida mod: o'z
 * jar faylingizni mods papkasiga qo'ying (1.21.11 uchun
 * baritone-standalone-fabric, v1.17.0). Bu modul faqat Baritone'ning "#"
 * chat buyruqlarini siz uchun yuboradi (chatga hech narsa tieyin serverga
 * yo'q). Modul bind tugmasi = panika: Baritone'ni to'xtatadi (#cancel).
 *
 * <p>Moslash: kuro barchasini {@code Setting.Button} panelda tugma ko'rsatadi;
 * Anormal sobrasi sozlamalarda tugma turlari yo'q (KuroMacro usulidagi
 * hujjatlarib), o'sha faoliyatlar chuqur metod bo'ildi: hohlasangiz
 * Anormal "Macros" yoki boshqa moduldan qo'ng'iroq qilish mumkin:
 * {@link #cancel()}, {@link #pause()}, {@link #resume()}, {@link #goThisWay()},
 * {@link #tunnelAhead()}, {@link #digToY()}, {@link #applyMoveSettings()},
 * {@link #surface()}, {@link #come()}, {@link #gotoBase()}, {@link #gotoTrail()},
 * {@link #gotoDebris()}, {@link #followNearest()}, {@link #blacklistClosest()},
 * {@link #invertGoal()}, {@link #etaProgress()}, {@link #gotoWaypoint()},
 * {@link #mineEspBlocks()}, {@link #explore()}, {@link #farm()} va
 * {@link #followPlayers()}.</p>
 */
public class KuroBaritoneControl extends Module {
    private static final String[] MOD_IDS = {"baritone", "baritone-meteor"};

    private final NumberSetting thisWay = new NumberSetting("This way (blocks)", "#thisway distance", 100.0, 10.0, 1000.0, 10.0);
    private final NumberSetting tunnelH = new NumberSetting("Tunnel height", "#tunnel height", 2.0, 1.0, 5.0, 1.0);
    private final NumberSetting tunnelW = new NumberSetting("Tunnel width", "#tunnel width", 1.0, 1.0, 5.0, 1.0);
    private final NumberSetting tunnelD = new NumberSetting("Tunnel length", "0 = forever", 0.0, 0.0, 500.0, 10.0);
    private final NumberSetting digY = new NumberSetting("Dig-to Y level", "#goto target Y", 12.0, -64.0, 320.0, 1.0);
    private final BooleanSetting allowBreak = new BooleanSetting("Allow break", true);
    private final BooleanSetting allowPlace = new BooleanSetting("Allow place", true);
    private final BooleanSetting allowParkour = new BooleanSetting("Allow parkour", true);
    private final BooleanSetting sprint = new BooleanSetting("Allow sprint", true);

    public KuroBaritoneControl() {
        super("Baritone", "Sends Baritone's own #commands for you", Category.KURO);
        addSetting(thisWay);
        addSetting(tunnelH);
        addSetting(tunnelW);
        addSetting(tunnelD);
        addSetting(digY);
        addSetting(allowBreak);
        addSetting(allowPlace);
        addSetting(allowParkour);
        addSetting(sprint);
    }

    public static boolean installed() {
        for (String id : MOD_IDS) if (FabricLoader.getInstance().isModLoaded(id)) return true;
        return false;
    }

    private void say(String s) {
        if (mc.player != null) mc.player.sendMessage(net.minecraft.text.Text.literal("[Baritone] " + s), false);
    }

    /** Runs a Baritone command, e.g. cmd("goto 1 64 2") sends "#goto 1 64 2" which Baritone handles client-side. */
    public void cmd(String command) {
        if (mc.player == null || mc.player.networkHandler == null) return;
        if (!installed()) {
            say("Baritone is not installed. Put baritone-standalone-fabric (v1.17.0 for 1.21.11) in your mods folder.");
            return;
        }
        mc.player.networkHandler.sendChatMessage("#" + command);
    }

    // ------------------------------------------------------------------ buttons (kuro: Setting.Button)

    public void cancel() { cmd("cancel"); }
    public void pause() { cmd("pause"); }
    public void resume() { cmd("resume"); }
    public void goThisWay() { cmd("thisway " + thisWay.getValue().intValue()); }
    public void digToY() { cmd("goto " + digY.getValue().intValue()); }
    public void applyMoveSettings() {
        cmd("set allowBreak " + allowBreak.isEnabled());
        cmd("set allowPlace " + allowPlace.isEnabled());
        cmd("set allowParkour " + allowParkour.isEnabled());
        cmd("set allowSprint " + sprint.isEnabled());
        say("Movement settings sent to Baritone.");
    }
    public void surface() { cmd("surface"); }
    public void come() { cmd("come"); }
    public void blacklistClosest() { cmd("blacklist"); }
    public void invertGoal() { cmd("invert"); }
    public void etaProgress() { cmd("eta"); }
    public void explore() { cmd("explore"); }
    public void farm() { cmd("farm"); }
    public void followPlayers() { cmd("follow players"); }

    /** Sends ordinary chat without Baritone grabbing it. With Baritone's "chatControl" on (its default), any chat
     * message that happens to be a Baritone command word ("come", "stop", "mine", "help", "goto 1 2 3"...) is run by
     * Baritone and never reaches the server. This switches chatControl / chatControlAnyway off for the single send,
     * then puts them back. Messages that start with Baritone's own prefix (default "#") are left alone. */
    public static void sendPlainChat(net.minecraft.client.network.ClientPlayNetworkHandler net, String msg) {
        java.util.List<Object[]> restore = new java.util.ArrayList<>();   // {setting object, value field, old value}
        try {
            if (installed()) {
                Object settings = Class.forName("baritone.api.BaritoneAPI").getMethod("getSettings").invoke(null);
                String prefix = "#";
                try {
                    Object ps = settings.getClass().getField("prefix").get(settings);
                    prefix = String.valueOf(ps.getClass().getField("value").get(ps));
                } catch (ReflectiveOperationException | RuntimeException ignored) {}
                if (prefix.isEmpty() || !msg.startsWith(prefix)) {
                    for (String name : new String[]{"chatControl", "chatControlAnyway"}) {
                        try {
                            Object setting = settings.getClass().getField(name).get(settings);
                            java.lang.reflect.Field value = setting.getClass().getField("value");
                            Object old = value.get(setting);
                            value.set(setting, Boolean.FALSE);
                            restore.add(new Object[]{setting, value, old});
                        } catch (ReflectiveOperationException | RuntimeException ignored) {}
                    }
                }
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {}
        try {
            net.sendChatMessage(msg);
        } finally {
            for (Object[] r : restore) {
                try { ((java.lang.reflect.Field) r[1]).set(r[0], r[2]); } catch (ReflectiveOperationException | RuntimeException ignored) {}
            }
        }
    }

    /** #tunnel <height> <width> <depth>; depth 0 digs until stopped. */
    public void tunnelAhead() {
        int d = tunnelD.getValue().intValue();
        cmd("tunnel " + tunnelH.getValue().intValue() + " " + tunnelW.getValue().intValue() + (d > 0 ? " " + d : ""));
    }

    /** Nearest "found" target that this port can know about: ancient debris (Anormal has no kuro Finds list yet). */
    public void gotoBase() { gotoDebris(); }

    public void gotoTrail() {
        say("No trails found (the kuro Trail Tracker / New Chunks modules were not ported).");
    }

    public void gotoDebris() {
        KuroAncientDebris ad = ModuleManager.getModule(KuroAncientDebris.class);
        net.minecraft.util.math.BlockPos b = ad == null || !ad.isEnabled() ? null : ad.nearest();
        if (b == null) { say("No ancient debris found (turn on Ancient Debris and get close to some)."); return; }
        say("Going to ancient debris at " + b.getX() + " " + b.getY() + " " + b.getZ());
        cmd("goto " + b.getX() + " " + b.getY() + " " + b.getZ());
    }

    public void followNearest() {
        if (mc.player == null || mc.world == null) return;
        PlayerEntity best = null;
        double bd = Double.MAX_VALUE;
        for (PlayerEntity p : mc.world.getPlayers()) {
            if (p == mc.player
                    || com.anormal.client.module.impl.client.Friends.isFriend(p.getName().getString())) continue;
            double d = mc.player.squaredDistanceTo(p);
            if (d < bd) { bd = d; best = p; }
        }
        if (best == null) { say("No other player nearby."); return; }
        cmd("follow player " + best.getName().getString());
    }

    public void gotoWaypoint() {
        if (mc.player == null) return;
        Waypoints w = ModuleManager.getModule(Waypoints.class);
        Waypoints.Waypoint best = null;
        double bd = Double.MAX_VALUE;
        if (w != null) {
            String dim = mc.world.getRegistryKey().getValue().toString();
            for (Waypoints.Waypoint p : w.getPoints()) {
                if (!p.on || !dim.equals(p.dim)) continue;
                double dx = mc.player.getX() - p.x, dy = mc.player.getY() - p.y, dz = mc.player.getZ() - p.z;
                double d = dx * dx + dy * dy + dz * dz;
                if (d < bd) { bd = d; best = p; }
            }
        }
        if (best == null) { say("No waypoints in this world."); return; }
        say("Going to " + best.name);
        cmd("goto " + (int) best.x + " " + (int) best.y + " " + (int) best.z);
    }

    /** #mine with the blocks the Anormal XRay list is set to (kuro: BlockEsp targets). */
    public void mineEspBlocks() {
        XRay esp = ModuleManager.getModule(XRay.class);
        if (esp == null || esp.getSelectedBlocks().isEmpty()) { say("Choose blocks in XRay first."); return; }
        StringJoiner ids = new StringJoiner(" ");
        for (net.minecraft.block.Block b : esp.getSelectedBlocks())
            ids.add(net.minecraft.registry.Registries.BLOCK.getId(b).toString());
        cmd("mine " + ids);
    }

    /** The module key = panic button: stop pathing. */
    @Override public void toggle() { cancel(); }
    @Override public void setEnabled(boolean value) { if (value) cancel(); }
}
