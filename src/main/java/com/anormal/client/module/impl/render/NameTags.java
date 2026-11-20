package com.anormal.client.module.impl.render;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.ColorUtils;
import com.anormal.client.util.ProjectionUtil;
import com.anormal.client.util.RenderUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Vec3d;

/**
 * Ported from LiquidBounce's Nametags module.
 *
 * <p>LiquidBounce draws a styled name plate above every entity showing the
 * name, health, ping and distance, with the target's equipment and
 * enchantments listed underneath. Its settings are mirrored here:</p>
 * <ul>
 *   <li>"BorderWidth" (1, 0..8) and "BackgroundRadius" (2, 0..16)</li>
 *   <li>Equipment: per-slot toggles, "SkipEmptySlot", "ShowInfo" and the
 *       fill / outline colours</li>
 *   <li>Enchantments: "Scale" (0.8, 0.25..4), "MaxCountPerItem" (4, 1..16)
 *       and the list filter</li>
 *   <li>Text parts: name, health, ping, distance and game mode</li>
 * </ul>
 */
public class NameTags extends Module {
    public final NumberSetting borderWidth = new NumberSetting("BorderWidth", "Thickness of the plate border", 1.0, 0.0, 8.0, 0.5);
    public final NumberSetting backgroundRadius = new NumberSetting("BackgroundRadius", "Corner rounding of the plate", 2.0, 0.0, 16.0, 1.0);
    public final NumberSetting scale = new NumberSetting("Scale", "Overall plate scale", 1.0, 0.5, 2.5, 0.05);
    public final NumberSetting range = new NumberSetting("Range", "Only show plates within this distance", 64.0, 8.0, 256.0, 8.0);

    // ---- Text parts (LiquidBounce's TextFormatter parts) ----
    public final BooleanSetting showName = new BooleanSetting("ShowName", "Show the entity name", true);
    public final BooleanSetting showHealth = new BooleanSetting("ShowHealth", "Show the entity health", true);
    public final BooleanSetting showPing = new BooleanSetting("ShowPing", "Show the player ping", true);
    public final BooleanSetting showDistance = new BooleanSetting("ShowDistance", "Show the distance", true);
    public final BooleanSetting showGamemode = new BooleanSetting("ShowGamemode", "Show the player game mode", false);
    public final BooleanSetting showOwnTag = new BooleanSetting("ShowOwnTag", "Show your own name plate", false);

    // ---- Colours / styling ----
    public final ColorSetting backgroundColor = new ColorSetting("BackgroundColor", "Plate background colour", ColorUtils.rgba(0, 0, 0, 130));
    public final ColorSetting borderColor = new ColorSetting("BorderColor", "Plate border colour", ColorUtils.rgba(0, 0, 0, 0));
    public final ColorSetting textColor = new ColorSetting("TextColor", "Plate text colour", ColorUtils.rgba(255, 255, 255, 255));
    public final ColorSetting friendColor = new ColorSetting("FriendColor", "Name colour for friends", ColorUtils.rgba(0, 160, 255, 255));
    public final BooleanSetting useTheme = new BooleanSetting("UseTheme", "Tint the plate with the active theme", false);

    // ---- Equipment ----
    public final BooleanSetting showEquipment = new BooleanSetting("ShowEquipment", "Show the entity's equipment", true);
    public final BooleanSetting slotHead = new BooleanSetting("SlotHead", "Show the helmet", true);
    public final BooleanSetting slotChest = new BooleanSetting("SlotChest", "Show the chestplate", true);
    public final BooleanSetting slotLegs = new BooleanSetting("SlotLegs", "Show the leggings", true);
    public final BooleanSetting slotFeet = new BooleanSetting("SlotFeet", "Show the boots", true);
    public final BooleanSetting slotMainHand = new BooleanSetting("SlotMainHand", "Show the main hand item", true);
    public final BooleanSetting slotOffHand = new BooleanSetting("SlotOffHand", "Show the off hand item", true);
    public final BooleanSetting skipEmptySlot = new BooleanSetting("SkipEmptySlot", "Do not reserve space for empty slots", true);
    public final BooleanSetting showInfo = new BooleanSetting("ShowInfo", "Show stack counts and durability", true);
    public final ColorSetting equipmentFill = new ColorSetting("EquipmentFill", "Equipment background colour", ColorUtils.rgba(255, 0, 0, 100));
    public final ColorSetting equipmentOutline = new ColorSetting("EquipmentOutline", "Equipment outline colour", ColorUtils.rgba(0, 0, 0, 0));

    // ---- Enchantments ----
    public final BooleanSetting showEnchantments = new BooleanSetting("ShowEnchantments", "List the target's enchantments", false);
    public final NumberSetting enchantmentScale = new NumberSetting("EnchantmentScale", "Scale of the enchantment list", 0.8, 0.25, 4.0, 0.05);
    public final NumberSetting maxCountPerItem = new NumberSetting("MaxCountPerItem", "Max enchantments shown per item", 4.0, 1.0, 16.0, 1.0);
    public final ModeSetting enchantmentFilter = new ModeSetting("EnchantmentFilter", "Which enchantments to list", "Blacklist", "Blacklist", "Whitelist");

    public NameTags() {
        super("NameTags", "Styled name plates with health, ping, distance and equipment", Category.RENDER);
        addSetting(borderWidth);
        addSetting(backgroundRadius);
        addSetting(scale);
        addSetting(range);
        addSetting(showName);
        addSetting(showHealth);
        addSetting(showPing);
        addSetting(showDistance);
        addSetting(showGamemode);
        addSetting(showOwnTag);
        addSetting(backgroundColor);
        addSetting(borderColor);
        addSetting(textColor);
        addSetting(friendColor);
        addSetting(useTheme);
        addSetting(showEquipment);
        addSetting(slotHead);
        addSetting(slotChest);
        addSetting(slotLegs);
        addSetting(slotFeet);
        addSetting(slotMainHand);
        addSetting(slotOffHand);
        addSetting(skipEmptySlot);
        addSetting(showInfo);
        addSetting(equipmentFill);
        addSetting(equipmentOutline);
        addSetting(showEnchantments);
        addSetting(enchantmentScale);
        addSetting(maxCountPerItem);
        addSetting(enchantmentFilter);

        for (var s : new com.anormal.client.setting.Setting<?>[]{slotHead, slotChest, slotLegs, slotFeet,
                slotMainHand, slotOffHand, skipEmptySlot, showInfo, equipmentFill, equipmentOutline}) {
            s.visibleIf(showEquipment::isEnabled);
        }
        for (var s : new com.anormal.client.setting.Setting<?>[]{enchantmentScale, maxCountPerItem, enchantmentFilter}) {
            s.visibleIf(showEnchantments::isEnabled);
        }
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.player == null || mc.world == null || mc.textRenderer == null) return;

        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof PlayerEntity living) || !living.isAlive()) continue;
            if (living == mc.player && !showOwnTag.isEnabled()) continue;
            if (living.isInvisible()) continue;

            double dist = mc.player.distanceTo(living);
            if (dist > range.getValue()) continue;

            try {
                renderTag(context, living, tickDelta, dist);
            } catch (Throwable ignored) {
            }
        }
    }

    private void renderTag(DrawContext context, LivingEntity living, float tickDelta, double dist) {
        Vec3d base = ProjectionUtil.lerpPos(living, tickDelta);
        int[] anchor = ProjectionUtil.project(base.add(0.0, living.getHeight() + 0.5, 0.0), tickDelta);
        if (anchor == null) return;

        float sc = scale.getValue().floatValue();
        String line = buildText(living, dist);
        int textW = mc.textRenderer.getWidth(line);

        // Everything below is laid out in local pixels around (0,0) = the anchor, then the whole
        // plate (text, boxes, item icons) is scaled together by the matrix.
        var m = context.getMatrices();
        m.pushMatrix();
        m.translate((float) anchor[0], (float) anchor[1]);
        m.scale(sc, sc);
        try {
            int pad = 3;
            int h = mc.textRenderer.fontHeight + 2;
            int w = textW + pad * 2;
            int x = -w / 2;
            int y = -h;

            int bg = useTheme.isEnabled() ? ColorUtils.rgba(15, 15, 20, 160) : backgroundColor.getValue();
            RenderUtils.fill(context, x, y, x + w, y + h, bg);

            int bw = (int) Math.ceil(borderWidth.getValue());
            if (bw > 0 && ((borderColor.getValue() >>> 24) & 0xFF) > 0) {
                RenderUtils.drawBorder(context, x, y, x + w, y + h, bw, borderColor.getValue());
            }

            RenderUtils.drawText(context, mc.textRenderer, line, x + pad, y + 2, textColor.getValue(), true);

            int below = y + h + 2;
            if (showEquipment.isEnabled()) {
                below = renderEquipment(context, living, below);
            }

            if (showEnchantments.isEnabled() && living instanceof PlayerEntity) {
                renderEnchantments(context, living, 0, below, enchantmentScale.getValue().floatValue());
            }
        } finally {
            m.popMatrix();
        }
    }

    /** Draws the equipment row (real item icons) centred under the plate; returns the next free y. */
    private int renderEquipment(DrawContext context, LivingEntity living, int y) {
        EquipmentSlot[] order = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
                EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND};
        boolean[] on = {slotHead.isEnabled(), slotChest.isEnabled(), slotLegs.isEnabled(), slotFeet.isEnabled(),
                slotMainHand.isEnabled(), slotOffHand.isEnabled()};
        int cell = 18;

        int n = 0;
        for (int i = 0; i < order.length; i++) {
            if (!on[i]) continue;
            if (living.getEquippedStack(order[i]).isEmpty() && skipEmptySlot.isEnabled()) continue;
            n++;
        }
        if (n == 0) return y;

        int x = -(n * (cell + 1) - 1) / 2;
        for (int i = 0; i < order.length; i++) {
            if (!on[i]) continue;
            ItemStack stack = living.getEquippedStack(order[i]);
            if (stack.isEmpty() && skipEmptySlot.isEnabled()) continue;
            drawSlot(context, stack, x, y, cell);
            x += cell + 1;
        }
        return y + cell + 2;
    }

    private void drawSlot(DrawContext context, ItemStack stack, int x, int y, int cell) {
        RenderUtils.fill(context, x, y, x + cell, y + cell, equipmentFill.getValue());
        if (((equipmentOutline.getValue() >>> 24) & 0xFF) > 0) {
            RenderUtils.drawBorder(context, x, y, x + cell, y + cell, 1, equipmentOutline.getValue());
        }
        if (stack.isEmpty()) return;

        // The actual item, so you can tell a block of stone from a sword.
        context.drawItem(stack, x + 1, y + 1);

        if (!showInfo.isEnabled()) return;

        if (stack.isDamageable() && stack.getMaxDamage() > 0) {
            float f = 1.0f - (float) stack.getDamage() / stack.getMaxDamage();
            f = Math.max(0.0f, Math.min(1.0f, f));
            int col = f > 0.6f ? 0xFF55FF55 : (f > 0.3f ? 0xFFFFFF55 : 0xFFFF5555);
            context.fill(x + 1, y + cell - 2, x + cell - 1, y + cell - 1, 0xFF000000);
            context.fill(x + 1, y + cell - 2, x + 1 + Math.max(1, Math.round((cell - 2) * f)), y + cell - 1, col);
        }
        if (stack.getCount() > 1) {
            String count = String.valueOf(stack.getCount());
            RenderUtils.drawText(context, mc.textRenderer, count, x + cell - mc.textRenderer.getWidth(count) - 1,
                    y + cell - 9, 0xFFFFFFFF, true);
        }
    }

    private void renderEnchantments(DrawContext context, LivingEntity living, int centerX, int y, float es) {
        int max = maxCountPerItem.getValue().intValue();
        int row = 0;
        var em = context.getMatrices();
        em.pushMatrix();
        em.translate((float) centerX, (float) y);
        em.scale(es, es);
        centerX = 0;
        y = 0;
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.HEAD,
                EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack stack = living.getEquippedStack(slot);
            if (stack.isEmpty()) continue;
            int shown = 0;
            try {
                var enchants = net.minecraft.enchantment.EnchantmentHelper.getEnchantments(stack);
                for (var entry : enchants.getEnchantments()) {
                    if (shown >= max) break;
                    String path = entry.getKey().map(k -> k.getValue().getPath()).orElse("");
                    if (path.isEmpty()) continue;
                    if (enchantmentFilter.is("Whitelist") && !path.contains("protection") && !path.contains("sharpness")) continue;

                    int lvl = enchants.getLevel(entry);
                    String label = shortName(path) + " " + lvl;
                    int w = mc.textRenderer.getWidth(label);
                    int lx = centerX - w / 2;
                    int ly = y + row * 9;
                    RenderUtils.fill(context, lx - 1, ly - 1, lx + w + 1, ly + 8, ColorUtils.rgba(0, 0, 0, 140));
                    RenderUtils.drawText(context, mc.textRenderer, label, lx, ly, 0xFFAAAAAA, true);
                    row++;
                    shown++;
                }
            } catch (Throwable ignored) {
            }
        }
        em.popMatrix();
    }

    private String shortName(String path) {
        return switch (path) {
            case "protection" -> "Prot";
            case "fire_protection" -> "FireProt";
            case "blast_protection" -> "BlastProt";
            case "projectile_protection" -> "ProjProt";
            case "sharpness" -> "Sharp";
            case "unbreaking" -> "Unbr";
            case "efficiency" -> "Eff";
            case "fortune" -> "Fort";
            case "silk_touch" -> "Silk";
            case "mending" -> "Mend";
            case "feather_falling" -> "FF";
            case "thorns" -> "Thorns";
            default -> path.length() > 8 ? path.substring(0, 8) : path;
        };
    }

    private String buildText(LivingEntity living, double dist) {
        StringBuilder sb = new StringBuilder();

        if (showName.isEnabled()) {
            sb.append(living == mc.player ? "§b" : (isFriend(living) ? "§9" : "§f"));
            sb.append(living.getName().getString());
        }

        if (showHealth.isEnabled()) {
            float hp = living.getHealth();
            float maxHp = Math.max(1.0f, living.getMaxHealth());
            float pct = Math.max(0.0f, Math.min(1.0f, hp / maxHp));
            String col = pct > 0.6 ? "§a" : (pct > 0.3 ? "§e" : "§c");
            if (sb.length() > 0) sb.append(' ');
            sb.append(col).append(String.format("%.1f", hp));
        }

        if (showPing.isEnabled() && living instanceof PlayerEntity pl) {
            int ping = pingOf(pl);
            if (ping >= 0) {
                if (sb.length() > 0) sb.append(' ');
                sb.append("§7").append(ping).append("ms");
            }
        }

        if (showDistance.isEnabled()) {
            if (sb.length() > 0) sb.append(' ');
            sb.append("§7").append((int) dist).append('m');
        }

        return sb.toString();
    }

    private int pingOf(PlayerEntity pl) {
        try {
            if (mc.getNetworkHandler() == null) return -1;
            var entry = mc.getNetworkHandler().getPlayerListEntry(pl.getUuid());
            return entry != null ? entry.getLatency() : -1;
        } catch (Throwable ignored) {
            return -1;
        }
    }

    private boolean isFriend(LivingEntity living) {
        if (!(living instanceof PlayerEntity pl)) return false;
        try {
            var friends = com.anormal.client.module.ModuleManager.getModule(
                    com.anormal.client.module.impl.client.Friends.class);
            if (friends == null) return false;
            java.lang.reflect.Field f = friends.getClass().getDeclaredField("friends");
            f.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.List<String> list = (java.util.List<String>) f.get(friends);
            return list != null && list.contains(pl.getName().getString());
        } catch (Throwable ignored) {
            return false;
        }
    }
}
