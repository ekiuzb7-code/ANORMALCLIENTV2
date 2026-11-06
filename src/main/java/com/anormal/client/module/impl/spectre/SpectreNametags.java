package com.anormal.client.module.impl.spectre;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Spectre port: "Nametags" — player nametags with held items, armour, health, ping and
 * distance.
 *
 * <p>Spectre draws a textured panel through its label renderer. This port keeps the content
 * (name, ping, health bar, armour durability, distance), the range, the tag cap and the
 * background alpha, and draws the panel in the Anormal 2D pass.</p>
 */
public final class SpectreNametags extends Module {
    private static final EquipmentSlot[] ARMOUR = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    public final BooleanSetting ping = addSetting(new BooleanSetting("Ping", "Show latency", true));
    public final BooleanSetting health = addSetting(new BooleanSetting("Health", "Show a health bar", true));
    public final BooleanSetting armor = addSetting(new BooleanSetting("Armor", "Show armour durability", true));
    public final BooleanSetting durability = addSetting(new BooleanSetting("Durability", "Show durability numbers", true));
    public final NumberSetting range = addSetting(new NumberSetting("Range", "Maximum tag distance", 128.0, 8.0, 512.0, 1.0));
    public final NumberSetting maxTags = addSetting(new NumberSetting("Max Tags", "How many tags may draw at once", 64.0, 1.0, 64.0, 1.0));
    public final NumberSetting scale = addSetting(new NumberSetting("Scale", "Panel scale", 1.0, 0.5, 2.0, 0.1));
    public final NumberSetting backgroundAlpha = addSetting(new NumberSetting("Background Alpha", "Panel opacity", 0.45, 0.0, 1.0, 0.05));
    public final BooleanSetting shadow = addSetting(new BooleanSetting("Shadow", "Draw text with a shadow", true));

    public SpectreNametags() {
        super("SpectreNametags", "Spectre port: player nametags with held items, armour, health, ping and distance.", Category.SPECTRE);
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        var self = mc.player;
        if (self == null || mc.world == null || mc.options.hudHidden) return;

        double max = range.getAsDouble();
        int budget = (int) maxTags.getAsDouble();
        List<PlayerEntity> players = new ArrayList<>();
        for (PlayerEntity other : mc.world.getPlayers()) {
            if (other == self || other.isRemoved() || other.isDead()) continue;
            if (self.distanceTo(other) > max) continue;
            players.add(other);
        }
        if (players.isEmpty()) return;
        players.sort(Comparator.comparingDouble(self::distanceTo));
        if (players.size() > budget) players = players.subList(0, budget);

        int bg = ((int) (backgroundAlpha.getAsDouble() * 255.0) << 24) | 0x00101010;
        int nameColor = 0xFFEEEEEE;

        for (PlayerEntity other : players) {
            float[] screen = project(other, self);
            if (screen == null) continue;
            String name = other.getGameProfile().name();
            if (name == null) name = other.getName().getString();
            int distance = (int) Math.round(self.distanceTo(other));

            List<String> lines = new ArrayList<>();
            if (ping.isEnabled()) {
                var entry = mc.getNetworkHandler() == null ? null : mc.getNetworkHandler().getPlayerListEntry(other.getUuid());
                lines.add((entry == null ? 0 : entry.getLatency()) + "ms");
            }
            if (armor.isEnabled()) {
                StringBuilder gear = new StringBuilder();
                for (EquipmentSlot slot : ARMOUR) {
                    ItemStack stack = other.getEquippedStack(slot);
                    if (stack.isEmpty() || !stack.isDamageable()) continue;
                    int left = stack.getMaxDamage() - stack.getDamage();
                    gear.append(durability.isEnabled() ? left + " " : "");
                }
                String text = gear.toString().trim();
                if (!text.isEmpty()) lines.add(text);
            }
            lines.add(distance + "m");

            String sub = String.join("  ·  ", lines);
            float scaleFactor = (float) Math.max(0.5, Math.min(2.0, scale.getAsDouble()));
            int width = Math.max(mc.textRenderer.getWidth(name), mc.textRenderer.getWidth(sub)) + 8;
            int height = health.isEnabled() ? 26 : 20;
            int x = (int) (screen[0] - width / 2.0F);
            int y = (int) screen[1];

            // The panel keeps its anchor at the tag's projected point and scales around it.
            boolean scaled = Math.abs(scaleFactor - 1.0F) > 0.001F;
            if (scaled) {
                context.getMatrices().pushMatrix();
                context.getMatrices().translate(screen[0], y);
                context.getMatrices().scale(scaleFactor, scaleFactor);
                context.getMatrices().translate(-screen[0], -y);
            }

            context.fill(x, y, x + width, y + height, bg);
            context.fill(x, y, x + width, y + 1, 0x6600D0FF);
            context.drawText(mc.textRenderer, name, x + 4, y + 3, nameColor, shadow.isEnabled());
            context.drawText(mc.textRenderer, sub, x + 4, y + 14, 0xFF9FB6C9, shadow.isEnabled());

            if (health.isEnabled()) {
                float fraction = Math.max(0.0F, Math.min(1.0F, other.getHealth() / Math.max(1.0F, other.getMaxHealth())));
                int barY = y + height - 4;
                context.fill(x + 3, barY, x + width - 3, barY + 3, 0x88000000);
                int filled = (int) ((width - 6) * fraction);
                int red = (int) (255 * (1.0F - fraction));
                int green = (int) (255 * fraction);
                context.fill(x + 3, barY, x + 3 + filled, barY + 3, 0xFF000000 | red << 16 | green << 8);
            }

            if (scaled) context.getMatrices().popMatrix();
        }
    }

    private static float[] project(PlayerEntity other, PlayerEntity self) {
        double x = other.getX() - self.getX();
        double y = other.getY() + other.getHeight() + 0.35 - (self.getY() + self.getStandingEyeHeight());
        double z = other.getZ() - self.getZ();
        double yaw = Math.toRadians(self.getYaw());
        double cos = Math.cos(-yaw);
        double sin = Math.sin(-yaw);
        double rx = x * cos - z * sin;
        double rz = x * sin + z * cos;
        if (rz < 0.1) return null;
        var client = net.minecraft.client.MinecraftClient.getInstance();
        double fov = Math.toRadians(client.options.getFov().getValue());
        double factor = (client.getWindow().getScaledHeight() * 0.5) / Math.tan(fov * 0.5);
        return new float[]{(float) (client.getWindow().getScaledWidth() * 0.5 + (rx / rz) * factor),
                (float) (client.getWindow().getScaledHeight() * 0.5 - (y / rz) * factor)};
    }
}
