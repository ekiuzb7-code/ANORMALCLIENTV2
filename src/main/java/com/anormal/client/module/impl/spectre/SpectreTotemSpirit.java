package com.anormal.client.module.impl.spectre;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.ColorUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;

import java.util.ArrayList;
import java.util.List;

/**
 * Spectre port: "Totem Spirit" — a glowing ghost rises when a player pops a totem.
 *
 * <p>Spectre hooks the totem-pop packet and builds a posed ghost mesh. This port detects the
 * pop from the absorption jump (the vanilla pop grants absorption) and rises a box spirit
 * with the same Ka time, ascent, opacity, colour and ground-seal timings.</p>
 */
public final class SpectreTotemSpirit extends Module {
    private static final int MAX_SPIRITS = 12;

    public final NumberSetting kaTime = addSetting(new NumberSetting("Ka Time", "Seconds the spirit lives", 2.4, 0.6, 6.0, 0.1));
    public final NumberSetting ascent = addSetting(new NumberSetting("Ascent", "Blocks the spirit rises", 1.6, 0.0, 4.0, 0.1));
    public final ModeSetting look = addSetting(new ModeSetting("Look", "Glass or wireframe spirit", "Glass", "Glass", "Wire"));
    public final NumberSetting opacity = addSetting(new NumberSetting("Opacity", "Spirit opacity", 0.65, 0.15, 1.0, 0.05));
    public final BooleanSetting seal = addSetting(new BooleanSetting("Ground Seal", "Draw the ground ring", true));
    public final BooleanSetting own = addSetting(new BooleanSetting("Own Pops", "Also show your own pops", false));
    public final ModeSetting colorMode = addSetting(new ModeSetting("Color", "Theme or manual colours", "Theme", "Theme", "Manual"));
    public final ColorSetting bodyColor = addSetting(new ColorSetting("Body Color", "Spirit body", ColorUtils.rgba(80, 200, 255, 255)));
    public final ColorSetting auraColor = addSetting(new ColorSetting("Aura Color", "Spirit aura", ColorUtils.rgba(120, 160, 255, 255)));

    private final Spirit[] spirits = new Spirit[MAX_SPIRITS];
    private int head;
    private int count;

    public SpectreTotemSpirit() {
        super("SpectreTotemSpirit", "Spectre port: a glowing ghost rises when a player pops a totem.", Category.SPECTRE);
        for (int i = 0; i < MAX_SPIRITS; i++) spirits[i] = new Spirit();
    }

    @Override
    public void onEnable() {
        head = 0;
        count = 0;
        if (mc.world != null) {
            for (PlayerEntity player : mc.world.getPlayers()) {
                Spirit.spawned.put(player.getUuid(), (double) player.getAbsorptionAmount());
            }
        }
    }

    @Override
    public void onDisable() {
        head = 0;
        count = 0;
        Spirit.spawned.clear();
    }

    @Override
    public void onTick() {
        if (mc.world == null || mc.player == null) return;
        long now = System.nanoTime();
        double life = kaTime.getAsDouble() * 1.0E9;

        for (PlayerEntity player : mc.world.getPlayers()) {
            double absorption = player.getAbsorptionAmount();
            Double previous = Spirit.spawned.put(player.getUuid(), absorption);
            if (previous != null && previous <= 0.0 && absorption > 0.0) {
                capture(player, now);
            }
        }

        while (count > 0 && now - spirits[head].born >= life) {
            head = (head + 1) % MAX_SPIRITS;
            count--;
        }
    }

    private void capture(PlayerEntity player, long now) {
        if (player.isSpectator()) return;
        boolean mine = player == mc.player;
        if (mine && !own.isEnabled()) return;
        if (count == MAX_SPIRITS) {
            head = (head + 1) % MAX_SPIRITS;
            count--;
        }
        Spirit spirit = spirits[(head + count) % MAX_SPIRITS];
        count++;
        spirit.x = player.getX();
        spirit.y = player.getY();
        spirit.z = player.getZ();
        spirit.height = player.getHeight();
        spirit.scale = Math.max(0.6F, player.getHeight() / 1.8F);
        spirit.mine = mine;
        spirit.born = now;
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (count == 0 || mc.world == null) return;
        long now = System.nanoTime();
        double life = kaTime.getAsDouble() * 1.0E9;
        int body = colorMode.is("Manual") ? bodyColor.getColor() : ColorUtils.rgba(80, 200, 255, 255);
        int aura = colorMode.is("Manual") ? auraColor.getColor() : ColorUtils.rgba(120, 160, 255, 255);

        for (int n = 0; n < count; n++) {
            Spirit spirit = spirits[(head + n) % MAX_SPIRITS];
            float t = Math.min(1.0F, (float) ((now - spirit.born) / life));
            float alpha = (float) opacity.getAsDouble() * Math.min(1.0F, t / 0.06F) * (1.0F - Math.max(0.0F, (t - 0.7F) / 0.3F));
            if (alpha < 0.02F) continue;
            float lift = (float) ascent.getAsDouble() * rise(t);
            float width = 0.35F * spirit.scale;
            double baseY = spirit.y + lift;
            Box body1 = new Box(spirit.x - width, baseY, spirit.z - width, spirit.x + width, baseY + spirit.height * (1.0F - 0.35F * t), spirit.z + width);
            int line = (body & 0x00FFFFFF) | ((int) (alpha * 255) << 24);
            int face = look.is("Wire") ? 0 : (aura & 0x00FFFFFF) | ((int) (alpha * 60) << 24);
            SpectreHoleEsp.drawBox(context, tickDelta, body1, line, face, true, !look.is("Wire"), 1);
            if (seal.isEnabled() && t < 0.6F) {
                float open = 1.0F - (1.0F - t / 0.6F) * (1.0F - t / 0.6F) * (1.0F - t / 0.6F);
                float radius = (0.35F + 1.15F * open) * spirit.scale;
                SpectreHoleEsp.drawBox(context, tickDelta,
                        new Box(spirit.x - radius, spirit.y + 0.02, spirit.z - radius,
                                spirit.x + radius, spirit.y + 0.04, spirit.z + radius),
                        (body & 0x00FFFFFF) | ((int) (alpha * 200) << 24), 0, true, false, 1);
            }
        }
    }

    private static float rise(float t) {
        float k = Math.max(0.0F, (t - 0.12F) / 0.88F);
        return k * k * (2.2F - 1.2F * k);
    }

    private static final class Spirit {
        static final java.util.Map<java.util.UUID, Double> spawned = new java.util.HashMap<>();
        double x;
        double y;
        double z;
        float height = 1.8F;
        float scale = 1.0F;
        boolean mine;
        long born;
    }
}
