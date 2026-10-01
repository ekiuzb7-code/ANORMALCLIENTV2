package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;

public class SilentAura extends com.anormal.client.module.impl.combat.SilentAura {
    public SilentAura() {
        super();
    }

    @Override
    public Category getCategory() {
        return Category.UZNY11;
    }
}
