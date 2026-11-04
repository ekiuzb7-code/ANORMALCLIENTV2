package com.anormal.client.knife;

import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.util.Identifier;
import net.minecraft.entity.player.PlayerModelPart;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.function.Consumer;

/**
 * Draws the player's real arm (skin, sleeve, slim/wide) as a two-point rig: a hand position and an elbow. The arm
 * model is drawn once at the hand and once stretched 3x as the upper arm / shoulder.
 */
final class FirstPersonArm {
    /** Direction from the hand back towards the elbow, in item frame. */
    static final Vector3f FOREARM = new Vector3f(0.75f, -0.45f, 0.5f).normalize();
    static final float ELBOW = 0.6f;
    private static final Vector3f ARM_UP = new Vector3f(0.0f, 1.0f, 0.0f);
    private static final float ARM_SHOULDER = -0.625f;

    private FirstPersonArm() {}

    /** Everything needed to draw the local player's arm; null when there is nothing to draw (invisible, no player). */
    record Arm(PlayerEntityRenderer<?> renderer, Identifier texture, boolean rightSleeve, boolean leftSleeve, boolean slim) {
        void draw(MatrixStack pose, boolean left, OrderedRenderCommandQueue collector, int light) {
            if (left) {
                pose.scale(-1.0f, 1.0f, 1.0f);
                renderer.renderLeftArm(pose, collector, light, texture, leftSleeve);
            } else {
                renderer.renderRightArm(pose, collector, light, texture, rightSleeve);
            }
        }
    }

    static Arm arm() {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity player = mc.player;
        if (player == null || player.isInvisible()) return null;
        EntityRenderer<?, ?> renderer = mc.getEntityRenderDispatcher().getRenderer(player);
        if (!(renderer instanceof PlayerEntityRenderer<?> avatar)) return null;
        var skin = player.getSkin();
        return new Arm(avatar, skin.body().texturePath(),
                player.isModelPartVisible(PlayerModelPart.RIGHT_SLEEVE),
                player.isModelPartVisible(PlayerModelPart.LEFT_SLEEVE),
                skin.model() == net.minecraft.entity.player.PlayerSkinType.SLIM);
    }

    /** Puts the arm's hand at {@code hand} pointing back to {@code elbow}; {@code draw} is called once per arm piece. */
    static void place(MatrixStack pose, Vector3f hand, Vector3f elbow, boolean slim, Consumer<MatrixStack> draw) {
        Vector3f along = new Vector3f(hand).sub(elbow).normalize();
        Vector3f front = new Vector3f(ARM_UP).fma(-ARM_UP.dot(along), along).normalize();
        Vector3f side = new Vector3f(along).cross(front);
        pose.push();
        pose.translate(hand.x, hand.y, hand.z);
        pose.multiply(new Quaternionf().setFromNormalized(new Matrix3f(side, along, front)));
        drawPart(pose, slim, draw);
        pose.translate(0.0f, ARM_SHOULDER, 0.0f);
        pose.scale(-1.0f, -3.0f, 1.0f);
        pose.translate(0.0f, -ARM_SHOULDER, 0.0f);
        drawPart(pose, slim, draw);
        pose.pop();
    }

    private static void drawPart(MatrixStack pose, boolean slim, Consumer<MatrixStack> draw) {
        pose.push();
        pose.translate(slim ? 0.03125f : 0.0625f, -0.5f, 0.0f);
        pose.multiply(RotationAxis.POSITIVE_Z.rotation(-0.1f));
        pose.translate(0.3125f, -0.125f, 0.0f);
        draw.accept(pose);
        pose.pop();
    }
}
