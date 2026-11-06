package com.anormal.client.module.impl.spectre;

import net.minecraft.entity.Entity;
import net.minecraft.entity.MovementType;
import net.minecraft.util.math.Vec3d;

/**
 * Shared dispatch point for Spectre-section movement rewrites
 * (Spectre's PlayerMoveEvent equivalent).
 *
 * <p>{@link com.anormal.client.mixin.SpectreMoveMixin} cancels the vanilla move and
 * re-calls it with the vector every active module returns, which keeps the vanilla
 * physics pipeline (collision resolution, step-up, ground detection) intact.</p>
 */
public final class SpectreMovementHooks {
    public interface MoveHook {
        /** Returns the replacement movement vector, or the input unchanged. */
        Vec3d onMove(Entity entity, MovementType type, Vec3d movement);
    }

    private static final ThreadLocal<Boolean> moving = ThreadLocal.withInitial(() -> false);
    private static SpectreMovementHooks.MoveHook[] hooks = {};

    private SpectreMovementHooks() {}

    /** Rebuild the hook list from the registered hook modules. */
    public static void rebind() {
        java.util.List<SpectreMovementHooks.MoveHook> list = new java.util.ArrayList<>();
        try {
            addIfHook(list, SpectreSpeed.class);
            addIfHook(list, SpectreAutoWalk.class);
            addIfHook(list, SpectreJesus.class);
        } catch (Throwable ignored) {
        }
        hooks = list.toArray(new SpectreMovementHooks.MoveHook[0]);
    }

    private static <T extends com.anormal.client.module.Module> void addIfHook(
            java.util.List<SpectreMovementHooks.MoveHook> list, Class<T> type) {
        T m = com.anormal.client.module.ModuleManager.getModule(type);
        if (m instanceof SpectreMovementHooks.MoveHook hook) list.add(hook);
    }

    public static boolean isReplacing() {
        return moving.get();
    }

    public static Vec3d onMove(Entity entity, MovementType type, Vec3d movement) {
        if (movement == null || type != MovementType.SELF) return movement;
        if (moving.get()) return movement;
        moving.set(true);
        try {
            for (SpectreMovementHooks.MoveHook hook : hooks) {
                movement = hook.onMove(entity, type, movement);
            }
        } catch (Throwable ignored) {
        } finally {
            moving.set(false);
        }
        return movement;
    }
}
