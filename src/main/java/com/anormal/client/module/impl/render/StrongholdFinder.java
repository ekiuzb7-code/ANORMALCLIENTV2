package com.anormal.client.module.impl.render;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.ColorUtils;
import com.anormal.client.util.ProjectionUtil;
import com.anormal.client.util.RenderUtils;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.EndPortalFrameBlock;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.WorldChunk;

import java.util.*;

/**
 * Finds End Portal rooms marking Strongholds through walls.
 *
 * <p>Upgraded to instant chunk search: scans portal depths (Y = -35 to 40)
 * in loaded chunks, highlighting all 12 portal frames, eye count, and tracer lines.</p>
 */
public class StrongholdFinder extends Module {
    public final NumberSetting range = new NumberSetting("Range", "Horizontal search radius in blocks", 96.0, 16.0, 256.0, 8.0);
    public final BooleanSetting chatLog = new BooleanSetting("Chat Log", "Log newly found portal coordinates in chat", true);
    public final BooleanSetting outline = new BooleanSetting("Outline", "Draw 3D block outlines around portal frames", true);
    public final BooleanSetting tracers = new BooleanSetting("Tracers", "Draw line to portal center", true);
    public final BooleanSetting showDistance = new BooleanSetting("Show Distance", "Show distance and eye count tag", true);
    public final ColorSetting color = new ColorSetting("Color", "Portal frame marker color", ColorUtils.rgba(100, 255, 60, 255));
    public final NumberSetting maxShown = new NumberSetting("Max Shown", "Max portal frame markers drawn", 24.0, 1.0, 64.0, 1.0);

    private static final long RENDER_BUDGET_NS = 5_000_000L;

    private final Map<BlockPos, Boolean> frames = new HashMap<>(); // Pos -> hasEye
    private final Set<String> logged = new HashSet<>();
    private int tickCounter = 0;

    public StrongholdFinder() {
        super("StrongholdFinder", "Finds end portal frames and strongholds through walls", Category.RENDER);
        addSetting(range);
        addSetting(chatLog);
        addSetting(outline);
        addSetting(tracers);
        addSetting(showDistance);
        addSetting(color);
        addSetting(maxShown);
    }

    @Override
    public void onEnable() {
        frames.clear();
        scanChunksForPortal();
    }

    @Override
    public void onDisable() {
        frames.clear();
        logged.clear();
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;

        // Scan every 5 ticks
        if (++tickCounter % 5 == 0) {
            scanChunksForPortal();
        }
    }

    private void scanChunksForPortal() {
        if (mc.player == null || mc.world == null) return;

        BlockPos origin = mc.player.getBlockPos();
        int r = range.getValue().intValue();
        int rr = r * r;

        int minChunkX = (origin.getX() - r) >> 4;
        int maxChunkX = (origin.getX() + r) >> 4;
        int minChunkZ = (origin.getZ() - r) >> 4;
        int maxChunkZ = (origin.getZ() + r) >> 4;

        BlockPos.Mutable p = new BlockPos.Mutable();

        try {
            for (int cx = minChunkX; cx <= maxChunkX; cx++) {
                for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                    if (!mc.world.isChunkLoaded(cx, cz)) continue;
                    WorldChunk chunk = mc.world.getChunk(cx, cz);
                    if (chunk == null) continue;

                    int baseX = cx << 4;
                    int baseZ = cz << 4;

                    // Stronghold portal frames generate between Y = -35 and Y = 40
                    for (int y = -35; y <= 40; y++) {
                        for (int x = 0; x < 16; x++) {
                            for (int z = 0; z < 16; z++) {
                                p.set(baseX + x, y, baseZ + z);
                                BlockState state = chunk.getBlockState(p);
                                if (state.getBlock() == Blocks.END_PORTAL_FRAME || state.getBlock() == Blocks.END_PORTAL) {
                                    boolean hasEye = false;
                                    try {
                                        if (state.contains(EndPortalFrameBlock.EYE)) {
                                            hasEye = state.get(EndPortalFrameBlock.EYE);
                                        }
                                    } catch (Throwable ignored) {}

                                    BlockPos imm = p.toImmutable();
                                    frames.put(imm, hasEye);

                                    // Announce in chat
                                    if (chatLog.isEnabled() && mc.inGameHud != null) {
                                        String chunkKey = cx + "," + cz;
                                        if (logged.add(chunkKey)) {
                                            int dist = (int) Math.sqrt(origin.getSquaredDistance(imm));
                                            mc.inGameHud.getChatHud().addMessage(Text.literal(
                                                    "§a[Stronghold] §fEnd Portal found at §eX: " + imm.getX() + ", Y: " + imm.getY() + ", Z: " + imm.getZ() +
                                                            " §7(" + dist + "m)"));
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}

        // Remove out of range or destroyed frames
        frames.keySet().removeIf(pos -> {
            int dx = pos.getX() - origin.getX();
            int dz = pos.getZ() - origin.getZ();
            if (dx * dx + dz * dz > rr + 256) return true;
            if (mc.world.isChunkLoaded(pos)) {
                BlockState bs = mc.world.getBlockState(pos);
                return bs.getBlock() != Blocks.END_PORTAL_FRAME && bs.getBlock() != Blocks.END_PORTAL;
            }
            return false;
        });
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.player == null || frames.isEmpty()) return;

        int col = color.getValue();
        int limit = maxShown.getValue().intValue();
        int drawn = 0;
        long deadline = System.nanoTime() + RENDER_BUDGET_NS;

        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();

        // Calculate portal center
        double sumX = 0, sumY = 0, sumZ = 0;
        int eyeCount = 0;
        for (Map.Entry<BlockPos, Boolean> entry : frames.entrySet()) {
            BlockPos p = entry.getKey();
            sumX += p.getX();
            sumY += p.getY();
            sumZ += p.getZ();
            if (entry.getValue()) eyeCount++;
        }
        int count = frames.size();
        BlockPos portalCenter = new BlockPos((int) (sumX / count), (int) (sumY / count), (int) (sumZ / count));

        // 1. Draw 3D outline for each frame block
        for (BlockPos p : frames.keySet()) {
            if (drawn >= limit || System.nanoTime() > deadline) break;

            if (outline.isEnabled()) {
                int[] bounds = RenderUtils.projectBlockBounds(p, tickDelta);
                if (bounds != null) {
                    RenderUtils.drawBlockOutline(context, p, col, tickDelta, 1);
                    drawn++;
                }
            }
        }

        // 2. Tracer line to portal center
        int[] centerScreen = ProjectionUtil.project(new Vec3d(portalCenter.getX() + 0.5, portalCenter.getY() + 0.5, portalCenter.getZ() + 0.5), tickDelta);
        if (centerScreen != null) {
            if (tracers.isEnabled()) {
                int steps = Math.min(150, Math.max(Math.abs(centerScreen[0] - sw / 2), Math.abs(centerScreen[1] - sh / 2)));
                for (int i = 0; i <= steps; i++) {
                    int lx = sw / 2 + (centerScreen[0] - sw / 2) * i / Math.max(1, steps);
                    int ly = sh / 2 + (centerScreen[1] - sh / 2) * i / Math.max(1, steps);
                    context.fill(lx, ly, lx + 1, ly + 1, col);
                }
            }

            // 3. Waypoint label
            if (showDistance.isEnabled() && mc.textRenderer != null) {
                double dist = Math.sqrt(mc.player.getBlockPos().getSquaredDistance(portalCenter));
                String label = "§aStronghold Portal §7[" + eyeCount + "/12 Eyes] §f" + (int) dist + "m";
                RenderUtils.drawText(context, mc.textRenderer, label, centerScreen[0] - mc.textRenderer.getWidth(label) / 2, centerScreen[1] - 12, 0xFFFFFFFF, true);
            }
        }
    }
}
