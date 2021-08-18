package org.vite.dex.mm.constant.enums;

import lombok.Getter;

public enum QuoteMarketType {
    BTCMarket(1, "btc market"),
    ETHMarket(2, "eth market"),
    VITEMarket(3, "vite market"),
    USDTMarket(4, "usdt market");

    QuoteMarketType(int value, String desc) {
        this.value = value;
        this.desc = desc;
    }

    @Getter
    private final int value;
    @Getter
    private final String desc;

}
