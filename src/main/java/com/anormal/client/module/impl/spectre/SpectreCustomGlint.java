package com.anormal.client.module.impl.spectre;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.ColorUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Box;

/**
 * Spectre port: "Custom Glint" — recolours the enchantment glint.
 *
 * <p>Spectre patches the glint texture pass with its own colour, intensity, speed and pattern
 * size. This port keeps those five settings, the separate-armour colour and the accent mode, and
 * draws the glint as an animated shell around your held item and enchanted armour, which is what
 * the shared box renderer can express without replacing the item pipeline.</p>
 */
public final class SpectreCustomGlint extends Module {
    public final ModeSetting colorMode = addSetting(new ModeSetting("Color", "Custom or accent colours", "Custom", "Custom", "Accent"));
    public final ColorSetting itemColor = addSetting(new ColorSetting("Item Color", "Glint on items", ColorUtils.rgba(120, 110, 255, 255)));
    public final BooleanSetting separateArmor = addSetting(new BooleanSetting("Separate Armor", "Use a different armour colour", false));
    public final ColorSetting armorColor = addSetting(new ColorSetting("Armor Color", "Glint on armour", ColorUtils.rgba(200, 120, 255, 255)));
    public final NumberSetting intensity = addSetting(new NumberSetting("Intensity", "Glint brightness", 1.0, 0.05, 1.0, 0.05));
    public final NumberSetting speed = addSetting(new NumberSetting("Speed", "Glint scroll speed", 1.0, 0.1, 4.0, 0.1));
    public final NumberSetting patternSize = addSetting(new NumberSetting("Pattern Size", "Glint stripe size", 1.0, 0.25, 4.0, 0.25));

    public SpectreCustomGlint() {
        super("SpectreCustomGlint", "Spectre port: recolours the enchantment glint.", Category.SPECTRE);
    }

    /** The colour the item pipeline would use, exposed for future item-render hooks. */
    public static int glintColor(boolean armor) {
        SpectreCustomGlint module = com.anormal.client.module.ModuleManager.getModule(SpectreCustomGlint.class);
        if (module == null) return -1;
        if (module.colorMode.is("Accent")) return module.itemColor.getColor();
        return armor && module.separateArmor.isEnabled() ? module.armorColor.getColor() : module.itemColor.getColor();
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        var player = mc.player;
        if (player == null || mc.options.hudHidden) return;
        long now = System.nanoTime();
        double phase = (now % 2_000_000_000L) / 2.0E9 * speed.getAsDouble();
        double stripe = Math.max(0.15, 0.35 / patternSize.getAsDouble());
        float alphaScale = intensity.getValue().floatValue();

        ItemStack held = player.getMainHandStack();
        if (!held.isEmpty() && held.hasEnchantments()) {
            drawGlint(context, tickDelta, player, player.getX(), player.getY() + player.getStandingEyeHeight() - 0.35,
                    player.getZ(), 0.22, phase, stripe, alphaScale, glintColor(false));
        }
        if (separateArmor.isEnabled()) {
            drawArmor(context, tickDelta, player, EquipmentSlot.HEAD, phase, stripe, alphaScale);
            drawArmor(context, tickDelta, player, EquipmentSlot.CHEST, phase, stripe, alphaScale);
            drawArmor(context, tickDelta, player, EquipmentSlot.LEGS, phase, stripe, alphaScale);
            drawArmor(context, tickDelta, player, EquipmentSlot.FEET, phase, stripe, alphaScale);
        }
    }

    private void drawArmor(DrawContext context, float tickDelta, net.minecraft.entity.player.PlayerEntity player,
                           EquipmentSlot slot, double phase, double stripe, float alphaScale) {
        ItemStack stack = player.getEquippedStack(slot);
        if (stack.isEmpty() || !stack.hasEnchantments()) return;
        float height = switch (slot) {
            case HEAD -> 1.62F;
            case CHEST -> 1.15F;
            case LEGS -> 0.68F;
            default -> 0.15F;
        };
        drawGlint(context, tickDelta, player, player.getX(), player.getY() + height, player.getZ(), 0.34, phase, stripe, alphaScale, glintColor(true));
    }

    private void drawGlint(DrawContext context, float tickDelta, net.minecraft.entity.Entity entity,
                           double x, double y, double z, double half, double phase, double stripe, float alphaScale, int color) {
        var look = entity.getRotationVec(1.0F);
        double cx = x + look.x * 0.05;
        double cz = z + look.z * 0.05;
        for (int i = 0; i < 6; i++) {
            double u = (i / 6.0 + phase) % 1.0;
            if (((u / stripe) % 1.0) > 0.55) continue;
            double top = y - half + 2.0 * half * u;
            int alpha = (int) Math.min(255, 80 * alphaScale);
            SpectreHoleEsp.drawBox(context, tickDelta,
                    new Box(cx - half, top, cz - half, cx + half, top + 0.015, cz + half),
                    (color & 0x00FFFFFF) | (alpha << 24), 0, true, false, 1);
        }
    }
}
