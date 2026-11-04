package com.anormal.client.module.impl.kuro;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.module.impl.client.Friends;
import com.anormal.client.util.ProjectionUtil;
import com.anormal.client.util.RenderUtils;
import com.anormal.client.util.kuro.KuroTriangles;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * kuro {@code PlayerIndicator} port: crosshair atrofidagi kichik o'qlar
 * halqasi — har o'yinchi uchun bittadan, har biri o'sha o'yinchi qaysi tomonda
 * bo'lgani (oldinga, yon yoki orqada) tomonga qaraydi, yonida nom va masofa.
 * Shikast chevroni (nur bag'dodli o'q uchi — halo + ko'rsatilgan kop + yorqin
 * markaz) sifatida chiziladi. Ixtiyoriy "Head markers" har bir bosh ustida
 * yo'g riubldik bo'g'ichni ko'rsatadi. Do'stlar do'stlik rangiga ega; AntiBot
 * botlar o'tkazib yuboriladi.
 *
 * <p>Anormaldagi farqlar: kuro'daki Setting.Hue g'ildiragi yo'q — doimiy
 * ranglar (yashil-siyoq / qizil-ishqor); ShaderUi.spot halo yo'q (parametrning
 * qadar o'qning yumshoq halosi o'tkazib yuboriladi); Freecam / AntiBot /
 * Projector Anormal ekvivalentlariga almashtirildi; ekranning orqasidagi
 * yo'q qilingan o'qlar uchun yo'nalish kamera-yaw / pitch farqidan taxmin
 * qilinadi.</p>
 */
public class KuroPlayerIndicator extends Module {
    private static final int NORMAL = 0xFF54FFC7;                  // kuro hue 0.56
    private static final int FRIEND = 0xFF7FD4FF;

    private final NumberSetting size = new NumberSetting("Size", "Arrow size", 7.0, 4.0, 14.0, 1.0);
    private final NumberSetting range = new NumberSetting("Range", "Max distance", 100.0, 10.0, 256.0, 5.0);
    private final BooleanSetting names = new BooleanSetting("Names", true);
    private final BooleanSetting distance = new BooleanSetting("Distance", true);
    private final BooleanSetting healthColor = new BooleanSetting("Colour by health", false);
    private final BooleanSetting bob = new BooleanSetting("Bobbing", true);
    private final BooleanSetting pulse = new BooleanSetting("Pulse", "Glow breathes slowly", true);
    private final BooleanSetting crosshair = new BooleanSetting("Crosshair arrows", true);
    private final NumberSetting radius = new NumberSetting("Ring radius", "Arrow ring distance", 55.0, 25.0, 160.0, 5.0);
    private final BooleanSetting headMarker = new BooleanSetting("Head markers", false);
    private final BooleanSetting invisible = new BooleanSetting("Invisible", true);
    private final BooleanSetting friends = new BooleanSetting("Friends", true);

    public KuroPlayerIndicator() {
        super("Player Indicator", "Arrows around the crosshair point to every player", Category.KURO);
        addSetting(size);
        addSetting(range);
        addSetting(names);
        addSetting(distance);
        addSetting(healthColor);
        addSetting(bob);
        addSetting(pulse);
        addSetting(crosshair);
        addSetting(radius);
        addSetting(headMarker);
        addSetting(invisible);
        addSetting(friends);
    }

    @Override
    public void onRender2D(DrawContext g, float tickDelta) {
        if (mc.player == null || mc.world == null || mc.currentScreen != null) return;
        ProjectionUtil.beginFrame(tickDelta);
        boolean cam = mc.getCameraEntity() != null && mc.getCameraEntity() != mc.player;
        Vec3d camPos = ProjectionUtil.eye(tickDelta);
        double max2 = range.getValue() * range.getValue();
        double w = RenderUtils.screenWidth(), h = RenderUtils.screenHeight();
        float pt0 = tickDelta;
        long gameTime = mc.world.getTime();
        for (PlayerEntity p : mc.world.getPlayers()) {
            if (p == mc.player && !cam) continue;
            if (p.isSpectator() || !p.isAlive() || KuroAntiBot.isBot(p)) continue;
            if (!invisible.isEnabled() && p.isInvisible()) continue;
            boolean friend = Friends.isFriend(p.getName().getString());
            if (friend && !friends.isEnabled()) continue;
            Vec3d pos = ProjectionUtil.lerpPos(p, pt0);
            double dx = pos.x - camPos.x, dy = pos.y - camPos.y, dz = pos.z - camPos.z;
            double d2 = dx * dx + dy * dy + dz * dz;
            if (d2 > max2) continue;
            double dist = Math.sqrt(d2);
            int rgb = (friend ? FRIEND : healthColor.isEnabled() ? healthRgb(p) : NORMAL) & 0xFFFFFF;
            int col = 0xFF000000 | rgb;
            int s = size.getValue().intValue();
            double wobble = bob.isEnabled() ? Math.sin((gameTime + pt0) * 0.18 + p.getId()) * 0.06 : 0.0;
            double headY = pos.y + p.getHeight() + 0.55 + wobble;

            if (crosshair.isEnabled()) ringArrow(g, pos.x, pos.y + p.getHeight() * 0.5, pos.z, col, s, dist, p.getName().getString(), camPos, tickDelta);
            if (headMarker.isEnabled()) {
                int[] pt = ProjectionUtil.project(new Vec3d(pos.x, headY, pos.z), tickDelta);
                if (pt != null && pt[0] > -20 && pt[0] < w + 20 && pt[1] > -20 && pt[1] < h + 20) {
                    // ko'rsatgich: uchi loyihalash nuqtasiga o'tirgan pastga qaragan uchburchak, masofa bilan kichikroq
                    double k = MathHelper.clamp((float) (1.25 - dist / 90.0), 0.65f, 1.25f);
                    double hw = s * k, hh = s * 1.15 * k;
                    arrowBody(g, pt[0], pt[1] - hh * 0.75, 0.0, 1.0, col, (int) Math.max(4, Math.round(hw)));
                    if (names.isEnabled() || distance.isEnabled()) {
                        String text = (names.isEnabled() ? p.getName().getString() : "")
                                + (names.isEnabled() && distance.isEnabled() ? "  " : "")
                                + (distance.isEnabled() ? Math.round(dist) + "m" : "");
                        drawTag(g, text, pt[0], (int) (pt[1] - hh - 17), col);
                    }
                }
            }
        }
    }

    /**
     * Yorqin o'q uchi (notched chevron, uchi old tomonda), (cx, cy) atrofida,
     * (vx, vy) yo'nalishida: kurodagi yorug'lik qatlamlaridan soddalashtirilgan
     * versiya (halo yo'q): ko'rsatilgan tanaga yaqin oqongan korpus, yorqin
     * markaz va yengil chegara.
     */
    private void arrowBody(DrawContext g, double cx, double cy, double vx, double vy, int col, int s) {
        double nx = -vy, ny = vx, a = s;
        int rgb = col & 0xFFFFFF;

        // shape: uchi, o'ng qanot, o'yik (notch), chap qanot
        double tipX = cx + vx * a * 1.3, tipY = cy + vy * a * 1.3;
        double rwX = cx - vx * a * 0.8 + nx * a * 0.95, rwY = cy - vy * a * 0.8 + ny * a * 0.95;
        double lwX = cx - vx * a * 0.8 - nx * a * 0.95, lwY = cy - vy * a * 0.8 - ny * a * 0.95;
        double ntX = cx - vx * a * 0.2, ntY = cy - vy * a * 0.2;

        KuroTriangles.fillTriangle(g, tipX, tipY, rwX, rwY, ntX, ntY, withAlpha(mix(rgb, 0xFFFFFF, 0.28f), 0xF0));
        KuroTriangles.fillTriangle(g, tipX, tipY, ntX, ntY, lwX, lwY, withAlpha(mix(rgb, 0xFFFFFF, 0.28f), 0xF0));

        // core: markazdan oldinda, deyarli oq kichik nusxa
        double k = 0.52, ox = vx * a * 0.18, oy = vy * a * 0.18;
        double ctX = cx + ox + (tipX - cx) * k, ctY = cy + oy + (tipY - cy) * k;
        double crX = cx + ox + (rwX - cx) * k, crY = cy + oy + (rwY - cy) * k;
        double clX = cx + ox + (lwX - cx) * k, clY = cy + oy + (lwY - cy) * k;
        double cnX = cx + ox + (ntX - cx) * k, cnY = cy + oy + (ntY - cy) * k;
        KuroTriangles.fillTriangle(g, ctX, ctY, crX, crY, cnX, cnY, withAlpha(mix(rgb, 0xFFFFFF, 0.8f), 0xE0));
        KuroTriangles.fillTriangle(g, ctX, ctY, cnX, cnY, clX, clY, withAlpha(mix(rgb, 0xFFFFFF, 0.8f), 0xE0));

        // chet chiziqlari (RenderUtils.drawLine o'zining engil xiraligiga ega)
        int edge = withAlpha(mix(rgb, 0xFFFFFF, 0.6f), 0xC0);
        RenderUtils.drawLine(g, (int) tipX, (int) tipY, (int) rwX, (int) rwY, edge, 1);
        RenderUtils.drawLine(g, (int) rwX, (int) rwY, (int) ntX, (int) ntY, edge, 1);
        RenderUtils.drawLine(g, (int) ntX, (int) ntY, (int) lwX, (int) lwY, edge, 1);
        RenderUtils.drawLine(g, (int) lwX, (int) lwY, (int) tipX, (int) tipY, edge, 1);
    }

    /** Crosshair atrofidagi halqada o'q, o'yinchiga qaragan (orqada/upaypachka ham). */
    private void ringArrow(DrawContext g, double x, double y, double z, int col, int s, double dist, String name, Vec3d camPos, float tickDelta) {
        double cx = RenderUtils.screenWidth() / 2.0, cy = RenderUtils.screenHeight() / 2.0, r = radius.getValue();
        int[] pt = ProjectionUtil.project(new Vec3d(x, y, z), tickDelta);
        if (pt != null && Math.hypot(pt[0] - cx, pt[1] - cy) < r + s * 2.5) return;   // onscreen va yaqin: faqat to'sqinlik

        // yo'nalish: ekrandan tashqari (yoki orqada) — kamera yaw/pitch farqidan taxmin qilinadi
        var camEnt = mc.getCameraEntity() != null ? mc.getCameraEntity() : mc.player;
        double ddx = x - camPos.x, ddy = y - camPos.y, ddz = z - camPos.z;
        double targetYaw = Math.toDegrees(Math.atan2(-ddx, ddz));
        double targetPitch = -Math.toDegrees(Math.atan2(ddy, Math.max(1e-4, Math.hypot(ddx, ddz))));
        double yawDiff = MathHelper.wrapDegrees(targetYaw - camEnt.getYaw());
        double pitchDiff = targetPitch - camEnt.getPitch();
        double vx = -Math.sin(Math.toRadians(yawDiff)), vy = pitchDiff;
        double len = Math.hypot(vx, vy);
        if (len < 1e-3) { vx = 0; vy = 1; len = 1; }
        vx /= len; vy /= len;
        double px = cx + vx * r, py = cy + vy * r;           // halqadagi o'q markazi
        arrowBody(g, px, py, vx, vy, col, s);
        if (names.isEnabled() || distance.isEnabled()) {
            String text = (names.isEnabled() ? name : "")
                    + (names.isEnabled() && distance.isEnabled() ? "  " : "")
                    + (distance.isEnabled() ? Math.round(dist) + "m" : "");
            double lx = cx + vx * (r + s * 2.0 + 6), ly = cy + vy * (r + s * 2.0 + 6);
            // label o'qdan tashqarida; o'qni hech qachon yopmaslikkerak uchun yoniga suriladi
            TextRenderer tr = mc.textRenderer;
            int half = tr != null ? (tr.getWidth(text) + 14) / 2 : 20;
            drawTag(g, text, (int) (lx + vx * half), (int) (ly + vy * 7) - 6, col);
        }
    }

    private void drawTag(DrawContext g, String text, int cx, int y, int argb) {
        TextRenderer tr = mc.textRenderer;
        if (tr == null) return;
        int w = tr.getWidth(text);
        g.fill(cx - w / 2 - 4, y - 3, cx + w / 2 + 4, y + 9, 0xB0101214);
        g.fill(cx - w / 2 - 4, y - 3, cx - w / 2 - 1, y + 9, argb);
        RenderUtils.drawText(g, tr, text, cx - w / 2, y, 0xFFE8E8F0, false);
    }

    private static int mix(int a, int b, float t) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int r = (int) (ar + (br - ar) * t), gr = (int) (ag + (bg - ag) * t), b2 = (int) (ab + (bb - ab) * t);
        return (r << 16) | (gr << 8) | b2;
    }

    private static int withAlpha(int rgb, int a) { return (a << 24) | (rgb & 0xFFFFFF); }

    private static int healthRgb(PlayerEntity p) {
        float f = MathHelper.clamp((p.getHealth() + p.getAbsorptionAmount()) / Math.max(1f, p.getMaxHealth()), 0f, 1f);
        int r = (int) (255 * Math.min(1f, (1f - f) * 2f)), gr = (int) (255 * Math.min(1f, f * 2f));
        return (r << 16) | (gr << 8) | 0x40;
    }
}
