package org.vite.dex.mm.constant.enums;

import lombok.Getter;

public enum SettleStatus {
    UnSettle(0, "unSettle"), 
    Settling(1, "settling"), 
    SettleSend(2, "settled send"),
    SettleFinish(3, "settle finish");

    SettleStatus(int value, String desc) {
        this.value = value;
        this.desc = desc;
    }

    @Getter
    private final int value;
    @Getter
    private final String desc;

    public static SettleStatus of(int v) {
        for (SettleStatus s : SettleStatus.values()) {
            if (s.getValue() == v) {
                return s;
            }
        }
        throw new RuntimeException("not found:" + v);
    }
}
