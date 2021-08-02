package org.vite.dex.mm.constant.enums;

import lombok.Getter;

/**
 * order event type
 */
public enum OrderEventType {
    OrderNew(1, "new order event"),
    OrderUpdate(2, "update order event"),
    OrderTX(3, "order transaction event");

    OrderEventType(int value, String desc) {
        this.value = value;
        this.desc = desc;
    }

    @Getter
    private final int value;
    @Getter
    private final String desc;

}
