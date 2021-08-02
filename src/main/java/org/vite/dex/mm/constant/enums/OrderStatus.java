package org.vite.dex.mm.constant.enums;

import lombok.Getter;

/**
 * order status
 */
public enum OrderStatus {
    Unknown(0, "status unknown"),
    PendingReq(1, "Order submitted. A corresponding request transaction has been created on chain"),
    Received(2, "Order received"),
    Open(3, "Order unfilled"),
    Filled(4, "Order filled"),
    PartiallyFilled(5, "Order partially filled"),
    PendingCancel(6, "Cancel order request submitted. A corresponding request transaction has been created on chain"),
    Cancelled(7, "Order cancelled"),
    PartiallyCancelled(8, "Order partially cancelled (the order was partially filled)"),
    Failed(9, "Request failed"),
    Expired(10, "Order expired");

    OrderStatus(int value, String desc) {
        this.value = value;
        this.desc = desc;
    }

    @Getter
    private final int value;
    @Getter
    private final String desc;

}
