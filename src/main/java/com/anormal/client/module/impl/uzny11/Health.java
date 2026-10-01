package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;

public class Health extends com.anormal.client.module.impl.render.Health {
    public Health() {
        super();
    }

    @Override
    public Category getCategory() {
        return Category.UZNY11;
    }
}
