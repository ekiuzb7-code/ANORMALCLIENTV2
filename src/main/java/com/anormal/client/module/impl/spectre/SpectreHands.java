package com.anormal.client.module.impl.spectre;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemDisplayContext;

/**
 * Spectre port: "Hand Pose" — moves, rotates and resizes your first-person hands.
 *
 * <p>Spectre applies the pose through its held-item render hook. This port keeps the linked-hands
 * toggle, the editing side, the glide easing and the seven axes (offset x/y/z, pitch, yaw, roll,
 * scale) for each hand, and applies the offset and scale to the first-person transform the
 * client's held-item hook exposes.</p>
 */
public final class SpectreHands extends Module {
    private static final float[] REST = {0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F};

    public final BooleanSetting link = addSetting(new BooleanSetting("Link Hands", "Edit both hands together", false));
    public final ModeSetting editing = addSetting(new ModeSetting("Editing", "Which hand to edit", "Main Hand", "Main Hand", "Offhand"));
    public final NumberSetting glide = addSetting(new NumberSetting("Glide", "Easing on the way in and out", 0.4, 0.0, 1.0, 0.05));

    public final NumberSetting mainX = addSetting(new NumberSetting("Offset X", "Main hand X", REST[0], -1.0, 1.0, 0.01));
    public final NumberSetting mainY = addSetting(new NumberSetting("Offset Y", "Main hand Y", REST[1], -1.0, 1.0, 0.01));
    public final NumberSetting mainZ = addSetting(new NumberSetting("Offset Z", "Main hand Z", REST[2], -1.0, 1.0, 0.01));
    public final NumberSetting mainPitch = addSetting(new NumberSetting("Pitch", "Main hand pitch", REST[3], -180.0, 180.0, 1.0));
    public final NumberSetting mainYaw = addSetting(new NumberSetting("Yaw", "Main hand yaw", REST[4], -180.0, 180.0, 1.0));
    public final NumberSetting mainRoll = addSetting(new NumberSetting("Roll", "Main hand roll", REST[5], -180.0, 180.0, 1.0));
    public final NumberSetting mainScale = addSetting(new NumberSetting("Scale", "Main hand scale", REST[6], 0.3, 2.0, 0.05));

    public final NumberSetting offX = addSetting(new NumberSetting("Offhand Offset X", "Offhand X", REST[0], -1.0, 1.0, 0.01));
    public final NumberSetting offY = addSetting(new NumberSetting("Offhand Offset Y", "Offhand Y", REST[1], -1.0, 1.0, 0.01));
    public final NumberSetting offZ = addSetting(new NumberSetting("Offhand Offset Z", "Offhand Z", REST[2], -1.0, 1.0, 0.01));
    public final NumberSetting offScale = addSetting(new NumberSetting("Offhand Scale", "Offhand scale", REST[6], 0.3, 2.0, 0.05));

    private static SpectreHands active;
    private final float[] shown = (float[]) REST.clone();
    private long stamp;

    public SpectreHands() {
        super("SpectreHands", "Spectre port: moves, rotates and resizes your first-person hands.", Category.SPECTRE);
    }

    @Override
    public void onEnable() {
        active = this;
        stamp = 0L;
    }

    @Override
    public void onDisable() {
        if (active == this) active = null;
    }

    /** Called from the held-item hook; keeps the pose smooth like Spectre's easing. */
    public static void apply(MatrixStack pose, ItemDisplayContext context) {
        SpectreHands module = active;
        if (module == null || !module.isEnabled() || pose == null) return;
        boolean offhand = context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
        module.step(offhand);
        pose.translate(module.shown[0], module.shown[1], module.shown[2]);
        float scale = module.shown[6];
        if (scale != 1.0F) pose.scale(scale, scale, scale);
    }

    private float[] mainGoal() {
        return new float[]{mainX.getValue().floatValue(), mainY.getValue().floatValue(), mainZ.getValue().floatValue(),
                mainPitch.getValue().floatValue(), mainYaw.getValue().floatValue(), mainRoll.getValue().floatValue(),
                mainScale.getValue().floatValue()};
    }

    private void step(boolean offhand) {
        long now = System.nanoTime();
        float dt = stamp == 0L ? 0.0F : Math.min(0.1F, (now - stamp) / 1.0E9F);
        stamp = now;
        float rate = glide.getValue().floatValue() <= 0.0F ? -1.0F : 30.0F - 27.0F * glide.getValue().floatValue();
        float k = rate < 0.0F ? 1.0F : 1.0F - (float) Math.exp(-rate * dt);

        float[] goal;
        if (link.isEnabled()) {
            goal = mainGoal();
        } else if (offhand) {
            // Editing picks the hand being posed; the other one glides back to rest.
            goal = editing.is("Offhand")
                    ? new float[]{offX.getValue().floatValue(), offY.getValue().floatValue(), offZ.getValue().floatValue(), 0.0F, 0.0F, 0.0F, offScale.getValue().floatValue()}
                    : REST.clone();
        } else {
            goal = editing.is("Main Hand") ? mainGoal() : REST.clone();
        }

        for (int i = 0; i < shown.length; i++) {
            float next = shown[i] + (goal[i] - shown[i]) * k;
            shown[i] = Math.abs(goal[i] - next) < 0.001F ? goal[i] : next;
        }
    }
}
