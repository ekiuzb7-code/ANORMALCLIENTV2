package com.anormal.client.module.impl.render;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.BlockScanner;
import com.anormal.client.util.ColorUtils;
import com.anormal.client.util.ProjectionUtil;
import com.anormal.client.util.RenderUtils;
import net.minecraft.block.BlockState;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * Marks villages with villager and bed counts.
 *
 * <p>Fixes over the old version: the villager scan actually honours {@code Range} (it only used
 * {@code Max Distance}), the bed counter resets once per scanner sweep instead of growing without
 * a bound, and the label uses plain text instead of emoji glyphs the vanilla font cannot draw.</p>
 */
public class VillageESP extends Module {
    public final BooleanSetting showName = new BooleanSetting("Show Name", "Show VILLAGE label", true);
    public final BooleanSetting showDistance = new BooleanSetting("Show Distance", "Show distance", true);
    public final BooleanSetting showCoords = new BooleanSetting("Show Coordinates", "Show coordinates", true);
    public final BooleanSetting showVillagers = new BooleanSetting("Show Villager Count", "Show villager count", true);
    public final BooleanSetting showBeds = new BooleanSetting("Show Bed Count", "Show bed count", true);
    public final BooleanSetting box = new BooleanSetting("Box", "Draw a 3D block outline", true);
    public final BooleanSetting tracer = new BooleanSetting("Tracer", "Line from crosshair", false);
    public final NumberSetting range = new NumberSetting("Range", "Villager search radius", 64.0, 16.0, 160.0, 8.0);
    public final NumberSetting maxDistance = new NumberSetting("Max Distance", "Skip beyond this", 800.0, 50.0, 2000.0, 50.0);
    public final NumberSetting bedScanRange = new NumberSetting("Bed Scan Range", "Bed search radius around village", 32.0, 8.0, 64.0, 4.0);
    public final NumberSetting bedVerticalRange = new NumberSetting("Bed Vertical Range", "How far up/down to count beds", 24.0, 8.0, 80.0, 4.0);
    public final ColorSetting color = new ColorSetting("Color", "Marker color", ColorUtils.rgba(85, 255, 85, 255));

    private final BlockScanner scanner = new BlockScanner();

    private BlockPos villagePos = null;
    private int villagers = 0;
    private int beds = 0;
    private int pendingBeds = 0;
    private int ticks = 0;
    private int bedCentreX = Integer.MIN_VALUE, bedCentreY, bedCentreZ;

    public VillageESP() {
        super("VillageESP", "Marks villages with villager and bed counts", Category.RENDER);
        addSetting(showName);
        addSetting(showDistance);
        addSetting(showCoords);
        addSetting(showVillagers);
        addSetting(showBeds);
        addSetting(box);
        addSetting(tracer);
        addSetting(range);
        addSetting(maxDistance);
        addSetting(bedScanRange);
        addSetting(bedVerticalRange);
        addSetting(color);
    }

    @Override
    public void onDisable() {
        villagePos = null;
        villagers = 0;
        beds = 0;
        pendingBeds = 0;
        scanner.resetCylinder();
        bedCentreX = Integer.MIN_VALUE;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;
        if (++ticks < 20) return;
        ticks = 0;

        villagePos = null;
        villagers = 0;
        double limit = Math.min(range.getValue(), maxDistance.getValue());
        double best = limit;
        try {
            for (Entity e : mc.world.getEntities()) {
                if (e instanceof VillagerEntity && e.isAlive()) {
                    double d = mc.player.distanceTo(e);
                    if (d <= limit) {
                        villagers++;
                        if (d < best) {
                            best = d;
                            villagePos = e.getBlockPos();
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }

        if (showBeds.isEnabled() && villagePos != null) {
            tickBedScan();
        } else {
            beds = 0;
            pendingBeds = 0;
            bedCentreX = Integer.MIN_VALUE;
        }
    }

    private void tickBedScan() {
        int r = bedScanRange.getValue().intValue();
        int vr = bedVerticalRange.getValue().intValue();
        BlockPos origin = villagePos;

        if (bedCentreX == Integer.MIN_VALUE
                || Math.abs(origin.getX() - bedCentreX) > 4
                || Math.abs(origin.getY() - bedCentreY) > 4
                || Math.abs(origin.getZ() - bedCentreZ) > 4) {
            bedCentreX = origin.getX();
            bedCentreY = origin.getY();
            bedCentreZ = origin.getZ();
            scanner.resetCylinder();
            beds = 0;
            pendingBeds = 0;
        }

        BlockPos.Mutable scratch = new BlockPos.Mutable();
        try {
            scanner.scanCylinder(mc.world, bedCentreX, bedCentreY, bedCentreZ, r, vr,
                    2000, scratch,
                    VillageESP::isBed,
                    new BlockScanner.Callback() {
                        @Override
                        public void accept(BlockPos.Mutable pos, BlockState state) {
                            pendingBeds++;
                        }

                        @Override
                        public void onSweepComplete() {
                            beds = pendingBeds;
                            pendingBeds = 0;
                        }
                    });
        } catch (Throwable ignored) {
        }
    }

    private static boolean isBed(BlockState state) {
        String path = Registries.BLOCK.getId(state.getBlock()).getPath();
        return path.contains("bed") && !path.contains("bedrock");
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.player == null || villagePos == null) return;

        int col = color.getValue();
        if (box.isEnabled()) {
            RenderUtils.drawBlockOutline(context, villagePos, col, tickDelta, 1);
        }

        int[] sc = ProjectionUtil.project(
                new Vec3d(villagePos.getX() + 0.5, villagePos.getY() + 2.4, villagePos.getZ() + 0.5), tickDelta);
        if (sc == null) return;

        if (tracer.isEnabled()) {
            RenderUtils.drawLine(context, RenderUtils.screenWidth() / 2, RenderUtils.screenHeight() / 2,
                    sc[0], sc[1], (col & 0x00FFFFFF) | 0x80000000, 1.0f);
        }
        if (mc.textRenderer == null) return;

        double dx = villagePos.getX() - mc.player.getX();
        double dy = villagePos.getY() - mc.player.getY();
        double dz = villagePos.getZ() - mc.player.getZ();
        int d = (int) Math.sqrt(dx * dx + dy * dy + dz * dz);

        StringBuilder label = new StringBuilder();
        if (showName.isEnabled()) label.append("VILLAGE");
        if (showDistance.isEnabled()) {
            if (label.length() > 0) label.append("  ");
            label.append(d).append('m');
        }
        if (showCoords.isEnabled()) {
            if (label.length() > 0) label.append("  ");
            label.append(villagePos.getX()).append(' ').append(villagePos.getY()).append(' ')
                    .append(villagePos.getZ());
        }
        if (showVillagers.isEnabled()) {
            if (label.length() > 0) label.append("  ");
            label.append("V: ").append(villagers);
        }
        if (showBeds.isEnabled()) {
            if (label.length() > 0) label.append("  ");
            label.append("Beds: ").append(beds);
        }
        if (label.length() > 0) {
            RenderUtils.drawChip(context, mc.textRenderer, label.toString(), sc[0], sc[1] + 4, col);
        }
    }
}
