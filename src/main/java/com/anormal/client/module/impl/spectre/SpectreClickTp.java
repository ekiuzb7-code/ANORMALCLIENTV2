package com.anormal.client.module.impl.spectre;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/**
 * Spectre port: "Click TP" — teleports you to the block you click on.
 */
public final class SpectreClickTp extends Module {
    public final NumberSetting blocksPerPacket = addSetting(new NumberSetting("Blocks Per Packet",
            "One interpolated position packet per this many blocks", 10.0, 1.0, 64.0, 1.0));
    public final NumberSetting maxPadding = addSetting(new NumberSetting("Max Padding",
            "Cap on interpolated position packets per teleport", 19.0, 0.0, 64.0, 1.0));

    private boolean useWasDown;

    public SpectreClickTp() {
        super("SpectreClickTp", "Spectre port: teleports you to the block you click on.", Category.SPECTRE);
    }

    @Override
    public void onTick() {
        var player = mc.player;
        if (player == null || mc.world == null || mc.getNetworkHandler() == null) return;
        if (mc.currentScreen != null) return;

        boolean useDown = mc.options.useKey.isPressed();
        boolean pressed = useDown && !useWasDown;
        useWasDown = useDown;
        if (!pressed) return;
        // don't fight with item-usable items
        if (player.getMainHandStack().getUseAction() != net.minecraft.item.consume.UseAction.NONE) return;

        HitResult crosshair = mc.crosshairTarget;
        if (crosshair == null || crosshair.getType() != HitResult.Type.BLOCK) return;
        BlockHitResult hit = (BlockHitResult) crosshair;
        BlockPos target = hit.getBlockPos().offset(hit.getSide());
        // stand on top of the clicked block
        double tpX = target.getX() + 0.5;
        double tpY = target.getY();
        double tpZ = target.getZ() + 0.5;

        double distance = Math.sqrt(Math.pow(tpX - player.getX(), 2) + Math.pow(tpY - player.getY(), 2) + Math.pow(tpZ - player.getZ(), 2));
        double perPacket = Math.max(1.0, blocksPerPacket.getAsDouble());
        int paddingPackets = (int) Math.min((int) maxPadding.getAsDouble(), Math.floor(distance / perPacket));

        try {
            // Spectre: pad the path with interpolated position packets so antivirus anticheats
            // see a sane teleport instead of an instant jump
            double startX = player.getX();
            double startY = player.getY();
            double startZ = player.getZ();
            for (int i = 1; i <= paddingPackets; i++) {
                double t = (double) i / (paddingPackets + 1);
                double x = startX + (tpX - startX) * t;
                double y = startY + (tpY - startY) * t;
                double z = startZ + (tpZ - startZ) * t;
                mc.getNetworkHandler().sendPacket(new net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.Full(
                        x, y, z, player.getYaw(), player.getPitch(), false, false));
            }
            player.setPosition(tpX, tpY, tpZ);
            player.setVelocity(0, 0, 0);
            player.fallDistance = 0;
            mc.getNetworkHandler().sendPacket(new net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.Full(
                    tpX, tpY, tpZ, player.getYaw(), player.getPitch(), true, false));
        } catch (Throwable ignored) {
        }
    }
}
