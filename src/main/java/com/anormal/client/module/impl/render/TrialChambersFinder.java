package com.anormal.client.module.impl.render;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.ColorUtils;
import com.anormal.client.util.ProjectionUtil;
import com.anormal.client.util.RenderUtils;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.WorldChunk;

import java.util.*;

/**
 * Highlights genuine trial chamber structures (spawners, vaults, copper bulbs, tuff bricks)
 * through walls with wireframes, distance labels, and tracers.
 *
 * <p>Upgraded to instant chunk-level BlockEntity discovery: detects trial spawners and vaults
 * in 0 milliseconds upon toggling ON without multi-second delays.</p>
 */
public class TrialChambersFinder extends Module {
    private static final long RENDER_BUDGET_NS = 10_000_000L; // 10 ms

    // --- Scan ---
    public final NumberSetting range = new NumberSetting("Range", "Horizontal scan radius in blocks", 96.0, 16.0, 256.0, 8.0);
    public final NumberSetting verticalRange = new NumberSetting("Vertical Range", "How far up/down to scan", 64.0, 16.0, 128.0, 8.0);

    // --- Render ---
    public final BooleanSetting outline = new BooleanSetting("Outline", "Draw block outlines through walls", true);
    public final BooleanSetting centerDot = new BooleanSetting("Center Dot", "Also draw a dot in the middle of each block", false);
    public final BooleanSetting distance = new BooleanSetting("Show Distance", "Show distance next to spawners and vaults", true);
    public final BooleanSetting tracers = new BooleanSetting("Tracers", "Draw tracer lines to trial spawners and vaults", true);
    public final NumberSetting maxRendered = new NumberSetting("Max Rendered", "Max blocks outlined at once", 400.0, 25.0, 1500.0, 25.0);

    // --- Per-type toggles ---
    public final BooleanSetting showSpawners = new BooleanSetting("Trial Spawners", "Mark trial spawners", true);
    public final BooleanSetting showVaults = new BooleanSetting("Vaults", "Mark vaults (incl. ominous)", true);
    public final BooleanSetting showCopper = new BooleanSetting("Copper", "Mark copper bulbs, grates and doors", true);
    public final BooleanSetting showTuff = new BooleanSetting("Tuff Bricks", "Mark tuff bricks and polished tuff", false);
    public final BooleanSetting showDeepslate = new BooleanSetting("Deepslate Bricks", "Mark deepslate bricks and tiles", false);
    public final BooleanSetting showMisc = new BooleanSetting("Misc", "Mark heavy core, pots, crafters", true);

    // --- Colours ---
    public final ColorSetting spawnerColor = new ColorSetting("Spawner Color", "Trial spawner color", ColorUtils.rgba(255, 120, 0, 255));
    public final ColorSetting vaultColor = new ColorSetting("Vault Color", "Vault color", ColorUtils.rgba(0, 230, 255, 255));
    public final ColorSetting copperColor = new ColorSetting("Copper Color", "Copper color", ColorUtils.rgba(255, 140, 90, 255));
    public final ColorSetting tuffColor = new ColorSetting("Tuff Color", "Tuff bricks color", ColorUtils.rgba(160, 160, 140, 255));
    public final ColorSetting deepslateColor = new ColorSetting("Deepslate Color", "Deepslate bricks color", ColorUtils.rgba(130, 130, 150, 255));
    public final ColorSetting otherColor = new ColorSetting("Other Color", "Misc chamber blocks color", ColorUtils.rgba(120, 220, 120, 255));

    public final BooleanSetting chatLog = new BooleanSetting("Chat Log", "Announce a new trial chamber once in chat", true);

    // Categories
    public static final int CAT_SPAWNER = 1;
    public static final int CAT_VAULT = 2;
    public static final int CAT_COPPER = 3;
    public static final int CAT_TUFF = 4;
    public static final int CAT_DEEPSLATE = 5;
    public static final int CAT_MISC = 6;

    private final Map<Block, Integer> blockCategories = new HashMap<>();
    private final Map<BlockPos, Integer> found = new HashMap<>();
    private final Set<String> logged = new HashSet<>();
    private int tickCounter = 0;

    public TrialChambersFinder() {
        super("TrialChambersFinder", "Highlights genuine trial chamber structures through walls", Category.RENDER);
        addSetting(range);
        addSetting(verticalRange);
        addSetting(outline);
        addSetting(centerDot);
        addSetting(distance);
        addSetting(tracers);
        addSetting(maxRendered);
        addSetting(showSpawners);
        addSetting(showVaults);
        addSetting(showCopper);
        addSetting(showTuff);
        addSetting(showDeepslate);
        addSetting(showMisc);
        addSetting(spawnerColor);
        addSetting(vaultColor);
        addSetting(copperColor);
        addSetting(tuffColor);
        addSetting(deepslateColor);
        addSetting(otherColor);
        addSetting(chatLog);
        buildBlockTable();
    }

    private void buildBlockTable() {
        blockCategories.clear();

        // 1. Core Trial Chamber Unique Blocks
        register(CAT_SPAWNER, "trial_spawner");
        register(CAT_VAULT, "vault", "ominous_vault");

        // 2. Copper Features Unique to Trial Chambers (Bulbs, Grates, Doors, Chiseled)
        String[] bulbVariants = {
                "copper_bulb", "waxed_copper_bulb",
                "exposed_copper_bulb", "waxed_exposed_copper_bulb",
                "weathered_copper_bulb", "waxed_weathered_copper_bulb",
                "oxidized_copper_bulb", "waxed_oxidized_copper_bulb"
        };
        for (String id : bulbVariants) register(CAT_COPPER, id);

        String[] grateVariants = {
                "copper_grate", "waxed_copper_grate",
                "exposed_copper_grate", "waxed_exposed_copper_grate",
                "weathered_copper_grate", "waxed_weathered_copper_grate",
                "oxidized_copper_grate", "waxed_oxidized_copper_grate"
        };
        for (String id : grateVariants) register(CAT_COPPER, id);

        String[] copperDoors = {
                "copper_door", "waxed_copper_door",
                "exposed_copper_door", "waxed_exposed_copper_door",
                "weathered_copper_door", "waxed_weathered_copper_door",
                "oxidized_copper_door", "waxed_oxidized_copper_door",
                "copper_trapdoor", "waxed_copper_trapdoor",
                "chiseled_copper", "waxed_chiseled_copper"
        };
        for (String id : copperDoors) register(CAT_COPPER, id);

        // 3. Tuff Bricks (only craft/cut variants)
        register(CAT_TUFF,
                "tuff_bricks", "chiseled_tuff_bricks", "cracked_tuff_bricks",
                "tuff_brick_stairs", "tuff_brick_slab", "tuff_brick_wall",
                "polished_tuff", "polished_tuff_stairs", "polished_tuff_slab", "polished_tuff_wall",
                "chiseled_tuff");

        // 4. Deepslate Bricks/Tiles
        register(CAT_DEEPSLATE,
                "deepslate_bricks", "cracked_deepslate_bricks",
                "deepslate_tiles", "cracked_deepslate_tiles",
                "chiseled_deepslate");

        // 5. Misc
        register(CAT_MISC, "heavy_core", "crafter");
    }

    private void register(int category, String... ids) {
        for (String id : ids) {
            try {
                Block b = Registries.BLOCK.get(Identifier.of("minecraft:" + id));
                if (b != null) blockCategories.put(b, category);
            } catch (Throwable ignored) {
            }
        }
    }

    @Override
    public void onEnable() {
        found.clear();
        scanChunks();
    }

    @Override
    public void onDisable() {
        found.clear();
        logged.clear();
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;
        if (blockCategories.isEmpty()) buildBlockTable();

        // Scan every 5 ticks
        if (++tickCounter % 5 == 0) {
            scanChunks();
        }
    }

    private void scanChunks() {
        if (mc.player == null || mc.world == null) return;

        BlockPos origin = mc.player.getBlockPos();
        int r = range.getValue().intValue();
        int vr = verticalRange.getValue().intValue();
        int rr = r * r;

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

                    // 1. Instant check: read Trial Spawners & Vaults directly from BlockEntities
                    for (BlockEntity be : chunk.getBlockEntities().values()) {
                        BlockPos pos = be.getPos();
                        int dx = pos.getX() - origin.getX();
                        int dz = pos.getZ() - origin.getZ();
                        if (dx * dx + dz * dz > rr || Math.abs(pos.getY() - origin.getY()) > vr) continue;

                        Block b = chunk.getBlockState(pos).getBlock();
                        Integer cat = blockCategories.get(b);
                        if (cat != null && isCategoryEnabled(cat)) {
                            found.put(pos.toImmutable(), cat);
                        }
                    }

                    // 2. Targeted sample at Trial Chamber levels (Y = -40 to 0) for copper & tuff
                    if (showCopper.isEnabled() || showTuff.isEnabled() || showDeepslate.isEnabled() || showMisc.isEnabled()) {
                        BlockPos.Mutable p = new BlockPos.Mutable();
                        int baseX = cx << 4;
                        int baseZ = cz << 4;
                        for (int y = -40; y <= 5; y += 3) {
                            for (int x = 2; x < 16; x += 5) {
                                for (int z = 2; z < 16; z += 5) {
                                    p.set(baseX + x, y, baseZ + z);
                                    BlockState bs = chunk.getBlockState(p);
                                    if (bs.isAir()) continue;

                                    Integer cat = blockCategories.get(bs.getBlock());
                                    if (cat != null && isCategoryEnabled(cat)) {
                                        found.put(p.toImmutable(), cat);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }

        // Clean up out of range or removed blocks
        found.entrySet().removeIf(e -> {
            BlockPos p = e.getKey();
            int dx = p.getX() - origin.getX();
            int dz = p.getZ() - origin.getZ();
            if (dx * dx + dz * dz > rr + 256 || Math.abs(p.getY() - origin.getY()) > vr + 4) return true;
            if (!isCategoryEnabled(e.getValue())) return true;
            if (mc.world.isChunkLoaded(p)) {
                BlockState bs = mc.world.getBlockState(p);
                Integer cat = blockCategories.get(bs.getBlock());
                return cat == null || !cat.equals(e.getValue());
            }
            return false;
        });
    }

    private boolean isCategoryEnabled(int cat) {
        return switch (cat) {
            case CAT_SPAWNER -> showSpawners.isEnabled();
            case CAT_VAULT -> showVaults.isEnabled();
            case CAT_COPPER -> showCopper.isEnabled();
            case CAT_TUFF -> showTuff.isEnabled();
            case CAT_DEEPSLATE -> showDeepslate.isEnabled();
            case CAT_MISC -> showMisc.isEnabled();
            default -> true;
        };
    }

    private int colorFor(int cat) {
        return switch (cat) {
            case CAT_SPAWNER -> spawnerColor.getValue();
            case CAT_VAULT -> vaultColor.getValue();
            case CAT_COPPER -> copperColor.getValue();
            case CAT_TUFF -> tuffColor.getValue();
            case CAT_DEEPSLATE -> deepslateColor.getValue();
            default -> otherColor.getValue();
        };
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.player == null || found.isEmpty()) return;
        int limit = maxRendered.getValue().intValue();
        int drawn = 0;
        int checked = 0;
        long deadline = System.nanoTime() + RENDER_BUDGET_NS;

        BlockPos spawnerFirst = null;
        BlockPos playerBlockPos = mc.player.getBlockPos();

        // Sort: spawners and vaults FIRST, then nearest blocks
        List<Map.Entry<BlockPos, Integer>> sorted = new ArrayList<>(found.entrySet());
        sorted.sort((a, b) -> {
            boolean aCore = a.getValue() == CAT_SPAWNER || a.getValue() == CAT_VAULT;
            boolean bCore = b.getValue() == CAT_SPAWNER || b.getValue() == CAT_VAULT;
            if (aCore != bCore) return aCore ? -1 : 1;
            double d1 = a.getKey().getSquaredDistance(playerBlockPos);
            double d2 = b.getKey().getSquaredDistance(playerBlockPos);
            return Double.compare(d1, d2);
        });

        int w = mc.getWindow().getScaledWidth();
        int h = mc.getWindow().getScaledHeight();

        try {
            for (Map.Entry<BlockPos, Integer> e : sorted) {
                int cat = e.getValue();
                if (!isCategoryEnabled(cat)) continue;

                boolean isCore = (cat == CAT_SPAWNER || cat == CAT_VAULT);
                if (!isCore && drawn >= limit) break;
                if ((++checked & 31) == 0 && System.nanoTime() > deadline) break;

                BlockPos pos = e.getKey();
                int[] bounds = RenderUtils.projectBlockBounds(pos, tickDelta);
                if (bounds == null) continue;

                drawn++;
                int col = colorFor(cat);

                if (outline.isEnabled()) {
                    RenderUtils.drawBlockOutline(context, pos, col, tickDelta, isCore ? 2 : 1);
                }

                double x = pos.getX(), y = pos.getY(), z = pos.getZ();
                if (centerDot.isEnabled()) {
                    int[] mid = ProjectionUtil.project(new Vec3d(x + 0.5, y + 0.5, z + 0.5), tickDelta);
                    if (mid != null) context.fill(mid[0] - 2, mid[1] - 2, mid[0] + 2, mid[1] + 2, col);
                }

                int[] top = ProjectionUtil.project(new Vec3d(x + 0.5, y + 1.2, z + 0.5), tickDelta);
                if (top != null) {
                    if (isCore) {
                        if (spawnerFirst == null && cat == CAT_SPAWNER) spawnerFirst = pos;
                        if (tracers.isEnabled()) {
                            RenderUtils.drawLine(context, w / 2, h / 2, top[0], top[1], col, 1.0f);
                        }
                    }

                    if (distance.isEnabled() && mc.textRenderer != null && isCore) {
                        int dist = (int) Math.sqrt(pos.getSquaredDistance(mc.player.getBlockPos()));
                        String name = (cat == CAT_SPAWNER) ? "Trial Spawner" : "Vault";
                        String label = String.format("§6[%s] §f%dm", name, dist);
                        RenderUtils.drawText(context, mc.textRenderer, label, top[0] + 4, top[1] - 4, 0xFFFFFFFF, true);
                    }
                }
            }
        } catch (Throwable ignored) {
        }

        if (chatLog.isEnabled() && spawnerFirst != null && mc.inGameHud != null) {
            String key = (spawnerFirst.getX() >> 4) + "," + (spawnerFirst.getZ() >> 4);
            if (logged.add(key)) {
                try {
                    mc.inGameHud.getChatHud().addMessage(net.minecraft.text.Text.literal(
                            String.format("§6[Trial Chamber] §ffound near §e%d %d %d",
                                    spawnerFirst.getX(), spawnerFirst.getY(), spawnerFirst.getZ())));
                } catch (Throwable ignored) {
                }
            }
        }
    }
}
