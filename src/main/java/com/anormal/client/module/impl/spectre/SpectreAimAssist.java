package com.anormal.client.module.impl.spectre;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.math.MathHelper;

import java.util.Comparator;
import java.util.Random;

/**
 * Spectre port: "Aim Assist" — smooth combat aim assist with FOV, range, part and weapon
 * filters.
 *
 * <p>Spectre runs this from its 3D render event; this port runs the same target selection,
 * humanised rotation path, mouse-GCD quantisation and weapon gate from the tick loop, which
 * is where the vanilla rotation is still free to be changed.</p>
 */
public final class SpectreAimAssist extends Module {
    public final ModeSetting targetMode = addSetting(new ModeSetting("Target Mode", "How the best target is chosen", "FOV", "FOV", "Closest"));
    public final NumberSetting targetRange = addSetting(new NumberSetting("Range", "Maximum target distance", 3.2, 0.0, 10.0, 0.1));
    public final NumberSetting fov = addSetting(new NumberSetting("FOV", "Field of view cone that targets must be inside", 90.0, 0.0, 360.0, 1.0));
    public final NumberSetting speed = addSetting(new NumberSetting("Speed", "Aim strength", 0.18, 0.01, 1.0, 0.01));
    public final ModeSetting part = addSetting(new ModeSetting("Aim Part", "Body part aimed at", "Torso", "Head", "Torso", "Legs"));
    public final BooleanSetting throughWalls = addSetting(new BooleanSetting("Through Walls", "Aim at targets behind blocks", false));
    public final BooleanSetting sword = addSetting(new BooleanSetting("Sword", "Assist while holding a sword", true));
    public final BooleanSetting axe = addSetting(new BooleanSetting("Axe", "Assist while holding an axe", true));
    public final BooleanSetting mace = addSetting(new BooleanSetting("Mace", "Assist while holding a mace", true));
    public final BooleanSetting spearTrident = addSetting(new BooleanSetting("Spear/Trident", "Assist while holding a spear or trident", true));
    public final BooleanSetting everything = addSetting(new BooleanSetting("Everything", "Assist with any item", false));

    private final Random random = new Random();
    private PlayerEntity currentTarget;

    public SpectreAimAssist() {
        super("SpectreAimAssist", "Spectre port: smooth combat aim assist with FOV, range, part and weapon filters.", Category.SPECTRE);
    }

    @Override
    public void onDisable() {
        currentTarget = null;
    }

    @Override
    public void onTick() {
        var player = mc.player;
        if (player == null || mc.world == null || mc.currentScreen != null) {
            currentTarget = null;
            return;
        }
        if (player.isUsingItem() || mc.options.useKey.isPressed() || !holdingValidWeapon(player)) {
            currentTarget = null;
            return;
        }

        if (currentTarget != null
                && (!currentTarget.isAlive()
                || player.distanceTo(currentTarget) > targetRange.getValue().floatValue() + 1.0F
                || angleTo(currentTarget) > fov.getValue().floatValue() / 2.0F)) {
            currentTarget = null;
        }

        if (currentTarget == null) {
            currentTarget = mc.world.getPlayers().stream()
                    .filter(other -> other != player && other.isAlive())
                    .filter(other -> player.distanceTo(other) <= targetRange.getValue().floatValue())
                    .filter(other -> angleTo(other) <= fov.getValue().floatValue() / 2.0F)
                    .min(Comparator.comparingDouble(other -> targetMode.is("FOV") ? angleTo(other) : player.distanceTo(other)))
                    .orElse(null);
        }
        if (currentTarget == null) return;
        if (!throughWalls.isEnabled() && !player.canSee(currentTarget)) return;

        float targetYaw = rotationYaw(currentTarget);
        float targetPitch = rotationPitch(player, currentTarget);
        double time = System.currentTimeMillis() / 1000.0;
        targetYaw += (float) (Math.sin(time * 1.4) * 0.7 + Math.cos(time * 2.1) * 0.3);
        targetPitch += (float) (Math.cos(time * 1.1) * 0.5 + Math.sin(time * 2.5) * 0.2);
        targetYaw += (random.nextFloat() - 0.5F) * 0.12F;
        targetPitch += (random.nextFloat() - 0.5F) * 0.12F;

        float diffYaw = MathHelper.wrapDegrees(targetYaw - player.getYaw());
        float diffPitch = MathHelper.wrapDegrees(targetPitch - player.getPitch());
        float distance = MathHelper.sqrt(diffYaw * diffYaw + diffPitch * diffPitch);
        if (distance < 0.05F) return;
        // Fraction of the remaining angle per tick: quick far away, gentle up close,
        // but never zero — the old pow(.,2.2) curve quantized the step to 0 and stalled.
        float curve = 0.35F + 0.65F * Math.min(distance / 15.0F, 1.0F);
        float randomScale = 0.85F + random.nextFloat() * 0.3F;
        float factor = speed.getValue().floatValue() * curve * randomScale;

        float yawStep = quantize(diffYaw * factor, diffYaw);
        float pitchStep = quantize(diffPitch * factor, diffPitch);
        if (yawStep != 0.0F || pitchStep != 0.0F) {
            float oldYaw = player.getYaw();
            float oldPitch = player.getPitch();
            player.setYaw(oldYaw + yawStep);
            player.setPitch(MathHelper.clamp(oldPitch + pitchStep, -90.0F, 90.0F));
            // Keep the interpolated render camera glued to the new rotation.
            player.lastYaw += player.getYaw() - oldYaw;
            player.lastPitch += player.getPitch() - oldPitch;
        }
    }

    private float angleTo(Entity entity) {
        var player = mc.player;
        float diffYaw = Math.abs(MathHelper.wrapDegrees(rotationYaw(entity) - player.getYaw()));
        float diffPitch = Math.abs(MathHelper.wrapDegrees(rotationPitch(player, entity) - player.getPitch()));
        return MathHelper.sqrt(diffYaw * diffYaw + diffPitch * diffPitch);
    }

    private float rotationYaw(Entity entity) {
        var player = mc.player;
        double dx = entity.getX() - player.getX();
        double dz = entity.getZ() - player.getZ();
        return (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0F;
    }

    private float rotationPitch(PlayerEntity player, Entity entity) {
        double dx = entity.getX() - player.getX();
        double dy = entity.getY() + targetOffset() - (player.getY() + player.getStandingEyeHeight());
        double dz = entity.getZ() - player.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        return (float) (-Math.toDegrees(Math.atan2(dy, horizontal)));
    }

    private double targetOffset() {
        return switch (part.getValue()) {
            case "Head" -> 1.5;
            case "Legs" -> 0.4;
            default -> 1.0;
        };
    }

    private boolean holdingValidWeapon(PlayerEntity player) {
        if (everything.isEnabled()) return true;
        ItemStack held = player.getMainHandStack();
        if (held.isEmpty()) return false;
        // Tag-based checks: ItemTags.AXES does NOT contain pickaxes, unlike a raw
        // "axe" substring match on the item id.
        if (sword.isEnabled() && held.isIn(ItemTags.SWORDS)) return true;
        if (axe.isEnabled() && held.isIn(ItemTags.AXES)) return true;
        if (mace.isEnabled() && held.isOf(Items.MACE)) return true;
        if (spearTrident.isEnabled()) {
            String id = net.minecraft.registry.Registries.ITEM.getId(held.getItem()).getPath();
            if (id.contains("spear") || held.isOf(Items.TRIDENT)) return true;
        }
        return false;
    }

    /** Spectre snaps every step to the mouse sensitivity grid so the movement looks vanilla. */
    private float quantize(float value, float raw) {
        float sensitivity = mc.options.getMouseSensitivity().getValue().floatValue();
        float f = sensitivity * 0.6F + 0.2F;
        float gcd = f * f * f * 8.0F * 0.15F;
        float q = Math.round(value / gcd) * gcd;
        // Never stall short of the target: a sub-grid remainder still creeps one grid cell.
        if (q == 0.0F && raw != 0.0F) q = Math.copySign(gcd, raw);
        return q;
    }
}
