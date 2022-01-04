package org.vite.dex.mm.constant.enums;

import lombok.Getter;

/**
 * order status
 */
public enum OrderType {

    LimitOrder(0, "Limit Order"),
    MarketOrder(1, "Market Order");

    OrderType(int value, String desc) {
        this.value = value;
        this.desc = desc;
    }

    @Getter
    private final int value;
    @Getter
    private final String desc;

}
