package org.vite.dex.mm.constant.enums;

import lombok.Getter;

public enum OrderUpdateInfoStatus {
    Pending(1, "pending"),
    PartialExecuted(2, "partial executed"),
    FullyExecuted(3, "fully executed"),
    Cancelled(4, "cancel");

    OrderUpdateInfoStatus(int value, String desc) {
        this.value = value;
        this.desc = desc;
    }

    @Getter
    private final int value;
    @Getter
    private final String desc;

}
