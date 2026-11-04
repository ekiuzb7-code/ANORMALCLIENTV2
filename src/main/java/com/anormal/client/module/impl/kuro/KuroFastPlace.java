package com.anormal.client.module.impl.kuro;

import com.anormal.client.mixin.ClientAccessor;
import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;

import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;

/** kuro {@code FastPlace} port: qisqartirilgan o'ng-tugma takror delay (vanilla 4 tick). */
public class KuroFastPlace extends Module {
    private final NumberSetting delay = new NumberSetting("Delay (ticks)", "Right-click repeat delay", 0.0, 0.0, 4.0, 1.0);
    private final BooleanSetting blocksOnly = new BooleanSetting("Blocks only", true);

    public KuroFastPlace() {
        super("Fast Place", "Shorter right-click repeat delay", Category.KURO);
        addSetting(delay);
        addSetting(blocksOnly);
    }

    @Override
    public void onPreTick() {
        if (mc.player == null || mc.currentScreen != null) return;
        if (blocksOnly.isEnabled()) {
            ItemStack held = mc.player.getMainHandStack();
            if (!(held.getItem() instanceof BlockItem)) return;
        }
        ClientAccessor acc = (ClientAccessor) mc;
        if (acc.getItemUseCooldown() > delay.getValue().intValue()) acc.setItemUseCooldown(delay.getValue().intValue());
    }
}
