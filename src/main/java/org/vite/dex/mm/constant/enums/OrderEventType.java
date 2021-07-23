package org.vite.dex.mm.constant.enums;

/**
 * order event type
 */
public enum OrderEventType {

    OrderNew(1, "new order event"),
    OrderCancel(2, "cancel order event"),
    OrderFill(3, "fill order event");

    OrderEventType(int value, String desc) {
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
