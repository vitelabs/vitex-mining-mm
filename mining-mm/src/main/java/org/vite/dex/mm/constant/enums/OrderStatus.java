package org.vite.dex.mm.constant.enums;

import lombok.Getter;

public enum OrderStatus {
    Pending(0, "pending"), 
    PartialExecuted(1, "partial executed"), 
    FullyExecuted(2, "fully executed"),
    Cancelled(3, "cancel"), 
    OrderFailure(4, "order failure");

    OrderStatus(int value, String desc) {
        this.value = value;
        this.desc = desc;
    }

    @Getter
    private final int value;
    @Getter
    private final String desc;

    public static OrderStatus of(int v) {
        for (OrderStatus s : OrderStatus.values()) {
            if (s.getValue() == v) {
                return s;
            }
        }
        throw new RuntimeException("not found:" + v);
    }
}
