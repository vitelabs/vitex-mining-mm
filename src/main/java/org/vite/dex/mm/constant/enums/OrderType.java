package org.vite.dex.mm.constant.enums;

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

    private int value;
    private String desc;

    public int getValue() {
        return value;
    }

    public String getDesc() {
        return desc;
    }
}
