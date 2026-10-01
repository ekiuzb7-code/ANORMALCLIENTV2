package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;

public class AntiAFK extends com.anormal.client.module.impl.world.AntiAFK {
    public AntiAFK() {
        super();
    }

    @Override
    public Category getCategory() {
        return Category.UZNY11;
    }
}
