package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;

public class Weather extends com.anormal.client.module.impl.legit.Weather {
    public Weather() {
        super();
    }

    @Override
    public Category getCategory() {
        return Category.UZNY11;
    }
}
