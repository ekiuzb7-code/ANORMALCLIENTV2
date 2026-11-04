package com.anormal.client.util.kuro;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.RespawnAnchorBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

/**
 * Kuro BlockUtils birga bir port (echo BlockUtils'dan Anormal mapping'larga).
 *
 * Anchor modullarining asosiy blok yordamchilari: shovqinsiz (charge) holat,
 * yurish uchun nishon nuqtalar, oyinchi/entity to'siqlari va "legit" (legal)
 * raycast qo'yish nuqtalari (to'g'ridan-to'g'ri server rad etadigan nuqtalarni
 * rad etadi).
 */
public final class KuroBlockUtils {
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    private static final double PLACE_RAY_EPSILON = 1.0E-4;
    private static final double PLACE_FACE_CORNER_OFFSET = 0.49;
    private static final double[][] PLACE_FACE_SAMPLE_OFFSETS = {
            {0.0, 0.0},
            {PLACE_FACE_CORNER_OFFSET, PLACE_FACE_CORNER_OFFSET},
            {PLACE_FACE_CORNER_OFFSET, -PLACE_FACE_CORNER_OFFSET},
            {-PLACE_FACE_CORNER_OFFSET, PLACE_FACE_CORNER_OFFSET},
            {-PLACE_FACE_CORNER_OFFSET, -PLACE_FACE_CORNER_OFFSET}
    };

    private KuroBlockUtils() {}

    // ---------------------------------------------------------------- holat tekshiruvlari

    public static boolean isBlock(BlockPos pos, Block block) {
        World w = mc.world;
        return w != null && pos != null && w.getBlockState(pos).getBlock() == block;
    }

    public static boolean isRespawnAnchorCharged(BlockPos pos) {
        World w = mc.world;
        if (w == null || pos == null) return false;
        BlockState s = w.getBlockState(pos);
        return s.isOf(Blocks.RESPAWN_ANCHOR)
                && s.getOrEmpty(RespawnAnchorBlock.CHARGES).orElse(0) != 0;
    }

    public static boolean isRespawnAnchorUncharged(BlockPos pos) {
        World w = mc.world;
        if (w == null || pos == null) return false;
        BlockState s = w.getBlockState(pos);
        return s.isOf(Blocks.RESPAWN_ANCHOR)
                && s.getOrEmpty(RespawnAnchorBlock.CHARGES).orElse(0) == 0;
    }

    // ---------------------------------------------------------------- to'siqlar

    /** O'yinchi hitbox'i shu blok AABB'ni egallayotgan bo'lsa true. */
    public static boolean collidesWithPlayer(BlockPos pos) {
        return mc.player != null && pos != null
                && mc.player.getBoundingBox().intersects(new Box(pos));
    }

    /** Blok qutisi boshqa to'siq qo'yadigan entity bilan kesishsa true. */
    public static boolean collidesWithBlockingEntity(BlockPos pos) {
        return pos != null && mc.world != null && hasBlockingEntity(new Box(pos));
    }

    /** Berilgan Box ichida boshqa blok qo'yishga to'sqinlik qiladigan entity bor-yo'qligini tekshiradi. */
    public static boolean hasBlockingEntity(Box box) {
        World w = mc.world;
        if (box == null || w == null) return false;
        for (Entity e : w.getOtherEntities(null, box)) {
            if (!isBlockingEntity(e)) continue;
            if (e.getBoundingBox().intersects(box)) return true;
        }
        return false;
    }

    private static boolean isBlockingEntity(Entity e) {
        // echo'da entity.blocksBuilding ishlatilardi; Yarn'dagi ekvivalent — isCollidable()
        // (qayiqlar, minecart'lar va boshqalar qo'yishga to'sqinlik qiladi, mob'lar qilmaydi).
        return e != null && e != mc.player && !e.isRemoved() && e.isCollidable(e);
    }

    /** Yozilgan blok qo'yilishi boshqa entity bilan kesishsa true. */
    public static boolean willPlacedBlockPlacementCollideWithEntity(BlockPos pos, BlockState state) {
        World w = mc.world;
        if (w == null || pos == null || state == null) return false;
        for (Entity e : w.getOtherEntities(null, new Box(pos))) {
            if (!(e instanceof LivingEntity) || e == mc.player) continue;
            if (e.getBoundingBox().intersects(new Box(pos))) return true;
        }
        return false;
    }

    public static boolean isBlockPlacingCollideWithEntity(BlockPos pos, Block block) {
        return willPlacedBlockPlacementCollideWithEntity(pos, block.getDefaultState());
    }

    // ---------------------------------------------------------------- yurish holati

    public static boolean isValidPlacement(BlockPos pos) {
        World w = mc.world;
        if (pos == null || w == null) return false;
        BlockState s = w.getBlockState(pos);
        return s.isAir() || s.isReplaceable();
    }

    public static boolean hasSupportingFace(BlockPos pos) {
        World w = mc.world;
        if (pos == null || w == null) return false;
        for (Direction d : Direction.values()) {
            BlockPos adjacent = pos.offset(d);
            if (w.getBlockState(adjacent).isSideSolidFullSquare(w, adjacent, d.getOpposite())) return true;
        }
        return false;
    }

    /** Blok ustidagi (yoki uning yaqinidagi) kristall mavjudligini tekshiradi. */
    public static boolean hasCrystalOnBlock(BlockPos pos) {
        World w = mc.world;
        if (w == null || pos == null) return false;
        BlockPos crystalPos = pos.up();
        if (!w.isAir(crystalPos)) return false;
        return !w.getOtherEntities(null, new Box(crystalPos.getX(), crystalPos.getY(),
                crystalPos.getZ(), crystalPos.getX() + 1.0, crystalPos.getY() + 2.0,
                crystalPos.getZ() + 1.0)).isEmpty();
    }

    // ---------------------------------------------------------------- klik

    /** Berilgan raycast natijasi bilan blok bilan o'zaro ta'sir qiladi va muvaffaqiyat bo'lsa qo'lni silkitadi. */
    public static ActionResult interactWithBlock(BlockHitResult hit, boolean shouldSwingHand) {
        if (mc.player == null || mc.interactionManager == null) return ActionResult.PASS;
        ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
        if (result.isAccepted() && shouldSwingHand) {
            mc.player.swingHand(Hand.MAIN_HAND);
        }
        return result;
    }

    /** Sichqoncha nishonlash nuqtasidagi blok holatini qaytaradi. */
    public static BlockPos getTargetPlacePos() {
        HitResult hit = mc.crosshairTarget;
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) return null;
        BlockHitResult bhr = (BlockHitResult) hit;
        return bhr.getBlockPos().offset(bhr.getSide());
    }

    // ---------------------------------------------------------------- legal qo'yish nuqtalari (raycast)

    public static boolean canPlaceLegit(BlockPos pos) {
        return findPlacementHit(pos, true) != null;
    }

    public static PlacementHit findPlacementHit(BlockPos pos, boolean requireRaycast) {
        World w = mc.world;
        if (pos == null || w == null || mc.player == null) return null;
        Vec3d eyePos = mc.player.getEyePos();
        PlacementHit bestHit = null;
        double bestDistanceSq = Double.MAX_VALUE;

        for (Direction direction : Direction.values()) {
            BlockPos adjacent = pos.offset(direction);
            Direction face = direction.getOpposite();

            if (!w.getBlockState(adjacent).isSideSolidFullSquare(w, adjacent, face)) continue;

            PlacementHit hit = findPlacementHitOnFace(eyePos, pos, adjacent, direction, face, requireRaycast);
            if (hit == null) continue;

            double distanceSq = eyePos.squaredDistanceTo(hit.hitResult().getPos());
            if (distanceSq < bestDistanceSq) {
                bestDistanceSq = distanceSq;
                bestHit = hit;
            }
        }
        return bestHit;
    }

    private static PlacementHit findPlacementHitOnFace(Vec3d eyePos, BlockPos placementPos, BlockPos adjacent,
                                                       Direction direction, Direction face, boolean requireRaycast) {
        for (double[] sampleOffset : PLACE_FACE_SAMPLE_OFFSETS) {
            Vec3d facePoint = getFaceSamplePoint(adjacent, face, sampleOffset[0], sampleOffset[1]);
            BlockHitResult hitResult = new BlockHitResult(facePoint, face, adjacent, false);
            if (requireRaycast && !canRaycastPlacementFace(eyePos, hitResult, direction)) continue;
            return new PlacementHit(placementPos, hitResult);
        }
        return null;
    }

    private static boolean canRaycastPlacementFace(Vec3d eyePos, BlockHitResult placementHit, Direction direction) {
        Vec3d facePoint = placementHit.getPos();
        Vec3d rayEnd = facePoint.add(direction.getOffsetX() * PLACE_RAY_EPSILON,
                direction.getOffsetY() * PLACE_RAY_EPSILON,
                direction.getOffsetZ() * PLACE_RAY_EPSILON);
        BlockHitResult hitResult = mc.world.raycast(new RaycastContext(
                eyePos, rayEnd, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, mc.player));
        if (hitResult.getType() != HitResult.Type.BLOCK) return false;
        if (!hitResult.getBlockPos().equals(placementHit.getBlockPos())) return false;
        return hitResult.getSide() == placementHit.getSide();
    }

    private static Vec3d getFaceSamplePoint(BlockPos adjacent, Direction face, double offsetA, double offsetB) {
        Vec3d faceCenter = Vec3d.ofCenter(adjacent).add(
                face.getOffsetX() * 0.5, face.getOffsetY() * 0.5, face.getOffsetZ() * 0.5);
        return switch (face.getAxis()) {
            case X -> faceCenter.add(0.0, offsetA, offsetB);
            case Y -> faceCenter.add(offsetA, 0.0, offsetB);
            case Z -> faceCenter.add(offsetA, offsetB, 0.0);
        };
    }

    /** Qo'yish nuqtasi: nishon blok + raycast hit natijasi. */
    public record PlacementHit(BlockPos placementPos, BlockHitResult hitResult) {}
}
