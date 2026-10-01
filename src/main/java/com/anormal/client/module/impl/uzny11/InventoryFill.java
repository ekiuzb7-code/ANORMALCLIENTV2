package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;

public class InventoryFill extends com.anormal.client.module.impl.inventory.InventoryFill {
    public InventoryFill() {
        super();
    }

    @Override
    public Category getCategory() {
        return Category.UZNY11;
    }
}
