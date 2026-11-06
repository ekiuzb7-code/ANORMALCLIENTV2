package com.anormal.client.module.impl.spectre;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.ColorUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.Box;

import java.util.HashSet;
import java.util.Set;

/**
 * Spectre port: "Render Util" — renders the chunks flagged by Quantum.
 *
 * <p>Spectre keeps a flagged set that Quantum fills and draws it as always-on gizmo cuboids
 * at y = 63. This port keeps the same sets, the same marker height and the same source-chunk
 * highlight, driven by {@code SusChunkFeedMixin} and drawn through the Spectre box renderer.</p>
 */
public final class SpectreRenderUtil extends Module {
    private static final double MARKER_Y = 63.0;
    private static final int CYAN = ColorUtils.rgba(0, 255, 255, 255);
    private static final int SOURCE_FILL = 0x2800FFFF;

    public final BooleanSetting clearOnToggle = addSetting(new BooleanSetting("Clear On Toggle", "Clear the markers when the module is toggled", true));
    public final BooleanSetting highlightSources = addSetting(new BooleanSetting("Highlight Source Chunks", "Also outline the chunks Quantum read from", true));
    public final ColorSetting fillColor = addSetting(new ColorSetting("Fill Color", "Marker fill", ColorUtils.rgba(60, 180, 255, 70)));
    public final ColorSetting lineColor = addSetting(new ColorSetting("Line Color", "Marker outline", CYAN));
    public final NumberSetting range = addSetting(new NumberSetting("Range", "Only draw markers within this range", 256.0, 16.0, 1024.0, 8.0));

    private static final Set<Long> FLAGGED = new HashSet<>();
    private static final Set<Long> SOURCES = new HashSet<>();
    private static SpectreRenderUtil instance;

    public SpectreRenderUtil() {
        super("SpectreRenderUtil", "Spectre port: renders the chunks flagged by Quantum.", Category.SPECTRE);
        instance = this;
    }

    @Override
    public void onEnable() {
        if (clearOnToggle.isEnabled()) {
            FLAGGED.clear();
            SOURCES.clear();
        }
    }

    @Override
    public void onDisable() {
        if (clearOnToggle.isEnabled()) {
            FLAGGED.clear();
            SOURCES.clear();
        }
    }

    /** Called from the chunk-data hook, mirroring Spectre's Quantum feed. */
    public static void flag(int chunkX, int chunkZ) {
        SpectreRenderUtil module = instance;
        if (module == null || !module.isEnabled()) return;
        FLAGGED.add(pack(chunkX, chunkZ));
        SOURCES.add(pack(chunkX, chunkZ));
    }

    public static boolean hasFlaggedChunks() {
        return !FLAGGED.isEmpty();
    }

    public static boolean isFlagged(int chunkX, int chunkZ) {
        return FLAGGED.contains(pack(chunkX, chunkZ));
    }

    public static void clearFlagged() {
        FLAGGED.clear();
        SOURCES.clear();
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (FLAGGED.isEmpty() || mc.player == null || mc.world == null) return;

        double max = range.getAsDouble();
        int line = lineColor.getColor();
        int fill = fillColor.getColor();
        for (long key : FLAGGED) {
            int cx = unpackX(key);
            int cz = unpackZ(key);
            double x = cx * 16.0;
            double z = cz * 16.0;
            if (Math.abs(x + 8.0 - mc.player.getX()) > max || Math.abs(z + 8.0 - mc.player.getZ()) > max) continue;
            Box box = new Box(x, MARKER_Y, z, x + 16.0, MARKER_Y, z + 16.0);
            SpectreHoleEsp.drawBox(context, tickDelta, box, line, fill, true, false, 1);
        }

        if (highlightSources.isEnabled()) {
            for (long key : SOURCES) {
                int cx = unpackX(key);
                int cz = unpackZ(key);
                double x = cx * 16.0;
                double z = cz * 16.0;
                Box box = new Box(x, MARKER_Y, z, x + 16.0, MARKER_Y, z + 16.0);
                SpectreHoleEsp.drawBox(context, tickDelta, box, CYAN, SOURCE_FILL, true, false, 1);
            }
        }
    }

    private static long pack(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    private static int unpackX(long key) {
        return (int) (key >> 32);
    }

    private static int unpackZ(long key) {
        return (int) key;
    }
}
