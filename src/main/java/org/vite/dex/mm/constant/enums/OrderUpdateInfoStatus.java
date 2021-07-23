package org.vite.dex.mm.constant.enums;

public enum OrderUpdateInfoStatus {
    Pending(1, "pending"),
    PartialExecuted(2, "partial executed"),
    FullyExecuted(3, "fully executed"),
    Cancelled(4, "cancel");

    OrderUpdateInfoStatus(int value, String desc) {
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
