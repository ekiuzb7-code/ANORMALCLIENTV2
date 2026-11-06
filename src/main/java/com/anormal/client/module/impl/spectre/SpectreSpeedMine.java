package com.anormal.client.module.impl.spectre;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.module.ModuleManager;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.BlockPos;

/**
 * Spectre port: "Donut Speed Mine" — finishes breaking blocks before the full break time.
 *
 * <p>{@link com.anormal.client.mixin.SpectreMiningMixin} scales
 * {@code AbstractBlockState.calcBlockBreakingDelta} by 1/breakAt (the visual bar and the
 * sent dig packets both follow it) and
 * {@link com.anormal.client.mixin.SpectreSpeedMineMixin} zeroes the vanilla post-break
 * {@code blockBreakingCooldown} the same way Spectre's {@code dropsDelay} hook does.</p>
 */
public final class SpectreSpeedMine extends Module {
    public final NumberSetting breakAt = addSetting(new NumberSetting("Break At", "Send the break at this progress", 0.7, 0.3, 1.0, 0.01));
    public final BooleanSetting skipDelay = addSetting(new BooleanSetting("Skip Break Delay", "Cut the post-break delay", true));
    public final BooleanSetting keepInstantDelay = addSetting(new BooleanSetting("Keep Instant Delay", "Keep instant-mineable blocks vanilla-fast", true));

    /** Last raw per-tick destroy progress seen by the mining mixin, with its block. */
    private static BlockPos lastPos;
    private static BlockState lastState;
    private static float lastRawProgress;

    public SpectreSpeedMine() {
        super("SpectreSpeedMine", "Spectre port: finishes breaking blocks before the full break time.", Category.SPECTRE);
    }

    /** Yarn port of Spectre's affects(): spawner/gravel/axe+shovel-minable are skipped. */
    private static boolean affects(BlockState state) {
        if (state.isAir() || state.isOf(Blocks.SPAWNER)) return false;
        if (state.isOf(Blocks.GRAVEL)) return true;
        return !state.isIn(BlockTags.SHOVEL_MINEABLE) && !state.isIn(BlockTags.AXE_MINEABLE);
    }

    /** Called from SpectreMiningMixin at calcBlockBreakingDelta RETURN, before scaling. */
    public static void recordRaw(BlockState state, BlockPos pos, float rawProgress) {
        lastState = state;
        lastPos = pos.toImmutable();
        lastRawProgress = rawProgress;
    }

    /** Called from SpectreMiningMixin at calcBlockBreakingDelta RETURN. */
    public static float scaleProgress(BlockState state, float progress) {
        SpectreSpeedMine m = ModuleManager.getModule(SpectreSpeedMine.class);
        if (m == null || !m.isEnabled() || !affects(state)) return progress;
        return Math.min(1.0f, progress / (float) m.breakAt.getAsDouble());
    }

    /**
     * Port of Spectre's {@code dropsDelay(state, progress)}: drop the vanilla post-break
     * cooldown for the block currently being mined. {@code keepInstantDelay} keeps the
     * delay for blocks that already break in a single tick.
     */
    public static boolean dropsDelayAt(BlockPos pos) {
        SpectreSpeedMine m = ModuleManager.getModule(SpectreSpeedMine.class);
        if (m == null || !m.isEnabled() || !m.skipDelay.isEnabled()) return false;
        if (lastState == null || lastPos == null || !lastPos.equals(pos)) return false;
        if (!affects(lastState)) return false;
        return !m.keepInstantDelay.isEnabled() || lastRawProgress < 1.0f;
    }
}
