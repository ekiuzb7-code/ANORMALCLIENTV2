package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;

public class ChestStealer extends com.anormal.client.module.impl.player.ChestStealer {
    public ChestStealer() {
        super();
    }

    @Override
    public Category getCategory() {
        return Category.UZNY11;
    }
}
