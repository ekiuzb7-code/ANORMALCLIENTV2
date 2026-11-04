package com.anormal.client.module.impl.kuro;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.Vec3d;

/**
 * Kuro Criticals birga bir port: yerga turgan holatda urushlaringiz kritik bo'ladi.
 * Hujum paketidan avval ikkta kichik "havoda" pozitsiya-paketi yuboriladi — server
 * sizni tushqonda deb ko'radi, keyin kritik urushga sabab bo'ladi.
 *
 * <p>kuro AttackEntityCallback'da aynan hujumda ushlaydi; Anormal'da fabric event
 * compile-classpath'da to'luq emas, shuning uchun hujum paytini swing-edge'ga
 * yaqin holda aniqlaymiz: attack belgilangan holda qo'l swing-eh qadami yangi
 * bo'lganda. Bu hujumdan bir tick o'tgan holatga qisqa holda jo'natadi; anticheat
 * kuchini biroz pasaytinsada, sozlanishi kafolatlanadi. AttackPackets orqali
 * qilingan kuro aura hujumlaridan bireng Criticals moduli static ichida ishlatadi.</p>
 */
public class KuroCriticals extends Module {
    private boolean wasSwinging;

    public KuroCriticals() {
        super("Criticals", "Yer ustda ham kritik urush (kuro port)", Category.KURO);
    }

    @Override
    public void onEnable() {
        wasSwinging = mc.player != null && mc.player.handSwinging;
    }

    @Override
    public void onDisable() {
        wasSwinging = false;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.currentScreen != null) {
            wasSwinging = false;
            return;
        }
        // Hujum aniqlash (KuroWTap'dagi xuddi shu ilova): hujum belgilangan holda
        // swing-lar belem qiluvchisiga yaqin bo'ladi.
        boolean attacking = mc.options.attackKey.isPressed() && mc.targetedEntity != null;
        boolean swingEdge = attacking && mc.player.handSwinging && !wasSwinging;
        wasSwinging = mc.player.handSwinging;
        if (swingEdge) {
            crit(mc.targetedEntity);
        }
    }

    /** Aura hujumlaridan oldin chaqiriladi (AttackPackets yuborilishidan avval). */
    public static void crit(Entity target) {
        MinecraftClient mci = MinecraftClient.getInstance();
        if (mci.player == null || mci.getNetworkHandler() == null) return;
        if (target != null && !(target instanceof LivingEntity)) return;
        if (!mci.player.isOnGround() || mci.player.isTouchingWater() || mci.player.isInLava()
                || mci.player.hasVehicle() || mci.player.isClimbing()) return;
        send(mci, mci.player.getX(), mci.player.getY() + 0.0625, mci.player.getZ());
        send(mci, mci.player.getX(), mci.player.getY(), mci.player.getZ());
    }

    private static void send(MinecraftClient mci, double x, double y, double z) {
        // PositionAndOnGround(pos, onGround, horizontalCollision)
        mci.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(new Vec3d(x, y, z), false, false));
    }
}
