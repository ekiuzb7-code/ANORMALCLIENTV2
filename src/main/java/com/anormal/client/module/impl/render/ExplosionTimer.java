package com.anormal.client.module.impl.render;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.theme.ThemeManager;
import com.anormal.client.util.ColorUtils;
import com.anormal.client.util.HudStyle;
import com.anormal.client.util.NotificationManager;
import com.anormal.client.util.ProjectionUtil;
import com.anormal.client.util.RenderUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.TntEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

import java.util.Locale;

/**
 * TNT / explosion countdown.
 *
 * <p>Every primed TNT gets a live countdown, a blast-radius ring and an estimated damage number
 * at your position; the HUD card repeats the nearest fuse. A short warning (chat + click) fires
 * when the nearest fuse drops under the warn threshold, which is when you still have time to
 * step out of the blast.</p>
 */
public class ExplosionTimer extends Module {

    public final NumberSetting maxDistance = new NumberSetting("Max Distance", "Ignore TNT further than this", 40.0, 4.0, 128.0, 4.0);
    public final NumberSetting warnTicks = new NumberSetting("Warn Ticks", "Warn when the nearest fuse is this many ticks away (0 = off)", 20.0, 0.0, 100.0, 1.0);
    public final BooleanSetting showRing = new BooleanSetting("Show Radius", "Draw the blast radius ring around each TNT", true);
    public final BooleanSetting showTimer = new BooleanSetting("Show Timer", "Draw the countdown above each TNT", true);
    public final BooleanSetting showDamage = new BooleanSetting("Damage Estimate", "Show the damage this TNT would deal where you stand", true);
    public final BooleanSetting showPanel = new BooleanSetting("Show Panel", "Draw the countdown card", true);
    public final BooleanSetting sound = new BooleanSetting("Sound", "Click when the nearest fuse passes the warning", true);
    public final BooleanSetting chatWarn = new BooleanSetting("Chat Warning", "Print a local chat warning", false);
    public final ColorSetting ringColor = new ColorSetting("Ring Color", "Blast radius color", ColorUtils.rgba(255, 70, 70, 230));
    public final NumberSetting posX = new NumberSetting("Pos X", "X Position on screen", 6.0, 0.0, 1920.0, 1.0);
    public final NumberSetting posY = new NumberSetting("Pos Y", "Y Position on screen", 460.0, 0.0, 1080.0, 1.0);

    private int lastWarnEntity = -1;
    private int lastWarnFuse = -1;

    public ExplosionTimer() {
        super("TNT Timer", "Counts down primed TNT with a blast ring, damage estimate and a warning", Category.RENDER);
        addSetting(maxDistance);
        addSetting(warnTicks);
        addSetting(showRing);
        addSetting(showTimer);
        addSetting(showDamage);
        addSetting(showPanel);
        addSetting(sound);
        addSetting(chatWarn);
        addSetting(ringColor);
        addSetting(posX);
        addSetting(posY);
    }

    @Override
    public void onEnable() {
        lastWarnEntity = -1;
        lastWarnFuse = -1;
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.player == null || mc.world == null || mc.textRenderer == null) return;

        double bestDistance = Double.MAX_VALUE;
        int bestFuse = -1;
        int bestId = -1;
        boolean warnedThisFrame = false;

        try {
            for (Entity entity : mc.world.getEntities()) {
                if (!(entity instanceof TntEntity tnt)) continue;
                double distance = mc.player.distanceTo(entity);
                if (distance > maxDistance.getValue()) continue;

                int fuse;
                try {
                    fuse = tnt.getFuse();
                } catch (Throwable t) {
                    continue;
                }
                if (fuse < 0) continue;

                double seconds = fuse / 20.0;

                Vec3d mark = ProjectionUtil.lerpPos(entity, tickDelta).add(0.0, 1.0, 0.0);
                int[] screen = ProjectionUtil.project(mark, tickDelta);
                if (screen != null) {
                    if (showRing.isEnabled()) {
                        double focal = ProjectionUtil.focal();
                        if (focal <= 0.0) focal = 400.0;
                        int ringRadius = (int) Math.min(240.0, focal * 8.0 / Math.max(1.0, distance));
                        circle(context, screen[0], screen[1], ringRadius, ringColor.getColor());
                    }
                    if (showTimer.isEnabled()) {
                        String label = String.format(Locale.ROOT, "%.1fs", seconds);
                        if (showDamage.isEnabled()) {
                            label += "  -" + Math.round(damageAt(distance)) + "hp";
                        }
                        RenderUtils.drawChip(context, mc.textRenderer, label, screen[0], screen[1] - 14,
                                fuse <= warnTicks.getValue() ? 0xFFF87171 : 0xFFFBBF24);
                    }
                }

                if (distance < bestDistance) {
                    bestDistance = distance;
                    bestFuse = fuse;
                    bestId = entity.getId();
                }
                if (fuse <= warnTicks.getValue() && distance <= 8.0 && !warnedThisFrame) {
                    warnedThisFrame = true;
                }
            }
        } catch (Throwable ignored) {
        }

        if (bestFuse >= 0) warnIfNeeded(bestId, bestFuse, bestDistance);

        if (!showPanel.isEnabled() || bestFuse < 0) return;
        int w = 172, h = 46;
        int[] at = HudStyle.place(posX.getValue().intValue(), posY.getValue().intValue(), w, h);
        int x = at[0], y = at[1];
        int accent = ThemeManager.getAccentColor();
        boolean hot = bestFuse <= warnTicks.getValue();
        HudStyle.panel(context, x, y, w, h, hot ? 0xFFF87171 : accent);
        HudStyle.title(context, mc.textRenderer, "Explosion", x + 10, y + 7);
        String time = String.format(Locale.ROOT, "%.1fs", bestFuse / 20.0);
        int tw = mc.textRenderer.getWidth(time);
        RenderUtils.drawText(context, mc.textRenderer, time, x + w - 10 - tw, y + 7,
                hot ? 0xFFF87171 : 0xFFFBBF24, true);
        String where = String.format(Locale.ROOT, "%.1f blocks away", bestDistance);
        RenderUtils.drawText(context, mc.textRenderer, where, x + 10, y + 20, 0xFFC7CBD6, false);
        if (showDamage.isEnabled()) {
            String dmg = "~" + Math.round(damageAt(bestDistance)) + " hp at your feet";
            RenderUtils.drawText(context, mc.textRenderer, dmg, x + 10, y + 30, 0xFF8A8A96, false);
        }
        HudStyle.bar(context, x + 10, y + 40, w - 20, 3,
                Math.max(0.0f, Math.min(1.0f, bestFuse / 80.0f)), hot ? 0xFFF87171 : 0xFFFBBF24);
    }

    private void warnIfNeeded(int entityId, int fuse, double distance) {
        int threshold = (int) warnTicks.getValue().intValue();
        if (threshold <= 0 || fuse > threshold) {
            if (fuse > threshold) {
                lastWarnEntity = -1;
                lastWarnFuse = -1;
            }
            return;
        }
        if (entityId == lastWarnEntity && lastWarnFuse >= 0 && fuse >= lastWarnFuse) return;
        lastWarnEntity = entityId;
        lastWarnFuse = fuse;

        if (sound.isEnabled() && mc.player != null) {
            try {
                mc.player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), 0.7f, 0.7f);
            } catch (Throwable ignored) {
            }
        }
        if (chatWarn.isEnabled() && mc.inGameHud != null) {
            try {
                mc.inGameHud.getChatHud().addMessage(Text.literal(String.format(Locale.ROOT,
                        "[Anormal] TNT %.1fs (%.1f blocks)", fuse / 20.0, distance)));
            } catch (Throwable ignored) {
            }
        }
        try {
            NotificationManager.info("TNT", String.format(Locale.ROOT, "%.1fs - %.1f blocks",
                    fuse / 20.0, distance));
        } catch (Throwable ignored) {
        }
    }

    /** Vanilla-like blast damage falloff at the player's feet (same curve AutoTotem uses). */
    private double damageAt(double distance) {
        return Math.max(0.0, 10.0 - distance * 1.2);
    }

    private void circle(DrawContext context, int cx, int cy, int r, int color) {
        if (r < 2) {
            context.fill(cx - 1, cy - 1, cx + 1, cy + 1, color);
            return;
        }
        for (int i = 0; i < 32; i++) {
            double a1 = i / 32.0 * Math.PI * 2.0;
            double a2 = (i + 1) / 32.0 * Math.PI * 2.0;
            RenderUtils.drawLine(context,
                    cx + (int) (Math.cos(a1) * r), cy + (int) (Math.sin(a1) * r),
                    cx + (int) (Math.cos(a2) * r), cy + (int) (Math.sin(a2) * r), color, 1.0f);
        }
    }
}
