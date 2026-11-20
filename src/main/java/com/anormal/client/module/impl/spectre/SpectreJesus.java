package com.anormal.client.module.impl.spectre;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.block.Blocks;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.fluid.FluidState;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Spectre port: "Jesus" — walk on liquids and powder snow like Jesus.
 *
 * <p>Uses the same mechanism vanilla gives Striders: {@code LivingEntity#canWalkOnFluid} is
 * answered by {@link SpectreJesusLivingMixin}, so the fluid surface becomes real ground (proper
 * jumping, no sinking between ticks, no packet spam). Powder snow goes through
 * {@link SpectreJesusPowderSnowMixin} and bubble columns are reported by
 * {@link SpectreJesusEntityMixin}.</p>
 *
 * <p>The move hook only smooths what the surface walk leaves behind: it kills downward drift and
 * keeps the feet on the surface height. NCP Bypass adds Spectre's packet spoof (onGround=false
 * plus a small Y offset) so strict servers do not see you standing on water.</p>
 */
public final class SpectreJesus extends Module
        implements SpectreMovementHooks.MoveHook, SpectrePackets.SendHook {

    /** The enabled instance, read by the mixins (Spectre's {@code JesusModule.active}). */
    public static volatile SpectreJesus active;

    private static final double ASCEND_SPEED = 0.11;

    public final BooleanSetting powderSnow = addSetting(new BooleanSetting("Powder Snow",
            "Powder snow counts as a walkable surface", true));
    public final BooleanSetting ncpBypass = addSetting(new BooleanSetting("NCP Bypass",
            "Spoof movement packets so strict servers accept the surface walk", false));
    public final BooleanSetting slowDown = addSetting(new BooleanSetting("Slow Down",
            "Reduce walk speed on the surface", false));
    public final ModeSetting waterMode = addSetting(new ModeSetting("Water Mode",
            "Water surface mode", "Solid", "Solid", "Ignore"));
    public final BooleanSetting dipIfBurning = addSetting(new BooleanSetting("Dip If Burning",
            "Sinks while on fire", true));
    public final BooleanSetting dipOnSneakWater = addSetting(new BooleanSetting("Water Dip On Sneak",
            "Sneak dips into water", true));
    public final BooleanSetting dipOnFallWater = addSetting(new BooleanSetting("Water Dip On Fall",
            "Falling from height dips in", true));
    public final NumberSetting dipFallHeightWater = addSetting(new NumberSetting("Water Dip Fall Height",
            "Fall height that dips", 4.0, 1.0, 255.0, 1.0));
    public final ModeSetting lavaMode = addSetting(new ModeSetting("Lava Mode",
            "Lava surface mode", "Solid", "Solid", "Ignore"));
    public final BooleanSetting dipIfFireResistant = addSetting(new BooleanSetting("Lava Dip If Resistant",
            "Dip in lava while fire resistant", true));
    public final BooleanSetting dipOnSneakLava = addSetting(new BooleanSetting("Lava Dip On Sneak",
            "Sneak dips into lava", true));
    public final BooleanSetting dipOnFallLava = addSetting(new BooleanSetting("Lava Dip On Fall",
            "Falling from height dips in", true));
    public final NumberSetting dipFallHeightLava = addSetting(new NumberSetting("Lava Dip Fall Height",
            "Fall height that dips", 4.0, 1.0, 255.0, 1.0));

    /** Spectre's surface-step counter: 0 = lift, 1 = hold, then settle. */
    private int ascending = 10;
    private boolean inBubbleColumn;

    public SpectreJesus() {
        super("SpectreJesus", "Spectre port: walk on liquids and powder snow like Jesus.", Category.SPECTRE);
        dipIfBurning.visibleIf(() -> waterMode.is("Solid"));
        dipOnSneakWater.visibleIf(() -> waterMode.is("Solid"));
        dipOnFallWater.visibleIf(() -> waterMode.is("Solid"));
        dipFallHeightWater.visibleIf(() -> waterMode.is("Solid") && dipOnFallWater.isEnabled());
        dipIfFireResistant.visibleIf(() -> lavaMode.is("Solid"));
        dipOnSneakLava.visibleIf(() -> lavaMode.is("Solid"));
        dipOnFallLava.visibleIf(() -> lavaMode.is("Solid"));
        dipFallHeightLava.visibleIf(() -> lavaMode.is("Solid") && dipOnFallLava.isEnabled());
    }

    // ------------------------------------------------------------------
    //  Lifecycle
    // ------------------------------------------------------------------

    @Override
    public void onEnable() {
        active = this;
        ascending = 10;
        inBubbleColumn = false;
        SpectrePackets.addHooks(this);
        SpectreMovementHooks.rebind();
    }

    @Override
    public void onDisable() {
        active = null;
        ascending = 10;
        inBubbleColumn = false;
        SpectrePackets.removeHook(this);
        SpectreMovementHooks.rebind();
    }

    // ------------------------------------------------------------------
    //  Queries used by the mixins / movement hook
    // ------------------------------------------------------------------

    /** True when water should behave like a floor for this player. */
    public boolean waterShouldBeSolid(ClientPlayerEntity player) {
        if (player.isSpectator() || player.getAbilities().flying) return false;
        if (player.hasVehicle()) return false;
        if (!waterMode.is("Solid")) return false;
        if (dipIfBurning.isEnabled() && player.isOnFire()) return false;
        if (dipOnSneakWater.isEnabled() && player.isSneaking()) return false;
        if (dipOnFallWater.isEnabled() && player.fallDistance > dipFallHeightWater.getAsDouble()) return false;
        return true;
    }

    /** True when lava should behave like a floor for this player. */
    public boolean lavaShouldBeSolid(ClientPlayerEntity player) {
        if (player.isSpectator() || player.getAbilities().flying) return false;
        if (!lavaMode.is("Solid")) return false;
        if (isLavaDangerous(player)) return true;
        if (dipOnSneakLava.isEnabled() && player.isSneaking()) return false;
        if (dipOnFallLava.isEnabled() && player.fallDistance > dipFallHeightLava.getAsDouble()) return false;
        return true;
    }

    /** Fire resistance only helps until it is nearly gone (Spectre's isLavaDangerous). */
    private boolean isLavaDangerous(ClientPlayerEntity player) {
        if (!dipIfFireResistant.isEnabled()) return true;
        return !player.hasStatusEffect(StatusEffects.FIRE_RESISTANCE);
    }

    /** Called from {@link SpectreJesusLivingMixin}. */
    public boolean canWalkOnFluid(FluidState fluid) {
        ClientPlayerEntity player = mc.player;
        if (player == null || player.isSwimming()) return false;
        if (fluid.isIn(FluidTags.WATER)) return waterShouldBeSolid(player);
        if (fluid.isIn(FluidTags.LAVA)) return lavaShouldBeSolid(player);
        return false;
    }

    /** Called from {@link SpectreJesusPowderSnowMixin}. */
    public boolean canWalkOnPowderSnow() {
        return powderSnow.isEnabled();
    }

    /** Called from {@link SpectreJesusEntityMixin} so the ascend boost works in bubble columns. */
    public void markInBubbleColumn() {
        inBubbleColumn = true;
    }

    // ------------------------------------------------------------------
    //  Per-tick surface keeping
    // ------------------------------------------------------------------

    @Override
    public void onTick() {
        if (!isEnabled() || mc.player == null || mc.world == null) return;
        ClientPlayerEntity player = mc.player;

        boolean bubble = inBubbleColumn;
        inBubbleColumn = false;

        if (player.isSwimming()) {
            ascending = 10;
            return;
        }
        Entity moving = player.hasVehicle() ? player.getVehicle() : player;
        if (moving == null) return;

        if (bubble) {
            if (mc.options.jumpKey.isPressed() && moving.getVelocity().y < ASCEND_SPEED) {
                setVelocityY(moving, ASCEND_SPEED);
            }
            return;
        }

        double top = surfaceTop(player);
        if (top >= 0) {
            // Standing on the surface: keep the feet glued to it and restore the jump impulse.
            player.fallDistance = 0;
            setVelocityY(moving, Math.max(ASCEND_SPEED, moving.getVelocity().y));
            ascending = 0;
            return;
        }
        if (player.isTouchingWater() || player.isInLava()) return;

        // Spectre's step-onto-the-surface sequence: lift once, hold once, then settle.
        if (ascending == 0) {
            setVelocityY(moving, ASCEND_SPEED);
        } else if (ascending == 1 && fluidBelow(player)) {
            setVelocityY(moving, 0);
        }
        ascending++;
    }

    // ------------------------------------------------------------------
    //  Move hook
    // ------------------------------------------------------------------

    @Override
    public Vec3d onMove(Entity entity, MovementType type, Vec3d movement) {
        if (!isEnabled() || mc.player == null || mc.world == null) return movement;
        if (entity != mc.player) return movement;
        ClientPlayerEntity player = mc.player;
        if (player.isSwimming()) return movement;

        double top = surfaceTop(player);
        if (top < 0 || movement == null) return movement;

        double feet = player.getBoundingBox().minY;
        if (feet > top + 0.05) return movement; // we are already airborne above the surface

        float slow = slowDown.isEnabled() ? 0.5f : 1.0f;
        player.fallDistance = 0;

        // Kill downward drift instead of letting vanilla pull the player under the surface.
        double vy = movement.y < 0 ? 0 : movement.y;
        if (feet < top - 1.0E-3) {
            vy = Math.min(vy, top - feet);
        }
        return new Vec3d(movement.x * slow, vy, movement.z * slow);
    }

    // ------------------------------------------------------------------
    //  NCP bypass packets (Spectre's PacketSendEvent handler)
    // ------------------------------------------------------------------

    @Override
    public Packet<?> onSend(Packet<?> packet, SpectrePackets.Holder holder) {
        if (!ncpBypass.isEnabled() || mc.player == null || mc.world == null) return null;
        if (!(packet instanceof PlayerMoveC2SPacket move)) return null;
        ClientPlayerEntity player = mc.player;
        if (player.fallDistance > 3.0) return null;
        if (surfaceTop(player) < 0) return null;

        // The server must not see "on ground" while we stand on liquid, and the Y has to dip
        // slightly below the surface so the position still looks like a normal fall.
        double dip = 0.02 + 1.0E-4 * Math.max(0, ascending);
        return spoof(move, dip);
    }

    private static PlayerMoveC2SPacket spoof(PlayerMoveC2SPacket p, double dip) {
        try {
            if (p instanceof PlayerMoveC2SPacket.OnGroundOnly q) {
                return new PlayerMoveC2SPacket.OnGroundOnly(false, q.horizontalCollision());
            }
            if (p instanceof PlayerMoveC2SPacket.LookAndOnGround q) {
                return new PlayerMoveC2SPacket.LookAndOnGround(
                        q.getYaw(0.0f), q.getPitch(0.0f), false, q.horizontalCollision());
            }
            if (p instanceof PlayerMoveC2SPacket.PositionAndOnGround q) {
                return new PlayerMoveC2SPacket.PositionAndOnGround(
                        q.getX(0.0), q.getY(0.0) - dip, q.getZ(0.0), false, q.horizontalCollision());
            }
            if (p instanceof PlayerMoveC2SPacket.Full q) {
                return new PlayerMoveC2SPacket.Full(
                        q.getX(0.0), q.getY(0.0) - dip, q.getZ(0.0),
                        q.getYaw(0.0f), q.getPitch(0.0f), false, q.horizontalCollision());
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    // ------------------------------------------------------------------
    //  Geometry helpers
    // ------------------------------------------------------------------

    /**
     * Top of the fluid surface the player is standing on, or {@code -1} when there is none.
     *
     * <p>Only the column under the player's feet is scanned, and the scan stops at the first
     * non-fluid block so a lake below a bridge is not mistaken for a surface.</p>
     */
    private double surfaceTop(ClientPlayerEntity player) {
        if (mc.world == null) return -1;
        Box box = player.getBoundingBox();
        boolean water = canWalkOnFluidFor(player, false);
        boolean lava = canWalkOnFluidFor(player, true);
        if (!water && !lava) return -1;

        double best = -1;
        BlockPos.Mutable pos = new BlockPos.Mutable();
        int minY = MathHelper.floor(box.minY) - 1;
        int maxY = MathHelper.floor(box.maxY);
        for (int x = MathHelper.floor(box.minX); x <= MathHelper.floor(box.maxX); x++) {
            for (int z = MathHelper.floor(box.minZ); z <= MathHelper.floor(box.maxZ); z++) {
                for (int y = maxY; y >= minY; y--) {
                    pos.set(x, y, z);
                    FluidState fluid = mc.world.getFluidState(pos);
                    boolean match = (water && fluid.isIn(FluidTags.WATER))
                            || (lava && fluid.isIn(FluidTags.LAVA));
                    if (!match) {
                        if (!mc.world.getBlockState(pos).isAir()) break; // solid floor ends the column
                        continue;
                    }
                    double top = y + fluid.getHeight();
                    if (top > best) best = top;
                    break;
                }
            }
        }
        return best;
    }

    private boolean canWalkOnFluidFor(ClientPlayerEntity player, boolean lava) {
        return lava ? lavaShouldBeSolid(player) : waterShouldBeSolid(player);
    }

    private boolean fluidBelow(ClientPlayerEntity player) {
        if (mc.world == null) return false;
        BlockPos below = BlockPos.ofFloored(player.getX(), player.getBoundingBox().minY - 0.08, player.getZ());
        FluidState fluid = mc.world.getFluidState(below);
        return fluid.isIn(FluidTags.WATER) || fluid.isIn(FluidTags.LAVA)
                || mc.world.getBlockState(below).isOf(Blocks.POWDER_SNOW);
    }

    private static void setVelocityY(Entity entity, double y) {
        Vec3d v = entity.getVelocity();
        entity.setVelocity(v.x, y, v.z);
    }
}
