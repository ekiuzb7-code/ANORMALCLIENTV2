package com.anormal.client.module.impl.spectre;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.ProjectionUtil;
import com.anormal.client.util.RenderUtils;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.block.entity.BarrelBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.EnchantingTableBlockEntity;
import net.minecraft.block.entity.EnderChestBlockEntity;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.block.entity.MobSpawnerBlockEntity;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Spectre port: "Storage ESP" — highlights storage blocks and spawners with per-type colours.
 *
 * <p>Spectre feeds this from its chunk/object layer. Block scanning was the wrong tool for
 * it: every container and spawner in the game is a {@link BlockEntity}, so reading the
 * loaded chunks' block-entity maps gives the <b>exact</b> set in O(containers) instead of
 * crawling millions of blocks. That also removes the old failure modes - a scan budget that
 * only covered the nearest few thousand blocks, and a per-tick clear that made the markers
 * flicker or disappear entirely when the scan could not keep up with the radius.</p>
 */
public final class SpectreStorageEsp extends Module {
    public final NumberSetting range = addSetting(new NumberSetting("Range", "Scan radius (blocks)", 64, 16, 128, 8));
    public final BooleanSetting sTracers = addSetting(new BooleanSetting("Tracers", "Draw tracer lines", false));
    public final BooleanSetting outline = addSetting(new BooleanSetting("Outline", "Draw block outlines", true));
    public final BooleanSetting fill = addSetting(new BooleanSetting("Fill", "Draw translucent fills", false));
    public final BooleanSetting fillBoxesOnly = addSetting(new BooleanSetting("Fill Boxes Only", "Fill flat boxes instead of screen quads", true));
    public final NumberSetting lineWidth = addSetting(new NumberSetting("Line Width", "Outline width", 1.5, 0.5, 5.0, 0.5));
    public final BooleanSetting sAlpha = addSetting(new BooleanSetting("Draw Tracer Color", "Tracers use their own color", false));

    private final Type chest = new Type("Chest", true, 0xFF31A854);
    private final Type enderChest = new Type("Ender Chest", true, 0xFF7555A0);
    private final Type shulker = new Type("Shulker Box", true, 0xFF866123);
    private final Type spawner = new Type("Spawner", true, 0xFF8A9BA6);
    private final Type furnace = new Type("Furnace", false, 0xFF630707);
    private final Type barrel = new Type("Barrel", true, 0xFF664D2A);
    private final Type enchanting = new Type("Enchanting Table", true, 0xFFC80000);
    private final Type hopper = new Type("Hopper", false, 0xFF505050);
    private final Type[] types = {chest, enderChest, shulker, spawner, furnace, barrel, enchanting, hopper};

    /** pos key -> {box colour, tracer colour} of the storage type */
    private final Map<Long, int[]> boxes = new HashMap<>();

    public SpectreStorageEsp() {
        super("SpectreStorageEsp", "Spectre port: highlights storage blocks and spawners with per-type colours.", Category.SPECTRE);
    }

    private final class Type {
        final BooleanSetting enabled;
        final BooleanSetting tracers;
        final ColorSetting color;
        final ColorSetting tracerColor;
        final String label;

        Type(String label, boolean on, int argb) {
            this.label = label;
            enabled = addSetting(new BooleanSetting(label, "Show " + label + " blocks", on));
            color = addSetting(new ColorSetting(label + " Color", label + " highlight color", argb));
            tracers = addSetting(new BooleanSetting(label + " Tracers", label + " tracer lines", false));
            tracerColor = addSetting(new ColorSetting(label + " Tracer Color", label + " tracer color", argb));
        }
    }

    /** Maps a block entity to the storage type the user enabled, or null. */
    private Type typeOf(WorldChunk chunk, BlockPos pos, BlockEntity be) {
        if (chest.enabled.isEnabled() && be instanceof ChestBlockEntity) return chest;
        if (enderChest.enabled.isEnabled() && be instanceof EnderChestBlockEntity) return enderChest;
        if (shulker.enabled.isEnabled() && be instanceof ShulkerBoxBlockEntity) return shulker;
        if (spawner.enabled.isEnabled() && be instanceof MobSpawnerBlockEntity) return spawner;
        if (furnace.enabled.isEnabled() && be instanceof AbstractFurnaceBlockEntity) return furnace;
        if (barrel.enabled.isEnabled() && be instanceof BarrelBlockEntity) return barrel;
        if (enchanting.enabled.isEnabled() && be instanceof EnchantingTableBlockEntity) return enchanting;
        if (hopper.enabled.isEnabled() && be instanceof HopperBlockEntity) return hopper;
        // Spawners are block entities only in loaded chunks; a plain state check keeps
        // them working when a chunk is still being built.
        if (spawner.enabled.isEnabled() && chunk.getBlockState(pos).isOf(Blocks.SPAWNER)) return spawner;
        return null;
    }

    @Override
    public void onDisable() {
        boxes.clear();
    }

    private boolean anyTracer() {
        if (!sTracers.isEnabled()) return false;
        for (Type t : types) {
            if (t.enabled.isEnabled() && t.tracers.isEnabled()) return true;
        }
        return false;
    }

    private int colorOf(Type t) {
        return t.color.getValue() | 0xFF000000;
    }

    /** "Draw Tracer Color": each storage type keeps its own tracer colour. */
    private int tracerOf(Type t) {
        return sAlpha.isEnabled() ? (t.tracerColor.getValue() | 0xFF000000) : (t.color.getValue() | 0xFF000000);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) {
            boxes.clear();
            return;
        }

        double px = mc.player.getX();
        double py = mc.player.getY();
        double pz = mc.player.getZ();
        int r = (int) range.getAsDouble();
        if (r <= 0) {
            boxes.clear();
            return;
        }
        double rSq = (double) r * r;
        int chunkR = (r >> 4) + 1;
        int pcx = mc.player.getChunkPos().x;
        int pcz = mc.player.getChunkPos().z;

        Map<Long, int[]> rebuild = new HashMap<>();
        for (int dx = -chunkR; dx <= chunkR; dx++) {
            for (int dz = -chunkR; dz <= chunkR; dz++) {
                WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(pcx + dx, pcz + dz);
                if (chunk == null) continue;
                for (Map.Entry<BlockPos, BlockEntity> entry : chunk.getBlockEntities().entrySet()) {
                    BlockEntity be = entry.getValue();
                    if (be == null || be.isRemoved()) continue;
                    BlockPos pos = entry.getKey();
                    double ex = pos.getX() + 0.5 - px;
                    double ey = pos.getY() + 0.5 - py;
                    double ez = pos.getZ() + 0.5 - pz;
                    if (ex * ex + ey * ey + ez * ez > rSq) continue;
                    Type t = typeOf(chunk, pos, be);
                    if (t == null) continue;
                    rebuild.put(pos.asLong(), new int[]{colorOf(t), tracerOf(t)});
                }
            }
        }

        boxes.clear();
        boxes.putAll(rebuild);
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (!isEnabled() || mc.player == null || boxes.isEmpty()) return;
        boolean o = outline.isEnabled();
        boolean f = fill.isEnabled();
        boolean tracing = anyTracer();
        if (!o && !f && !tracing) return;

        int lw = Math.max(1, (int) Math.round(lineWidth.getAsDouble()));
        List<double[]> tracerHeads = new ArrayList<>();

        Vec3d eye = ProjectionUtil.eye(tickDelta);
        for (Map.Entry<Long, int[]> e : boxes.entrySet()) {
            long k = e.getKey();
            int x = BlockPos.unpackLongX(k);
            int y = BlockPos.unpackLongY(k);
            int z = BlockPos.unpackLongZ(k);
            int boxColor = e.getValue()[0];

            Box outlineBox = new Box(x, y, z, x + 1, y + 1, z + 1);
            SpectreHoleEsp.drawBox(context, tickDelta, outlineBox, o ? boxColor : 0, 0, o, false, lw);
            if (f) {
                // "Fill Boxes Only" keeps the fill as a thin plate so the block face stays visible.
                Box fillBox = fillBoxesOnly.isEnabled()
                        ? new Box(x, y + 0.02, z, x + 1, y + 0.04, z + 1)
                        : new Box(x, y, z, x + 1, y + 1, z + 1);
                SpectreHoleEsp.drawBox(context, tickDelta, fillBox,
                        0, (boxColor & 0x00FFFFFF) | 0x50000000, false, true, lw);
            }
            if (tracing) {
                tracerHeads.add(new double[]{x + 0.5, y + 0.5, z + 0.5, e.getValue()[1]});
            }
        }

        if (tracing) {
            for (double[] head : tracerHeads) {
                drawTracer(context, tickDelta, eye, head, (int) head[3]);
            }
        }
    }

    private static void drawTracer(DrawContext context, float tickDelta, Vec3d eye, double[] target, int color) {
        int[] from = ProjectionUtil.project(eye, tickDelta);
        int[] to = ProjectionUtil.project(new Vec3d(target[0], target[1], target[2]), tickDelta);
        if (from == null || to == null) return;
        RenderUtils.drawLine(context, from[0], from[1], to[0], to[1], color, 1);
    }
}
