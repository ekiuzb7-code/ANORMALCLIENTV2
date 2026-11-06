package com.anormal.client.module.impl.spectre;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.module.ModuleManager;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.ColorUtils;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.Random;

/**
 * Spectre port: "Hit Particles" — spawns a particle burst where your hits land.
 *
 * <p>Particles are spawned on the client through {@code world.addParticleClient}, so
 * nothing extra is sent to the server. Called from the attack hook in
 * {@code ClientPlayerInteractionManagerMixin}.</p>
 */
public final class SpectreHitParticles extends Module {
    public final ModeSetting type = addSetting(new ModeSetting("Type", "Particle type", "Crit",
            "Crit", "Magic Crit", "Spark", "Flame", "Soul Flame", "End Rod", "Totem", "Heart", "Dust"));
    public final NumberSetting amount = addSetting(new NumberSetting("Amount", "Particles per hit", 10.0, 1.0, 40.0, 1.0));
    public final NumberSetting spread = addSetting(new NumberSetting("Spread", "Burst spread", 1.0, 0.25, 3.0, 0.25));
    public final ModeSetting origin = addSetting(new ModeSetting("Origin", "Where the burst starts", "Impact", "Impact", "Body"));
    public final BooleanSetting onlyCrits = addSetting(new BooleanSetting("Only Crits", "Only spawn on critical hits", false));
    public final ModeSetting targets = addSetting(new ModeSetting("Targets", "Which entities react", "Living", "Living", "Players", "All"));
    public final ModeSetting dustColorMode = addSetting(new ModeSetting("Color", "Dust color source", "Accent", "Custom", "Accent"));
    public final ColorSetting dustColor = addSetting(new ColorSetting("Custom Color", "Dust color", ColorUtils.rgba(246, 190, 71, 255)));
    public final NumberSetting dustSize = addSetting(new NumberSetting("Dust Size", "Dust particle size", 1.0, 0.5, 2.0, 0.1));

    private final Random random = new Random();

    public SpectreHitParticles() {
        super("SpectreHitParticles", "Spectre port: spawns particles where your hits land.", Category.SPECTRE);
    }

    /** Additive hook from the attack mixin. */
    public static void onAttack(Entity target) {
        SpectreHitParticles module = ModuleManager.getModule(SpectreHitParticles.class);
        if (module != null && module.isEnabled()) {
            module.handleAttack(target);
        }
    }

    private void handleAttack(Entity target) {
        var player = mc.player;
        if (player == null || mc.world == null || target == null || target == player) return;
        if (!target.isAttackable() || !accepts(target)) return;
        if (onlyCrits.isEnabled() && !isCritical(player, target)) return;
        spawnBurst(player, target);
    }

    private boolean accepts(Entity target) {
        return switch (targets.getValue()) {
            case "Players" -> target instanceof PlayerEntity;
            case "All" -> true;
            default -> target instanceof LivingEntity;
        };
    }

    private static boolean isCritical(PlayerEntity player, Entity target) {
        return player.getAttackCooldownProgress(0.5F) > 0.9F
                && player.fallDistance > 0.0F
                && !player.isOnGround()
                && !player.isClimbing()
                && !player.isTouchingWater()
                && !player.hasVehicle()
                && !player.isSprinting()
                && target instanceof LivingEntity;
    }

    private void spawnBurst(PlayerEntity player, Entity target) {
        Burst burst = burst();
        Box box = target.getBoundingBox();
        Vec3d center = box.getCenter();
        boolean body = origin.is("Body");
        Vec3d impact = body ? null : impactPoint(player, target, box);
        Vec3d fallbackOut = player.getRotationVec(1.0F).multiply(-1.0);
        double spreadScale = spread.getAsDouble();
        double jitter = burst.jitter() * spreadScale;
        int count = amount.getValue().intValue();

        for (int i = 0; i < count; i++) {
            Vec3d from = body ? randomPointIn(box) : impact;
            Vec3d out = from.subtract(center);
            out = out.lengthSquared() < 1.0E-6 ? fallbackOut : out.normalize();
            Vec3d dir = out.multiply(0.8).add(randomUnit()).add(0.0, 0.25, 0.0).normalize();
            double speed = burst.speed() * spreadScale * (0.6 + 0.4 * random.nextDouble());
            mc.world.addParticleClient(burst.particle(), true, false,
                    from.x + random.nextGaussian() * jitter,
                    from.y + random.nextGaussian() * jitter,
                    from.z + random.nextGaussian() * jitter,
                    dir.x * speed, dir.y * speed, dir.z * speed);
        }
    }

    private Burst burst() {
        return switch (type.getValue()) {
            case "Magic Crit" -> new Burst(ParticleTypes.ENCHANTED_HIT, 0.8, 0.1);
            case "Spark" -> new Burst(ParticleTypes.ELECTRIC_SPARK, 0.16, 0.1);
            case "Flame" -> new Burst(ParticleTypes.FLAME, 0.035, 0.12);
            case "Soul Flame" -> new Burst(ParticleTypes.SOUL_FIRE_FLAME, 0.035, 0.12);
            case "End Rod" -> new Burst(ParticleTypes.END_ROD, 0.09, 0.1);
            case "Totem" -> new Burst(ParticleTypes.TOTEM_OF_UNDYING, 0.45, 0.1);
            case "Heart" -> new Burst(ParticleTypes.HEART, 0.0, 0.35);
            case "Dust" -> new Burst(new DustParticleEffect(dustRgb(), dustSize.getValue().floatValue()), 1.0, 0.3);
            default -> new Burst(ParticleTypes.CRIT, 0.8, 0.1);
        };
    }

    private int dustRgb() {
        int argb = dustColor.getColor();
        return argb & 0xFFFFFF;
    }

    private static Vec3d impactPoint(PlayerEntity player, Entity target, Box box) {
        Vec3d eye = player.getEyePos();
        return new Vec3d(
                Math.min(Math.max(eye.x, box.minX), box.maxX),
                Math.min(Math.max(eye.y, box.minY), box.maxY),
                Math.min(Math.max(eye.z, box.minZ), box.maxZ));
    }

    private Vec3d randomPointIn(Box box) {
        return new Vec3d(
                box.minX + box.getLengthX() * (0.1 + 0.8 * random.nextDouble()),
                box.minY + box.getLengthY() * (0.1 + 0.8 * random.nextDouble()),
                box.minZ + box.getLengthZ() * (0.1 + 0.8 * random.nextDouble()));
    }

    private Vec3d randomUnit() {
        Vec3d v = new Vec3d(random.nextGaussian(), random.nextGaussian(), random.nextGaussian());
        return v.lengthSquared() < 1.0E-9 ? new Vec3d(0.0, 1.0, 0.0) : v.normalize();
    }

    private record Burst(ParticleEffect particle, double speed, double jitter) { }
}
