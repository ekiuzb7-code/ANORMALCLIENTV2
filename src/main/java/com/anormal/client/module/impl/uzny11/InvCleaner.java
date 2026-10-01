package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;

public class InvCleaner extends com.anormal.client.module.impl.inventory.InvCleaner {
    public InvCleaner() {
        super();
    }

    @Override
    public Category getCategory() {
        return Category.UZNY11;
    }
}
