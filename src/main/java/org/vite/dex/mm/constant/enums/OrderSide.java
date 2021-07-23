package org.vite.dex.mm.constant.enums;

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

    private int value;
    private String desc;

    public int getValue() {
        return value;
    }

    public String getDesc() {
        return desc;
    }
}
