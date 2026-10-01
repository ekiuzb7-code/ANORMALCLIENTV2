package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;

public class Parkour extends com.anormal.client.module.impl.movement.Parkour {
    public Parkour() {
        super();
    }

    @Override
    public Category getCategory() {
        return Category.UZNY11;
    }
}
