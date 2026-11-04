package com.anormal.client.module.impl.kuro;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.util.kuro.KuroCombatReach;
import com.anormal.client.util.kuro.KuroRotations;
import com.anormal.client.util.kuro.KuroTickPhase;
import net.minecraft.entity.Entity;
import net.minecraft.util.Hand;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Kuro AttackPackets birga bir port. Klientning har bir o'z hujumi uchun paket
 * tartibi (Trigger Bot, Kill Aura, Silent Aura, Auto Clicker, Fast Crystal,
 * Auto Dtap, Auto Shield Break). Har bir urush aynan bitta swing paketi va bitta
 * attack paketi yuboradi:
 *  - Swing before attack OFF (default): attack paketi, keyin swing paketi
 *    (1.21 vanilla klient tartibi).
 *  - Swing before attack ON: swing paketi, keyin attack paketi (eski 1.8 tartibi).
 *
 * Paket vaqti: vanilla klient hujumlarni input paytida, o'sha tickning harakat
 * paketidan OLDIN yuboradi. Ko'pchilik modullar tick OXIRIDA qaror qabul qiladi,
 * harakat paketi esa allaqachon chiqib ketgan bo'ladi. "Send before movement
 * packet" ON (default) bo'lsa bunday urush keyingi tickning eng boshigacha
 * ushlab turiladi (hech narsa yuborilmay turib), shu bilan hujum o'sha tickning
 * harakat paketidan aslo keyin qolib ketmaydi. Bir nishonga bir tickda ko'pi
 * bilan bitta hujumga navbat qo'yiladi.
 *
 * Bu faqat settings-moduli: kalitlar modul yoqilgan-yoqilmaganidan qat'iy nazar
 * ishlaydi. O'zingizning sichqoncha kliklaringizni o'zini yuboradi va doim
 * vanilla tartibdan foydalanadi.
 */
public class KuroAttackPackets extends Module {
    private static KuroAttackPackets instance;
    public final BooleanSetting swingFirst = new BooleanSetting("Swing before attack", "Attackdan avval swing paketi (1.8 tartibi)", false);
    public final BooleanSetting beforeMove = new BooleanSetting("Send before movement packet", "Hujum harakat paketidan oldin ketsin (keyingi tick boshida)", true);

    private static final int MAX_QUEUED = 6;
    private static final double MAX_REACH = 6.0;   // ushlangan urush nishon shu qadar uzoqlashsa tashlanadi

    /** action == null: oddiy urush. Aks holda action butun paket ketma-ketligini o'zi bajradi ({@link #sequence}). */
    private record Pending(Entity target, Predicate<Entity> check, Consumer<Entity> action) {}
    private static final List<Pending> queue = new ArrayList<>();

    public KuroAttackPackets() {
        super("KuroAttackPackets", "Paket tartibi barcha jangovar hujumlar uchun", Category.KURO);
        instance = this;
        addSetting(swingFirst);
        addSetting(beforeMove);
    }

    public static boolean swingFirst() { return instance != null && instance.swingFirst.isEnabled(); }

    private static boolean holdBack() {
        return (instance == null || instance.beforeMove.isEnabled()) && KuroTickPhase.afterMovement();
    }

    /** Bitta urush: swing + attack tanlangan tartibda. Qurol qo'lingizda turgani (Auto Weapon oldindan tanlaydi). */
    public static void hit(Entity target) { submit(target, null); }

    /** Xuddi shu, lekin ushlangan urush yuborilishidan oldin "check" bilan qayta tekshiriladi va noto'g'ri bo'lsa tashlanadi. */
    public static void hit(Entity target, Predicate<Entity> check) { submit(target, check); }

    /**
     * Bitta nishonga qarshi ko'p qadamli paket ketma-ketligi (masalan: bolta hujumi, slot o'zgarishi, mace hujumi, slot
     * qaytish) — birga chiqishi shart. Oddiy urush singari keyingi tick boshigacha ushlanadi, keyin "action" bir marta,
     * bir yozuvda bajariladi va har bir hujum uchun {@link #sendNow} chaqirishi kutib olinadi. Xuddi shu nishonga navbat
     * qilingan oddiy urushni o'rnini bosadi.
     */
    public static void sequence(Entity target, Predicate<Entity> check, Consumer<Entity> action) {
        var mc = net.minecraft.client.MinecraftClient.getInstance();
        if (mc.player == null || mc.interactionManager == null || target == null || action == null) return;
        if (holdBack()) {
            queue.removeIf(p -> p.target == target);
            if (queue.size() < MAX_QUEUED) queue.add(new Pending(target, check, action));
            return;
        }
        action.accept(target);
    }

    private static void submit(Entity target, Predicate<Entity> check) {
        var mc = net.minecraft.client.MinecraftClient.getInstance();
        if (mc.player == null || mc.interactionManager == null || target == null) return;
        if (holdBack()) {
            for (Pending p : queue) if (p.target == target) return;     // bir nishonga bir tickda bir hujum
            if (queue.size() < MAX_QUEUED) queue.add(new Pending(target, check, null));
            return;
        }
        send(target);
    }

    /** Klient tick boshlanishi (harakat paketidan oldin): ushlab turilgan urushlarni yuboradi. */
    public static void flush() {
        if (queue.isEmpty()) return;
        List<Pending> todo = new ArrayList<>(queue);
        queue.clear();
        var mc = net.minecraft.client.MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null || mc.interactionManager == null || mc.currentScreen != null) return;
        for (Pending p : todo) {
            Entity e = p.target;
            if (!e.isAlive() || e.isRemoved() || mc.player.distanceTo(e) > MAX_REACH) continue;
            if (p.check != null && !p.check.test(e)) continue;
            if (p.action != null) p.action.accept(e);
            else send(e);
        }
    }

    /** Ushlab turilgan urushlarni unutish (dunyo almashganda, modul o'chirilganda...). */
    public static void clear() { queue.clear(); }

    /** Silent Aura bu nishonga nishonlagan: urushdan avval serverga o'sha burchaklarni aytadi (kamera o'zgarmaydi). */
    public static void sendSilentLook(Entity target) {
        if (!KuroRotations.active() || net.minecraft.client.MinecraftClient.getInstance().player == null) return;
        if (KuroCombatReach.rayHits(target, KuroRotations.yaw(), KuroRotations.pitch(), KuroCombatReach.MAX))
            KuroRotations.sendLookPacket(true);
    }

    /** Swing + attack hozir, tanlangan tartibda (ketma-ketliklar ishlatadi; ushlash/navbat yo'q). */
    public static void sendNow(Entity target) {
        var mc = net.minecraft.client.MinecraftClient.getInstance();
        if (mc.player != null && mc.interactionManager != null && target != null) send(target);
    }

    private static void send(Entity target) {
        var mc = net.minecraft.client.MinecraftClient.getInstance();
        if (mc.player == null || mc.interactionManager == null) return;
        sendSilentLook(target);
        boolean first = swingFirst();
        if (first) mc.player.swingHand(Hand.MAIN_HAND);
        mc.interactionManager.attackEntity(mc.player, target);
        if (!first) mc.player.swingHand(Hand.MAIN_HAND);
    }
}
