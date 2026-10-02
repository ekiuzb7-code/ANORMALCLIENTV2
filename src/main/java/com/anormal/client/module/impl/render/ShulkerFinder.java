package com.anormal.client.module.impl.render;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.ColorUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

public class ShulkerFinder extends Module {
    public final NumberSetting range = new NumberSetting("Range", "Scan radius in blocks", 48.0, 16.0, 96.0, 8.0);
    public final ColorSetting color = new ColorSetting("Color", "Shulker marker color", ColorUtils.rgba(200, 120, 255, 255));
    public final NumberSetting maxShown = new NumberSetting("Max Shown", "Max shulker markers", 32.0, 8.0, 64.0, 1.0);

    private final List<BlockPos> cache = new ArrayList<>();
    private int ticks = 0;

    public ShulkerFinder() {
        super("ShulkerFinder", "Highlights shulker boxes through walls", Category.RENDER);
        addSetting(range);
        addSetting(color);
        addSetting(maxShown);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;
        if (++ticks < 20) return;
        ticks = 0;
        cache.clear();
        BlockPos origin = mc.player.getBlockPos();
        int r = range.getValue().intValue();
        int cap = maxShown.getValue().intValue();
        try {
            for (int x = -r; x <= r && cache.size() < cap; x++)
                for (int y = -r; y <= r && cache.size() < cap; y++)
                    for (int z = -r; z <= r && cache.size() < cap; z++) {
                        if (x * x + y * y + z * z > r * r) continue;
                        BlockPos p = origin.add(x, y, z);
                        try {
                            if (!mc.world.isChunkLoaded(p)) continue;
                            String path = Registries.BLOCK.getId(mc.world.getBlockState(p).getBlock()).getPath();
                            if (path.contains("shulker_box")) cache.add(p.toImmutable());
                        } catch (Throwable ignored) {}
                    }
        } catch (Throwable ignored) {}
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.player == null || cache.isEmpty()) return;
        try {
            int c = color.getValue();
            for (BlockPos p : cache) {
                int[] sc = project(new Vec3d(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5));
                if (sc == null) continue;
                context.fill(sc[0] - 4, sc[1] - 4, sc[0] + 4, sc[1] + 4, (c & 0x00FFFFFF) | 0x66000000);
                context.fill(sc[0] - 4, sc[1] - 4, sc[0] + 4, sc[1] - 3, c);
                context.fill(sc[0] - 4, sc[1] + 3, sc[0] + 4, sc[1] + 4, c);
                context.fill(sc[0] - 4, sc[1] - 3, sc[0] - 3, sc[1] + 3, c);
                context.fill(sc[0] + 3, sc[1] - 3, sc[0] + 4, sc[1] + 3, c);
            }
        } catch (Throwable ignored) {}
    }

    private int[] project(Vec3d p) {
        try {
            Camera cam = mc.gameRenderer.getCamera();
            Vec3d c = mc.player.getEyePos();
            double dx = p.x - c.x, dy = p.y - c.y, dz = p.z - c.z;
            double yaw = Math.toRadians(cam.getYaw());
            double pitch = Math.toRadians(cam.getPitch());
            double cosP = Math.cos(pitch);
            double fx = -Math.sin(yaw) * cosP, fy = -Math.sin(pitch), fz = Math.cos(yaw) * cosP;
            double depth = dx * fx + dy * fy + dz * fz;
            if (depth < 0.1) return null;
            double rx = -Math.cos(yaw), rz = -Math.sin(yaw);
            double ux = -rz * fy, uy = rz * fx - rx * fz, uz = rx * fy;
            double cx = dx * rx + dz * rz;
            double cy = dx * ux + dy * uy + dz * uz;
            int w = mc.getWindow().getScaledWidth(), h = mc.getWindow().getScaledHeight();
            int fov = 70;
            try {
                fov = mc.options.getFov().getValue();
            } catch (Throwable ignored) {}
            double f = (h / 2.0) / Math.tan(Math.toRadians(Math.max(30, Math.min(110, fov)) / 2.0));
            return new int[]{(int) (w / 2.0 + cx / depth * f), (int) (h / 2.0 - cy / depth * f)};
        } catch (Throwable t) {
            return null;
        }
    }
}
