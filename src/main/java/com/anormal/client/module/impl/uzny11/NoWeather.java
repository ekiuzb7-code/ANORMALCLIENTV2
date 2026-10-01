package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;

public class NoWeather extends com.anormal.client.module.impl.legit.NoWeather {
    public NoWeather() {
        super();
    }

    @Override
    public Category getCategory() {
        return Category.UZNY11;
    }
}
