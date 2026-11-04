package com.anormal.client.module.impl.kuro;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.module.ModuleManager;
import com.anormal.client.module.impl.kuro.KuroAttackPackets;
import com.anormal.client.module.impl.world.Freecam;
import com.anormal.client.mixin.ClientAccessor;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;

import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Map;

/**
 * Ported from kuro (dev.kuro.client.module.impl.FastCrystal).
 *
 * <ul>
 *   <li>Fast place: removes the 4-tick delay between right-clicks while you hold an end crystal.</li>
 *   <li>Instant break: hits a crystal the moment it appears (entity-spawn event), not a tick later,
 *       as long as your crosshair is on it (or any crystal in range with "Only when aimed at" off).
 *       A per-tick scan catches the rest.</li>
 * </ul>
 *
 * <p>Safety: each crystal is hit once and only retried after "Retry hit after", total hits are
 * capped per second, and the spawn hook swallows errors. If AutoDtap is also on, its extra taps
 * are added to every hit: use one or the other. The break range never exceeds your real entity
 * interaction range (so Reach extends it), because the server rejects hits from further away.</p>
 */
public class KuroFastCrystal extends Module {
    private static KuroFastCrystal instance;
    private final BooleanSetting fastPlace = new BooleanSetting("Fast place",
            "No delay between right-clicks while holding a crystal", true);
    private final BooleanSetting instantBreak = new BooleanSetting("Instant break",
            "Hit crystals the moment they appear", true);
    private final BooleanSetting aimedOnly = new BooleanSetting("Only when aimed at",
            "Only break crystals under the crosshair", true);
    private final NumberSetting range = new NumberSetting("Break range",
            "Maximum distance to a breakable crystal", 4.0, 1.0, 6.0, 0.5);
    private final NumberSetting placeDelay = new NumberSetting("Place delay (ticks)",
            "Right-click cooldown while holding a crystal (vanilla is 4)", 0.0, 0.0, 4.0, 1.0);
    private final NumberSetting retry = new NumberSetting("Retry hit after (ticks)",
            "A crystal still present after this long gets hit again", 6.0, 2.0, 20.0, 1.0);
    private final NumberSetting maxHits = new NumberSetting("Max hits per second",
            "Packet cap: too many packets gets you kicked or ignored", 20.0, 5.0, 40.0, 1.0);

    private final Map<Integer, Integer> hitAt = new HashMap<>();   // crystal id -> tick we hit it
    private int tick;
    private double budget;                                         // hits we may still send (refills every tick)
    private boolean registered;

    public KuroFastCrystal() {
        super("FastCrystal", "Fast crystal placement and instant breaking (kuro port)", Category.KURO);
        instance = this;
        addSetting(fastPlace);
        addSetting(instantBreak);
        addSetting(aimedOnly);
        addSetting(range);
        addSetting(placeDelay);
        addSetting(retry);
        addSetting(maxHits);
    }

    @Override
    public void onEnable() {
        hitAt.clear();
        budget = 1;
        registerSpawnHook();
    }

    @Override
    public void onDisable() { hitAt.clear(); }

    /** Fires KuroFastCrystal.onSpawn on every newly loaded client entity (idempotent). */
    private void registerSpawnHook() {
        if (registered) return;
        try {
            net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents.ENTITY_LOAD.register(
                    (e, world) -> onSpawn(e));
            registered = true;
        } catch (Throwable ignored) {
            // per-tick scan still catches every crystal
        }
    }

    @Override
    public void onTickAlways() {
        tick++;
        budget = Math.min(Math.max(2.0, maxHits.getValue() / 20.0 * 2), budget + maxHits.getValue() / 20.0);
        if (tick % 40 == 0) hitAt.values().removeIf(t -> tick - t > 60);          // forget old crystals
        if (!fastPlace.isEnabled() || mc.player == null || mc.currentScreen != null) return;
        if (mc.player.getMainHandStack().isOf(Items.END_CRYSTAL)
                || mc.player.getOffHandStack().isOf(Items.END_CRYSTAL)) {
            ClientAccessor acc = (ClientAccessor) mc;
            if (acc.getItemUseCooldown() > placeDelay.getValue().intValue())
                acc.setItemUseCooldown(placeDelay.getValue().intValue());
        }
    }

    @Override
    public void onTick() {
        if (!instantBreak.isEnabled() || !ready()) return;
        double r = reach();
        Box area = mc.player.getBoundingBox().expand(r + 1.0);
        for (EndCrystalEntity c : mc.world.getEntitiesByClass(EndCrystalEntity.class, area, Entity::isAlive)) tryBreak(c);
    }

    /** Called from the entity-spawn event, so a freshly placed crystal is hit in the same tick. */
    public static void onSpawn(Entity e) {
        // never let an exception escape into the game's entity handler
        try {
            if (instance != null && instance.isEnabled() && instance.instantBreak.isEnabled()
                    && e instanceof EndCrystalEntity c && instance.ready())
                instance.tryBreak(c);
        } catch (Throwable ignored) {}
    }

    private boolean ready() {
        Freecam freecam = ModuleManager.getModule(Freecam.class);
        boolean freecamActive = freecam != null && freecam.isEnabled();
        return mc.player != null && mc.world != null && mc.interactionManager != null
                && mc.currentScreen == null && !mc.player.isSpectator() && !freecamActive;
    }

    private double reach() { return Math.min(range.getValue(), mc.player.getEntityInteractionRange()); }

    private void tryBreak(EndCrystalEntity c) {
        if (!c.isAlive()) return;
        Integer last = hitAt.get(c.getId());
        if (last != null && tick - last < retry.getValue().intValue()) return;   // already hit; wait for the server
        if (budget < 1.0) return;                                                 // packet cap reached this tick
        double r = reach();
        Vec3d eye = mc.player.getEyePos();
        if (aimedOnly.isEnabled()) {
            Vec3d end = eye.add(mc.player.getRotationVec(1.0f).multiply(r));
            if (c.getBoundingBox().expand(c.getTargetingMargin()).raycast(eye, end).isEmpty()) return;
        } else {
            if (!KuroCombatReachFree(c, r, eye)) return;   // nearest point of the crystal is out of reach
        }
        hitAt.put(c.getId(), tick);
        budget -= 1.0;
        KuroAttackPackets.hit(c);
    }

    private static boolean KuroCombatReachFree(EndCrystalEntity c, double r, Vec3d eye) {
        Box b = c.getBoundingBox();
        double dx = Math.max(Math.max(b.minX - eye.x, 0), eye.x - b.maxX);
        double dy = Math.max(Math.max(b.minY - eye.y, 0), eye.y - b.maxY);
        double dz = Math.max(Math.max(b.minZ - eye.z, 0), eye.z - b.maxZ);
        return dx * dx + dy * dy + dz * dz <= r * r;
    }
}
