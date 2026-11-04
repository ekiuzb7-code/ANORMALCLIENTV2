package com.anormal.client.module.impl.kuro;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;

import org.lwjgl.glfw.GLFW;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Ported from kuro (dev.kuro.client.module.impl.WTap).
 *
 * <p>WTap / STap: resets your sprint after a hit so the next hit knocks back again.</p>
 *
 * <p>The module sends NO packets. It only changes what the game reads as the forward / back
 * key: a hit arms a tap, the next tick the forward key reads as released (W-tap), or the back
 * key reads as pressed (S-tap) when Dynamic is on and the target is closer than the S-Tap
 * distance. It lasts "Keyboard delay" ticks (random between min and max), then the keys read
 * normally again. A tap starts and ends in different ticks, so the key state never changes
 * twice in one tick, and sprint resumes the vanilla way.</p>
 *
 * <p>kuro drives this from the AttackEntityCallback at the exact attack; Anormal's compile
 * classpath does not resolve the Fabric event's Minecraft types, so the same attack moment is
 * detected by the swing-edge check (attack held + target under the crosshair + handSwinging
 * edge), which lands within a tick of the vanilla attack.</p>
 */
public class KuroWTap extends Module {
    private final BooleanSetting dynamic = new BooleanSetting("Dynamic (S-tap when close)",
            "S-tap instead of W-tap when the target is close", true);
    private final NumberSetting delayMin = new NumberSetting("Keyboard delay min (ticks)",
            "Minimum tap length", 1.0, 1.0, 10.0, 1.0);
    private final NumberSetting delayMax = new NumberSetting("Keyboard delay max (ticks)",
            "Maximum tap length", 2.0, 1.0, 10.0, 1.0);
    private final NumberSetting rangeMin = new NumberSetting("Range min",
            "Minimum attack distance to arm a tap", 0.0, 0.0, 6.0, 0.1);
    private final NumberSetting rangeMax = new NumberSetting("Range max",
            "Maximum attack distance to arm a tap", 3.5, 0.0, 6.0, 0.1);
    private final NumberSetting sTapDistance = new NumberSetting("S-Tap distance",
            "Targets closer than this get an S-tap", 2.7, 0.0, 6.0, 0.1);
    private final BooleanSetting onlySprinting = new BooleanSetting("Only sprinting",
            "Only arm a tap while sprinting", true);
    private final BooleanSetting onlyGround = new BooleanSetting("Only ground",
            "Only arm a tap while on the ground", false);

    private boolean armed;
    private double armDistance;
    private int ticksLeft;      // > 0 while a tap is running
    private boolean tapIsBack;
    private boolean wasSwinging;

    public KuroWTap() {
        super("WTap", "Resets sprint after each hit (kuro port)", Category.KURO);
        addSetting(dynamic);
        addSetting(delayMin);
        addSetting(delayMax);
        addSetting(rangeMin);
        addSetting(rangeMax);
        addSetting(sTapDistance);
        addSetting(onlySprinting);
        addSetting(onlyGround);
    }

    @Override
    public void onEnable() {
        armed = false;
        ticksLeft = 0;
        wasSwinging = mc.player != null && mc.player.handSwinging;
    }

    @Override
    public void onDisable() {
        stopTap();
        armed = false;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.options == null
                || mc.currentScreen != null || mc.player.isSpectator()) {
            stopTap();
            armed = false;
            wasSwinging = false;
            return;
        }

        // A running tap: count down, end it in a later tick than it started
        if (ticksLeft > 0) {
            if (--ticksLeft == 0) stopTap();
            wasSwinging = mc.player.handSwinging;
            return;
        }

        // Attack arming (kuro uses the AttackEntityCallback; the swing edge happens this tick):
        boolean attacking = mc.options.attackKey.isPressed() && mc.targetedEntity != null;
        boolean swingEdge = attacking && mc.player.handSwinging && !wasSwinging;
        wasSwinging = mc.player.handSwinging;
        if (swingEdge && !armed) {
            if (onlySprinting.isEnabled() && !mc.player.isSprinting()) return;
            if (onlyGround.isEnabled() && !mc.player.isOnGround()) return;
            double d = mc.player.distanceTo(mc.targetedEntity);
            double lo = Math.min(rangeMin.getValue(), rangeMax.getValue());
            double hi = Math.max(rangeMin.getValue(), rangeMax.getValue());
            if (d < lo || d > hi) return;
            armed = true;                        // acted on at the start of the next tick
            armDistance = d;
        }
    }

    /**
     * Runs right after each game tick (END_CLIENT_TICK, before the next tick's input read):
     * a tap armed on the previous tick starts here, so the next movement tick reads the
     * changed key from the very first moment, exactly like kuro's early() preTick.
     */
    @Override
    public void onTickAlways() {
        boolean enabled = isEnabled();
        if (!enabled || mc.player == null || mc.currentScreen != null) {
            stopTap();
            armed = false;
            return;
        }
        if (ticksLeft > 0) {
            armed = false;
            return;                       // a tap is running; onTick handles the countdown
        }

        if (armed) {
            armed = false;
            if (!isKeyPhysicallyDown(GLFW.GLFW_KEY_W)) return;   // not walking forward: nothing to tap
            int lo = (int) Math.min(delayMin.getAsDouble(), delayMax.getAsDouble());
            int hi = (int) Math.max(delayMin.getAsDouble(), delayMax.getAsDouble());
            int len = lo == hi ? lo : ThreadLocalRandom.current().nextInt(lo, hi + 1);
            tapIsBack = dynamic.isEnabled() && armDistance < sTapDistance.getValue();
            if (tapIsBack) {
                com.anormal.client.util.InputForcer.backward = true;
                mc.options.backKey.setPressed(true);
            } else {
                mc.options.forwardKey.setPressed(false);          // W-tap: forward reads as released
            }
            ticksLeft = len;              // this tick counts as the first tap tick
        }
    }

    private void stopTap() {
        if (ticksLeft > 0) {
            if (tapIsBack) {
                com.anormal.client.util.InputForcer.backward = false;
                mc.options.backKey.setPressed(isKeyPhysicallyDown(GLFW.GLFW_KEY_S));
            } else {
                mc.options.forwardKey.setPressed(isKeyPhysicallyDown(GLFW.GLFW_KEY_W));
            }
        }
        ticksLeft = 0;
    }

    private boolean isKeyPhysicallyDown(int key) {
        try {
            long handle = mc.getWindow().getHandle();
            return GLFW.glfwGetKey(handle, key) == GLFW.GLFW_PRESS;
        } catch (Throwable t) {
            return key == GLFW.GLFW_KEY_W && mc.options.forwardKey.isPressed();
        }
    }
}
