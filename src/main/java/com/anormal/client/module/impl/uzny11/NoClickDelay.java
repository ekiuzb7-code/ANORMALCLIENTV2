package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;

public class NoClickDelay extends com.anormal.client.module.impl.player.NoClickDelay {
    public NoClickDelay() {
        super();
    }

    @Override
    public Category getCategory() {
        return Category.UZNY11;
    }
}
