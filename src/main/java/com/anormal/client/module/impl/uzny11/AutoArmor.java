package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;

public class AutoArmor extends com.anormal.client.module.impl.inventory.AutoArmor {
    public AutoArmor() {
        super();
    }

    @Override
    public Category getCategory() {
        return Category.UZNY11;
    }
}
