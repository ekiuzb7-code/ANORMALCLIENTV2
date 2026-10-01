package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;

public class Sprint extends com.anormal.client.module.impl.movement.Sprint {
    public Sprint() {
        super();
    }

    @Override
    public Category getCategory() {
        return Category.UZNY11;
    }
}
