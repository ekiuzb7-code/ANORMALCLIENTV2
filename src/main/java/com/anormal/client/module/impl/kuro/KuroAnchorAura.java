package com.anormal.client.module.impl.kuro;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.kuro.KuroAnchorAimSearch;
import com.anormal.client.util.kuro.KuroBlockPlacer;
import com.anormal.client.util.kuro.KuroBlockUtils;
import com.anormal.client.util.kuro.KuroExplosionUtils;
import com.anormal.client.util.kuro.KuroInventoryUtils;
import com.anormal.client.util.kuro.KuroInputHandler;
import com.anormal.client.util.kuro.KuroRotations;
import com.anormal.client.util.kuro.KuroRotationConvergenceTracker;
import com.anormal.client.util.kuro.KuroSwapStateManager;
import com.anormal.client.util.kuro.KuroTargetUtils;

import net.minecraft.client.util.InputUtil;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.block.Blocks;
import net.minecraft.block.RespawnAnchorBlock;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Kuro Anchor Aura port (dev.kuro.client.module.impl.AnchorAura'dan Anormal'da).
 *
 * <p>echo "Auto Anchor"ning porti: eng yaqin nishon yaqinidagi joyga anchor qo'yadi,
 * glowstone bilan zaryadlaydi va portlaydi. Ixtiyoriy safety-blok qadamida
 * zaryadlashdan oldingiz va anchor o'rtasiga glowstone tushadi va o'z portlash
 * zararingizni kamaytiradi.</p>
 *
 * <ul>
 *   <li>{@link KuroAnchorAimSearch} anchorning raycast bilan toza o'q bo'ladigan aim nuqtasini topadi
 *       (qo'shni blok ko'z-yuzasini to'sib qo'yganda ham ishlaydi).</li>
 *   <li>{@link KuroRotationConvergenceTracker} har tick'dagi yaw/pitch deltani kuzatadi,
 *       interaksiya faqat Grim DuplicateRotPlace check yopilgan tickda o'tadi.</li>
 *   <li>{@link KuroSwapStateManager} hotbar swapini boshqaradi va restore qiladi.</li>
 *   <li>Aim: "Auto Aim" yiqilgan payt orqali kameraga eng yaqin interaksiyadagi nuqtaga
 *       qaraydi. "Silent Aim" da aim faqat server tomonda (KuroRotations), kamerangiz ko'rinmaydi.</li>
 *   <li>"Can Place Legit" vazifasi: qo'yish uchun ray Yoshio bilan aniq bo'lgan yuzadan klik kerak.</li>
 *   <li>"Safe Anchor" zaryadlashdan oldingiz samoviy blok (glowstone / nima hotbar'dagi blok)
 *       bilan siz va anchor orasiga qiladi. Timeout bilan (ms).</li>
 * </ul>
 */
public class KuroAnchorAura extends Module {

    private static final int VANILLA_PLACE_COOLDOWN_TICKS = 4;
    private static final int MAX_ANCHOR_CHARGES = 4;
    private static final float ROTATION_SETTLE_THRESHOLD = 1.7f;
    /** Aim shu burchak ostida qolsa "qaradi" deb hisoblanadi (gradus). */
    private static final float AIM_TOLERANCE = 0.6f;
    /** {@code Entity#changeLookDirection} sichqoncha deltasini shu koeffitsiyent bilan gradusga aylantiradi. */
    private static final double MOUSE_UNIT = 0.15;

    private enum AnchorStep { PLACE_SAFETY, CHARGE, EXPLODE }

    // -- Settings --

    private final NumberSetting mode = new NumberSetting("Mode", "0 = Click, 1 = Hold", 0.0, 0.0, 1.0, 1.0);
    private final NumberSetting activateKeyCode = new NumberSetting("Activate Key (code)", "-1 = none, 0..399 keyboard, 1000+ mouse button +1000", -1.0, -1.0, 1007.0, 1.0);
    private final BooleanSetting repeat = new BooleanSetting("Repeat", "Keep anchoring while the key is held", true);
    private final BooleanSetting autoAim = new BooleanSetting("Auto Aim", "Aim at the anchor for you", true);
    private final BooleanSetting silentAim = new BooleanSetting("Silent Aim", "Aim server-side only", false);
    private final NumberSetting aimSpeed = new NumberSetting("Aim Speed", "Silent aim turn speed (deg/tick)", 120.0, 0.0, 600.0, 1.0);
    private final BooleanSetting canPlaceLegit = new BooleanSetting("Can Place Legit", "Only placements a real click could also do", true);
    private final BooleanSetting safeAnchor = new BooleanSetting("Safe Anchor", "Safety block at your feet before charging", false);
    private final NumberSetting safetyTimeout = new NumberSetting("Safety Timeout (ms)", "Give up waiting for the safety block", 200.0, 0.0, 2000.0, 10.0);
    private final NumberSetting minDamage = new NumberSetting("Min Damage", "Minimum self damage worth a safety block", 6.0, 0.0, 20.0, 0.5);
    private final Range charges = new Range("Charges", 1, 1);
    private final Range chargeDelay = new Range("Charge Delay (ticks)", 1, 1);
    private final NumberSetting explodeSlot = new NumberSetting("Explode Slot", "Hotbar slot to hold while exploding (1-9)", 1.0, 1.0, 9.0, 1.0);
    private final Range explodeDelay = new Range("Explode Delay (ticks)", 1, 1);

    // -- State --

    private boolean clickQueued;
    private boolean keyWasDown;
    private boolean wasAiming;
    private boolean restoringSwap;
    private boolean holdCycleCompleted;
    private int placeCooldown;
    private AnchorStep anchorStep;
    private BlockPos activeAnchorPos;
    private BlockPos pendingUseKeyAnchorPos;
    private int remainingCharges;
    private int nextAnchorActionTick;
    private Vec3d cachedAnchorAim;
    private long safetyStepStartMillis;

    /** RangeSetting: min va max sliderlar, orasidagi tasodifiy qiymat. */
    private final class Range {
        final NumberSetting min, max;

        Range(String name, float def, float lower) {
            min = new NumberSetting(name + " min", "Range lower bound", lower, 0.0, 20.0, 1.0);
            max = new NumberSetting(name + " max", "Range upper bound", def, 0.0, 20.0, 1.0);
        }

        float getRandom() {
            float lo = (float) Math.min(min.getAsDouble(), max.getAsDouble());
            float hi = (float) Math.max(min.getAsDouble(), max.getAsDouble());
            return (float) (Math.random() * (hi - lo)) + lo;
        }
    }

    public KuroAnchorAura() {
        super("Anchor Aura", "Place, charge and detonate an anchor at the closest target automatically (kuro port)", Category.COMBAT);
        addSetting(mode);
        addSetting(activateKeyCode);
        addSetting(repeat);
        addSetting(autoAim);
        addSetting(silentAim);
        addSetting(aimSpeed);
        addSetting(canPlaceLegit);
        addSetting(safeAnchor);
        addSetting(safetyTimeout);
        addSetting(minDamage);
        addSetting(charges.min); addSetting(charges.max);
        addSetting(chargeDelay.min); addSetting(chargeDelay.max);
        addSetting(explodeSlot);
        addSetting(explodeDelay.min); addSetting(explodeDelay.max);
    }

    private boolean isHold() {
        return mode.getValue() > 0.5;
    }

    /** -1 = none, 0..399 = keyboard, 1000+n = mouse button n (1000;left, 1001;right, 1002;middle, 1003;MOUSE4...). */
    private int activateBind() {
        int code = (int) activateKeyCode.getAsDouble();
        if (code < 0) return -1;
        if (code >= 1000) return 0x80000000 | (code - 1000);
        return code;
    }

    // -- Lifecycle --

    @Override
    public void onEnable() {
        clickQueued = false;
        keyWasDown = false;
        wasAiming = false;
        restoringSwap = false;
        holdCycleCompleted = false;
        placeCooldown = 0;
        KuroRotationConvergenceTracker.reset();
        resetAnchorSequence();
    }

    @Override
    public void onDisable() {
        clickQueued = false;
        keyWasDown = false;
        wasAiming = false;
        restoringSwap = false;
        holdCycleCompleted = false;
        placeCooldown = 0;
        resetAnchorSequence();
        KuroSwapStateManager.cancel(this, true);
        KuroRotations.release(this);
    }

    // -- Tick --

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;

        if (placeCooldown > 0) placeCooldown--;
        if (!KuroSwapStateManager.isOwnerActive(this)) restoringSwap = false;

        if (mc.currentScreen != null) {
            clickQueued = false;
            resetAnchorSequence();
            handleInactive();
            keyWasDown = false;
            return;
        }

        boolean keyDown = KuroInputHandler.isBindDown(activateBind());
        boolean holdMode = isHold();
        if (holdMode && !keyDown) holdCycleCompleted = false;

        if (!holdMode && keyDown && !keyWasDown && !isHoldingRespawnAnchor()) {
            clickQueued = true;
            if (!isAnchorSequenceActive()) {
                restoringSwap = false;
                placeCooldown = 0;
            }
        }

        boolean active = holdMode ? keyDown : clickQueued || isAnchorSequenceActive();
        if (!active) {
            resetAnchorSequence();
            handleInactive();
            keyWasDown = keyDown;
            return;
        }

        if (holdMode && !repeat.isEnabled() && holdCycleCompleted && !isAnchorSequenceActive()) {
            handleInactive();
            keyWasDown = keyDown;
            return;
        }

        if (!isAnchorSequenceActive() && !isHoldingRespawnAnchor() && !clickQueued) {
            if (!KuroSwapStateManager.isOwnerActive(this)) handleInactive();
            keyWasDown = keyDown;
            return;
        }

        if (isAnchorSequenceActive()) {
            continueAnchorSequence();
            keyWasDown = keyDown;
            return;
        }

        // Qolgan shaxsiy holat: reja qilib anchor qo'yamiz
        BlockPos existingAnchor = consumePendingUseKeyAnchorPos();
        if (existingAnchor == null) {
            BlockHitResult hit = currentBlockHit();
            if (hit != null && isRespawnAnchor(hit.getBlockPos())) existingAnchor = hit.getBlockPos();
        }
        if (existingAnchor != null) {
            if (mc.player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(existingAnchor)) > Math.pow(mc.player.getBlockInteractionRange(), 2)) {
                if (!holdMode) clickQueued = false;
                handleInactive();
                keyWasDown = keyDown;
                return;
            }
            beginAnchorSequence(existingAnchor, rollChargeCount());
            keyWasDown = keyDown;
            return;
        }

        keyWasDown = keyDown;
        runFreshAnchorPlacement(holdMode, isHoldingRespawnAnchor(), keyDown);
    }

    private void runFreshAnchorPlacement(boolean holdMode, boolean useHeldAnchorSlot, boolean keyDown) {
        boolean useAutoAim = autoAim.isEnabled() && !useHeldAnchorSlot;
        KuroBlockUtils.PlacementHit autoAimHit = null;
        BlockHitResult manualHit = null;
        boolean useAutoAimPlacement = false;

        if (useAutoAim) {
            autoAimHit = findBestPlacementHit();
            if (autoAimHit != null) {
                useAutoAimPlacement = true;
            } else {
                manualHit = currentBlockHit();
            }
            if (manualHit == null && !useAutoAimPlacement) {
                if (!holdMode) clickQueued = false;
                handleInactive();
                return;
            }
        } else {
            manualHit = currentBlockHit();
            if (manualHit == null) {
                if (!holdMode) clickQueued = false;
                handleInactive();
                return;
            }
        }

        int anchorSlot = useHeldAnchorSlot
                ? mc.player.getInventory().getSelectedSlot()
                : KuroInventoryUtils.findItemWithPredicateInHotbar(stack -> stack.isOf(Items.RESPAWN_ANCHOR));
        if (anchorSlot == -1) {
            clickQueued = false;
            handleInactive();
            return;
        }

        restoringSwap = false;
        if (!useHeldAnchorSlot && !KuroSwapStateManager.swapToIfNeeded(this, anchorSlot, false, -1, false)) return;
        if (placeCooldown > 0) return;
        if (!mc.player.getMainHandStack().isOf(Items.RESPAWN_ANCHOR)) return;

        BlockHitResult hitResult;
        BlockPos placedAnchorPos;
        if (useAutoAimPlacement) {
            if (autoAimHit == null) return;
            if (!aimAt(autoAimHit.hitResult().getPos())) return;
            if (!isReadyToPlace(autoAimHit)) return;
            hitResult = autoAimHit.hitResult();
            placedAnchorPos = autoAimHit.placementPos();
        } else {
            hitResult = manualHit;
            placedAnchorPos = placedAnchorPosOf(hitResult);
            if (!isManualAnchorPlacementValid(placedAnchorPos)) {
                if (!holdMode) clickQueued = false;
                handleInactive();
                return;
            }
        }

        ActionResult result = KuroBlockUtils.interactWithBlock(hitResult, true);
        placeCooldown = VANILLA_PLACE_COOLDOWN_TICKS;
        if (result.isAccepted()) KuroRotationConvergenceTracker.markInteraction();

        if (result.isAccepted() && placedAnchorPos != null) {
            beginAnchorSequence(placedAnchorPos, rollChargeCount());
        } else if (!holdMode) {
            finishAnchorSequence();
        }

        if (holdMode && !result.isAccepted()) {
            placeCooldown = Math.max(placeCooldown, 1);
        }
    }

    // -- Sequence state machine --

    private void beginAnchorSequence(BlockPos anchorPos, int chargeCount) {
        activeAnchorPos = anchorPos;
        remainingCharges = Math.max(0, chargeCount - getAnchorCharge(anchorPos));
        anchorStep = safeAnchor.isEnabled() ? AnchorStep.PLACE_SAFETY : AnchorStep.CHARGE;
        nextAnchorActionTick = mc.player.age + (anchorStep == AnchorStep.CHARGE ? Math.max(0, Math.round(chargeDelay.getRandom())) : 0);
        restoringSwap = false;
        cachedAnchorAim = KuroAnchorAimSearch.findClearAimPoint(anchorPos);
        safetyStepStartMillis = System.currentTimeMillis();
    }

    private void continueAnchorSequence() {
        if (!isRespawnAnchor(activeAnchorPos) || !isAnchorWithinInteractionRange(activeAnchorPos)) {
            finishAnchorSequence();
            return;
        }
        if (mc.player.age < nextAnchorActionTick) return;

        switch (anchorStep) {
            case PLACE_SAFETY -> placeSafetyBlock();
            case CHARGE -> chargeActiveAnchor();
            case EXPLODE -> explodeActiveAnchor();
        }
    }

    private void finishAnchorSequence() {
        resetAnchorSequence();
        if (isHold() && KuroInputHandler.isBindDown(activateBind())) {
            if (!repeat.isEnabled()) {
                holdCycleCompleted = true;
                scheduleSwapBack(1);
                disengageRotations();
            }
            return;
        }
        holdCycleCompleted = false;
        clickQueued = false;
        scheduleSwapBack(1);
        disengageRotations();
    }

    private void resetAnchorSequence() {
        anchorStep = null;
        activeAnchorPos = null;
        pendingUseKeyAnchorPos = null;
        remainingCharges = 0;
        nextAnchorActionTick = 0;
        cachedAnchorAim = null;
    }

    private boolean isAnchorSequenceActive() {
        return anchorStep != null && activeAnchorPos != null;
    }

    // -- Step: PLACE_SAFETY --

    private void placeSafetyBlock() {
        List<BlockPos> ranked = rankedSafetyCandidates();

        if (!shouldPlaceSafety(ranked)) {
            advanceToChargeStep();
            return;
        }

        int timeoutMs = (int) safetyTimeout.getAsDouble();
        if (timeoutMs > 0 && System.currentTimeMillis() - safetyStepStartMillis >= timeoutMs) {
            advanceToChargeStep();
            return;
        }

        boolean useAutoAim = autoAim.isEnabled();
        BlockHitResult hitResult;

        if (useAutoAim) {
            KuroBlockUtils.PlacementHit placementHit = pickSafetyPlacement(ranked);
            if (placementHit == null) return;
            if (!aimAt(placementHit.hitResult().getPos())) return;
            if (!isReadyToPlace(placementHit)) return;
            hitResult = placementHit.hitResult();
        } else {
            BlockHitResult playerHit = currentBlockHit();
            if (playerHit == null) return;
            BlockPos lookedAt = playerHit.getBlockPos();
            if (lookedAt.equals(activeAnchorPos)) return;

            BlockPos placement = lookedAt.offset(playerHit.getSide());
            if (placement.equals(activeAnchorPos)) return;
            if (!KuroBlockUtils.isValidPlacement(placement)) return;
            if (KuroBlockUtils.collidesWithPlayer(placement)) return;
            if (KuroBlockUtils.collidesWithBlockingEntity(placement)) return;
            hitResult = playerHit;
        }

        int safetySlot = findSafetyBlockSlot();
        if (safetySlot == -1) {
            advanceToChargeStep();
            return;
        }

        if (!KuroSwapStateManager.swapToIfNeeded(this, safetySlot, false, -1, false)) return;
        if (placeCooldown > 0) return;
        if (!(mc.player.getMainHandStack().getItem() instanceof BlockItem)) return;

        ActionResult result = KuroBlockUtils.interactWithBlock(hitResult, true);
        placeCooldown = VANILLA_PLACE_COOLDOWN_TICKS;
        if (result.isAccepted()) {
            KuroRotationConvergenceTracker.markInteraction();
            advanceToChargeStep();
        }
    }

    private boolean shouldPlaceSafety(List<BlockPos> ranked) {
        if (!safeAnchor.isEnabled()) return false;
        if (mc.player == null || mc.world == null || activeAnchorPos == null) return false;

        float threshold = (float) minDamage.getAsDouble();
        if (threshold > 0.0f && KuroExplosionUtils.getAnchorDamageTo(mc.player, activeAnchorPos) < threshold) {
            return false;
        }

        BlockPos ideal = ranked.isEmpty() ? null : ranked.getFirst();
        return ideal == null || !hasExistingSafetyBlock(ideal);
    }

    private List<BlockPos> rankedSafetyCandidates() {
        List<BlockPos> ordered = new ArrayList<>(8);
        if (mc.player == null || activeAnchorPos == null) return ordered;

        BlockPos feet = BlockPos.ofFloored(mc.player.getEntityPos());
        Vec3d feetCenter = Vec3d.ofCenter(feet);
        Vec3d anchorCenter = Vec3d.ofCenter(activeAnchorPos);
        Vec3d toAnchor = anchorCenter.subtract(feetCenter);

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                BlockPos pos = feet.add(dx, 0, dz);
                Vec3d toCand = Vec3d.ofCenter(pos).subtract(feetCenter);
                if (toCand.x * toAnchor.x + toCand.z * toAnchor.z <= 0) continue;
                ordered.add(pos);
            }
        }
        ordered.sort(Comparator.comparingDouble(p -> Vec3d.ofCenter(p).squaredDistanceTo(anchorCenter)));
        return ordered;
    }

    private KuroBlockUtils.PlacementHit pickSafetyPlacement(List<BlockPos> ranked) {
        for (BlockPos pos : ranked) {
            KuroBlockUtils.PlacementHit hit = trySafetyPlacementAt(pos);
            if (hit != null) return hit;
        }
        return null;
    }

    private KuroBlockUtils.PlacementHit trySafetyPlacementAt(BlockPos pos) {
        if (pos == null || mc.world == null || mc.player == null) return null;
        if (pos.equals(activeAnchorPos)) return null;
        if (!KuroBlockUtils.isValidPlacement(pos)) return null;
        if (!KuroBlockUtils.hasSupportingFace(pos)) return null;
        if (KuroBlockUtils.collidesWithPlayer(pos)) return null;
        if (KuroBlockUtils.collidesWithBlockingEntity(pos)) return null;

        KuroBlockUtils.PlacementHit hit = KuroBlockUtils.findPlacementHit(pos, canPlaceLegit.isEnabled());
        if (hit == null) return null;

        Vec3d eye = mc.player.getEyePos();
        double range = mc.player.getBlockInteractionRange();
        if (eye.squaredDistanceTo(hit.hitResult().getPos()) > range * range) return null;
        return hit;
    }

    private boolean hasExistingSafetyBlock(BlockPos pos) {
        if (mc.world == null || pos == null) return false;
        return !mc.world.getBlockState(pos).isReplaceable();
    }

    private int findSafetyBlockSlot() {
        int slot = KuroInventoryUtils.findItemWithPredicateInHotbar(stack -> stack.isOf(Items.GLOWSTONE));
        if (slot != -1) return slot;
        return KuroInventoryUtils.findItemWithPredicateInHotbar(stack ->
                stack.getItem() instanceof BlockItem && !stack.isOf(Items.RESPAWN_ANCHOR));
    }

    // -- Step: CHARGE --

    private void chargeActiveAnchor() {
        int currentCharge = getAnchorCharge(activeAnchorPos);
        if (remainingCharges <= 0 || currentCharge >= MAX_ANCHOR_CHARGES) {
            beginExplodeStepOrFinish();
            return;
        }

        int glowstoneSlot = KuroInventoryUtils.findItemWithPredicateInHotbar(stack -> stack.isOf(Items.GLOWSTONE));
        if (glowstoneSlot == -1) {
            remainingCharges = 0;
            beginExplodeStepOrFinish();
            return;
        }

        if (!KuroSwapStateManager.swapToIfNeeded(this, glowstoneSlot, false, -1, false)) return;
        if (!mc.player.getMainHandStack().isOf(Items.GLOWSTONE)) return;

        if (!aimAt(getAnchorAimPoint(activeAnchorPos))) return;

        BlockHitResult hitResult = getReadyAnchorHit(activeAnchorPos);
        if (hitResult == null) return;

        ActionResult result = KuroBlockUtils.interactWithBlock(hitResult, true);
        if (!result.isAccepted()) return;

        KuroRotationConvergenceTracker.markInteraction();
        remainingCharges--;
        if (remainingCharges <= 0) {
            anchorStep = AnchorStep.EXPLODE;
            nextAnchorActionTick = mc.player.age + Math.max(0, Math.round(explodeDelay.getRandom()));
            return;
        }
        nextAnchorActionTick = mc.player.age + Math.max(0, Math.round(chargeDelay.getRandom()));
    }

    // -- Step: EXPLODE --

    private void explodeActiveAnchor() {
        int currentCharge = getAnchorCharge(activeAnchorPos);
        if (currentCharge <= 0) {
            finishAnchorSequence();
            return;
        }

        int targetSlot = (int) explodeSlot.getAsDouble() - 1;
        if (!KuroSwapStateManager.swapToIfNeeded(this, targetSlot, false, -1, false)) return;

        ItemStack explodeStack = mc.player.getInventory().getStack(targetSlot);
        if (explodeStack.isOf(Items.GLOWSTONE) && currentCharge < MAX_ANCHOR_CHARGES) {
            finishAnchorSequence();
            return;
        }

        if (!aimAt(getAnchorAimPoint(activeAnchorPos))) return;

        BlockHitResult hitResult = getReadyAnchorHit(activeAnchorPos);
        if (hitResult == null) return;

        ActionResult result = KuroBlockUtils.interactWithBlock(hitResult, true);
        if (!result.isAccepted()) return;

        KuroRotationConvergenceTracker.markInteraction();
        placeCooldown = VANILLA_PLACE_COOLDOWN_TICKS;
        finishAnchorSequence();
    }

    private void advanceToChargeStep() {
        anchorStep = AnchorStep.CHARGE;
        nextAnchorActionTick = mc.player.age + Math.max(0, Math.round(chargeDelay.getRandom()));
    }

    private void beginExplodeStepOrFinish() {
        if (getAnchorCharge(activeAnchorPos) <= 0) {
            finishAnchorSequence();
            return;
        }
        anchorStep = AnchorStep.EXPLODE;
        nextAnchorActionTick = mc.player.age + Math.max(0, Math.round(explodeDelay.getRandom()));
    }

    // -- Aim picking --

    private Vec3d getAnchorAimPoint(BlockPos anchorPos) {
        if (anchorPos == null) return null;
        if (mc.player == null) return Vec3d.ofCenter(anchorPos);

        // Aim nuqtani qayta tekshirish: shu interval achana qo'ygan safety blok asl yuzani
        // o'chirib qo'yganda uchun qayta tanlanadi.
        if (cachedAnchorAim != null && anchorPos.equals(activeAnchorPos)) {
            Vec3d eye = mc.player.getEyePos();
            if (KuroAnchorAimSearch.canHitAnchor(eye, cachedAnchorAim, anchorPos)) return cachedAnchorAim;
            cachedAnchorAim = KuroAnchorAimSearch.findClearAimPoint(anchorPos);
            return cachedAnchorAim != null ? cachedAnchorAim : Vec3d.ofCenter(anchorPos);
        }
        Vec3d fresh = KuroAnchorAimSearch.findClearAimPoint(anchorPos);
        return fresh != null ? fresh : Vec3d.ofCenter(anchorPos);
    }

    /**
     * Nuqtaga qaratadi: har tick eng ko'pi bilan {@code aimSpeed} gradus buriladi.
     *
     * <ul>
     *   <li>"Silent Aim" yoqilganda burchak faqat serverga yuboriladi (KuroRotations),
     *       kamera qimirlamaydi — {@link #effectiveYaw()} shu burchakni o'qiydi.</li>
     *   <li>Aks holda kamera vanilla yo'l bilan ({@code changeLookDirection}) buriladi.</li>
     * </ul>
     *
     * @return burchak kerakli qiymatga yetgan bo'lsa {@code true}; aks holda shu tickda
     *         interaksiya qilinmaydi va keyingi tick yana buriladi
     */
    private boolean aimAt(Vec3d target) {
        if (!autoAim.isEnabled()) return true;
        if (target == null || mc.player == null) return true;

        Vec3d eye = mc.player.getEyePos();
        Vec3d dir = target.subtract(eye);
        double flat = Math.hypot(dir.x, dir.z);
        if (flat < 1.0e-4 && Math.abs(dir.y) < 1.0e-4) return true;

        float wantYaw = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
        float wantPitch = (float) Math.toDegrees(-Math.atan2(dir.y, flat));

        boolean silent = silentAim.isEnabled();
        float curYaw = silent && KuroRotations.active() ? KuroRotations.yaw() : mc.player.getYaw();
        float curPitch = silent && KuroRotations.active() ? KuroRotations.pitch() : mc.player.getPitch();

        float dYaw = MathHelper.wrapDegrees(wantYaw - curYaw);
        float dPitch = wantPitch - curPitch;

        float maxStep = (float) Math.max(1.0, aimSpeed.getAsDouble());
        float stepYaw = MathHelper.clamp(dYaw, -maxStep, maxStep);
        float stepPitch = MathHelper.clamp(dPitch, -maxStep, maxStep);

        if (silent) {
            if (!KuroRotations.set(this, curYaw + stepYaw, MathHelper.clamp(curPitch + stepPitch, -90.0f, 90.0f))) {
                return false;   // boshqa modul rotatsiyani ushlab turibdi
            }
            KuroRotations.sendLookPacket();
        } else {
            if (KuroRotations.ownedBy(this)) KuroRotations.release(this);
            if (Math.abs(stepYaw) > 1.0e-4f || Math.abs(stepPitch) > 1.0e-4f) {
                mc.player.changeLookDirection(stepYaw / MOUSE_UNIT, stepPitch / MOUSE_UNIT);
            }
        }

        wasAiming = true;
        return Math.abs(dYaw - stepYaw) <= AIM_TOLERANCE && Math.abs(dPitch - stepPitch) <= AIM_TOLERANCE;
    }

    private boolean isReadyToPlace(KuroBlockUtils.PlacementHit placementHit) {
        float yaw = effectiveYaw();
        float pitch = effectivePitch();

        // Grim's DuplicateRotPlace compares pitch delta against the previous
        // place packet. Falling changes the needed pitch every tick, so allow
        // those non-duplicate deltas instead of waiting until we land.
        if (!KuroRotationConvergenceTracker.isDuplicateRotPlaceSafe(ROTATION_SETTLE_THRESHOLD)) return false;

        if (!canPlaceLegit.isEnabled()) {
            return rayIntersectsBlock(yaw, pitch, placementHit.hitResult().getBlockPos());
        }

        BlockHitResult raycast = raycastFromRotation(yaw, pitch);
        if (raycast == null || raycast.getType() != HitResult.Type.BLOCK) return false;
        if (!raycast.getBlockPos().equals(placementHit.hitResult().getBlockPos())) return false;
        return raycast.getSide() == placementHit.hitResult().getSide();
    }

    private BlockHitResult getReadyAnchorHit(BlockPos anchorPos) {
        if (!isAnchorWithinInteractionRange(anchorPos)) return null;

        if (!KuroRotationConvergenceTracker.isDuplicateRotPlaceSafe(ROTATION_SETTLE_THRESHOLD)) return null;

        float yaw = effectiveYaw();
        float pitch = effectivePitch();

        if (!canPlaceLegit.isEnabled()) {
            if (!rayIntersectsBlock(yaw, pitch, anchorPos)) return null;
            return new BlockHitResult(getAnchorAimPoint(anchorPos), Direction.UP, anchorPos, false);
        }

        BlockHitResult currentHit = currentBlockHit();
        if (currentHit != null && currentHit.getBlockPos().equals(anchorPos)) return currentHit;

        BlockHitResult raycast = raycastFromRotation(yaw, pitch);
        if (raycast == null || raycast.getType() != HitResult.Type.BLOCK) return null;
        if (!raycast.getBlockPos().equals(anchorPos)) return null;
        return raycast;
    }

    // -- Best-placement search (anchor placement candidate) --

    private KuroBlockUtils.PlacementHit findBestPlacementHit() {
        Vec3d playerEye = mc.player.getEyePos();
        LivingEntity target = KuroTargetUtils.getNearestTarget(8.0);
        if (target == null) return null;

        double blockRange = mc.player.getBlockInteractionRange();
        double blockRangeSq = blockRange * blockRange;

        Vec3d targetPos = target.getEntityPos();
        Box targetBox = target.getBoundingBox();
        Vec3d targetFeet = new Vec3d(targetPos.x, targetBox.minY, targetPos.z);

        int floorX = MathHelper.floor(targetFeet.x);
        int floorY = MathHelper.floor(targetFeet.y);
        int floorZ = MathHelper.floor(targetFeet.z);

        KuroBlockUtils.PlacementHit bestHit = null;
        double bestScore = Double.MAX_VALUE;

        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = -1; dy <= 2; dy++) {
                    BlockPos pos = new BlockPos(floorX + dx, floorY + dy, floorZ + dz);

                    if (!KuroBlockUtils.isValidPlacement(pos)) continue;
                    if (!KuroBlockUtils.hasSupportingFace(pos)) continue;

                    KuroBlockUtils.PlacementHit hit = KuroBlockUtils.findPlacementHit(pos, canPlaceLegit.isEnabled());
                    if (hit == null) continue;
                    if (KuroBlockUtils.collidesWithPlayer(pos)) continue;
                    if (KuroBlockUtils.hasBlockingEntity(new Box(pos))) continue;
                    if (playerEye.squaredDistanceTo(hit.hitResult().getPos()) > blockRangeSq) continue;

                    Vec3d blockCenter = Vec3d.ofCenter(pos);
                    float exposure = KuroExplosionUtils.getSeenPercent(blockCenter, targetBox, target);
                    double feetDistSq = targetFeet.squaredDistanceTo(blockCenter);
                    // Prefer high-exposure hits; break ties by proximity to feet.
                    double score = (1.0 - exposure) * 100.0 + feetDistSq * 0.01;

                    if (score < bestScore) {
                        bestScore = score;
                        bestHit = hit;
                    }
                }
            }
        }
        return bestHit;
    }

    // -- Misc helpers --

    private float effectiveYaw() {
        return KuroRotations.active() ? KuroRotations.yaw() : mc.player.getYaw();
    }

    private float effectivePitch() {
        return KuroRotations.active() ? KuroRotations.pitch() : mc.player.getPitch();
    }

    private boolean isHoldingRespawnAnchor() {
        return mc.player != null && mc.player.getInventory().getSelectedStack().isOf(Items.RESPAWN_ANCHOR);
    }

    private BlockHitResult currentBlockHit() {
        if (!(mc.crosshairTarget instanceof BlockHitResult blockHit)) return null;
        if (blockHit.getType() != HitResult.Type.BLOCK) return null;
        return blockHit;
    }

    private BlockPos consumePendingUseKeyAnchorPos() {
        BlockPos pos = pendingUseKeyAnchorPos;
        pendingUseKeyAnchorPos = null;
        if (pos == null) return null;
        return isRespawnAnchor(pos) ? pos : null;
    }

    private BlockPos placedAnchorPosOf(BlockHitResult hitResult) {
        return hitResult == null ? null : hitResult.getBlockPos().offset(hitResult.getSide());
    }

    private boolean isManualAnchorPlacementValid(BlockPos pos) {
        if (pos == null) return false;
        if (!KuroBlockUtils.isValidPlacement(pos)) return false;
        if (KuroBlockUtils.collidesWithPlayer(pos)) return false;
        return !KuroBlockUtils.collidesWithBlockingEntity(pos);
    }

    private boolean isRespawnAnchor(BlockPos pos) {
        if (pos == null || mc.world == null) return false;
        return mc.world.getBlockState(pos).isOf(Blocks.RESPAWN_ANCHOR);
    }

    private int getAnchorCharge(BlockPos pos) {
        if (pos == null || mc.world == null) return 0;
        return mc.world.getBlockState(pos).getOrEmpty(RespawnAnchorBlock.CHARGES).orElse(0);
    }

    private boolean isAnchorWithinInteractionRange(BlockPos anchorPos) {
        return mc.player != null && anchorPos != null
                && mc.player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(anchorPos))
                        <= Math.pow(mc.player.getBlockInteractionRange() + 1.0, 2);
    }

    private void handleInactive() {
        resetAnchorSequence();
        if (KuroSwapStateManager.isOwnerActive(this) && !restoringSwap) scheduleSwapBack(1);
        disengageRotations();
    }

    private void scheduleSwapBack(int delayTicks) {
        if (!KuroSwapStateManager.isOwnerActive(this)) return;
        int activeSlot = KuroSwapStateManager.getActiveTargetSlot(this);
        if (activeSlot == -1) {
            KuroSwapStateManager.cancel(this, true);
            restoringSwap = false;
            return;
        }
        KuroSwapStateManager.swapToIfNeeded(this, activeSlot, false, delayTicks, true);
        restoringSwap = true;
    }

    private void disengageRotations() {
        if (!KuroRotations.ownedBy(this)) {
            wasAiming = false;
            return;
        }
        KuroRotations.release(this);
        wasAiming = false;
    }

    // --------------------------------- raycast yordamchilar ---------------------------------

    /** Real burchaklardan o'yin raycast qilish Line (outline shapes, block reach 4.5). */
    private BlockHitResult raycastFromRotation(float yaw, float pitch) {
        if (mc.world == null || mc.player == null) return null;
        double reach = mc.player.getBlockInteractionRange();
        Vec3d eye = mc.player.getEyePos();
        return mc.world.raycast(new net.minecraft.world.RaycastContext(
                eye, eye.add(Vec3d.fromPolar(pitch, yaw).multiply(reach)),
                net.minecraft.world.RaycastContext.ShapeType.OUTLINE,
                net.minecraft.world.RaycastContext.FluidHandling.NONE,
                mc.player));
    }

    private boolean rayIntersectsBlock(float yaw, float pitch, BlockPos target) {
        if (mc.world == null || mc.player == null) return false;
        Vec3d from = new Vec3d(mc.player.getX(), mc.player.getEyeY(), mc.player.getZ());
        double reach = mc.player.getBlockInteractionRange();
        Box box = new Box(target).expand(0.6);
        Vec3d atReach = from.add(Vec3d.fromPolar(pitch, yaw).multiply(reach));
        return box.raycast(from, atReach).isPresent();
    }

    // Random rolls -- delegate to Range.getRandom() and clamp to int.
    private int rollChargeCount() {
        return MathHelper.clamp(Math.round(charges.getRandom()), 1, MAX_ANCHOR_CHARGES);
    }

    private static String describeKeyName(int code) {
        if (code < 0) return "none";
        if (code >= 1000) return "MOUSE" + (code - 1000 + 1);
        String name = InputUtil.Type.KEYSYM.createFromCode(code).getTranslationKey();
        return name.replace("key.keyboard.", "").toUpperCase(Locale.ROOT);
    }
}
