package org.vite.dex.mm.constant.enums;

import lombok.Getter;

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

    @Getter
    private final int value;
    @Getter
    private final String desc;

}
