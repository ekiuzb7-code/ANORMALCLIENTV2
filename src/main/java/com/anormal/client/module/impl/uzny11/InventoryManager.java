package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;

public class InventoryManager extends com.anormal.client.module.impl.inventory.InventoryManager {
    public InventoryManager() {
        super();
    }

    @Override
    public Category getCategory() {
        return Category.UZNY11;
    }
}
