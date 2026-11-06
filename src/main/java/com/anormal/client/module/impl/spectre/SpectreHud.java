package com.anormal.client.module.impl.spectre;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.util.ColorUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Spectre port: "HUD" — module list, coordinates and status overlays.
 *
 * <p>Spectre draws these through its own widget set. This port keeps the element switches
 * (coordinates with nether conversion, ping, FPS, clock, armour row/column with gauge or
 * percent) and draws them as one compact HUD block in the Anormal pass.</p>
 */
public final class SpectreHud extends Module {
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");
    private static final EquipmentSlot[] ARMOUR = {
            EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD
    };

    public final BooleanSetting coordinates = addSetting(new BooleanSetting("Overworld Coords", "Show your coordinates", true));
    public final BooleanSetting netherCoordinates = addSetting(new BooleanSetting("Nether Coords", "Also show the nether conversion", true));
    public final BooleanSetting ping = addSetting(new BooleanSetting("Ping", "Show your latency", true));
    public final BooleanSetting fps = addSetting(new BooleanSetting("FPS", "Show the frame rate", true));
    public final BooleanSetting clock = addSetting(new BooleanSetting("Clock", "Show the local time", true));
    public final BooleanSetting armorStatus = addSetting(new BooleanSetting("Armor HUD", "Show armour durability", true));
    public final ModeSetting armorLayout = addSetting(new ModeSetting("Armor Layout", "Row or column", "Row", "Row", "Column"));
    public final ModeSetting armorWear = addSetting(new ModeSetting("Wear As", "Gauge, percent or both", "Gauge", "Gauge", "Percent", "Gauge + Percent"));
    public final BooleanSetting shadow = addSetting(new BooleanSetting("Shadow", "Draw text with a shadow", true));
    public final BooleanSetting customAccent = addSetting(new BooleanSetting("Custom Accent", "Use your own accent colour", false));
    public final ColorSetting accentColor = addSetting(new ColorSetting("Accent Color", "Accent colour", ColorUtils.rgba(80, 200, 255, 255)));

    private long lastFrame;
    private long smoothedFrame;
    private int frames;
    private long fpsWindowStart;
    private int currentFps;

    public SpectreHud() {
        super("SpectreHud", "Spectre port: module list, coordinates and status overlays.", Category.SPECTRE);
    }

    @Override
    public void onEnable() {
        lastFrame = 0L;
        smoothedFrame = 0L;
        frames = 0;
        fpsWindowStart = 0L;
        currentFps = 0;
    }

    public int accent() {
        return accentColor.getColor();
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        var player = mc.player;
        if (player == null || mc.world == null || mc.options.hudHidden) return;

        long now = System.nanoTime();
        if (lastFrame != 0L) smoothedFrame = smoothedFrame + ((now - lastFrame) - smoothedFrame) / 8L;
        lastFrame = now;
        frames++;
        if (fpsWindowStart == 0L) fpsWindowStart = now;
        if (now - fpsWindowStart >= 1_000_000_000L) {
            currentFps = frames;
            frames = 0;
            fpsWindowStart = now;
        }

        int x = 4;
        int y = mc.getWindow().getScaledHeight() - 10;
        int accent = customAccent.isEnabled() ? accentColor.getColor() : ColorUtils.rgba(80, 200, 255, 255);

        StringBuilder line = new StringBuilder();
        if (coordinates.isEnabled()) {
            line.append("XYZ ").append((int) player.getX()).append(' ').append((int) player.getY()).append(' ').append((int) player.getZ());
            if (netherCoordinates.isEnabled()) {
                // Both directions, like Spectre: the Nether shows the overworld equivalent and
                // the overworld shows where the same spot lands in the Nether.
                if (mc.world.getRegistryKey().equals(net.minecraft.world.World.NETHER)) {
                    line.append("  OW ").append((int) player.getX() * 8).append(' ').append((int) player.getZ() * 8);
                } else if (mc.world.getRegistryKey().equals(net.minecraft.world.World.OVERWORLD)) {
                    line.append("  NETHER ").append((int) Math.floor(player.getX() / 8.0))
                            .append(' ').append((int) Math.floor(player.getZ() / 8.0));
                }
            }
        }
        if (ping.isEnabled()) {
            var entry = mc.getNetworkHandler() == null ? null : mc.getNetworkHandler().getPlayerListEntry(player.getUuid());
            if (line.length() > 0) line.append("  ·  ");
            line.append("Ping ").append(entry == null ? 0 : entry.getLatency()).append("ms");
        }
        if (fps.isEnabled()) {
            if (line.length() > 0) line.append("  ·  ");
            line.append(currentFps).append(" FPS");
        }
        if (clock.isEnabled()) {
            if (line.length() > 0) line.append("  ·  ");
            line.append(LocalTime.now().format(CLOCK));
        }
        if (line.length() > 0) {
            context.drawText(mc.textRenderer, line.toString(), x, y, accent, shadow.isEnabled());
            y -= 11;
        }

        if (!armorStatus.isEnabled()) return;
        boolean column = armorLayout.is("Column");
        int index = 0;
        for (EquipmentSlot slot : ARMOUR) {
            ItemStack stack = player.getEquippedStack(slot);
            if (stack.isEmpty() || !stack.isDamageable()) continue;
            int max = stack.getMaxDamage();
            int left = max - stack.getDamage();
            float fraction = max <= 0 ? 0.0F : (float) left / max;
            String label = switch (armorWear.getValue()) {
                case "Percent" -> Math.round(fraction * 100.0F) + "%";
                case "Gauge + Percent" -> left + " (" + Math.round(fraction * 100.0F) + "%)";
                default -> left + "/" + max;
            };
            int dx = column ? x : x + index * 34;
            int dy = column ? y - index * 10 : y;
            context.drawText(mc.textRenderer, label, dx, dy, wearColour(fraction), shadow.isEnabled());
            index++;
        }
    }

    private static int wearColour(float fraction) {
        int red = (int) (255 * (1.0F - fraction));
        int green = (int) (255 * fraction);
        return 0xFF000000 | red << 16 | green << 8;
    }
}
