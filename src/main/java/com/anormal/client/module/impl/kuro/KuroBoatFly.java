package com.anormal.client.module.impl.kuro;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.InputForcer;
import com.anormal.client.util.NotificationManager;

import net.minecraft.client.option.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.registry.Registries;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;

/**
 * Ported from kuro (dev.kuro.client.module.impl.BoatFly).
 *
 * <p>Flies the boat you are sitting in, and every few ticks gets out and back in again.
 * While you ride, the boat's motion is set from your keys: WASD for the heading, jump to climb,
 * otherwise it hovers (or sinks slowly). Every "Cycle" ticks the module taps sneak for one tick
 * (the normal way out of a boat) and the tick after that right-clicks the same boat to get back in,
 * so the server never sees one long flight in a single ride.</p>
 *
 * <p>Notes:</p>
 * <ul>
 *   <li>Whether a server lets this through depends on the server. Many plugins flag vehicle
 *       flight; use it where it is allowed.</li>
 *   <li>The module switches itself off if the boat cannot be entered again
 *       (too far away, taken, broken).</li>
 * </ul>
 */
public class KuroBoatFly extends Module {
    private final NumberSetting hSpeed = new NumberSetting("Horizontal speed",
            "Boat speed while a movement key is held", 0.8, 0.1, 3.0, 0.1);
    private final NumberSetting climb = new NumberSetting("Climb speed (jump)",
            "Upward speed while holding jump", 0.35, 0.05, 1.5, 0.05);
    private final NumberSetting sink = new NumberSetting("Sink speed (no jump)",
            "Downward speed while jump is not held", 0.0, 0.0, 0.5, 0.01);
    private final NumberSetting cycle = new NumberSetting("Exit / re-enter every (ticks)",
            "Ticks between the exit / re-enter taps", 8, 2, 40, 1);
    private final BooleanSetting needKeys = new BooleanSetting("Only move while a key is held",
            "Leave the boat still when no movement key is down", false);

    private enum Phase { RIDING, EXIT, ENTER }

    private Phase phase = Phase.RIDING;
    private Entity boat;
    private int ticks, enterTries;
    private boolean sneakByUs;

    public KuroBoatFly() {
        super("Boat Fly", "Flies the boat you are riding (kuro port)", Category.KURO);
        addSetting(hSpeed);
        addSetting(climb);
        addSetting(sink);
        addSetting(cycle);
        addSetting(needKeys);
    }

    @Override
    public void onEnable() {
        reset();
    }

    @Override
    public void onDisable() {
        releaseSneak();
        reset();
    }

    private void reset() {
        phase = Phase.RIDING;
        boat = null;
        ticks = 0;
        enterTries = 0;
    }

    private static boolean isBoat(Entity e) {
        if (e == null) return false;
        String path = Registries.ENTITY_TYPE.getId(e.getType()).getPath();
        return path.endsWith("boat") || path.endsWith("raft");
    }

    private void holdSneak(boolean down) {
        if (mc.options == null) return;
        mc.options.sneakKey.setPressed(down);
        InputForcer.sneak = down;
        sneakByUs = down;
    }

    private void releaseSneak() {
        if (!sneakByUs) return;
        sneakByUs = false;
        InputForcer.sneak = false;
        try {
            KeyBinding.updatePressedStates();          // kuro: KeyMapping.setAll()
        } catch (Throwable ignored) {
        }
    }

    /** Right-click the boat to sit in it again. */
    private void remount() {
        if (boat == null || mc.interactionManager == null || mc.player == null) return;
        try {
            mc.interactionManager.interactEntity(mc.player, boat, Hand.MAIN_HAND);
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void onPreTick() {
        if (mc.player == null || mc.world == null || mc.currentScreen != null) return;

        switch (phase) {
            case RIDING -> {
                Entity v = mc.player.getVehicle();
                if (isBoat(v)) {
                    boat = v;
                    ticks++;
                } else if (boat != null && ticks > 0) {
                    phase = Phase.ENTER;                    // thrown out by something else
                    enterTries = 0;
                }
                if (isBoat(v) && ticks >= cycle.getValue().intValue()) {
                    phase = Phase.EXIT;
                    ticks = 0;
                    holdSneak(true);                        // sneak = leave the vehicle
                }
            }
            case EXIT -> {
                releaseSneak();
                phase = mc.player.getVehicle() == null ? Phase.ENTER : Phase.RIDING;
                enterTries = 0;
                if (phase == Phase.ENTER) remount();        // same tick the sneak was let go
            }
            case ENTER -> {
                if (mc.player.getVehicle() != null) {
                    phase = Phase.RIDING;
                    ticks = 0;
                    return;
                }
                if (++enterTries > 10 || boat == null || !boat.isAlive()
                        || mc.player.distanceTo(boat) > 5.0) {
                    NotificationManager.info("Boat Fly", "Could not get back into the boat, switching off");
                    setEnabled(false);
                    return;
                }
                remount();
            }
        }
    }

    /** kuro's {@code onTick}: the boat's motion while you sit in it. */
    @Override
    public void onTick() {
        if (mc.player == null || mc.currentScreen != null) return;
        Entity vehicle = mc.player.getVehicle();
        if (!isBoat(vehicle)) return;

        double f = (mc.options.forwardKey.isPressed() ? 1 : 0) - (mc.options.backKey.isPressed() ? 1 : 0);
        double s = (mc.options.leftKey.isPressed() ? 1 : 0) - (mc.options.rightKey.isPressed() ? 1 : 0);
        boolean up = mc.options.jumpKey.isPressed();
        if (needKeys.isEnabled() && f == 0 && s == 0 && !up) return;

        double mx = 0, mz = 0;
        if (f != 0 || s != 0) {
            double yaw = Math.toRadians(mc.player.getYaw());
            double dx = -Math.sin(yaw) * f + Math.cos(yaw) * s;
            double dz = Math.cos(yaw) * f + Math.sin(yaw) * s;
            double len = Math.hypot(dx, dz);
            mx = dx / len * hSpeed.getValue();
            mz = dz / len * hSpeed.getValue();
        }
        double my = up ? climb.getValue() : -sink.getValue();
        vehicle.setVelocity(new Vec3d(mx, my, mz));
        vehicle.fallDistance = 0.0;
    }
}
