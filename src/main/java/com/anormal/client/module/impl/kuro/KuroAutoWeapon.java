package com.anormal.client.module.impl.kuro;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.module.ModuleManager;
import com.anormal.client.module.impl.client.Friends;
import com.anormal.client.module.impl.world.Freecam;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.kuro.KuroBlockPlacer;

import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;

/**
 * Ported from kuro (dev.kuro.client.module.impl.AutoWeapon), modelled on LiquidBounce's
 * AutoWeapon. It picks the best weapon in your hotbar for the target. The pick is a plain
 * function of the target's CURRENT state, so there is no multi-step sequence to get stuck in:
 *
 * <ul>
 *   <li>Auto shield break: the target would block this hit → axe (an axe hit disables the shield).</li>
 *   <li>Auto mace: you are falling far enough to smash → mace (Density from the set fall height, else Breach).</li>
 *   <li>Stun slam (on): falling AND the target is blocking → axe first; as soon as the shield is
 *       down the same rule picks the mace, so axe → mace happens by itself.</li>
 *   <li>Otherwise: the best of the preferred weapon types (Priority: 0 def / 1 kb / 2 dmg / 3 spd).</li>
 * </ul>
 *
 * <p>KuroTriggerBot / KuroAura-family modules call {@link #onTarget} when they have a target
 * (the slot is changed ahead of the hit), {@link #readyToAttack} (never swap and hit in the same
 * tick) and {@link #bypass} (axe-on-a-shield and mace smashes skip the normal charge wait).</p>
 */
public class KuroAutoWeapon extends Module {
    private final BooleanSetting preferSword = new BooleanSetting("Prefer sword", "Also uses swords for normal hits", true);
    private final BooleanSetting preferAxe = new BooleanSetting("Prefer axe", "Also uses axes for normal hits", false);
    private final BooleanSetting preferMace = new BooleanSetting("Prefer mace", "Also uses maces for normal hits", false);
    private final NumberSetting priority = new NumberSetting("Priority",
            "0 = default (sword → axe → mace), 1 = knockback, 2 = damage, 3 = speed", 0.0, 0.0, 3.0, 1.0);
    private final BooleanSetting autoShieldBreak = new BooleanSetting("Auto shield break",
            "Switch to an axe when the target would block the hit", true);
    private final BooleanSetting autoMace = new BooleanSetting("Auto mace",
            "Switch to a mace when you can smash", true);
    private final BooleanSetting stunSlam = new BooleanSetting("Stun slam (axe, then mace)",
            "Axe disables the shield, the mace hits right after", true);
    private final NumberSetting densityFrom = new NumberSetting("Density from fall (blocks)",
            "Fall height to prefer Density enchantment", 3.0, 0.0, 10.0, 0.1);
    private final BooleanSetting anyMace = new BooleanSetting("Use any mace if none",
            "Fallback to a mace without Density/Breach", true);
    private final NumberSetting switchBack = new NumberSetting("Switch back (ticks)",
            "Ticks without a target before restoring your slot", 20.0, 1.0, 300.0, 1.0);
    private final BooleanSetting changeOnTarget = new BooleanSetting("Change on target",
            "Swap as soon as a target is picked", true);
    private final BooleanSetting changeOnAttack = new BooleanSetting("Change on attack",
            "Swap right at the attack moment instead", false);
    private final BooleanSetting fromWeaponOnly = new BooleanSetting("Only from weapon",
            "Only manage the slot while already holding a weapon (or managing it)", true);
    private final NumberSetting followUpGap = new NumberSetting("Follow-up gap (ticks)",
            "Minimum ticks between two managed hits", 1.0, 1.0, 10.0, 1.0);
    private final NumberSetting assumeDown = new NumberSetting("Assume shield down after axe (ticks, 0 = off)",
            "Client learns the shield dropped a ping late; assume it earlier", 4.0, 0.0, 10.0, 1.0);
    private final BooleanSetting debug = new BooleanSetting("Debug (chat log)",
            "Print the state changes to chat", false);

    private static KuroAutoWeapon instance;

    private int clientTick;
    private int swapBackSlot = -1;     // the slot to go back to (-1 = nothing to restore)
    private int ourSlot = -1;          // the slot we selected last; if you scroll away from it we stop managing
    private int restoreAt;
    private int swapTick = -10;
    private int lastAttackTick = -100;
    private int axedId = -1;           // the player our last stun-slam axe hit went to
    private int axedTick = -100;       // shield counts as down for "Assume shield down" ticks after it
    private int axeStreak;             // stun-slam axe hits in this fall; two without the shield dropping = go to the mace

    // After a smash the server resets your fall distance. The client value stays high, so we track our own:
    // falling again is measured from where the last smash happened, until you touch the ground.
    private boolean smashSpent;
    private double spentY;
    private boolean wasSwinging;

    public KuroAutoWeapon() {
        super("AutoWeapon", "Picks the best weapon in your hotbar for the target (kuro port)", Category.KURO);
        instance = this;
        addSetting(preferSword);
        addSetting(preferAxe);
        addSetting(preferMace);
        addSetting(priority);
        addSetting(autoShieldBreak);
        addSetting(autoMace);
        addSetting(stunSlam);
        addSetting(densityFrom);
        addSetting(anyMace);
        addSetting(switchBack);
        addSetting(changeOnTarget);
        addSetting(changeOnAttack);
        addSetting(fromWeaponOnly);
        addSetting(followUpGap);
        addSetting(assumeDown);
        addSetting(debug);
    }

    private static boolean freecamActive() {
        Freecam f = ModuleManager.getModule(Freecam.class);
        return f != null && f.isEnabled();
    }

    @Override
    public void onEnable() { resetState(); }

    @Override
    public void onDisable() {
        if (swapBackSlot >= 0 && mc.player != null && KuroBlockPlacer.selected() == ourSlot)
            KuroBlockPlacer.selectForce(swapBackSlot);
        resetState();
    }

    private void resetState() {
        swapBackSlot = -1;
        ourSlot = -1;
        swapTick = -10;
        lastAttackTick = -100;
        axeStreak = 0;
        axedId = -1;
        axedTick = -100;
        smashSpent = false;
    }

    // -------------------------------------------------------------------------
    // Public API for KuroTriggerBot / aura callers
    // -------------------------------------------------------------------------

    /** Charge of the item you hold (0..1). */
    public static float progress() {
        return mc.player == null ? 0f : mc.player.getAttackCooldownProgress(0.5f);
    }

    /** Call when the target is known (before the hit): changes to the right weapon now if "Change on target" is on. */
    public static void onTarget(Entity target) {
        KuroAutoWeapon m = instance;
        if (m == null || !m.isEnabled() || mc.player == null || !m.changeOnTarget.isEnabled()) return;
        if (!(target instanceof LivingEntity) || mc.currentScreen != null || freecamActive()) return;
        if (m.busy() || !m.heldAllowed()) return;
        m.applySlot(m.determineSlot(target));
    }

    /** False in the tick a slot change was made: the hit waits one tick so the slot and the attack never share a tick. */
    public static boolean readyToAttack() {
        KuroAutoWeapon m = instance;
        return m == null || !m.isEnabled() || m.clientTick != m.swapTick;
    }

    /**
     * True when the right tool for this target is ALREADY in your hand and the hit is a shield
     * break or a mace smash: the caller may skip its usual charge / delay wait. Spaced by
     * "Follow-up gap" so it is one hit, not a spam.
     */
    public static boolean bypass(Entity target) {
        KuroAutoWeapon m = instance;
        if (m == null || !m.isEnabled() || mc.player == null || !(target instanceof LivingEntity)) return false;
        if (m.clientTick - m.lastAttackTick < m.followUpGap.getValue().intValue()) return false;
        ItemStack held = mc.player.getMainHandStack();
        if (m.determineSlot(target) != KuroBlockPlacer.selected()) return false;
        if (isMace(held)) return m.autoMace.isEnabled() && m.canSmash();
        if (isAxe(held)) {
            // a second axe hit on the same player waits long enough for the client to see the shield state
            if (m.axedId == target.getId() && m.clientTick - m.lastAttackTick < Math.max(m.followUpGap.getValue().intValue(), 5))
                return false;
            return m.autoShieldBreak.isEnabled() && target instanceof PlayerEntity p && m.blockingNow(p);
        }
        return false;
    }

    /**
     * True when the weapon this target calls for is a shield-break axe or a mace smash, whether
     * or not it is in your hand yet. Callers use it to start aiming early; the hit itself still
     * goes through {@link #bypass}.
     */
    public static boolean wantsStep(Entity target) {
        KuroAutoWeapon m = instance;
        if (m == null || !m.isEnabled() || mc.player == null || !(target instanceof LivingEntity)) return false;
        int slot = m.determineSlot(target);
        if (slot < 0) return false;
        ItemStack s = mc.player.getInventory().getStack(slot);
        if (isMace(s)) return m.autoMace.isEnabled() && m.canSmash();
        if (isAxe(s)) return m.autoShieldBreak.isEnabled() && target instanceof PlayerEntity p && m.blockingNow(p);
        return false;
    }

    /** An axe hit on a blocking target is about to be followed by the mace hit (stun slam): callers keep their aim. */
    public static boolean followUpExpected(Entity target) {
        KuroAutoWeapon m = instance;
        if (m == null || !m.isEnabled() || mc.player == null || !m.stunSlam.isEnabled() || !m.autoMace.isEnabled())
            return false;
        return isAxe(mc.player.getMainHandStack()) && target instanceof PlayerEntity p && m.blockingNow(p)
                && m.canSmash() && KuroBlockPlacer.findSlot(KuroAutoWeapon::isMace) >= 0;
    }

    /** True when the next hit can be a mace smash and a mace is in the hotbar. */
    public static boolean willMaceSmash() {
        KuroAutoWeapon m = instance;
        return m != null && m.isEnabled() && mc.player != null && m.autoMace.isEnabled() && m.canSmash()
                && KuroBlockPlacer.findSlot(KuroAutoWeapon::isMace) >= 0;
    }

    /** True while AutoWeapon is on with Auto shield break (KuroTriggerBot's shield check backs off then). */
    public static boolean shieldBreakActive() {
        return (instance != null && instance.isEnabled() && instance.autoShieldBreak.isEnabled())
                || KuroAutoShieldBreak.active();
    }

    // -------------------------------------------------------------------------
    // Ticks
    // -------------------------------------------------------------------------

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;
        clientTick++;
        if (mc.player.isOnGround()) { smashSpent = false; axeStreak = 0; axedId = -1; }

        // Attack moment (the swing edge stands in for kuro's AttackEntityCallback)
        boolean attacking = mc.currentScreen == null && mc.options.attackKey.isPressed()
                && mc.crosshairTarget instanceof EntityHitResult hit
                && hit.getType() == HitResult.Type.ENTITY;
        boolean swingEdge = attacking && mc.player.handSwinging && !wasSwinging;
        wasSwinging = mc.player.handSwinging;
        if (swingEdge) {
            Entity entity = ((EntityHitResult) mc.crosshairTarget).getEntity();
            if (entity instanceof PlayerEntity p && p != mc.player
                    && p.isAlive() && !Friends.isFriend(p.getName().getString())) {
                onAttack(p);
            }
        }

        // Playing by hand: while you hold attack on a player, get the weapon ready.
        if (changeOnTarget.isEnabled() && mc.currentScreen == null && mc.options.attackKey.isPressed()
                && !freecamActive()
                && mc.crosshairTarget instanceof EntityHitResult hit && hit.getType() == HitResult.Type.ENTITY
                && hit.getEntity() instanceof PlayerEntity p && p != mc.player && p.isAlive()
                && !Friends.isFriend(p.getName().getString())) {
            onTarget(p);
        }

        // Back to the slot we came from once nothing has needed the weapon for "Switch back" ticks.
        if (swapBackSlot >= 0 && clientTick >= restoreAt && !busy()) {
            if (KuroBlockPlacer.selected() != ourSlot) {
                swapBackSlot = -1;                       // you changed slot yourself: leave it alone
            } else if (KuroBlockPlacer.select(swapBackSlot)) {
                dbg("back to slot " + swapBackSlot);
                swapBackSlot = -1;                       // refused (one swap per tick) = retried next tick
            }
        }
    }

    /** Runs at the detected attack moment, before the game sends the slot + attack packets. */
    private void onAttack(Entity target) {
        if (mc.player == null) return;
        if (changeOnAttack.isEnabled() && !busy() && heldAllowed()) applySlot(determineSlot(target));

        ItemStack held = mc.player.getMainHandStack();
        lastAttackTick = clientTick;
        if (swapBackSlot >= 0) restoreAt = clientTick + switchBack.getValue().intValue();
        if (isMace(held) && canSmash()) {
            markSmash();
            axeStreak = 0;
            axedId = -1;
        } else if (isAxe(held) && target instanceof PlayerEntity p && wouldBlockHit(p) && canSmash()) {
            axeStreak++;
            axedId = p.getId();          // shield is going down: do not wait a whole ping for the client to see it
            axedTick = clientTick;
        }
    }

    // -------------------------------------------------------------------------
    // Weapon choice
    // -------------------------------------------------------------------------

    private int determineSlot(Entity target) {
        if (mc.player == null) return -1;
        PlayerEntity p = target instanceof PlayerEntity pe ? pe : null;
        boolean smash = autoMace.isEnabled() && canSmash();
        boolean blocks = autoShieldBreak.isEnabled() && p != null && blockingNow(p);
        // No smash available: a blocking target gets the axe. Smash available: the mace, unless stun
        // slam is on, in which case the axe goes first until the shield is down (two failed axe hits = stop trying).
        boolean axeFirst = blocks && (!smash || (stunSlam.isEnabled() && axeStreak < 2));

        if (axeFirst) {
            int axe = KuroBlockPlacer.findSlot(KuroAutoWeapon::isAxe);
            if (axe >= 0) return axe;
        }
        if (smash) {
            int mace = pickMaceSlot();
            if (mace >= 0) return mace;
        }
        return pickPreferred();
    }

    private int pickMaceSlot() {
        boolean deep = effectiveFall() >= densityFrom.getValue();
        var want = deep ? Enchantments.DENSITY : Enchantments.BREACH;
        int slot = KuroBlockPlacer.findSlot(s -> isMace(s) && level(s, want) > 0);
        if (slot < 0 && anyMace.isEnabled()) slot = KuroBlockPlacer.findSlot(KuroAutoWeapon::isMace);
        return slot;
    }

    /** Best hotbar item of the preferred types by Priority. The item already in hand wins ties (no pointless swaps). */
    private int pickPreferred() {
        var inv = mc.player.getInventory();
        int cur = KuroBlockPlacer.selected();
        int best = -1;
        double bestScore = -1;
        ItemStack held = inv.getStack(cur);
        if (preferred(held)) { best = cur; bestScore = score(held); }
        for (int i = 0; i < 9; i++) {
            ItemStack s = inv.getStack(i);
            if (i == cur || s.isEmpty() || !preferred(s)) continue;
            double sc = score(s);
            if (sc > bestScore) { bestScore = sc; best = i; }
        }
        return best;
    }

    private boolean preferred(ItemStack s) {
        if (s.isEmpty()) return false;
        return (preferSword.isEnabled() && isSword(s)) || (preferAxe.isEnabled() && isAxe(s))
                || (preferMace.isEnabled() && isMace(s));
    }

    private double score(ItemStack s) {
        double dmg = baseDamage(s);
        return switch (priority.getValue().intValue()) {
            case 1 -> level(s, Enchantments.KNOCKBACK) * 1000.0 + dmg;
            case 2 -> dmg;
            case 3 -> attackSpeedOf(s) * 1000.0 + dmg;
            default -> typeRank(s) * 1000.0 + dmg;       // sword, then axe, then mace
        };
    }

    private static int typeRank(ItemStack s) { return isSword(s) ? 3 : isAxe(s) ? 2 : isMace(s) ? 1 : 0; }

    /** Applies the choice: one swap per tick (KuroSwapLimit). Remembers where to go back to. */
    private boolean applySlot(int slot) {
        if (slot < 0 || mc.player == null) return false;
        int cur = KuroBlockPlacer.selected();
        if (slot == cur) {
            if (swapBackSlot >= 0) restoreAt = clientTick + switchBack.getValue().intValue();
            return true;
        }
        if (!KuroBlockPlacer.select(slot)) return false;
        if (swapBackSlot < 0) swapBackSlot = cur;
        ourSlot = slot;
        swapTick = clientTick;
        restoreAt = clientTick + switchBack.getValue().intValue();
        dbg("slot " + cur + " -> " + slot + " (" + mc.player.getInventory().getStack(slot).getItem() + ")");
        return true;
    }

    /** Eating, drinking, drawing a bow...: never take the slot away. */
    private boolean busy() {
        return mc.player != null && mc.player.isUsingItem() && mc.player.getActiveHand() == Hand.MAIN_HAND;
    }

    private boolean heldAllowed() {
        return !fromWeaponOnly.isEnabled() || swapBackSlot >= 0 || isWeapon(mc.player.getMainHandStack());
    }

    // -------------------------------------------------------------------------
    // Smash and shield rules
    // -------------------------------------------------------------------------

    private double effectiveFall() {
        if (mc.player == null) return 0.0;
        return smashSpent ? Math.max(0.0, spentY - mc.player.getY()) : mc.player.fallDistance;
    }

    /** Vanilla MaceItem.canSmashAttack / LiquidBounce's canMaceSmash: fallen more than 1.5 blocks and not gliding. */
    private boolean canSmash() {
        return mc.player != null && !mc.player.isOnGround() && !mc.player.isGliding() && effectiveFall() > 1.5;
    }

    private void markSmash() {
        smashSpent = true;
        spentY = mc.player.getY();
        dbg("smash used, need to fall >1.5 again");
    }

    /**
     * wouldBlockHit, except that for "Assume shield down" ticks after our own stun-slam axe hit on
     * this player the shield counts as already down.
     */
    private boolean blockingNow(PlayerEntity p) {
        if (!wouldBlockHit(p)) return false;
        int w = assumeDown.getValue().intValue();
        return !(w > 0 && axedId == p.getId() && clientTick - axedTick <= w);
    }

    /**
     * Vanilla shield check (LivingEntity.blockedByShield): the shield blocks when the attacker is
     * in front of the holder's HORIZONTAL facing. Height is ignored, so a target you are falling
     * onto from above is judged exactly like the server will judge it.
     */
    public static boolean wouldBlockHit(PlayerEntity p) {
        if (mc.player == null || !p.isBlocking()) return false;
        Vec3d facing = Vec3d.fromPolar(0.0f, p.getHeadYaw());
        Vec3d fromAttacker = p.getEntityPos().subtract(mc.player.getEntityPos());
        Vec3d flat = new Vec3d(fromAttacker.x, 0.0, fromAttacker.z).normalize();
        return flat.dotProduct(facing) < 0.0;
    }

    // -------------------------------------------------------------------------
    // Item helpers
    // -------------------------------------------------------------------------

    private static boolean isSword(ItemStack s) { return !s.isEmpty() && s.isIn(ItemTags.SWORDS); }
    private static boolean isAxe(ItemStack s) { return !s.isEmpty() && s.isIn(ItemTags.AXES); }
    private static boolean isMace(ItemStack s) { return !s.isEmpty() && s.isOf(Items.MACE); }
    private static boolean isWeapon(ItemStack s) { return isSword(s) || isAxe(s) || isMace(s) || s.isOf(Items.TRIDENT); }

    private static int level(ItemStack s, net.minecraft.registry.RegistryKey<net.minecraft.enchantment.Enchantment> key) {
        ItemEnchantmentsComponent enc = s.getEnchantments();
        for (var e : enc.getEnchantments()) {
            if (e.matchesKey(key)) return enc.getLevel(e);
        }
        return 0;
    }

    /** Vanilla base melee damage by item (used for Priority: damage). */
    private static double baseDamage(ItemStack s) {
        var i = s.getItem();
        if (i == Items.NETHERITE_SWORD) return 8;
        if (i == Items.DIAMOND_SWORD) return 7;
        if (i == Items.IRON_SWORD) return 6;
        if (i == Items.STONE_SWORD) return 5;
        if (i == Items.GOLDEN_SWORD || i == Items.WOODEN_SWORD) return 4;
        if (isSword(s)) return 5;
        if (i == Items.NETHERITE_AXE) return 10;
        if (i == Items.WOODEN_AXE || i == Items.GOLDEN_AXE) return 7;
        if (isAxe(s)) return 9;
        if (isMace(s)) return 6;
        if (i == Items.TRIDENT) return 9;
        return 1;
    }

    /** Vanilla attack speed by item (used for Priority: speed). */
    private static double attackSpeedOf(ItemStack s) {
        var i = s.getItem();
        if (isSword(s)) return 1.6;
        if (i == Items.WOODEN_AXE || i == Items.STONE_AXE) return 0.8;
        if (i == Items.IRON_AXE) return 0.9;
        if (isAxe(s)) return 1.0;
        if (isMace(s)) return 0.6;
        if (i == Items.TRIDENT) return 1.1;
        return 4.0;
    }

    @SuppressWarnings("unused")
    private static EquipmentSlot handSlot(Hand h) {
        return h == Hand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
    }

    private void dbg(String msg) {
        if (!debug.isEnabled() || mc.player == null) return;
        mc.player.sendMessage(Text.literal("§7[weapon] §f" + msg), false);
    }
}
