package com.anormal.client.module.impl.spectre;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.client.network.ClientPlayerEntity;

/**
 * Spectre port: "Jesus" — walk on liquids and powder snow like Jesus.
 *
 * <p>Solid mode pins the feet to the fluid surface each tick (the server still sees
 * normal swimming-free packets, so it works on most vanilla-style servers);
 * Ignore mode leaves the fluid untouched. Dip-on-sneak/fall settings match Spectre.</p>
 */
public final class SpectreJesus extends Module implements SpectreMovementHooks.MoveHook {
    private static final double ASCEND_SPEED = 0.11;

    public final BooleanSetting powderSnow = addSetting(new BooleanSetting("Powder Snow", "Powder snow counts as a walkable surface", true));
    public final BooleanSetting slowDown = addSetting(new BooleanSetting("Slow Down", "Reduce walk speed on the surface", false));
    public final ModeSetting waterMode = addSetting(new ModeSetting("Water Mode", "Water surface mode", "Solid", "Solid", "Ignore"));
    public final BooleanSetting dipIfBurning = addSetting(new BooleanSetting("Dip If Burning", "Sinks on fire damage", true));
    public final BooleanSetting dipOnSneakWater = addSetting(new BooleanSetting("Water Dip On Sneak", "Sneak dips into water", true));
    public final BooleanSetting dipOnFallWater = addSetting(new BooleanSetting("Water Dip On Fall", "Falling from height dips in", true));
    public final NumberSetting dipFallHeightWater = addSetting(new NumberSetting("Water Dip Fall Height", "Fall height that dips", 4.0, 1.0, 255.0, 1.0));
    public final ModeSetting lavaMode = addSetting(new ModeSetting("Lava Mode", "Lava surface mode", "Solid", "Solid", "Ignore"));
    public final BooleanSetting dipIfFireResistant = addSetting(new BooleanSetting("Lava Dip If Resistant", "Dip in lava when fire resistant", true));
    public final BooleanSetting dipOnSneakLava = addSetting(new BooleanSetting("Lava Dip On Sneak", "Sneak dips into lava", true));
    public final BooleanSetting dipOnFallLava = addSetting(new BooleanSetting("Lava Dip On Fall", "Falling from height dips in", true));
    public final NumberSetting dipFallHeightLava = addSetting(new NumberSetting("Lava Dip Fall Height", "Fall height that dips", 4.0, 1.0, 255.0, 1.0));

    private boolean burningDipHappening = false;

    public SpectreJesus() {
        super("SpectreJesus", "Spectre port: walk on liquids and powder snow like Jesus.", Category.SPECTRE);
    }

    @Override
    public void onEnable() {
        SpectreMovementHooks.rebind();
    }

    @Override
    public void onDisable() {
        SpectreMovementHooks.rebind();
    }

    @Override
    public void onPreTick() {
        if (!isEnabled() || mc.player == null) return;
        burningDipHappening = false;
        PlayerEntity player = mc.player;
        // Dip If Burning: authenticate the "I caught fire" dip for honest drops
        if (dipIfBurning.isEnabled() && player.isOnFire() && overWaterSurface(player)) {
            burningDipHappening = true;
        }
        if (dipIfFireResistant.isEnabled() && hasFireResistance(player) && overLavaSurface(player)) {
            burningDipHappening = true;
        }
        // Bubble-column style ascend when the jump key is held and visibly swimming
        if (mc.options.jumpKey.isPressed() && player.isSwimming()) {
            if (player.getVelocity().y < ASCEND_SPEED) {
                player.setVelocity(player.getVelocity().x, ASCEND_SPEED, player.getVelocity().z);
            }
        }
    }

    @Override
    public Vec3d onMove(Entity entity, MovementType type, Vec3d movement) {
        if (!isEnabled() || !(entity instanceof ClientPlayerEntity player) || mc.world == null) return movement;
        boolean dipping = shouldDip(player);
        if (dipping) {
            return movement;
        }

        float slowFactor = slowDown.isEnabled() ? 0.5f : 1.0f;

        // Powder Snow: treat the snow layer as a walkable surface instead of sinking in.
        if (powderSnow.isEnabled() && onPowderSnow(player) && !player.isSneaking()) {
            player.fallDistance = 0;
            if (movement.y < 0) {
                return new Vec3d(movement.x * slowFactor, 0, movement.z * slowFactor);
            }
        }
        if (player.isTouchingWater() && waterMode.is("Solid")) {
            double surf = surfaceY(player, false);
            if (surf > 0 && player.getY() < surf
                    && !(dipOnSneakWater.isEnabled() && player.isSneaking())) {
                double vy = movement.y;
                // cap vertical: no swimming drift, no sinking below the surface
                if (vy < 0) vy = 0;
                player.fallDistance = 0;
                // when the surface is above the feet, pin the feet there
                Vec3d result = new Vec3d(movement.x * slowFactor, Math.min(vy, surf - player.getY()), movement.z * slowFactor);
                if (mc.world.getBlockState(BlockPos.ofFloored(player.getX(), player.getY() - 0.1, player.getZ())).isOf(Blocks.WATER)
                        || mc.world.getFluidState(BlockPos.ofFloored(player.getX(), player.getY() - 0.1, player.getZ())).isStill()) {
                    player.setOnGround(false);
                }
                return result;
            }
        }
        if (player.isInLava() && lavaMode.is("Solid")) {
            double surf = surfaceY(player, true);
            if (surf > 0 && player.getY() < surf
                    && !(dipOnSneakLava.isEnabled() && player.isSneaking())) {
                double vy = movement.y;
                if (vy < 0) vy = 0;
                player.fallDistance = 0;
                return new Vec3d(movement.x, Math.min(vy, surf - player.getY()), movement.z);
            }
        }
        return movement;
    }

    private boolean shouldDip(PlayerEntity player) {
        if (burningDipHappening) return true;
        if (player.isSwimming()) return true;
        boolean sneakDipOnWater = dipOnSneakWater.isEnabled() && player.isSneaking() && player.isTouchingWater();
        boolean sneakDipOnLava = dipOnSneakLava.isEnabled() && player.isSneaking() && player.isInLava();
        if (sneakDipOnWater || sneakDipOnLava) return true;

        boolean fallDip = (player.isTouchingWater() && dipOnFallWater.isEnabled() && player.fallDistance > dipFallHeightWater.getAsDouble())
                || (player.isInLava() && dipOnFallLava.isEnabled() && player.fallDistance > dipFallHeightLava.getAsDouble());
        return fallDip;
    }

    /** True when the block right under the feet is powder snow. */
    private static boolean onPowderSnow(ClientPlayerEntity player) {
        if (mc.world == null) return false;
        BlockPos below = BlockPos.ofFloored(player.getX(), player.getBoundingBox().minY - 0.08, player.getZ());
        return mc.world.getBlockState(below).isOf(Blocks.POWDER_SNOW);
    }

    private double surfaceY(PlayerEntity player, boolean lava) {
        BlockPos pos = BlockPos.ofFloored(player.getX(), Math.ceil(player.getY()), player.getZ());
        for (int i = 0; i < 4; i++) {
            FluidState fluid = mc.world.getFluidState(pos);
            boolean isFluid = lava ? fluid.isIn(net.minecraft.registry.tag.FluidTags.LAVA)
                    : fluid.isIn(net.minecraft.registry.tag.FluidTags.WATER);
            if (!isFluid) {
                pos = pos.down();
                continue;
            }
            if (mc.world.getFluidState(pos.up()).isIn(net.minecraft.registry.tag.FluidTags.LAVA)
                    && lava) {
                pos = pos.up();
                continue;
            }
            return pos.getY() + 1;
        }
        // the fluid below the feet is deep; the feet sit at the top of it
        return Math.floor(player.getBoundingBox().minY) + (lava ? 0.9 : 0.9);
    }

    private static boolean hasFireResistance(PlayerEntity player) {
        return player.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.FIRE_RESISTANCE);
    }

    private boolean overWaterSurface(PlayerEntity player) {
        return player.isTouchingWater() && waterMode.is("Solid");
    }

    private boolean overLavaSurface(PlayerEntity player) {
        return player.isInLava() && lavaMode.is("Solid");
    }
}
