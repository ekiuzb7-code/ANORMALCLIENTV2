package com.anormal.client.module.impl.kuro;

import com.anormal.client.lucid.WorldRender3D;
import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.module.ModuleManager;
import com.anormal.client.module.impl.client.Friends;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.ColorUtils;
import com.anormal.client.util.ProjectionUtil;
import com.anormal.client.util.RenderUtils;
import com.anormal.client.util.kuro.KuroBallistics;
import com.anormal.client.util.kuro.KuroFreecam;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/**
 * kuro {@code PearlFinder} port.
 *
 * <p>Boshqa ko'ongan har bir ender pearl'ni havoda xorlaydi. Har pearl
 * devorlar orasidan ko'rinadigan yorqin quti, crosshair'dan tracer,
 * otganning ismi va masofasi bilan yozuv, bashorat qilingan parvoz
 * (gravity 0.03, drag 0.99) va qo'nish beligisi — shu odam teleport
 * bo'ladigan joy. O'z pearl'laringiz va do'stlaringizniki o'tkaziladi
 * (do'stlar faqat "Friends" settingsi yoqilmagan bo'lsa).</p>
 *
 * <p>Moslash: kuro world-quti'ni WorldGfx hook orqali; bu yerda Fabric
 * {@code WorldRenderEvents.END_MAIN} bilan {@code WorldRender3D} dan
 * foydalaniladi (Anormal'ning mavjud kuro render yechimi), tracer/yozuvlar
 * onRender2D'da loyihalanadi.</p>
 */
public class KuroPearlFinder extends Module {
    private final NumberSetting range = new NumberSetting("Range", "Scan distance in blocks", 128.0, 16.0, 256.0, 8.0);
    private final ColorSetting color = new ColorSetting("Color", "Pearl box color", ColorUtils.rgba(214, 108, 255, 220));
    private final ColorSetting landColor = new ColorSetting("Landing color", "Landing marker color", ColorUtils.rgba(31, 224, 158, 220));
    private final NumberSetting thickness = new NumberSetting("Thickness", "Line thickness", 2.0, 1.0, 3.0, 1.0);
    private final BooleanSetting glow = new BooleanSetting("Glow", true);
    private final BooleanSetting fill = new BooleanSetting("Fill", true);
    private final BooleanSetting tracers = new BooleanSetting("Tracers", true);
    private final BooleanSetting labels = new BooleanSetting("Labels", true);
    private final BooleanSetting path = new BooleanSetting("Predicted path", true);
    private final BooleanSetting landing = new BooleanSetting("Landing marker", true);
    private final BooleanSetting unknown = new BooleanSetting("Unknown owners", true);   // thrower not loaded on your client
    private final BooleanSetting friends = new BooleanSetting("Friends", false);

    private record Found(Vec3d pos, String owner, double dist, KuroBallistics.Result flight) {}

    private List<Found> found = List.of();
    private boolean registered;

    public KuroPearlFinder() {
        super("Pearl Finder", "Highlights ender pearls other players threw", Category.KURO);
        addSetting(range);
        addSetting(color);
        addSetting(landColor);
        addSetting(thickness);
        addSetting(glow);
        addSetting(fill);
        addSetting(tracers);
        addSetting(labels);
        addSetting(path);
        addSetting(landing);
        addSetting(unknown);
        addSetting(friends);
    }

    @Override public void onDisable() { found = List.of(); }

    @Override
    public void onEnable() {
        if (registered) return;
        try {
            WorldRenderEvents.END_MAIN.register(this::anormal$dispatchWorld);
            registered = true;
        } catch (Throwable ignored) {
        }
    }

    /** True for a pearl that should be shown. owner[0] gets the display name. */
    private boolean enemy(Entity e, String[] owner) {
        if (!(e instanceof ProjectileEntity pr)) return false;
        Entity o = pr.getOwner();
        if (o == mc.player) return false;
        if (o == null) { owner[0] = "Unknown"; return unknown.isEnabled(); }
        owner[0] = o.getName().getString();
        if (o instanceof PlayerEntity && Friends.isFriend(owner[0]) && !friends.isEnabled()) return false;
        return true;
    }

    private void scan() {
        if (mc.world == null || mc.player == null) { found = List.of(); return; }
        List<Found> out = new ArrayList<>();
        double max2 = range.getValue() * range.getValue();
        String[] owner = new String[1];
        float tickProgress = mc.getRenderTickCounter().getTickProgress(false);
        for (Entity e : mc.world.getEntities()) {
            if (e.getType() != EntityType.ENDER_PEARL || e.isRemoved()) continue;
            Vec3d pos = e.getLerpedPos(tickProgress);
            Vec3d cam = mc.gameRenderer.getCamera().getCameraPos();
            double d2 = pos.squaredDistanceTo(cam);
            if (d2 > max2 || !enemy(e, owner)) continue;
            KuroBallistics.Result flight = null;
            if (path.isEnabled() || landing.isEnabled()) {
                Vec3d v = e.getVelocity();
                if (v.lengthSquared() > 1.0E-4) flight = KuroBallistics.simulate(e, pos, v, 0.03, 0.99, 120, false);
            }
            out.add(new Found(pos, owner[0], Math.sqrt(d2), flight));
        }
        found = out;
    }

    @Override
    public void onTick() {
        scan();
    }

    // ------------------------------------------------------------------ qutilar (world)

    private void anormal$dispatchWorld(WorldRenderContext wrc) {
        if (!isEnabled() || found.isEmpty()) return;
        Camera camera = wrc.gameRenderer().getCamera();
        if (camera == null || !camera.isReady()) return;
        MatrixStack matrices = wrc.matrices();
        VertexConsumerProvider consumers = wrc.consumers();
        if (matrices == null || consumers == null) return;

        int rgb = color.getColor() & 0xFFFFFF;
        int line = 0xFF000000 | rgb;
        int shade = fill.isEnabled() ? (0x50 << 24) | rgb : 0;
        for (Found f : found) {
            Vec3d p = f.pos();
            double s = 0.25;      // a little bigger than the 0.25 pearl hitbox so it is easy to spot at range
            net.minecraft.util.math.Box box = new net.minecraft.util.math.Box(p.x - s, p.y - s + 0.125, p.z - s,
                    p.x + s, p.y + s + 0.125, p.z + s);
            WorldRender3D.begin(matrices, camera);
            try {
                if (shade != 0) WorldRender3D.drawFilledBox(consumers, box, shade);
                WorldRender3D.drawBox(consumers, box, line);
                if (landing.isEnabled() && f.flight() != null) {
                    Vec3d e = f.flight().end;
                    int lr = landColor.getColor() & 0xFFFFFF;
                    double h = 0.3;
                    net.minecraft.util.math.Box lb = new net.minecraft.util.math.Box(e.x - h, e.y, e.z - h,
                            e.x + h, e.y + 2 * h, e.z + h);
                    if (fill.isEnabled()) WorldRender3D.drawFilledBox(consumers, lb, (0x50 << 24) | lr);
                    WorldRender3D.drawBox(consumers, lb, 0xFF000000 | lr);
                }
            } finally {
                WorldRender3D.end(matrices);
            }
        }
    }

    // ------------------------------------------------------------------ tracers / path / labels (2D)

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (found.isEmpty() || mc.currentScreen != null) return;
        if (mc.textRenderer == null) return;
        float tickProgress = mc.getRenderTickCounter().getTickProgress(false);
        int rgb = 0xFFFFFFFF;
        int line = rgb;
        int landLine = landColor.getColor();
        int t = thickness.getValue().intValue();
        float cx = RenderUtils.screenWidth() / 2f, cy = RenderUtils.screenHeight() / 2f;
        for (Found f : found) {
            Vec3d p = f.pos();
            int[] pt = ProjectionUtil.project(p, tickProgress);
            if (tracers.isEnabled() && pt != null) {
                RenderUtils.drawLine(context, (int) cx, (int) cy, pt[0], pt[1], withAlpha(color.getColor(), 0xB4), t);
            }
            KuroBallistics.Result fl = f.flight();
            if (path.isEnabled() && fl != null) {
                for (int i = 1; i < fl.pts.size(); i++) {
                    int[] a = ProjectionUtil.project(fl.pts.get(i - 1), tickProgress);
                    int[] b = ProjectionUtil.project(fl.pts.get(i), tickProgress);
                    if (a != null && b != null) RenderUtils.drawLine(context, a[0], a[1], b[0], b[1], landLine, t);
                }
            }
            if (labels.isEnabled()) {
                if (pt != null)
                    RenderUtils.drawText(context, mc.textRenderer,
                            "Pearl  " + f.owner() + "  " + Math.round(f.dist()) + "m", pt[0], pt[1] - 7, line, true);
                if (landing.isEnabled() && fl != null) {
                    int[] le = ProjectionUtil.project(fl.end.add(0, 0.9, 0), tickProgress);
                    if (le != null)
                        RenderUtils.drawText(context, mc.textRenderer,
                                "Lands " + f.owner() + "  " + fl.ticks + "t", le[0], le[1] - 7, landLine, true);
                }
            }
        }
    }

    private static int withAlpha(int argb, int alpha) {
        return (alpha << 24) | (argb & 0xFFFFFF);
    }
}
