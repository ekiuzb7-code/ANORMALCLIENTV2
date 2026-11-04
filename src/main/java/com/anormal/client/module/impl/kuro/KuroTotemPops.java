package com.anormal.client.module.impl.kuro;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.module.impl.client.Friends;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.theme.ThemeManager;
import com.anormal.client.util.RenderUtils;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Kuro TotemPops porti (dev.kuro.client.module.impl.TotemPops'dan Anormal'da).
 *
 * <p>Totem poplarini sanaydi. Server har bir klientga o'yinchining totem ochganini
 * (entity status 35) va o'lganini (status 3) aytadi; {@code KuroTotemPopsMixin}
 * ikkalasini shu yerga uzatadi. Har popda chat xabari, ixtiyoriy alert ovozi va
 * ekranda oxirgi poplar ro'yxati. O'yinchi o'lsa, uning sanog'i nolga tushadi.</p>
 */
public class KuroTotemPops extends Module {
    private static final byte STATUS_TOTEM = 35;
    private static final byte STATUS_DEATH = 3;

    private static KuroTotemPops instance;

    private final BooleanSetting chat = new BooleanSetting("Chat message",
            "Har popda chatga yozish", true);
    private final BooleanSetting alertSound = new BooleanSetting("Alert sound",
            "Popda ovoz chiqarish (faqat klientda)", false);
    private final BooleanSetting hudList = new BooleanSetting("HUD list",
            "Ekranda oxirgi poplar ro'yxatini ko'rsatish", true);
    private final NumberSetting showFor = new NumberSetting("List time (s)",
            "Yozuv ro'yxatda qancha soniya turadi", 60.0, 10.0, 600.0, 10.0);
    private final BooleanSetting self = new BooleanSetting("Count yourself",
            "O'zingizning poplaringizni ham sanash", false);
    private final BooleanSetting skipFriends = new BooleanSetting("Ignore friends",
            "Do'stlarni hisobga olmaslik", false);
    private final BooleanSetting deathMsg = new BooleanSetting("Death message",
            "O'limda nechta pop bo'lganini yozish", true);

    private static final class Pops {
        String name;
        int count;
        long last;
    }

    private final Map<UUID, Pops> pops = new HashMap<>();
    private World lastWorld;

    public KuroTotemPops() {
        super("TotemPops", "Totem poplarini sanaydi (kuro port)", Category.KURO);
        instance = this;
        addSetting(chat);
        addSetting(alertSound);
        addSetting(hudList);
        addSetting(showFor);
        addSetting(self);
        addSetting(skipFriends);
        addSetting(deathMsg);
    }

    @Override
    public void onDisable() {
        pops.clear();
    }

    @Override
    public void onTick() {
        if (mc.world != lastWorld) {
            lastWorld = mc.world;
            pops.clear();
        }
    }

    /** KuroTotemPopsMixin entity status paketlarini shu yerga uzatadi (game thread). */
    public static void onStatus(Entity e, byte status) {
        if (instance == null || !instance.isEnabled()) return;
        if (status != STATUS_TOTEM && status != STATUS_DEATH) return;
        if (e instanceof PlayerEntity p) instance.handle(p, status);
    }

    private void handle(PlayerEntity p, byte status) {
        if (mc.player == null) return;
        if (p == mc.player && !self.isEnabled()) return;
        String name = p.getName().getString();
        if (skipFriends.isEnabled() && Friends.isFriend(name)) return;

        if (status == STATUS_TOTEM) {
            Pops e = pops.computeIfAbsent(p.getUuid(), k -> new Pops());
            e.name = name;
            e.count++;
            e.last = System.currentTimeMillis();
            if (chat.isEnabled()) {
                mc.player.sendMessage(Text.literal("[Pops] ").formatted(Formatting.GOLD)
                        .append(Text.literal(name + " popped a totem (" + e.count + ")").formatted(Formatting.YELLOW)), false);
            }
            if (alertSound.isEnabled()) {
                mc.getSoundManager().play(PositionedSoundInstance.ui(SoundEvents.ITEM_TOTEM_USE, 1.0f, 1.0f));
            }
        } else {
            Pops e = pops.remove(p.getUuid());
            if (e != null && deathMsg.isEnabled()) {
                mc.player.sendMessage(Text.literal("[Pops] ").formatted(Formatting.GOLD)
                        .append(Text.literal(name + " died after " + e.count + " pop" + (e.count == 1 ? "" : "s"))
                                .formatted(Formatting.RED)), false);
            }
        }
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (!hudList.isEnabled() || mc.textRenderer == null) return;
        if (pops.isEmpty()) return;

        long now = System.currentTimeMillis();
        long keep = (long) showFor.getAsDouble() * 1000L;
        pops.values().removeIf(p -> now - p.last > keep);
        if (pops.isEmpty()) return;

        List<Pops> list = new ArrayList<>(pops.values());
        list.sort(Comparator.<Pops>comparingInt(p -> -p.count).thenComparingLong(p -> -p.last));
        if (list.size() > 8) list = list.subList(0, 8);

        int w = mc.textRenderer.getWidth("Totem pops") + 16;
        for (Pops p : list) {
            w = Math.max(w, mc.textRenderer.getWidth(p.name + " x" + p.count) + 18);
        }
        int h = 14 + list.size() * 11;
        int x = 4, y = 38;

        RenderUtils.fill(context, x, y, x + w, y + h, ThemeManager.getBackgroundColor());
        RenderUtils.drawBorder(context, x, y, x + w, y + h, 1, ThemeManager.getBorderColor());
        RenderUtils.drawText(context, mc.textRenderer, "Totem pops", x + 8, y + 3, 0xFFAAAAAA, false);

        int ry = y + 14;
        for (Pops p : list) {
            RenderUtils.drawText(context, mc.textRenderer, p.name, x + 8, ry, 0xFFFFFFFF, false);
            String c = "x" + p.count;
            RenderUtils.drawText(context, mc.textRenderer, c,
                    x + w - 7 - mc.textRenderer.getWidth(c), ry, p.count >= 3 ? 0xFFFF5555 : 0xFFFFAA00, false);
            ry += 11;
        }
    }
}
