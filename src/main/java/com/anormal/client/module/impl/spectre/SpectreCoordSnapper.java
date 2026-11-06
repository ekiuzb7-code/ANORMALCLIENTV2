package com.anormal.client.module.impl.spectre;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.module.ModuleManager;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.KeybindSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.Locale;

/**
 * Spectre port: "Coord Snapper" — copies the coordinates you look at or stand on with one key.
 */
public final class SpectreCoordSnapper extends Module {
    private static final String TARGET_LOOKED_AT = "Looked At";
    private static final String TARGET_OWN_POSITION = "Own Position";
    private static final String FORMAT_SPACES = "X Y Z";
    private static final String FORMAT_COMMAS = "X, Y, Z";
    private static final String FORMAT_TP = "/tp Command";
    private static final double STEP_PAST_BLOCK = 0.001;

    public final KeybindSetting key = addSetting(new KeybindSetting("Key", "Snap key", org.lwjgl.glfw.GLFW.GLFW_KEY_UNKNOWN));
    public final ModeSetting target = addSetting(new ModeSetting("Target", "What to copy", TARGET_LOOKED_AT, TARGET_LOOKED_AT, TARGET_OWN_POSITION));
    public final NumberSetting range = addSetting(new NumberSetting("Range", "Look-at raycast range", 256, 16, 512, 1));
    public final BooleanSetting stopAtFluids = addSetting(new BooleanSetting("Stop at Fluids", "The raycast stops on fluids", false));
    public final ModeSetting format = addSetting(new ModeSetting("Format", "Coordinate text format", FORMAT_SPACES, FORMAT_SPACES, FORMAT_COMMAS, FORMAT_TP));
    public final BooleanSetting includeDimension = addSetting(new BooleanSetting("Include Dimension", "Append the dimension name", true));
    public final BooleanSetting confirm = addSetting(new BooleanSetting("Chat Confirmation", "Show a chat confirmation", true));

    private boolean prevDown;
    private net.minecraft.client.network.ClientPlayerEntity localPlayer;

    public SpectreCoordSnapper() {
        super("SpectreCoordSnapper", "Spectre port: copies the coordinates you look at or stand on with one key.", Category.SPECTRE);
    }

    @Override
    public void onTickAlways() {
        // The snap key belongs to this module, so it must not fire while the module is off
        // (background hooks still tick for every module, enabled or not).
        if (!isEnabled()) {
            prevDown = false;
            return;
        }
        int bound = key.getKey();
        // Unbound (GLFW_KEY_UNKNOWN = -1) and mouse codes are not snap keys: a stray right
        // click used to copy coordinates into chat. Mouse support lives on the module keybind.
        if (bound <= 0) {
            prevDown = false;
            return;
        }
        boolean down = keyIsDown(bound);
        boolean pressed = down && !prevDown;
        prevDown = down;
        if (pressed && mc.currentScreen == null && mc.player != null && mc.world != null) {
            snap(mc, mc.player, mc.world);
        }
    }

    private void snap(net.minecraft.client.MinecraftClient client, net.minecraft.client.network.ClientPlayerEntity player, net.minecraft.client.world.ClientWorld level) {
        BlockPos pos;
        if (target.is(TARGET_OWN_POSITION)) {
            pos = player.getBlockPos();
        } else {
            pos = raycast(player, level);
            if (pos == null) {
                player.sendMessage(net.minecraft.text.Text.literal("Nothing copied: no loaded block within "
                        + (int) range.getAsDouble() + " blocks of your aim"), false);
                return;
            }
            if (format.is(FORMAT_TP)) {
                pos = pos.up();
            }
        }

        String text = formatCoords(pos, level.getRegistryKey());
        mc.keyboard.setClipboard(text);
        if (confirm.isEnabled()) {
            player.sendMessage(net.minecraft.text.Text.literal("Copied " + text), false);
        }
    }

    private BlockPos raycast(net.minecraft.client.network.ClientPlayerEntity player, net.minecraft.client.world.ClientWorld level) {
        Vec3d origin;
        Vec3d direction;
        var camera = mc.gameRenderer.getCamera();
        if (camera != null) {
            // Honor a detached camera (Freecam) exactly like Spectre does
            origin = camera.getCameraPos();
            try {
                direction = Vec3d.fromPolar(camera.getPitch(), camera.getYaw());
            } catch (Throwable t) {
                direction = player.getRotationVec(1.0f);
            }
        } else {
            origin = player.getEyePos();
            direction = player.getRotationVec(1.0f);
        }

        Vec3d end = origin.add(direction.multiply(range.getAsDouble()));
        RaycastContext.FluidHandling fluids = stopAtFluids.isEnabled() ? RaycastContext.FluidHandling.ANY : RaycastContext.FluidHandling.NONE;
        RaycastContext context = new RaycastContext(origin, end, RaycastContext.ShapeType.OUTLINE, fluids, player);
        BlockPos viewBlock = BlockPos.ofFloored(origin);
        if (startsInside(context, level, viewBlock, origin)) {
            var box = new net.minecraft.util.math.Box(viewBlock);
            var exit = box.raycast(end, origin);
            if (exit.isEmpty()) return null;
            Vec3d start = exit.get().add(direction.multiply(STEP_PAST_BLOCK));
            context = new RaycastContext(start, end, RaycastContext.ShapeType.OUTLINE, fluids, player);
        }

        BlockHitResult hit = level.raycast(context);
        return hit.getType() == HitResult.Type.BLOCK ? hit.getBlockPos() : null;
    }

    private static boolean startsInside(RaycastContext context, net.minecraft.client.world.ClientWorld level, BlockPos pos, Vec3d point) {
        return contains(context.getBlockShape(level.getBlockState(pos), level, pos), pos, point)
                || contains(context.getFluidShape(level.getFluidState(pos), level, pos), pos, point);
    }

    private static boolean contains(net.minecraft.util.shape.VoxelShape shape, BlockPos pos, Vec3d point) {
        for (net.minecraft.util.math.Box box : shape.getBoundingBoxes()) {
            if (box.offset(pos).contains(point)) return true;
        }
        return false;
    }

    private String formatCoords(BlockPos pos, net.minecraft.registry.RegistryKey<net.minecraft.world.World> dimension) {
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        boolean withDimension = includeDimension.isEnabled();
        String value = format.getValue();
        return switch (value) {
            case FORMAT_TP -> withDimension
                    ? String.format(Locale.ROOT, "/execute in %s run tp @s %d %d %d", dimensionId(dimension), x, y, z)
                    : String.format(Locale.ROOT, "/tp %d %d %d", x, y, z);
            case FORMAT_COMMAS -> String.format(Locale.ROOT, "%d, %d, %d", x, y, z) + dimensionSuffix(dimension, withDimension);
            default -> String.format(Locale.ROOT, "%d %d %d", x, y, z) + dimensionSuffix(dimension, withDimension);
        };
    }

    private static String dimensionSuffix(net.minecraft.registry.RegistryKey<net.minecraft.world.World> dimension, boolean enabled) {
        return enabled ? " (" + dimensionName(dimension) + ")" : "";
    }

    private static String dimensionId(net.minecraft.registry.RegistryKey<net.minecraft.world.World> dimension) {
        return dimension.getValue().toString();
    }

    private static String dimensionName(net.minecraft.registry.RegistryKey<net.minecraft.world.World> dimension) {
        if (net.minecraft.world.World.OVERWORLD.equals(dimension)) return "Overworld";
        if (net.minecraft.world.World.NETHER.equals(dimension)) return "Nether";
        if (net.minecraft.world.World.END.equals(dimension)) return "End";
        return dimension.getValue().toString();
    }

    private static boolean keyIsDown(int code) {
        try {
            if (KeybindSetting.isMouseCode(code)) {
                int button = KeybindSetting.mouseButton(code);
                return org.lwjgl.glfw.GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), button)
                        == org.lwjgl.glfw.GLFW.GLFW_PRESS;
            }
            return net.minecraft.client.util.InputUtil.isKeyPressed(mc.getWindow(), code);
        } catch (Throwable ignored) {
            return false;
        }
    }

}
