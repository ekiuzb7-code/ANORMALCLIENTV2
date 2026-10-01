package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;

public class SafeWalk extends com.anormal.client.module.impl.movement.SafeWalk {
    public SafeWalk() {
        super();
    }

    @Override
    public Category getCategory() {
        return Category.UZNY11;
    }
}
