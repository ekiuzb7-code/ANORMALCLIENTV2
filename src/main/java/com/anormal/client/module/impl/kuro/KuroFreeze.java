package com.anormal.client.module.impl.kuro;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;

import net.minecraft.util.math.Vec3d;

/**
 * kuro {@code Freeze} port. Standart ("Move locally" yoqilgan) rejimda server
 * sizni yoqilgandagi joyni saqlaydi — hech qanday harakat paketlari yuborilmaydi,
 * lekin o‘z ekraningizda yurib, uchib va aylanaverasiz. O‘chirsangiz sizni
 * joyingizga qaytaradi ("Snap back"). "Move locally" o‘chirilsa o‘rnatingiz
 * lokally bloklanadi (yurish/tushish yo‘q, knockback bekor).
 *
 * <p>Anormaldagi farqlar: kuro'daki paket-yutish tizimi alohida
 * {@link com.anormal.client.mixin.KuroFreezePacketMixin} orqali ishlaydi;
 * server teleportini aniqlovchi qism {@code ConnectionMixin} ichida
 * {@code PlayerPositionLookS2CPacket} bilan bunday hushyorlikcha aniqlanadi;
 * kuro'daki {@code early()} bosqichi bu portda {@code onTickAlways()} bilan
 * vositalashtiriladi.</p>
 */
public class KuroFreeze extends Module {
    private static KuroFreeze instance;

    private final BooleanSetting local = new BooleanSetting("Move locally", "Yoqilgan bo‘lsa — faqat server hisobini tomda ushlab turadi", true);
    private final BooleanSetting snapBack = new BooleanSetting("Snap back on release", "O‘chirilganda saqlangan nuqtaga qaytaradi", false);
    private final BooleanSetting lockPosition = new BooleanSetting("Local lock: snap if pushed", "Itarishga qaramasdan joyga qaytaradi (faqat Move locally bilan)", true);
    private final BooleanSetting midAir = new BooleanSetting("Local lock: freeze mid-air", "Havoda ham tushmay qetish (faqat Move locally bilan)", true);

    // Freeze moduli ozi tilak-lopat yordamchisi: harakat paketlarini yutib yuboradi (mixin) va
    // server teleportini PlayerPositionLookS2CPacket orqali bilib oladigan ConnectionMixin qismi bilan boglanadi.

    private Vec3d anchor;       // saqlangan server nuqtasi
    private int letThrough;     // server teleportdan keyin 3 tick harakat paketi o‘tishga ruhsat etiladi

    public KuroFreeze() {
        super("Freeze", "Hold the position: on the server (hold packets) or locally (kuro port)", Category.KURO);
        addSetting(local);
        addSetting(snapBack);
        addSetting(lockPosition);
        addSetting(midAir);
        instance = this;
    }

    /** Chiqarilayotgan harakat paketlari yutib yuborilishi kerak (mixin ko‘radi). */
    public static boolean holding() { return instance != null && instance.isEnabled() && instance.local.isEnabled() && instance.letThrough <= 0; }

    /** Server sizni ko‘chirganda (teleport / rubber-band): yangi nuqtadan ushlash va tasdiqni o‘tqazish. */
    public static void onServerTeleport() {
        if (instance == null || !instance.isEnabled() || instance.mc.player == null) return;
        instance.letThrough = 3;
        instance.anchor = null;      // keyingi tickda yangi joydan o‘qiladi
    }

    @Override
    public void onEnable() {
        letThrough = 0;
        anchor = mc.player != null ? new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ()) : null;
    }

    @Override
    public void onDisable() {
        if (mc.player != null && anchor != null && local.isEnabled() && snapBack.isEnabled()) {
            mc.player.setPos(anchor.x, anchor.y, anchor.z);
            mc.player.setVelocity(Vec3d.ZERO);
        }
        anchor = null;
    }

    /** Tick boshida, simulyatsiyadan oldin — muzlagan holat shu tick hisoblanadi. */
    @Override
    public void onTickAlways() { tickHold(); }

    @Override
    public void onTick() { if (!local.isEnabled()) hold(); }

    private void tickHold() {
        if (mc.player == null) return;
        if (letThrough > 0) letThrough--;
        if (anchor == null) anchor = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        if (!local.isEnabled()) hold();
    }

    /** lokal blokls âksiyada: o‘z ekraningizda umuman yoka qo‘shimcha haqiqatlari tuxsuklaringiz elingan bo‘lmasdan. */
    private void hold() {
        if (mc.player == null || anchor == null) return;
        Vec3d v = mc.player.getVelocity();
        mc.player.setVelocity(0, midAir.isEnabled() ? 0 : Math.min(0, v.y), 0);
        if (lockPosition.isEnabled()) {
            double dx = mc.player.getX() - anchor.x, dz = mc.player.getZ() - anchor.z, dy = mc.player.getY() - anchor.y;
            boolean fallingFree = !midAir.isEnabled();
            if (dx * dx + dz * dz > 1.0E-4 || (!fallingFree && dy * dy > 1.0E-4)) {
                mc.player.setPos(anchor.x, fallingFree ? mc.player.getY() : anchor.y, anchor.z);
            }
            if (fallingFree) anchor = new Vec3d(anchor.x, mc.player.getY(), anchor.z);
        }
    }
}
