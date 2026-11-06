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
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.entity.projectile.TridentEntity;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Spectre port: "Particles" — spectral ghost and stardust particles for totem pops,
 * projectiles and footsteps.
 *
 * <p>Spectre builds glyph quads; this port keeps the particle sources (totem pops via the
 * absorption jump, projectile trails and footsteps), the burst/trail/step densities, the
 * lifetime, the limit and the colour mode, drawing them as projected sparks.</p>
 */
public final class SpectreRelicParticles extends Module {
    private static final int CAPACITY = 1200;
    private static final String GLYPH_MIXED = "Mixed";
    private static final String GLYPH_GHOST = "Ghost";
    private static final String GLYPH_CRESCENT = "Crescent";
    private static final String GLYPH_STARDUST = "Stardust";
    private static final String GLYPH_SPARK = "Spark";
    private static final String[] GLYPHS = {GLYPH_GHOST, GLYPH_CRESCENT, GLYPH_STARDUST, GLYPH_SPARK};

    public final BooleanSetting pops = addSetting(new BooleanSetting("Totem Pops", "Burst on totem pops", true));
    public final ModeSetting popGlyph = addSetting(new ModeSetting("Pop Glyph", "Burst spark shape", "Mixed", "Mixed", "Ghost", "Crescent", "Stardust", "Spark"));
    public final NumberSetting popBurst = addSetting(new NumberSetting("Pop Burst", "Sparks per pop", 26.0, 4.0, 80.0, 1.0));
    public final BooleanSetting ownPops = addSetting(new BooleanSetting("Own Pops", "Include your own pops", true));
    public final BooleanSetting projectiles = addSetting(new BooleanSetting("Projectiles", "Trail behind arrows and tridents", true));
    public final NumberSetting trailDensity = addSetting(new NumberSetting("Trail Density", "Sparks per trail step", 2.0, 1.0, 6.0, 1.0));
    public final BooleanSetting footsteps = addSetting(new BooleanSetting("Footsteps", "Sparks behind walking players", false));
    public final NumberSetting stepDensity = addSetting(new NumberSetting("Step Density", "Sparks per footstep", 2.0, 1.0, 6.0, 1.0));
    public final NumberSetting size = addSetting(new NumberSetting("Size", "Spark size", 1.0, 0.3, 2.5, 0.05));
    public final NumberSetting lifetime = addSetting(new NumberSetting("Lifetime", "Spark lifetime in seconds", 1.4, 0.3, 4.0, 0.1));
    public final NumberSetting limit = addSetting(new NumberSetting("Limit", "Maximum live sparks", 240.0, 40.0, CAPACITY, 10.0));
    public final ModeSetting colorMode = addSetting(new ModeSetting("Color", "Theme or manual colours", "Theme", "Theme", "Manual"));
    public final ColorSetting mainColor = addSetting(new ColorSetting("Main Color", "Primary spark colour", ColorUtils.rgba(120, 220, 255, 255)));
    public final ColorSetting secondColor = addSetting(new ColorSetting("Second Color", "Secondary spark colour", ColorUtils.rgba(180, 140, 255, 255)));

    private final List<Spark> sparks = new ArrayList<>();
    private final Map<UUID, Double> absorption = new java.util.HashMap<>();
    private final Map<UUID, Vec3d> lastStep = new java.util.HashMap<>();

    public SpectreRelicParticles() {
        super("SpectreRelicParticles", "Spectre port: spectral particles for totem pops, projectiles and footsteps.", Category.SPECTRE);
    }

    @Override
    public void onEnable() {
        sparks.clear();
        absorption.clear();
        lastStep.clear();
    }

    @Override
    public void onDisable() {
        sparks.clear();
        absorption.clear();
        lastStep.clear();
    }

    @Override
    public void onTick() {
        var self = mc.player;
        if (self == null || mc.world == null) return;
        long now = System.nanoTime();
        long life = (long) (lifetime.getAsDouble() * 1.0E9);

        if (pops.isEnabled()) {
            for (PlayerEntity player : mc.world.getPlayers()) {
                double value = player.getAbsorptionAmount();
                Double previous = absorption.put(player.getUuid(), value);
                if (previous != null && previous <= 0.0 && value > 0.0) {
                    if (player == self && !ownPops.isEnabled()) continue;
                    int amount = (int) popBurst.getAsDouble();
                    for (int i = 0; i < amount; i++) {
                        add(player.getX(), player.getY() + 1.0, player.getZ(), 0.35, life, now, glyphFor(i));
                    }
                }
            }
        }

        if (projectiles.isEnabled()) {
            for (var entity : mc.world.getEntities()) {
                if (!(entity instanceof PersistentProjectileEntity) && !(entity instanceof TridentEntity)) continue;
                int amount = (int) trailDensity.getAsDouble();
                for (int i = 0; i < amount; i++) add(entity.getX(), entity.getY(), entity.getZ(), 0.12, life, now, GLYPH_SPARK);
            }
        }

        if (footsteps.isEnabled()) {
            for (PlayerEntity player : mc.world.getPlayers()) {
                Vec3d pos = new Vec3d(player.getX(), player.getY(), player.getZ());
                Vec3d previous = lastStep.put(player.getUuid(), pos);
                if (previous != null && previous.squaredDistanceTo(pos) > 0.04) {
                    int amount = (int) stepDensity.getAsDouble();
                    for (int i = 0; i < amount; i++) add(pos.x, pos.y + 0.1, pos.z, 0.2, life, now, GLYPH_SPARK);
                }
            }
        }

        int cap = (int) limit.getAsDouble();
        sparks.removeIf(spark -> now - spark.born > life);
        while (sparks.size() > cap) sparks.remove(0);
    }

    /** "Mixed" picks one of the four glyphs per spark, the same way Spectre blends them. */
    private String glyphFor(int index) {
        String chosen = popGlyph.getValue();
        if (!chosen.equals(GLYPH_MIXED)) return chosen;
        return GLYPHS[(index + ThreadLocalRandom.current().nextInt(GLYPHS.length)) % GLYPHS.length];
    }

    private void add(double x, double y, double z, double spread, long life, long now, String style) {
        var random = ThreadLocalRandom.current();
        Spark spark = new Spark();
        spark.x = x + (random.nextDouble() - 0.5) * spread;
        spark.y = y + (random.nextDouble() - 0.5) * spread;
        spark.z = z + (random.nextDouble() - 0.5) * spread;
        spark.dx = (random.nextDouble() - 0.5) * 0.06;
        spark.dy = random.nextDouble() * 0.07;
        spark.dz = (random.nextDouble() - 0.5) * 0.06;
        spark.born = now;
        spark.second = random.nextBoolean();

        // The glyph shapes the burst: a ghost column, a crescent arc, a tight stardust cluster
        // or a sharp radial spark.
        switch (style) {
            case GLYPH_GHOST -> {
                spark.x = x + (random.nextDouble() - 0.5) * spread * 0.5;
                spark.z = z + (random.nextDouble() - 0.5) * spread * 0.5;
                spark.dx = (random.nextDouble() - 0.5) * 0.025;
                spark.dz = (random.nextDouble() - 0.5) * 0.025;
                spark.dy = 0.06 + random.nextDouble() * 0.06;
            }
            case GLYPH_CRESCENT -> {
                double side = random.nextBoolean() ? 1.0 : -1.0;
                spark.dx = side * (0.05 + random.nextDouble() * 0.04);
                spark.dz = (random.nextDouble() - 0.5) * 0.03;
                spark.dy = 0.02 + random.nextDouble() * 0.05;
            }
            case GLYPH_STARDUST -> {
                spark.x = x + (random.nextDouble() - 0.5) * spread * 0.35;
                spark.y = y + (random.nextDouble() - 0.5) * spread * 0.35;
                spark.z = z + (random.nextDouble() - 0.5) * spread * 0.35;
                spark.dx *= 0.4;
                spark.dy *= 0.4;
                spark.dz *= 0.4;
            }
            default -> {
                spark.dx = (random.nextDouble() - 0.5) * 0.12;
                spark.dy = 0.03 + random.nextDouble() * 0.09;
                spark.dz = (random.nextDouble() - 0.5) * 0.12;
            }
        }
        sparks.add(spark);
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        var self = mc.player;
        if (self == null || sparks.isEmpty()) return;
        long now = System.nanoTime();
        long life = (long) (lifetime.getAsDouble() * 1.0E9);
        int primary = colorMode.is("Manual") ? mainColor.getColor() : ColorUtils.rgba(120, 220, 255, 255);
        int secondary = colorMode.is("Manual") ? secondColor.getColor() : ColorUtils.rgba(180, 140, 255, 255);
        float scale = size.getValue().floatValue();

        for (Spark spark : sparks) {
            float t = Math.min(1.0F, (float) (now - spark.born) / life);
            float alpha = 1.0F - t;
            if (alpha < 0.05F) continue;
            double x = spark.x + spark.dx * t * 20.0;
            double y = spark.y + spark.dy * t * 20.0 - 0.05 * t * t;
            double z = spark.z + spark.dz * t * 20.0;
            float[] screen = project(self, x, y, z);
            if (screen == null) continue;
            int color = ((spark.second ? secondary : primary) & 0x00FFFFFF) | ((int) (alpha * 220) << 24);
            int span = Math.max(1, Math.round(2.0F * scale * (1.0F - t * 0.5F)));
            context.fill((int) screen[0], (int) screen[1], (int) screen[0] + span, (int) screen[1] + span, color);
        }
    }

    private static float[] project(PlayerEntity self, double x, double y, double z) {
        double dx = x - self.getX();
        double dy = y - (self.getY() + self.getStandingEyeHeight());
        double dz = z - self.getZ();
        double yaw = Math.toRadians(self.getYaw());
        double cos = Math.cos(-yaw);
        double sin = Math.sin(-yaw);
        double rx = dx * cos - dz * sin;
        double rz = dx * sin + dz * cos;
        if (rz < 0.1) return null;
        var client = net.minecraft.client.MinecraftClient.getInstance();
        double fov = Math.toRadians(client.options.getFov().getValue());
        double factor = (client.getWindow().getScaledHeight() * 0.5) / Math.tan(fov * 0.5);
        return new float[]{(float) (client.getWindow().getScaledWidth() * 0.5 + (rx / rz) * factor),
                (float) (client.getWindow().getScaledHeight() * 0.5 - (dy / rz) * factor)};
    }

    private static final class Spark {
        double x;
        double y;
        double z;
        double dx;
        double dy;
        double dz;
        boolean second;
        long born;
    }
}
