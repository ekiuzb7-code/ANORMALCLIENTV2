package com.anormal.client.module.impl.kuro;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.FishingBobberEntity;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;

import java.util.concurrent.ThreadLocalRandom;

/**
 * kuro {@code AutoFish} port.
 *
 * <p>Sh(service)cho'ng'ir cho'zilgan holda Auto Fish yoqiladi: cho'qiret,
 * chichish (tishlash) javob kuzatadi, insoniy birozligi o'tirib qaytaradi
 * va qaytadan cho'qiradi. Tishlash bobber nido imdoni (yoki bobber
 * to'satdan cho'qilishi) orqali seziladi. Belgilagan vaqtda kleydek
 * chiqmagan bo'lsa, qaytadan cho'qiret, shuning uchun bobber qotib
 * qolmaydi. Faqat asosiy/off qo'lda sh(service)cho'ng'ir bo'lsa va
 * menyuv ochiq bo'lmasa ishlaydi.</p>
 */
public class KuroAutoFish extends Module {
    private static KuroAutoFish instance;
    private static final int GRACE = 40;               // ticks after casting during which splashes (landing) are ignored

    private final BooleanSetting autoCast = new BooleanSetting("Auto cast", true);
    private final NumberSetting reelDelay = new NumberSetting("Reel delay (ticks)", "Wait before reeling after a bite", 6.0, 1.0, 20.0, 1.0);
    private final NumberSetting castDelay = new NumberSetting("Cast delay (ticks)", "Wait before re-casting", 20.0, 5.0, 60.0, 1.0);
    private final NumberSetting timeout = new NumberSetting("Recast timeout (s)", "Re-cast if nothing bites for this long", 45.0, 10.0, 120.0, 5.0);

    private enum Stage { WAIT, REEL, RECAST }
    private Stage stage = Stage.WAIT;
    private int timer, sinceCast;
    private boolean bite;

    public KuroAutoFish() {
        super("Auto Fish", "Casts, waits for the bite and reels in for you", Category.KURO);
        instance = this;
        addSetting(autoCast);
        addSetting(reelDelay);
        addSetting(castDelay);
        addSetting(timeout);
    }

    @Override public void onEnable() { reset(); }
    @Override public void onDisable() { reset(); }

    private void reset() { stage = Stage.WAIT; timer = 0; sinceCast = 0; bite = false; }

    /** Sound-paket hook'dan chaqiriladi: bobber splash = baliq to'qish. */
    public static void onSound(String path, double x, double y, double z) {
        KuroAutoFish a = instance;
        if (a == null || !a.isEnabled() || mc.player == null) return;
        if (!path.equals("entity.fishing_bobber.splash")) return;
        FishingBobberEntity hook = mc.player.fishHook;
        if (hook == null) return;
        double dx = hook.getX() - x, dy = hook.getY() - y, dz = hook.getZ() - z;
        if (dx * dx + dy * dy + dz * dz < 4.0) a.bite = true;
    }

    private static int jitter() { return ThreadLocalRandom.current().nextInt(0, 3); }

    private void use(PlayerEntity p, Hand hand) {
        mc.interactionManager.interactItem(p, hand);
        p.swingHand(hand);
    }

    @Override
    public void onTick() {
        PlayerEntity p = mc.player;
        if (p == null || mc.world == null || mc.interactionManager == null || mc.currentScreen != null || p.isSpectator()) return;
        Hand hand = p.getMainHandStack().isOf(Items.FISHING_ROD) ? Hand.MAIN_HAND
                : p.getOffHandStack().isOf(Items.FISHING_ROD) ? Hand.OFF_HAND : null;
        if (hand == null) { reset(); return; }
        FishingBobberEntity hook = p.fishHook;

        switch (stage) {
            case WAIT -> {
                if (hook == null) {
                    sinceCast = 0;
                    bite = false;
                    if (autoCast.isEnabled() && --timer <= 0) { use(p, hand); timer = castDelay.getValue().intValue(); }
                    return;
                }
                sinceCast++;
                if (sinceCast > timeout.getValue().intValue() * 20) {          // stuck: reel in and start over
                    use(p, hand);
                    stage = Stage.RECAST;
                    timer = castDelay.getValue().intValue() + jitter();
                    return;
                }
                boolean dip = hook.isTouchingWater() && hook.getVelocity().y < -0.15;
                if (sinceCast > GRACE && (bite || dip)) {
                    bite = false;
                    stage = Stage.REEL;
                    timer = reelDelay.getValue().intValue() + jitter();
                }
            }
            case REEL -> {
                if (--timer > 0) return;
                if (hook != null) use(p, hand);
                stage = Stage.RECAST;
                timer = castDelay.getValue().intValue() + jitter();
            }
            case RECAST -> {
                if (--timer > 0) return;
                stage = Stage.WAIT;
                timer = 0;
            }
        }
    }
}
