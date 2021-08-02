package org.vite.dex.mm.constant.enums;

import lombok.Getter;

/**
 * order side
 */
public enum OrderSide {
    Buy(0, "Buy"),
    Sell(1, "Sell");

    OrderSide(int value, String desc) {
        this.value = value;
        this.desc = desc;
    }

    @Getter
    private final int value;
    @Getter
    private final String desc;

}
