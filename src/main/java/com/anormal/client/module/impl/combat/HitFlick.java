package com.anormal.client.module.impl.combat;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.util.math.MathHelper;

/**
 * Ported from Vape V4's HitFlick behaviour.
 *
 * <p>On every attack the aim is flicked away and back again. On the server this
 * looks like a small aim correction between hits, which is what Vape uses to
 * keep a natural-looking rotation while still landing every hit.</p>
 *
 * <p>Vape drives this from the same item gate as BlockHit, so the sword / food
 * / potion toggles are mirrored here too.</p>
 */
public class HitFlick extends Module {
    public final ModeSetting mode = new ModeSetting("Mode", "Which axes to flick", "Yaw", "Yaw", "Pitch", "Both");
    public final NumberSetting flickAngle = new NumberSetting("Flick Angle", "How far to flick the aim", 12.0, 1.0, 90.0, 1.0);
    public final NumberSetting flickTicks = new NumberSetting("Flick Ticks", "Ticks the flick is held for", 1.0, 1.0, 10.0, 1.0);
    public final NumberSetting chance = new NumberSetting("Chance", "Chance to flick on each hit", 100.0, 1.0, 100.0, 1.0);
    public final BooleanSetting randomize = new BooleanSetting("Randomize", "Randomize the flick direction", true);
    public final BooleanSetting onlyKillAura = new BooleanSetting("Only KillAura", "Only flick when a KillAura target exists", false);
    public final BooleanSetting allowSwords = new BooleanSetting("Swords", "Only flick while holding a sword", false);
    public final BooleanSetting allowFood = new BooleanSetting("Food", "Only flick while holding food", false);
    public final BooleanSetting allowPotions = new BooleanSetting("Potions", "Only flick while holding a potion", false);

    // ---- smooth flick state -------------------------------------------------
    private boolean flicking;
    private long startNs;
    private float targetYaw;      // total yaw offset at the peak of the flick
    private float targetPitch;    // total pitch offset at the peak of the flick
    private float appliedYaw;     // offset currently applied to the player's view
    private float appliedPitch;
    private long outNs, holdNs, backNs;

    public HitFlick() {
        super("HitFlick", "Flicks your aim on every attack", Category.COMBAT);
        addSetting(mode);
        addSetting(flickAngle);
        addSetting(flickTicks);
        addSetting(chance);
        addSetting(randomize);
        addSetting(onlyKillAura);
        addSetting(allowSwords);
        addSetting(allowFood);
        addSetting(allowPotions);
    }

    @Override
    public void onDisable() {
        finish();
    }

    /** Called by the attack path right after a hit lands. */
    public void onAttack() {
        if (!isEnabled() || mc.player == null) return;
        if (Math.random() * 100.0 > chance.getValue()) return;
        if (onlyKillAura.isEnabled() && !killAuraHasTarget()) return;
        if (!itemAllowed()) return;
        if (flicking) return; // already flicking

        float angle = flickAngle.getValue().floatValue();
        float dir = randomize.isEnabled() && Math.random() < 0.5 ? -1.0f : 1.0f;

        targetYaw = (mode.is("Yaw") || mode.is("Both")) ? angle * dir : 0.0f;
        targetPitch = (mode.is("Pitch") || mode.is("Both")) ? angle * dir : 0.0f;
        appliedYaw = 0.0f;
        appliedPitch = 0.0f;

        // Move like a hand: the bigger the flick the longer it takes, never a single-frame snap.
        outNs = (long) (Math.max(50.0, Math.min(170.0, 45.0 + angle * 3.0)) * 1_000_000.0);
        holdNs = (long) (flickTicks.getValue() * 50.0 * 1_000_000.0);
        backNs = (long) (outNs * 1.35);
        startNs = System.nanoTime();
        flicking = true;
    }

    /** Runs every rendered frame so the view turns smoothly instead of once per tick. */
    @Override
    public void onRender2D(net.minecraft.client.gui.DrawContext context, float tickDelta) {
        step();
    }

    @Override
    public void onTick() {
        step();
    }

    private static float smooth(float t) {
        t = MathHelper.clamp(t, 0.0f, 1.0f);
        return t * t * t * (t * (t * 6.0f - 15.0f) + 10.0f); // smootherstep
    }

    private void step() {
        if (!flicking) return;
        if (mc.player == null) {
            flicking = false;
            return;
        }
        long t = System.nanoTime() - startNs;
        float k;
        if (t < outNs) {
            k = smooth(t / (float) outNs);
        } else if (t < outNs + holdNs) {
            k = 1.0f;
        } else if (t < outNs + holdNs + backNs) {
            k = 1.0f - smooth((t - outNs - holdNs) / (float) backNs);
        } else {
            k = 0.0f;
        }
        rotateTo(targetYaw * k, targetPitch * k);
        if (t >= outNs + holdNs + backNs) {
            flicking = false;
        }
    }

    /** Moves the view so that the total offset equals the given one (mouse input in between is preserved). */
    private void rotateTo(float yawOff, float pitchOff) {
        var p = mc.player;
        float dYaw = yawOff - appliedYaw;
        float dPitch = pitchOff - appliedPitch;
        if (dYaw == 0.0f && dPitch == 0.0f) return;
        try {
            float oldPitch = p.getPitch();
            float newPitch = MathHelper.clamp(oldPitch + dPitch, -90.0f, 90.0f);
            p.setYaw(p.getYaw() + dYaw);
            p.setPitch(newPitch);
            // Shift the "previous" values too, exactly like real mouse movement does. Without this the
            // renderer interpolates between the old and new angle and the screen jumps somewhere else.
            p.lastYaw += dYaw;
            p.lastPitch += newPitch - oldPitch;
            appliedYaw = yawOff;
            appliedPitch += newPitch - oldPitch;
        } catch (Throwable ignored) {
        }
    }

    private void finish() {
        if (flicking && mc.player != null) rotateTo(0.0f, 0.0f);
        flicking = false;
    }

    private boolean itemAllowed() {
        if (!allowSwords.isEnabled() && !allowFood.isEnabled() && !allowPotions.isEnabled()) return true;
        try {
            var stack = mc.player.getMainHandStack();
            if (stack.isEmpty()) return false;
            String path = net.minecraft.registry.Registries.ITEM.getId(stack.getItem()).getPath();
            if (allowSwords.isEnabled() && path.endsWith("_sword")) return true;
            if (allowFood.isEnabled() && stack.getItem().getComponents().contains(
                    net.minecraft.component.DataComponentTypes.FOOD)) return true;
            if (allowPotions.isEnabled() && path.contains("potion")) return true;
            return false;
        } catch (Throwable ignored) {
            return true;
        }
    }

    private boolean killAuraHasTarget() {
        try {
            KillAura ka = com.anormal.client.module.ModuleManager.getModule(KillAura.class);
            return ka != null && ka.isEnabled();
        } catch (Throwable ignored) {
            return false;
        }
    }
}
