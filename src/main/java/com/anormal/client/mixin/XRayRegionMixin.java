package com.anormal.client.mixin;

import com.anormal.client.module.ModuleManager;
import com.anormal.client.module.impl.world.XRay;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.render.chunk.ChunkRendererRegion;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Real X-Ray at the chunk mesher, the only place that still works in 1.21.11.
 *
 * <p>The old hook targeted {@code BlockModelRenderer.shouldDrawFace}, a method that no longer
 * exists in this version, so it never applied and X-Ray did nothing. The modern section mesher
 * ({@code SectionBuilder}) reads every block through {@link ChunkRendererRegion}, so replacing a
 * hidden block with air here both removes its geometry and makes its neighbours render their
 * faces: an ore buried in stone is no longer culled by the stone around it.</p>
 *
 * <p>The hook only rewrites the mesher's view of the world. Real block states, collisions,
 * lighting and block entities are untouched, and nothing happens in "dim" mode (Background
 * Opacity > 0) or when the whitelist is empty, so the world can never go blank by accident.</p>
 */
@Mixin(ChunkRendererRegion.class)
public class XRayRegionMixin {

    @Inject(method = "getBlockState", at = @At("RETURN"), cancellable = true, require = 0)
    private void anormal$hideBlock(BlockPos pos, CallbackInfoReturnable<BlockState> cir) {
        try {
            BlockState state = cir.getReturnValue();
            if (state == null || state.isAir()) return;
            XRay xray = hidingXray();
            if (xray == null) return;
            if (!xray.isVisibleBlock(state.getBlock())) {
                cir.setReturnValue(Blocks.AIR.getDefaultState());
            }
        } catch (Throwable ignored) {
        }
    }

    @Inject(method = "getFluidState", at = @At("RETURN"), cancellable = true, require = 0)
    private void anormal$hideFluid(BlockPos pos, CallbackInfoReturnable<FluidState> cir) {
        try {
            FluidState fluid = cir.getReturnValue();
            if (fluid == null || fluid.isEmpty()) return;
            XRay xray = hidingXray();
            if (xray == null) return;
            if (!xray.isVisibleBlock(fluid.getBlockState().getBlock())) {
                cir.setReturnValue(Fluids.EMPTY.getDefaultState());
            }
        } catch (Throwable ignored) {
        }
    }

    /** The X-Ray module, but only while it is actually hiding blocks. */
    private static XRay hidingXray() {
        XRay xray = ModuleManager.getModule(XRay.class);
        if (xray == null || !xray.isEnabled() || !xray.isRealHiding()) return null;
        if (xray.getSelectedBlocks().isEmpty()) return null;
        return xray;
    }
}
