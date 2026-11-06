package com.anormal.client.module.impl.spectre;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.util.NotificationManager;
import com.anormal.client.util.ColorUtils;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;

import java.util.ArrayList;
import java.util.List;

/**
 * Spectre port: "Anti Trap" — allows you to escape from armor stands and chest minecarts.
 */
public final class SpectreAntiTrap extends Module {
    private static final int SWEEP_INTERVAL_TICKS = 20;
    private static final double PREVENT_SPAWN_RADIUS_SQ = 64.0;

    public final BooleanSetting removeExisting = addSetting(new BooleanSetting("Remove Existing", "Remove trap entities already around you on enable", true));
    public final BooleanSetting preventSpawn = addSetting(new BooleanSetting("Prevent Spawn", "Discard new trap entities immediately", true));
    public final BooleanSetting armorStands = addSetting(new BooleanSetting("Armor Stands", "Treat armor stands as traps", true));
    public final BooleanSetting chestMinecarts = addSetting(new BooleanSetting("Chest Minecarts", "Treat chest minecarts as traps", true));
    public final BooleanSetting notify = addSetting(new BooleanSetting("Notifications", "Show a notification when traps are removed", true));

    private final List<Entity> pendingSpawns = new ArrayList<>();
    /** Trap-entity ids seen on the previous tick (stands in for Spectre's WorldEntityAddEvent). */
    private final java.util.Set<Integer> knownTrapIds = new java.util.HashSet<>();
    private boolean seeded;

    public SpectreAntiTrap() {
        super("SpectreAntiTrap", "Spectre port: allows you to escape from armor stands and chest minecarts.", Category.SPECTRE);
    }

    @Override
    public void onEnable() {
        pendingSpawns.clear();
        knownTrapIds.clear();
        seeded = false;
        if (removeExisting.isEnabled()) {
            removeTrapEntities();
        }
    }

    @Override
    public void onDisable() {
        pendingSpawns.clear();
        knownTrapIds.clear();
        seeded = false;
    }

    @Override
    public void onTick() {
        if (mc.world == null || mc.player == null) {
            pendingSpawns.clear();
            knownTrapIds.clear();
            seeded = false;
            return;
        }

        // Diff the trap-entity ids each tick: anything that appeared since the last tick is a
        // fresh spawn, exactly what Spectre queues from its WorldEntityAddEvent.
        java.util.Set<Integer> current = new java.util.HashSet<>();
        for (Entity entity : mc.world.getEntities()) {
            if (!isTrapEntity(entity) || !canRemove(entity)) continue;
            current.add(entity.getId());
            if (preventSpawn.isEnabled() && seeded && !knownTrapIds.contains(entity.getId())
                    && entity.squaredDistanceTo(mc.player) <= PREVENT_SPAWN_RADIUS_SQ
                    && mc.world.getEntityById(entity.getId()) == entity) {
                pendingSpawns.add(entity);
            }
        }
        knownTrapIds.clear();
        knownTrapIds.addAll(current);
        seeded = true;

        if (!preventSpawn.isEnabled()) {
            pendingSpawns.clear();
        }

        if (preventSpawn.isEnabled() && !pendingSpawns.isEmpty()) {
            for (Entity entity : pendingSpawns) {
                if (entity == null || entity.isRemoved()) continue;
                if (isTrapEntity(entity) && canRemove(entity)) {
                    entity.discard();
                }
            }
            pendingSpawns.clear();
        }

        if (mc.player.age % SWEEP_INTERVAL_TICKS == 0) {
            for (Entity entity : collectTrapEntities()) {
                entity.discard();
            }
        }
    }

    /** New-entity detection runs per tick over the render set (Spectre's WorldEntityAddEvent). */
    public void onWorldEntitiesChanged() {
        // handled by the periodic sweep; no separate hook needed
    }

    private void removeTrapEntities() {
        if (mc.world == null) return;
        List<Entity> traps = collectTrapEntities();
        for (Entity entity : traps) {
            entity.discard();
        }
        if (!traps.isEmpty() && notify.isEnabled()) {
            NotificationManager.push("Anti Trap", "Removed " + traps.size() + " trap entities", ColorUtils.rgba(255, 120, 0, 255));
        }
    }

    private List<Entity> collectTrapEntities() {
        List<Entity> traps = new ArrayList<>();
        if (mc.world == null || mc.player == null) return traps;
        for (Entity entity : mc.world.getEntities()) {
            if (isTrapEntity(entity) && canRemove(entity)) traps.add(entity);
        }
        return traps;
    }

    private boolean isTrapEntity(Entity entity) {
        if (entity == null) return false;
        EntityType<?> type = entity.getType();
        return (armorStands.isEnabled() && type == EntityType.ARMOR_STAND)
                || (chestMinecarts.isEnabled() && type == EntityType.CHEST_MINECART);
    }

    private static boolean canRemove(Entity entity) {
        return entity != mc.player && entity != mc.getCameraEntity();
    }
}
