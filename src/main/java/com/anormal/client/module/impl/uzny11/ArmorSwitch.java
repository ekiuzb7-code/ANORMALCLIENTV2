package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;

public class ArmorSwitch extends com.anormal.client.module.impl.inventory.ArmorSwitch {
    public ArmorSwitch() {
        super();
    }

    @Override
    public Category getCategory() {
        return Category.UZNY11;
    }
}
