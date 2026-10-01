package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;

public class AutoTotem extends com.anormal.client.module.impl.inventory.AutoTotem {
    public AutoTotem() {
        super();
    }

    @Override
    public Category getCategory() {
        return Category.UZNY11;
    }
}
