package com.anormal.client.util.kuro;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * Vanilla yaqin jang masofasi: o'yinchining KO'ZLARidan nishon hitbox'ining
 * eng yaqin nuqtasiga qadar ko'pi bilan 3.0 blok. Reach modulini ataylab
 * hisobga olmaydi — bolta/mace urushlari vanilladan uzoqroqni bormasin.
 */
public final class KuroCombatReach {
    public static final double MAX = 3.0;

    private KuroCombatReach() {}

    /** Ko'zlardan entity hitbox'ining eng yaqin nuqtasiga masofa (ko'z ichida bo'lsa 0 bo'ladi). */
    public static double eyeDistance(Entity e) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || e == null) return Double.MAX_VALUE;
        Vec3d eye = mc.player.getEyePos();
        Box b = e.getBoundingBox();
        double dx = Math.max(Math.max(b.minX - eye.x, 0), eye.x - b.maxX);
        double dy = Math.max(Math.max(b.minY - eye.y, 0), eye.y - b.maxY);
        double dz = Math.max(Math.max(b.minZ - eye.z, 0), eye.z - b.maxZ);
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public static boolean inReach(Entity e) { return inReach(e, MAX); }

    /** "limit" vanilla 3.0 gacha cheklab qo'yilgan. */
    public static boolean inReach(Entity e, double limit) { return eyeDistance(e) <= Math.min(limit, MAX); }

    /** Ko'zlardan (yaw, pitch) yo'nalishda ko'pi bilan "limit" (may 3.0) uzunlikdagi nur entity hitbox'iga tegadi. */
    public static boolean rayHits(Entity e, float yaw, float pitch, double limit) { return rayHits(e, yaw, pitch, limit, 0.0); }

    /** Xuddi shu, hitbox "margin" blokga kengaytirilgan (mace davomi uchun: bolta nishonni orqaga itarib yuborganidan keyin). */
    public static boolean rayHits(Entity e, float yaw, float pitch, double limit, double margin) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || e == null) return false;
        double len = Math.min(limit, MAX);
        Vec3d eye = mc.player.getEyePos();
        Vec3d end = eye.add(Vec3d.fromPolar(pitch, yaw).multiply(len));
        Box box = margin > 0 ? e.getBoundingBox().expand(margin) : e.getBoundingBox();
        return box.contains(eye) || box.raycast(eye, end).isPresent();
    }

    /** Aniq "len" bloklik nur, 3.0 ga cheklanmagan (faqat tez tushuv paytida nishonni o'ldirishda; urush o'zi 3.0 da tekshiriladi). */
    public static boolean rayHitsLen(Entity e, float yaw, float pitch, double len) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || e == null) return false;
        Vec3d eye = mc.player.getEyePos();
        Vec3d end = eye.add(Vec3d.fromPolar(pitch, yaw).multiply(len));
        Box box = e.getBoundingBox();
        return box.contains(eye) || box.raycast(eye, end).isPresent();
    }
}
