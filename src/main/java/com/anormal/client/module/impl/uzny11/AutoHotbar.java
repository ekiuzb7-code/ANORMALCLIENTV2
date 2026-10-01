package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;

public class AutoHotbar extends com.anormal.client.module.impl.inventory.AutoHotbar {
    public AutoHotbar() {
        super();
    }

    @Override
    public Category getCategory() {
        return Category.UZNY11;
    }
}
