package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;

public class BlockHit extends com.anormal.client.module.impl.combat.BlockHit {
    public BlockHit() {
        super();
    }

    @Override
    public Category getCategory() {
        return Category.UZNY11;
    }
}
