package com.anormal.client.module.impl.kuro;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.InputForcer;
import com.anormal.client.util.NotificationManager;
import com.anormal.client.util.kuro.KuroBlockPlacer;
import com.anormal.client.util.kuro.KuroFreecam;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/**
 * Ported from kuro (dev.kuro.client.module.impl.ElytraHop).
 *
 * <p>Hop-glide-hop along the ground, with a spear jab just before every landing to keep the speed.
 * Wear an elytra, keep a spear in the hotbar, switch it on and hold W:</p>
 * <ol>
 *   <li>jump,</li>
 *   <li>release jump for one tick and press it again in the air (that is what starts the glide),</li>
 *   <li>keep jump held; a moment before you touch the ground the spear is selected and jabbed
 *       (the lunge pushes you along),</li>
 *   <li>your old slot comes back, you land and jump again.</li>
 * </ol>
 *
 * <p>The landing is predicted from the height above the ground and your fall speed. "Jab lead" is
 * how many ticks before touching down the jab goes out; the spear is taken one tick earlier.</p>
 *
 * <p>Runs in {@code onPreTick} exactly like kuro's {@code onPreTick}. The jump key is driven through
 * {@link InputForcer} (it survives vanilla's key state refresh, kuro only needs
 * {@code KeyMapping.setDown}), the release is kuro's {@code KeyMapping.setAll} as
 * {@code KeyBinding.updatePressedStates()}, and the jab is the same real left click kuro sends
 * ({@code KeyBinding.onKeyPressed} with the bound attack key).</p>
 */
public class KuroElytraHop extends Module {
    private final NumberSetting lead = new NumberSetting("Jab lead (ticks)",
            "Ticks before touchdown the jab goes out", 2.0, 0.0, 6.0, 0.5);
    private final NumberSetting minHunger = new NumberSetting("Min hunger (Lunge)",
            "Skip the jab below this hunger when the spear has Lunge", 6, 0, 20, 1);
    private final NumberSetting dive = new NumberSetting("Dive pitch (0 = off)",
            "Look down while gliding so you come back to the ground quickly", 0, 0, 60, 5);
    private final NumberSetting jabPitch = new NumberSetting("Jab pitch",
            "Pitch used for the jab (the lunge goes where you look)", 5, -30, 30, 5);
    private final BooleanSetting needForward = new BooleanSetting("Only while holding W",
            "Do nothing unless you hold the forward key", true);

    private boolean pressedByUs, released, armed, jabbed, warned;
    private int restoreSlot = -1, restoreIn;

    public KuroElytraHop() {
        super("Elytra Hop", "Hop-glide-hop along the ground with a spear jab (kuro port)", Category.KURO);
        addSetting(lead);
        addSetting(minHunger);
        addSetting(dive);
        addSetting(jabPitch);
        addSetting(needForward);
    }

    @Override
    public void onEnable() {
        resetAll();
        warned = false;
    }

    @Override
    public void onDisable() {
        if (restoreSlot >= 0 && mc.player != null) KuroBlockPlacer.selectForce(restoreSlot);
        releaseJump();
        resetAll();
    }

    private void resetAll() {
        released = false;
        armed = false;
        jabbed = false;
        restoreSlot = -1;
        restoreIn = 0;
    }

    private void releaseJump() {
        if (!pressedByUs) return;
        pressedByUs = false;
        InputForcer.jump = false;
        try {
            KeyBinding.updatePressedStates();          // kuro: KeyMapping.setAll()
        } catch (Throwable ignored) {
        }
    }

    private void holdJump(boolean down) {
        InputForcer.jump = down;
        if (mc.options != null) mc.options.jumpKey.setPressed(down);
        pressedByUs = down || pressedByUs;
    }

    // ------------------------------------------------------------------ the loop

    @Override
    public void onPreTick() {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null || mc.interactionManager == null || mc.currentScreen != null
                || KuroFreecam.active()) {
            releaseJump();
            return;
        }

        handleRestore(p);

        ItemStack chest = p.getEquippedStack(EquipmentSlot.CHEST);
        if (!chest.isOf(Items.ELYTRA) || chest.getMaxDamage() - chest.getDamage() <= 2) {
            releaseJump();
            if (!warned) {
                warned = true;
                NotificationManager.info("Elytra Hop", "Wear an elytra with durability left");
            }
            return;
        }
        if (needForward.isEnabled() && !mc.options.forwardKey.isPressed()) {
            releaseJump();
            return;
        }
        if (p.isTouchingWater() || p.isInLava() || p.isClimbing() || p.isSneaking()) {
            releaseJump();
            return;
        }

        if (p.isOnGround()) {
            // Landed (or standing): hop again.
            if (armed || jabbed) finishJab();
            armed = false;
            jabbed = false;
            released = false;
            holdJump(true);
            return;
        }

        if (!p.isGliding()) {
            // In the air after the hop and not gliding yet: a glide needs a fresh jump press.
            if (!released) {
                holdJump(false);
                released = true;
            } else {
                holdJump(true);
            }
            return;
        }

        holdJump(true);                                   // gliding: keep jump held until the ground
        glideStep(p);
    }

    private void glideStep(ClientPlayerEntity p) {
        Vec3d v = p.getVelocity();
        double h = heightAboveGround(p);
        double t = v.y < -0.02 ? h / -v.y : 99.0;          // ticks until touchdown at the current fall speed

        boolean near = t <= lead.getValue() + 1.0;
        if (dive.getValue() > 0) {
            float want = near ? jabPitch.getValue().floatValue() : dive.getValue().floatValue();
            p.setPitch(MathHelper.clamp(p.getPitch() + (want - p.getPitch()) * 0.35f, -90f, 90f));
        }
        if (jabbed) return;

        if (near && !armed) {
            arm(p);
            return;
        }
        if (armed && t <= lead.getValue()) jab(p);
    }

    /** Takes the spear into the hand (one swap per tick). */
    private void arm(ClientPlayerEntity p) {
        ItemStack held = p.getMainHandStack();
        if (KuroSpearSwap.isSpear(held)) {
            armed = true;
            return;
        }
        int slot = findSpear();
        if (slot < 0) {
            if (!warned) {
                warned = true;
                NotificationManager.info("Elytra Hop", "No spear in your hotbar");
            }
            jabbed = true;                                // nothing to jab with: skip it for this hop
            return;
        }
        int cur = KuroBlockPlacer.selected();
        if (!KuroBlockPlacer.select(slot)) return;        // refused this tick: try again next tick
        restoreSlot = cur;
        armed = true;
    }

    private void jab(ClientPlayerEntity p) {
        ItemStack held = p.getMainHandStack();
        if (!KuroSpearSwap.isSpear(held)) {
            armed = false;                                // the swap did not stick: arm again
            return;
        }
        if (hasLunge(held) && p.getHungerManager().getFoodLevel() < minHunger.getValue().intValue()) {
            if (!warned) {
                warned = true;
                NotificationManager.info("Elytra Hop", "Too hungry for the lunge");
            }
            jabbed = true;
            restoreIn = 1;
            return;
        }
        // A real left click, handled by vanilla like your own click.
        KeyBinding.onKeyPressed(KeyBindingHelper.getBoundKeyOf(mc.options.attackKey));
        jabbed = true;
        restoreIn = 1;                                    // slot goes back next tick, after the click
    }

    private void handleRestore(ClientPlayerEntity p) {
        if (restoreSlot < 0) return;
        if (!jabbed && !p.isOnGround()) return;           // still waiting for the jab
        if (restoreIn > 0) {
            restoreIn--;
            if (restoreIn > 0) return;
        }
        if (KuroBlockPlacer.select(restoreSlot)) restoreSlot = -1;   // refused: retried next tick
    }

    private void finishJab() {
        restoreIn = 0;
        if (restoreSlot >= 0 && KuroBlockPlacer.select(restoreSlot)) restoreSlot = -1;
    }

    // ------------------------------------------------------------------ helpers

    /** Distance from your feet straight down to the first solid block (99 when there is none within 12 blocks). */
    private double heightAboveGround(ClientPlayerEntity p) {
        Vec3d from = new Vec3d(p.getX(), p.getY(), p.getZ());
        Vec3d to = from.add(0, -12, 0);
        var hit = mc.world.raycast(new RaycastContext(from, to, RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE, p));
        if (hit.getType() == net.minecraft.util.hit.HitResult.Type.MISS) return 99.0;
        return Math.max(0.0, from.y - hit.getPos().y);
    }

    private int findSpear() {
        int slot = KuroBlockPlacer.findSlot(s -> KuroSpearSwap.isSpear(s) && hasLunge(s));
        return slot >= 0 ? slot : KuroBlockPlacer.findSlot(KuroSpearSwap::isSpear);
    }

    private static boolean hasLunge(ItemStack s) {
        ItemEnchantmentsComponent enc = s.getEnchantments();
        for (var e : enc.getEnchantments()) {
            if (e.matchesKey(Enchantments.LUNGE)) return true;
        }
        return false;
    }
}
