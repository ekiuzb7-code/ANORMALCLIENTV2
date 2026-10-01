package com.anormal.client.module.impl.world;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.util.math.Vec3d;

public class Freecam extends Module {
    public final NumberSetting speed = new NumberSetting("Speed", "Flight camera speed", 1.5, 0.5, 5.0, 0.5);
    public final BooleanSetting allowInteracting = new BooleanSetting("Allow Interacting", "Interact with blocks and entities", false);
    public final BooleanSetting spawnFake = new BooleanSetting("Spawn Fake", "Keep client-side snapshot at origin", true);
    public final BooleanSetting moveFake = new BooleanSetting("Move Fake", "Let snapshot track camera anchor", false);

    private double anchorX, anchorY, anchorZ;
    private float anchorYaw, anchorPitch;
    private double fakeX, fakeY, fakeZ;
    private boolean active = false;

    public Freecam() {
        super("Freecam", "Client-side noclip look (server rubber-bands, use with caution)", Category.WORLD);
        addSetting(speed);
        addSetting(allowInteracting);
        addSetting(spawnFake);
        addSetting(moveFake);
    }

    @Override
    public void onEnable() {
        active = false;
        if (mc.player == null) return;
        anchorX = mc.player.getX();
        anchorY = mc.player.getY();
        anchorZ = mc.player.getZ();
        anchorYaw = mc.player.getYaw();
        anchorPitch = mc.player.getPitch();
        fakeX = anchorX;
        fakeY = anchorY;
        fakeZ = anchorZ;
        mc.player.noClip = true;
        mc.player.setVelocity(new Vec3d(0.0, 0.0, 0.0));
        active = true;
    }

    @Override
    public void onDisable() {
        active = false;
        if (mc.player == null) return;
        mc.player.noClip = false;
        mc.player.setVelocity(new Vec3d(0.0, 0.0, 0.0));
        if (spawnFake.isEnabled()) {
            mc.player.setPosition(anchorX, anchorY, anchorZ);
            mc.player.setYaw(anchorYaw);
            mc.player.setPitch(anchorPitch);
        }
    }

    @Override
    public void onTick() {
        if (mc.player == null || !active) return;
        if (!allowInteracting.isEnabled()) {
            mc.options.attackKey.setPressed(false);
            mc.options.useKey.setPressed(false);
        }
        double s = speed.getValue() * 0.35;
        float yaw = mc.player.getYaw();
        double rad = Math.toRadians(yaw);
        double fwdX = -Math.sin(rad);
        double fwdZ = Math.cos(rad);
        double mx = 0.0, my = 0.0, mz = 0.0;
        if (mc.options.forwardKey.isPressed()) {
            mx += fwdX;
            mz += fwdZ;
        }
        if (mc.options.backKey.isPressed()) {
            mx -= fwdX;
            mz -= fwdZ;
        }
        if (mc.options.leftKey.isPressed()) {
            mx += fwdZ;
            mz -= fwdX;
        }
        if (mc.options.rightKey.isPressed()) {
            mx -= fwdZ;
            mz += fwdX;
        }
        if (mc.options.jumpKey.isPressed()) my += 1.0;
        if (mc.options.sneakKey.isPressed()) my -= 1.0;
        mc.player.setVelocity(new Vec3d(mx * s, my * s, mz * s));
        mc.player.fallDistance = 0.0f;
        if (spawnFake.isEnabled() && moveFake.isEnabled()) {
            fakeX = mc.player.getX();
            fakeY = mc.player.getY();
            fakeZ = mc.player.getZ();
        }
    }

    public Vec3d getFakePos() {
        return new Vec3d(fakeX, fakeY, fakeZ);
    }
}
