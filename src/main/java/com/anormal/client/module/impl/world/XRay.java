package com.anormal.client.module.impl.world;

import com.anormal.client.mixin.GammaAccessor;
import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.BlockScanner;
import com.anormal.client.util.ProjectionUtil;
import com.anormal.client.util.RenderUtils;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.WorldChunk;

import java.util.*;

/**
 * Modern XRay module:
 * - Real XRay hides non-selected blocks via mixin.
 * - Proper FullBright (gamma override + night vision fallback).
 * - Per-ore distinct colors (Diamond=Cyan, Emerald=Green, Debris=Red, Gold=Yellow, etc.).
 * - Clean 3D wireframe outlines and tracers to high-value ores without ocean/water obstruction.
 */
public class XRay extends Module {
    // Core settings
    public final BooleanSetting realXray = new BooleanSetting("Real XRay", "Hide non-selected blocks via mixin", true);
    public final BooleanSetting fullBright = new BooleanSetting("FullBright", "Full brightness while XRay is active", true);
    public final NumberSetting gamma = new NumberSetting("Gamma", "Fullbright gamma value", 16.0, 1.0, 32.0, 1.0);

    // Fluids
    public final BooleanSetting showWater = new BooleanSetting("Show Water", "Show ocean and water blocks (disabled by default to prevent blocking view)", false);
    public final BooleanSetting showLava = new BooleanSetting("Show Lava", "Show lava lakes and streams", false);

    // Background/opacity settings
    public final NumberSetting backgroundOpacity = new NumberSetting("Background Opacity", "0 = hide non-selected blocks fully, 1-255 = dim the world on screen", 0, 0, 255, 1);
    public final BooleanSetting exposedOnly = new BooleanSetting("Exposed Only", "Only show blocks exposed to air (cave mode)", false);

    // Markers & Render
    public final BooleanSetting markers = new BooleanSetting("Markers", "Highlight selected blocks through walls", false);
    public final BooleanSetting outlines = new BooleanSetting("3D Outlines", "Draw 3D wireframe boxes around ores", false);
    public final BooleanSetting coloredMarkers = new BooleanSetting("Colored Markers", "Use unique color for each ore type", false);
    public final BooleanSetting tracers = new BooleanSetting("Tracers", "Draw lines to valuable ores (Diamonds, Debris, Emeralds)", false);
    public final BooleanSetting showDistance = new BooleanSetting("Show Distance", "Show distance in meters above valuable ores", false);
    public final NumberSetting markerRange = new NumberSetting("Marker Range", "Marker scan radius", 48.0, 8.0, 160.0, 8.0);
    public final NumberSetting maxRendered = new NumberSetting("Max Rendered", "Max ore markers drawn at once", 400.0, 20.0, 2000.0, 20.0);

    // Read from the chunk-meshing worker threads while the GUI may toggle blocks, so it has to
    // be a concurrent set: a plain HashSet could throw or serve a half-updated whitelist.
    private final Set<Block> selectedBlocks = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final List<String> selectedBlockNames = new ArrayList<>();

    private final Map<BlockPos, Integer> cache = new HashMap<>();
    private final BlockScanner markerScanner = new BlockScanner();
    private int centreX = Integer.MIN_VALUE, centreY, centreZ;
    private double savedGamma = 1.0;
    private boolean gammaApplied = false;
    private boolean nightVisionGranted = false;
    // Chunk meshes must be rebuilt when a setting that changes the rendered world changes.
    private boolean lastHiding;
    private boolean lastWater;
    private boolean lastLava;
    private boolean lastExposed;
    private int lastOpacity = -1;

    public XRay() {
        super("XRay", "Real mode hides blocks via mixin, highlights ores through walls", Category.WORLD);
        addSetting(realXray);
        addSetting(fullBright);
        addSetting(gamma);
        addSetting(showWater);
        addSetting(showLava);
        addSetting(backgroundOpacity);
        addSetting(exposedOnly);
        addSetting(markers);
        addSetting(outlines);
        addSetting(coloredMarkers);
        addSetting(tracers);
        addSetting(showDistance);
        addSetting(markerRange);
        addSetting(maxRendered);
        selectAllOres();
    }

    public void selectAllOres() {
        selectedBlocks.clear();
        selectedBlockNames.clear();

        // Overworld ores
        addBlock(Blocks.DIAMOND_ORE);
        addBlock(Blocks.DEEPSLATE_DIAMOND_ORE);
        addBlock(Blocks.EMERALD_ORE);
        addBlock(Blocks.DEEPSLATE_EMERALD_ORE);
        addBlock(Blocks.GOLD_ORE);
        addBlock(Blocks.DEEPSLATE_GOLD_ORE);
        addBlock(Blocks.IRON_ORE);
        addBlock(Blocks.DEEPSLATE_IRON_ORE);
        addBlock(Blocks.LAPIS_ORE);
        addBlock(Blocks.DEEPSLATE_LAPIS_ORE);
        addBlock(Blocks.REDSTONE_ORE);
        addBlock(Blocks.DEEPSLATE_REDSTONE_ORE);
        addBlock(Blocks.COPPER_ORE);
        addBlock(Blocks.DEEPSLATE_COPPER_ORE);
        addBlock(Blocks.COAL_ORE);
        addBlock(Blocks.DEEPSLATE_COAL_ORE);

        // Raw mineral blocks
        addBlock(Blocks.RAW_COPPER_BLOCK);
        addBlock(Blocks.RAW_GOLD_BLOCK);
        addBlock(Blocks.RAW_IRON_BLOCK);

        // Mineral blocks
        addBlock(Blocks.DIAMOND_BLOCK);
        addBlock(Blocks.EMERALD_BLOCK);
        addBlock(Blocks.GOLD_BLOCK);
        addBlock(Blocks.IRON_BLOCK);
        addBlock(Blocks.LAPIS_BLOCK);
        addBlock(Blocks.REDSTONE_BLOCK);
        addBlock(Blocks.COAL_BLOCK);

        // Nether ores & minerals
        addBlock(Blocks.ANCIENT_DEBRIS);
        addBlock(Blocks.NETHERITE_BLOCK);
        addBlock(Blocks.NETHER_GOLD_ORE);
        addBlock(Blocks.NETHER_QUARTZ_ORE);
        addBlock(Blocks.QUARTZ_BLOCK);

        // Storage & Portals
        addBlock(Blocks.CHEST);
        addBlock(Blocks.TRAPPED_CHEST);
        addBlock(Blocks.ENDER_CHEST);
        addBlock(Blocks.BARREL);
        addBlock(Blocks.SHULKER_BOX);
        addBlock(Blocks.SPAWNER);
        addBlock(Blocks.TRIAL_SPAWNER);
        addBlock(Blocks.VAULT);
        addBlock(Blocks.END_PORTAL_FRAME);
        addBlock(Blocks.END_PORTAL);
        addBlock(Blocks.NETHER_PORTAL);

        if (isEnabled() && mc.worldRenderer != null) {
            mc.worldRenderer.reload();
        }
    }

    private void addBlock(Block block) {
        selectedBlocks.add(block);
        selectedBlockNames.add(Registries.BLOCK.getId(block).toString());
    }

    public void clearSelection() {
        selectedBlocks.clear();
        selectedBlockNames.clear();
        if (isEnabled() && mc.worldRenderer != null) {
            mc.worldRenderer.reload();
        }
    }

    public void toggleBlock(Block block) {
        String name = Registries.BLOCK.getId(block).toString();
        if (selectedBlocks.contains(block)) {
            selectedBlocks.remove(block);
            selectedBlockNames.remove(name);
        } else {
            selectedBlocks.add(block);
            selectedBlockNames.add(name);
        }
        if (isEnabled() && mc.worldRenderer != null) {
            mc.worldRenderer.reload();
        }
    }

    public boolean isBlockSelected(Block block) {
        return selectedBlocks.contains(block);
    }

    public Set<Block> getSelectedBlocks() {
        return selectedBlocks;
    }

    public List<String> getSelectedBlockNames() {
        return selectedBlockNames;
    }

    public boolean isVisibleBlock(Block block) {
        if (block == Blocks.WATER) return showWater.isEnabled();
        if (block == Blocks.LAVA) return showLava.isEnabled();
        return selectedBlocks.contains(block);
    }

    public boolean isRealMode() {
        return realXray.isEnabled();
    }

    /**
     * Yoshi bo'lib qarayadi: real hide faqat Background Opacity == 0 bo'lganda qo'llanadi.
     * Opacity > 0 "dim" rejimi: dunyo normal ko'rinadi va ekranga qora parda tushadi.
     */
    public boolean isRealHiding() {
        return realXray.isEnabled() && getBackgroundOpacity() == 0;
    }

    public boolean markersEnabled() {
        return markers.isEnabled();
    }

    public int getBackgroundOpacity() {
        return backgroundOpacity.getValue().intValue();
    }

    public boolean isExposedOnly() {
        return exposedOnly.isEnabled();
    }

    public boolean isFullBright() {
        return fullBright.isEnabled();
    }

    public static int colorFor(Block block) {
        if (block == Blocks.DIAMOND_ORE || block == Blocks.DEEPSLATE_DIAMOND_ORE || block == Blocks.DIAMOND_BLOCK) return 0xFF00E5FF;
        if (block == Blocks.EMERALD_ORE || block == Blocks.DEEPSLATE_EMERALD_ORE || block == Blocks.EMERALD_BLOCK) return 0xFF00E676;
        if (block == Blocks.ANCIENT_DEBRIS || block == Blocks.NETHERITE_BLOCK) return 0xFFFF3D00;
        if (block == Blocks.GOLD_ORE || block == Blocks.DEEPSLATE_GOLD_ORE || block == Blocks.GOLD_BLOCK || block == Blocks.NETHER_GOLD_ORE || block == Blocks.RAW_GOLD_BLOCK) return 0xFFFFD700;
        if (block == Blocks.IRON_ORE || block == Blocks.DEEPSLATE_IRON_ORE || block == Blocks.IRON_BLOCK || block == Blocks.RAW_IRON_BLOCK) return 0xFFD7CCC8;
        if (block == Blocks.LAPIS_ORE || block == Blocks.DEEPSLATE_LAPIS_ORE || block == Blocks.LAPIS_BLOCK) return 0xFF2979FF;
        if (block == Blocks.REDSTONE_ORE || block == Blocks.DEEPSLATE_REDSTONE_ORE || block == Blocks.REDSTONE_BLOCK) return 0xFFFF1744;
        if (block == Blocks.COPPER_ORE || block == Blocks.DEEPSLATE_COPPER_ORE || block == Blocks.COPPER_BLOCK || block == Blocks.RAW_COPPER_BLOCK) return 0xFFFF9100;
        if (block == Blocks.COAL_ORE || block == Blocks.DEEPSLATE_COAL_ORE || block == Blocks.COAL_BLOCK) return 0xFF616161;
        if (block == Blocks.NETHER_QUARTZ_ORE || block == Blocks.QUARTZ_BLOCK) return 0xFFEEEEEE;
        if (block == Blocks.CHEST || block == Blocks.TRAPPED_CHEST || block == Blocks.BARREL) return 0xFFFFA000;
        if (block == Blocks.ENDER_CHEST || block == Blocks.SHULKER_BOX) return 0xFFB388FF;
        if (block == Blocks.SPAWNER || block == Blocks.TRIAL_SPAWNER || block == Blocks.VAULT) return 0xFFFF6D00;
        if (block == Blocks.END_PORTAL_FRAME || block == Blocks.END_PORTAL) return 0xFF76FF03;
        return 0xFFFF8800;
    }

    public static boolean isValuable(Block block) {
        return block == Blocks.DIAMOND_ORE || block == Blocks.DEEPSLATE_DIAMOND_ORE || block == Blocks.DIAMOND_BLOCK
                || block == Blocks.ANCIENT_DEBRIS || block == Blocks.NETHERITE_BLOCK
                || block == Blocks.EMERALD_ORE || block == Blocks.DEEPSLATE_EMERALD_ORE || block == Blocks.EMERALD_BLOCK
                || block == Blocks.SPAWNER || block == Blocks.TRIAL_SPAWNER || block == Blocks.VAULT;
    }

    @Override
    public void onEnable() {
        cache.clear();
        centreX = Integer.MIN_VALUE;

        // Apply fullbright (ambient = vurik116 icon ko'rinmaydi)
        if (fullBright.isEnabled()) {
            try {
                savedGamma = mc.options.getGamma().getValue();
                ((GammaAccessor) (Object) mc.options.getGamma()).setRawValue(gamma.getValue());
                gammaApplied = true;
                StatusEffectInstance cur = mc.player == null ? null : mc.player.getStatusEffect(StatusEffects.NIGHT_VISION);
                if (mc.player != null && (cur == null || cur.getDuration() < 200)) {
                    mc.player.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 600, 0, true, false, false));
                    nightVisionGranted = true;
                }
            } catch (Throwable ignored) {}
        }

        rememberRenderState();
        if (mc.worldRenderer != null) {
            mc.worldRenderer.reload();
        }
    }

    @Override
    public void onDisable() {
        cache.clear();
        markerScanner.resetCylinder();
        centreX = Integer.MIN_VALUE;

        // Restore gamma & remove night vision
        if (gammaApplied) {
            try {
                ((GammaAccessor) (Object) mc.options.getGamma()).setRawValue(savedGamma);
                gammaApplied = false;
            } catch (Throwable ignored) {}
        }
        // Only take back the effect this module added - a night vision potion the player drank
        // must survive X-Ray being switched off.
        try {
            if (nightVisionGranted && mc.player != null) {
                mc.player.removeStatusEffect(StatusEffects.NIGHT_VISION);
            }
            nightVisionGranted = false;
        } catch (Throwable ignored) {}

        if (mc.worldRenderer != null) {
            mc.worldRenderer.reload();
        }
    }

    /**
     * Toggling Real XRay, Show Water/Lava or Background Opacity changes which blocks the mesher
     * must draw. Without this the change only appeared after a manual F3+A, which made the
     * module look broken.
     */
    private void syncRenderState() {
        boolean hiding = isRealHiding();
        boolean water = showWater.isEnabled();
        boolean lava = showLava.isEnabled();
        boolean exposed = exposedOnly.isEnabled();
        int opacity = getBackgroundOpacity();
        if (hiding == lastHiding && water == lastWater && lava == lastLava
                && exposed == lastExposed && opacity == lastOpacity) {
            return;
        }
        rememberRenderState();
        if (mc.worldRenderer != null) {
            mc.worldRenderer.reload();
        }
    }

    private void rememberRenderState() {
        lastHiding = isRealHiding();
        lastWater = showWater.isEnabled();
        lastLava = showLava.isEnabled();
        lastExposed = exposedOnly.isEnabled();
        lastOpacity = getBackgroundOpacity();
    }

    @Override
    public void onTick() {
        syncRenderState();
        if (fullBright.isEnabled() && mc.player != null) {
            try {
                ((GammaAccessor) (Object) mc.options.getGamma()).setRawValue(gamma.getValue());
                // Ikki marta yoqilgach iknota spam qilmaslik: faqat qisqarganda yangilanadi,
                // shuning uchun efficient bo'L + HUD effect iconi butunlay ko'rinmaydi.
                StatusEffectInstance cur = mc.player.getStatusEffect(StatusEffects.NIGHT_VISION);
                if (cur == null || cur.getDuration() < 200) {
                    mc.player.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 600, 0, true, false, false));
                    nightVisionGranted = true;
                }
            } catch (Throwable ignored) {}
        }

        if (!markersEnabled() || mc.player == null || mc.world == null) return;

        BlockPos origin = mc.player.getBlockPos();
        int r = markerRange.getValue().intValue();
        int vr = Math.min(64, Math.max(16, r));
        int rr = r * r;

        // 1. Instant check of chunk BlockEntities (Spawners, Chests, etc.)
        int minChunkX = (origin.getX() - r) >> 4;
        int maxChunkX = (origin.getX() + r) >> 4;
        int minChunkZ = (origin.getZ() - r) >> 4;
        int maxChunkZ = (origin.getZ() + r) >> 4;

        try {
            for (int cx = minChunkX; cx <= maxChunkX; cx++) {
                for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                    if (!mc.world.isChunkLoaded(cx, cz)) continue;
                    WorldChunk chunk = mc.world.getChunk(cx, cz);
                    if (chunk == null) continue;

                    for (net.minecraft.block.entity.BlockEntity be : chunk.getBlockEntities().values()) {
                        BlockPos pos = be.getPos();
                        int dx = pos.getX() - origin.getX();
                        int dz = pos.getZ() - origin.getZ();
                        if (dx * dx + dz * dz > rr || Math.abs(pos.getY() - origin.getY()) > vr) continue;

                        Block b = chunk.getBlockState(pos).getBlock();
                        if (selectedBlocks.contains(b)) {
                            int col = coloredMarkers.isEnabled() ? colorFor(b) : 0xFFFF8800;
                            cache.put(pos.toImmutable(), col);
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}

        // 2. Budgeted cylinder sweep for embedded mineral ore blocks
        if (centreX == Integer.MIN_VALUE
                || Math.abs(origin.getX() - centreX) > 8
                || Math.abs(origin.getY() - centreY) > 8
                || Math.abs(origin.getZ() - centreZ) > 8) {
            centreX = origin.getX();
            centreY = origin.getY();
            centreZ = origin.getZ();
            markerScanner.resetCylinder();
        }

        // Clean cache entries
        cache.entrySet().removeIf(e -> {
            BlockPos p = e.getKey();
            int dx = p.getX() - origin.getX();
            int dz = p.getZ() - origin.getZ();
            if (dx * dx + dz * dz > rr || Math.abs(p.getY() - origin.getY()) > vr + 2) return true;
            if (mc.world.isChunkLoaded(p)) {
                return !selectedBlocks.contains(mc.world.getBlockState(p).getBlock());
            }
            return false;
        });

        BlockPos.Mutable scratch = new BlockPos.Mutable();
        try {
            markerScanner.scanCylinder(mc.world, centreX, centreY, centreZ, r, vr, 6000, scratch,
                    state -> !state.isAir() && selectedBlocks.contains(state.getBlock()),
                    (pos, state) -> {
                        if (cache.size() >= maxRendered.getValue().intValue()) return;
                        Block b = state.getBlock();
                        if (b == Blocks.WATER || b == Blocks.LAVA) return;
                        BlockPos imm = pos.toImmutable();
                        if (exposedOnly.isEnabled() && !exposed(imm)) return;
                        int col = coloredMarkers.isEnabled() ? colorFor(b) : 0xFFFF8800;
                        cache.put(imm, col);
                    });
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (!markersEnabled() || mc.player == null || mc.world == null || cache.isEmpty()) return;

        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();

        // "Dim" rejimi (Background Opacity > 0): dunyo normal chiziladi, ustiga qora parda
        // tiklanadi — ore kontulari ostida yanada yorqin bo'ladi. Hiding faqat opacity == 0'da.
        int bg = getBackgroundOpacity();
        if (bg > 0) {
            context.fill(0, 0, sw, sh, bg << 24);
        }
        int limit = maxRendered.getValue().intValue();
        int drawn = 0;

        Vec3d eye = mc.player.getEyePos();
        List<Map.Entry<BlockPos, Integer>> list = new ArrayList<>(cache.entrySet());
        list.sort(Comparator.comparingDouble(e -> e.getKey().getSquaredDistance(eye.x, eye.y, eye.z)));

        for (Map.Entry<BlockPos, Integer> e : list) {
            if (drawn >= limit) break;
            BlockPos p = e.getKey();
            int col = e.getValue();

            // 1. 3D Wireframe outline
            if (outlines.isEnabled()) {
                int[] bounds = RenderUtils.projectBlockBounds(p, tickDelta);
                if (bounds != null) {
                    RenderUtils.drawBlockOutline(context, p, col, tickDelta, 1);
                    drawn++;

                    // Distance text for high-value blocks
                    if (showDistance.isEnabled() && mc.textRenderer != null) {
                        Block b = mc.world.getBlockState(p).getBlock();
                        if (isValuable(b)) {
                            double dist = Math.sqrt(p.getSquaredDistance(eye.x, eye.y, eye.z));
                            String tag = "§7" + (int) dist + "m";
                            int cx = (bounds[0] + bounds[2]) / 2;
                            RenderUtils.drawText(context, mc.textRenderer, tag, cx - mc.textRenderer.getWidth(tag) / 2, bounds[1] - 9, col, true);
                        }
                    }
                }
            } else {
                int[] s = ProjectionUtil.project(new Vec3d(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5), tickDelta);
                if (s != null && s[0] >= -20 && s[0] <= sw + 20 && s[1] >= -20 && s[1] <= sh + 20) {
                    double dist = Math.sqrt(p.getSquaredDistance(eye.x, eye.y, eye.z));
                    int half = Math.max(2, Math.min(5, (int) (50.0 / Math.max(1.0, dist))));
                    context.fill(s[0] - half, s[1] - half, s[0] + half, s[1] + half, (col & 0x00FFFFFF) | 0x88000000);
                    RenderUtils.drawBorder(context, s[0] - half, s[1] - half, s[0] + half, s[1] + half, 1, col);
                    drawn++;
                }
            }

            // 2. Tracers to valuable ores
            if (tracers.isEnabled()) {
                Block b = mc.world.getBlockState(p).getBlock();
                if (isValuable(b)) {
                    int[] center = ProjectionUtil.project(new Vec3d(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5), tickDelta);
                    if (center != null) {
                        // One batched line instead of one fill per pixel: hundreds of traced ores
                        // used to cost tens of thousands of fills per frame.
                        RenderUtils.drawLine(context, sw / 2, sh / 2, center[0], center[1], col, 1.0f);
                    }
                }
            }
        }
    }

    public boolean shouldRenderBlock(BlockState state, BlockPos pos) {
        Block block = state.getBlock();
        if (!isVisibleBlock(block)) return false;

        if (exposedOnly.isEnabled()) {
            for (net.minecraft.util.math.Direction d : net.minecraft.util.math.Direction.values()) {
                try {
                    BlockState neighbor = mc.world.getBlockState(pos.offset(d));
                    if (!neighbor.isSolid() && !neighbor.getFluidState().isStill()) {
                        return true;
                    }
                } catch (Throwable ignored) {}
            }
            return false;
        }

        return true;
    }

    private boolean exposed(BlockPos p) {
        try {
            for (net.minecraft.util.math.Direction d : net.minecraft.util.math.Direction.values()) {
                try {
                    if (mc.world.getBlockState(p.offset(d)).isAir()) return true;
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
        return false;
    }
}