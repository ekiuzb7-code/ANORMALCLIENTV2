package com.anormal.client.module.impl.spectre;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.ColorUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Spectre port: "Glow ESP" — outlines entities through walls with a glow.
 *
 * <p>Spectre paints the outline with a shader pass; this port keeps the entity groups
 * (players, passives, items, crystals), the friend and target tints, the distance and limit
 * settings and the aura/sheen strengths, drawing the outlines as boxes instead of shader
 * strokes.</p>
 */
public final class SpectreGlowEsp extends Module {
    public final BooleanSetting players = addSetting(new BooleanSetting("Players", "Highlight players", true));
    public final BooleanSetting passives = addSetting(new BooleanSetting("Passives", "Highlight passive mobs", false));
    public final BooleanSetting items = addSetting(new BooleanSetting("Items", "Highlight dropped items", false));
    public final BooleanSetting crystals = addSetting(new BooleanSetting("Crystals", "Highlight end crystals", true));
    public final BooleanSetting markTarget = addSetting(new BooleanSetting("Mark Target", "Tint the entity you last hit", true));
    public final ModeSetting colorMode = addSetting(new ModeSetting("Color", "Theme or manual colours", "Theme", "Theme", "Manual"));
    public final ColorSetting playerColor = addSetting(new ColorSetting("Player Color", "Player tint", ColorUtils.rgba(60, 200, 255, 255)));
    public final ColorSetting friendColor = addSetting(new ColorSetting("Friend Color", "Friend tint", ColorUtils.rgba(140, 255, 170, 255)));
    public final ColorSetting passiveColor = addSetting(new ColorSetting("Passive Color", "Passive tint", ColorUtils.rgba(190, 190, 190, 255)));
    public final ColorSetting itemColor = addSetting(new ColorSetting("Item Color", "Item tint", ColorUtils.rgba(255, 220, 120, 255)));
    public final ColorSetting crystalColor = addSetting(new ColorSetting("Crystal Color", "Crystal tint", ColorUtils.rgba(220, 120, 255, 255)));
    public final ColorSetting targetColor = addSetting(new ColorSetting("Target Color", "Target tint", ColorUtils.rgba(255, 90, 90, 255)));
    public final NumberSetting stroke = addSetting(new NumberSetting("Stroke", "Outline width", 2.0, 0.5, 6.0, 0.5));
    public final NumberSetting aura = addSetting(new NumberSetting("Aura", "Outline strength", 55.0, 0.0, 100.0, 5.0));
    public final NumberSetting auraReach = addSetting(new NumberSetting("Aura Reach", "Aura halo size", 10.0, 3.0, 24.0, 1.0));
    public final NumberSetting fill = addSetting(new NumberSetting("Fill", "Fill strength", 10.0, 0.0, 60.0, 5.0));
    public final BooleanSetting breathe = addSetting(new BooleanSetting("Breathe", "Pulse the aura", false));
    public final NumberSetting distance = addSetting(new NumberSetting("Distance", "Maximum distance", 64.0, 8.0, 192.0, 4.0));
    public final NumberSetting limit = addSetting(new NumberSetting("Limit", "Maximum entities drawn", 48.0, 4.0, 96.0, 1.0));

    private static final int THEME_PLAYER = 0xFF3CC8FF;
    private static final int THEME_FRIEND = 0xFF8CFFAA;
    private static final int THEME_PASSIVE = 0xFFBEBEBE;
    private static final int THEME_ITEM = 0xFFFFDC78;
    private static final int THEME_CRYSTAL = 0xFFDC78FF;
    private static final int THEME_TARGET = 0xFFFF5A5A;

    private static LivingEntity lastHit;
    private static long lastHitAt;

    public SpectreGlowEsp() {
        super("SpectreGlowEsp", "Spectre port: outlines entities through walls with a glow.", Category.SPECTRE);
    }

    /** Fed from the attack hook so the target tint follows your last hit. */
    public static void onAttack(Entity target) {
        if (target instanceof LivingEntity living) {
            lastHit = living;
            lastHitAt = System.currentTimeMillis();
        }
    }

    @Override
    public void onDisable() {
        lastHit = null;
        lastHitAt = 0L;
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        var self = mc.player;
        if (self == null || mc.world == null || mc.options.hudHidden) return;

        double max = distance.getAsDouble();
        double maxSq = max * max;
        int cap = (int) limit.getAsDouble();
        LivingEntity mark = markTarget.isEnabled() && System.currentTimeMillis() - lastHitAt < 3500L ? lastHit : null;

        // Colour mode decides where the tints come from: the theme palette or the colour settings.
        boolean manual = colorMode.is("Manual");
        List<Entity> picked = new ArrayList<>();
        for (Entity entity : mc.world.getEntities()) {
            if (entity == self || entity.isRemoved()) continue;
            if (entity instanceof LivingEntity living && living.isDead()) continue;
            if (self.squaredDistanceTo(entity) > maxSq) continue;
            if (tintOf(entity, manual) == 0) continue;
            picked.add(entity);
        }
        if (picked.isEmpty()) return;
        picked.sort(Comparator.comparingDouble(self::squaredDistanceTo));
        if (picked.size() > cap) picked = picked.subList(0, cap);

        float pulse = breathe.isEnabled()
                ? 0.45F + 0.55F * (float) (0.5 - 0.5 * Math.cos(System.nanoTime() % 2_600_000_000L / 2.6E9 * Math.PI * 2.0))
                : 1.0F;
        int alpha = (int) Math.max(0, Math.min(255, aura.getAsDouble() * 2.55 * pulse)) << 24;
        int fillAlpha = (int) Math.max(0, Math.min(255, fill.getAsDouble() * 2.55));
        int width = Math.max(1, (int) Math.round(stroke.getAsDouble()));

        for (Entity entity : picked) {
            int tint = tintOf(entity, manual);
            if (tint == 0) continue;
            if (mark != null && entity == mark) tint = manual ? targetColor.getColor() : THEME_TARGET;
            Box box = interpolated(entity, tickDelta);
            int line = (tint & 0x00FFFFFF) | alpha;
            int face = fillAlpha == 0 ? 0 : (tint & 0x00FFFFFF) | (fillAlpha << 24);
            SpectreHoleEsp.drawBox(context, tickDelta, box, line, face, true, fillAlpha > 0, width);
            if (aura.getAsDouble() > 0) {
                SpectreHoleEsp.drawBox(context, tickDelta, box.expand(auraReach.getAsDouble() / 100.0), (tint & 0x00FFFFFF) | (alpha / 3), 0, true, false, 1);
            }
        }
    }

    private int tintOf(Entity entity, boolean manual) {
        if (entity instanceof PlayerEntity player) {
            if (!players.isEnabled() || player.isSpectator()) return 0;
            String name = player.getGameProfile().name();
            if (name != null && com.anormal.client.module.impl.client.Friends.isFriend(name)) {
                return manual ? friendColor.getColor() : THEME_FRIEND;
            }
            return manual ? playerColor.getColor() : THEME_PLAYER;
        }
        if (entity instanceof EndCrystalEntity) return crystals.isEnabled() ? (manual ? crystalColor.getColor() : THEME_CRYSTAL) : 0;
        if (entity instanceof ItemEntity) return items.isEnabled() ? (manual ? itemColor.getColor() : THEME_ITEM) : 0;
        if (entity instanceof HostileEntity) return 0;
        if (entity instanceof MobEntity) return passives.isEnabled() ? (manual ? passiveColor.getColor() : THEME_PASSIVE) : 0;
        return 0;
    }

    private static Box interpolated(Entity entity, float tickDelta) {
        double dx = entity.lastRenderX + (entity.getX() - entity.lastRenderX) * tickDelta - entity.getX();
        double dy = entity.lastRenderY + (entity.getY() - entity.lastRenderY) * tickDelta - entity.getY();
        double dz = entity.lastRenderZ + (entity.getZ() - entity.lastRenderZ) * tickDelta - entity.getZ();
        return entity.getBoundingBox().offset(dx, dy, dz);
    }
}
