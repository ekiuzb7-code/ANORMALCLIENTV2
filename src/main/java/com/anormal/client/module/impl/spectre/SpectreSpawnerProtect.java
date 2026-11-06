package com.anormal.client.module.impl.spectre;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.module.impl.client.Friends;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Spectre port: "Spawner Protect" — mines your spawners when someone gets close.
 *
 * <p>Spectre runs a long state machine that also walks to a chest and deposits the loot.
 * This port keeps the trigger (a stranger inside the trigger range), the whitelist-by-friends
 * rule, the break range and the break cooldown, and mines the spawners it finds around the
 * detected stranger.</p>
 */
public final class SpectreSpawnerProtect extends Module {
    private static final int SCAN_BUDGET = 4096;
    private static final int RESCAN_TICKS = 20;

    public final NumberSetting triggerRange = addSetting(new NumberSetting("Trigger Range", "How close a player must get", 64.0, 16.0, 128.0, 1.0));
    public final NumberSetting breakRange = addSetting(new NumberSetting("Break Range", "How far a spawner may be mined", 5.5, 1.0, 8.0, 0.5));
    public final NumberSetting rotationSpeed = addSetting(new NumberSetting("Rotation Speed", "Look speed towards the spawner", 15.0, 1.0, 30.0, 1.0));
    public final BooleanSetting detectBlockUpdates = addSetting(new BooleanSetting("Detect Block Updates", "React to nearby spawners appearing", true));
    public final BooleanSetting ignoreFriends = addSetting(new BooleanSetting("Ignore Friends", "Friends never trigger the module", true));
    public final BooleanSetting notify = addSetting(new BooleanSetting("Notify", "Chat message when it triggers", true));

    private final ArrayDeque<Long> queue = new ArrayDeque<>();
    private final Set<Long> spawners = new HashSet<>();
    private int tick;
    private int breakCooldown;
    private boolean triggered;

    public SpectreSpawnerProtect() {
        super("SpectreSpawnerProtect", "Spectre port: mines your spawners when someone gets close.", Category.SPECTRE);
    }

    @Override
    public void onEnable() {
        queue.clear();
        spawners.clear();
        tick = 0;
        breakCooldown = 0;
        triggered = false;
    }

    @Override
    public void onDisable() {
        queue.clear();
        spawners.clear();
        if (mc.interactionManager != null && mc.interactionManager.isBreakingBlock()) {
            mc.interactionManager.cancelBlockBreaking();
        }
    }

    @Override
    public void onTick() {
        var self = mc.player;
        if (self == null || mc.world == null || mc.interactionManager == null) {
            onEnable();
            return;
        }
        if (breakCooldown > 0) breakCooldown--;

        if (queue.isEmpty()) {
            // Detect Block Updates reacts to spawners appearing the tick they show up.
            int interval = detectBlockUpdates.isEnabled() ? 1 : RESCAN_TICKS;
            if (++tick < interval) return;
            tick = 0;
            spawners.clear();
            int radius = Math.min(mc.options.getViewDistance().getValue(), 6);
            int pcx = self.getChunkPos().x;
            int pcz = self.getChunkPos().z;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (mc.world.getChunkManager().getWorldChunk(pcx + dx, pcz + dz) != null) {
                        queue.add(pack(pcx + dx, pcz + dz));
                    }
                }
            }
        }

        int budget = SCAN_BUDGET;
        outer:
        while (!queue.isEmpty() && budget > 0) {
            long key = queue.poll();
            WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(unpackX(key), unpackZ(key));
            if (chunk == null) continue;
            ChunkSection[] sections = chunk.getSectionArray();
            int bottom = mc.world.getBottomSectionCoord();
            for (int i = 0; i < sections.length; i++) {
                ChunkSection section = sections[i];
                if (section == null || section.isEmpty()) continue;
                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        for (int x = 0; x < 16; x++) {
                            if (budget-- <= 0) break outer;
                            if (!section.getBlockState(x, y, z).isOf(Blocks.SPAWNER)) continue;
                            spawners.add(new BlockPos((chunk.getPos().x << 4) + x, ((bottom + i) << 4) + y,
                                    (chunk.getPos().z << 4) + z).asLong());
                        }
                    }
                }
            }
        }

        PlayerEntity stranger = stranger(self);
        if (stranger == null) {
            triggered = false;
            return;
        }
        if (!triggered) {
            triggered = true;
            if (notify.isEnabled() && mc.inGameHud != null) {
                mc.inGameHud.getChatHud().addMessage(Text.literal(
                        "[SpectreSpawnerProtect] " + stranger.getGameProfile().name() + " is close — mining your spawners."));
            }
        }
        if (breakCooldown > 0) return;

        BlockPos target = nearestSpawner(self);
        if (target == null) return;

        lookAt(self, target);
        mc.interactionManager.updateBlockBreakingProgress(target, sideOf(self, target));
        self.swingHand(Hand.MAIN_HAND);
        breakCooldown = 6 + (ThreadLocalRandom.current().nextInt(3));
    }

    private PlayerEntity stranger(PlayerEntity self) {
        double range = triggerRange.getAsDouble();
        for (PlayerEntity other : mc.world.getPlayers()) {
            if (other == self || other.isSpectator() || !other.isAlive()) continue;
            if (self.distanceTo(other) > range) continue;
            String name = other.getGameProfile().name();
            if (ignoreFriends.isEnabled() && name != null && Friends.isFriend(name)) continue;
            return other;
        }
        return null;
    }

    private BlockPos nearestSpawner(PlayerEntity self) {
        double max = breakRange.getAsDouble();
        double maxSq = max * max;
        BlockPos best = null;
        double bestSq = Double.MAX_VALUE;
        for (long packed : spawners) {
            BlockPos pos = BlockPos.fromLong(packed);
            double distanceSq = pos.getSquaredDistance(self.getEyePos());
            if (distanceSq > maxSq || distanceSq >= bestSq) continue;
            if (!mc.world.getBlockState(pos).isOf(Blocks.SPAWNER)) continue;
            best = pos;
            bestSq = distanceSq;
        }
        return best;
    }

    private void lookAt(PlayerEntity self, BlockPos target) {
        double dx = target.getX() + 0.5 - self.getX();
        double dy = target.getY() + 0.5 - (self.getY() + self.getStandingEyeHeight());
        double dz = target.getZ() + 0.5 - self.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float) (-Math.toDegrees(Math.atan2(dy, horizontal)));
        float step = rotationSpeed.getValue().floatValue();
        self.setYaw(self.getYaw() + Math.max(-step, Math.min(step, yaw - self.getYaw())));
        self.setPitch(self.getPitch() + Math.max(-step, Math.min(step, pitch - self.getPitch())));
    }

    private static Direction sideOf(PlayerEntity self, BlockPos target) {
        double dx = self.getX() - (target.getX() + 0.5);
        double dz = self.getZ() - (target.getZ() + 0.5);
        if (Math.abs(dx) > Math.abs(dz)) return dx > 0 ? Direction.EAST : Direction.WEST;
        return dz > 0 ? Direction.SOUTH : Direction.NORTH;
    }

    private static long pack(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    private static int unpackX(long key) {
        return (int) (key >> 32);
    }

    private static int unpackZ(long key) {
        return (int) key;
    }
}
