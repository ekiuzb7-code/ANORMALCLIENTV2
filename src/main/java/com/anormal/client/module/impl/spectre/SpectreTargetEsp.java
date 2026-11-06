package com.anormal.client.module.impl.spectre;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.ColorUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;

/**
 * Spectre port: "Target ESP" — draws a mark on your combat target.
 *
 * <p>Spectre renders posed 3D glyphs; this port keeps the four styles, the colour palette,
 * the scale/tempo/opacity/memory settings, the hit flash and the crosshair pick, and draws
 * them as projected rings around the target instead of glyph geometry.</p>
 */
public final class SpectreTargetEsp extends Module {
    public final ModeSetting style = addSetting(new ModeSetting("Style", "Ring style", "Ankh Circle", "Ankh Circle", "Horus Halo", "Sand Vortex", "Capstone"));
    public final ModeSetting colorMode = addSetting(new ModeSetting("Color", "Theme or manual colours", "Theme", "Theme", "Manual"));
    public final ColorSetting mainColor = addSetting(new ColorSetting("Main Color", "Primary ring colour", ColorUtils.rgba(60, 200, 255, 255)));
    public final ColorSetting secondColor = addSetting(new ColorSetting("Second Color", "Secondary ring colour", ColorUtils.rgba(80, 140, 255, 255)));
    public final ColorSetting hitColor = addSetting(new ColorSetting("Hit Color", "Flash colour on hit", ColorUtils.rgba(255, 220, 40, 255)));
    public final NumberSetting scale = addSetting(new NumberSetting("Scale", "Mark size", 1.0, 0.6, 1.8, 0.1));
    public final NumberSetting tempo = addSetting(new NumberSetting("Tempo", "Spin speed", 1.0, 0.3, 2.4, 0.1));
    public final NumberSetting opacity = addSetting(new NumberSetting("Opacity", "Mark opacity", 0.9, 0.2, 1.0, 0.05));
    public final NumberSetting memory = addSetting(new NumberSetting("Memory", "How long the last hit target stays marked (ms)", 4000.0, 500.0, 15000.0, 500.0));
    public final BooleanSetting crosshair = addSetting(new BooleanSetting("Crosshair Pick", "Mark the entity under the crosshair", true));
    public final BooleanSetting playersOnly = addSetting(new BooleanSetting("Players Only", "Only mark players", false));
    public final BooleanSetting hurtFlash = addSetting(new BooleanSetting("Hit Flash", "Flash on hit", true));

    private static SpectreTargetEsp instance;
    private static LivingEntity lastHit;
    private static long lastHitAt;
    private double phase;

    public SpectreTargetEsp() {
        super("SpectreTargetEsp", "Spectre port: draws a mark on your combat target.", Category.SPECTRE);
        instance = this;
    }

    /** Fed from the attack hook so the mark lingers on the last hit target. */
    public static void onAttack(Entity target) {
        if (target instanceof LivingEntity living) {
            lastHit = living;
            lastHitAt = System.currentTimeMillis();
        }
    }

    @Override
    public void onDisable() {
        lastHit = null;
        lastHitAt = 0L;
    }

    @Override
    public void onTick() {
        phase += 0.05 * tempo.getAsDouble();
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        var player = mc.player;
        if (player == null || mc.world == null || mc.options.hudHidden) return;

        LivingEntity target = pick(player);
        if (target == null) return;

        float[] screen = project(target, player);
        if (screen == null) return;

        float grow = (float) scale.getAsDouble();
        float radius = Math.max(10.0F, target.getHeight() * 26.0F) * grow;
        int alpha = (int) (opacity.getAsDouble() * 255.0) << 24;
        int main = (colorMode.is("Manual") ? mainColor.getColor() : ColorUtils.rgba(60, 200, 255, 255)) & 0x00FFFFFF;
        int second = (colorMode.is("Manual") ? secondColor.getColor() : ColorUtils.rgba(80, 140, 255, 255)) & 0x00FFFFFF;
        boolean flash = hurtFlash.isEnabled() && target.hurtTime > 0;
        int ring = flash ? (hitColor.getColor() & 0x00FFFFFF) : main;

        switch (style.getValue()) {
            case "Horus Halo" -> ring(context, screen[0], screen[1] - radius * 0.9F, radius, 48, (ring | alpha), 1);
            case "Sand Vortex" -> {
                for (int i = 0; i < 3; i++) {
                    float r = radius * (0.7F + 0.18F * i) + (float) Math.sin(phase + i) * 3.0F;
                    ring(context, screen[0], screen[1], r, 40, ((i == 0 ? ring : second) | alpha), 1);
                }
            }
            case "Capstone" -> {
                ring(context, screen[0], screen[1] - radius * 1.1F, radius * 0.7F, 32, (ring | alpha), 1);
                context.fill((int) (screen[0] - radius * 0.7F), (int) screen[1], (int) (screen[0] + radius * 0.7F),
                        (int) screen[1] + 1, second | alpha);
            }
            default -> {
                ring(context, screen[0], screen[1], radius, 40, (ring | alpha), 1);
                ring(context, screen[0], screen[1], radius * 0.55F, 28, (second | alpha), 1);
            }
        }
    }

    private LivingEntity pick(PlayerEntity player) {
        Entity looked = crosshair.isEnabled() ? mc.targetedEntity : null;
        LivingEntity candidate = looked instanceof LivingEntity living ? living : lastHit;
        if (candidate == null) return null;
        if (!candidate.isAlive() || candidate == player) return null;
        if (!crosshair.isEnabled() && System.currentTimeMillis() - lastHitAt > (long) memory.getAsDouble()) return null;
        if (playersOnly.isEnabled() && !(candidate instanceof PlayerEntity)) return null;
        if (player.distanceTo(candidate) > 128.0F) return null;
        return candidate;
    }

    private static float[] project(Entity entity, PlayerEntity player) {
        double x = entity.getX() - player.getX();
        double y = entity.getY() + entity.getHeight() * 0.5 - (player.getY() + player.getStandingEyeHeight());
        double z = entity.getZ() - player.getZ();
        double yaw = Math.toRadians(player.getYaw());
        double cos = Math.cos(-yaw);
        double sin = Math.sin(-yaw);
        double rx = x * cos - z * sin;
        double rz = x * sin + z * cos;
        if (rz < 0.1) return null;
        double fov = Math.toRadians(mc().options.getFov().getValue());
        double factor = (mc().getWindow().getScaledHeight() * 0.5) / Math.tan(fov * 0.5);
        return new float[]{(float) (mc().getWindow().getScaledWidth() * 0.5 + (rx / rz) * factor),
                (float) (mc().getWindow().getScaledHeight() * 0.5 - (y / rz) * factor)};
    }

    private static net.minecraft.client.MinecraftClient mc() {
        return net.minecraft.client.MinecraftClient.getInstance();
    }

    private static void ring(DrawContext context, float cx, float cy, float radius, int segments, int color, int thickness) {
        for (int i = 0; i < segments; i++) {
            double a = (Math.PI * 2.0 * i) / segments;
            int x = (int) (cx + Math.cos(a) * radius);
            int y = (int) (cy + Math.sin(a) * radius);
            context.fill(x, y, x + thickness, y + thickness, color);
        }
    }
}
