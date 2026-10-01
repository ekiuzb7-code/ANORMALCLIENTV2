package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;

public class Refill extends com.anormal.client.module.impl.inventory.Refill {
    public Refill() {
        super();
    }

    @Override
    public Category getCategory() {
        return Category.UZNY11;
    }
}
