package com.anormal.client.module.impl.kuro;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.util.ProjectionUtil;
import com.anormal.client.util.RenderUtils;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.util.math.Vec3d;

import java.util.List;

/**
 * kuro {@code Tracers} port: crosshair'dan (ekran markazi) klient yuklagan
 * har o'yinchiga chiziq chizadi. Nishon orqada bo'lsa yoki ekrandan chiqsa,
 * chiziq baribir to'g'ri tomonga ko'rsatadi. kuro'dagi {@code Setting.Hue}
 * rang g'ildiragi Anormalda yo'q — doimiy issiq amber rang ishlatiladi
 * (KuroAncientDebris'dagi holatga o'xshab).
 */
public class KuroTracers extends Module {
    private static final int LINE = 0xC8FFD27F;                 // amber, alpha C8
    private static final int OPPOSITE = 0xC8FF7AEE;             // x желудov вleft: contrast hue for off-screen

    private final NumberSetting thickness = new NumberSetting("Thickness", "Line width", 1.0, 1.0, 3.0, 1.0);
    private final BooleanSetting body = new BooleanSetting("Aim at body", "Off = feet", true);
    private final BooleanSetting invisible = new BooleanSetting("Invisible", "Also draw invisible players", true);

    public KuroTracers() {
        super("Tracers", "Lines from the crosshair to every loaded player", Category.KURO);
        addSetting(thickness);
        addSetting(body);
        addSetting(invisible);
    }

    @Override
    public void onRender2D(DrawContext g, float tickDelta) {
        if (mc.player == null || mc.world == null) return;
        ProjectionUtil.beginFrame(tickDelta);
        boolean cam = freecamActive();
        double cx = RenderUtils.screenWidth() / 2.0, cy = RenderUtils.screenHeight() / 2.0;
        List<AbstractClientPlayerEntity> players = mc.world.getPlayers();
        for (AbstractClientPlayerEntity p : players) {
            if (p == mc.player && !cam) continue;
            if (p.isSpectator() || (!invisible.isEnabled() && p.isInvisible())) continue;
            Vec3d pos = ProjectionUtil.lerpPos(p, tickDelta);
            double y = pos.y + (body.isEnabled() ? p.getHeight() * 0.5 : 0.0);
            int[] pt = ProjectionUtil.project(new Vec3d(pos.x, y, pos.z), tickDelta);   // off-screen / behind still point the right way
            if (pt == null) continue;
            RenderUtils.drawLine(g, (int) cx, (int) cy, pt[0], pt[1], LINE, thickness.getValue().intValue());
        }
    }

    /** Faqat boshqa modullar freecam pastlarida ko'rinadi; bu yerda: kamera o'yinchi emas bo'lsa. */
    private static boolean freecamActive() {
        return mc.getCameraEntity() != null && mc.getCameraEntity() != mc.player;
    }
}
