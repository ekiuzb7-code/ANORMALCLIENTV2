package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;

public class FakeLag extends com.anormal.client.module.impl.world.FakeLag {
    public FakeLag() {
        super();
    }

    @Override
    public Category getCategory() {
        return Category.UZNY11;
    }
}
