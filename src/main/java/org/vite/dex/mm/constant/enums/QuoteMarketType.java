package org.vite.dex.mm.constant.enums;

import lombok.Getter;

public enum QuoteMarketType {
    VITEMarket(1, "vite market"),
    ETHMarket(2, "eth market"),
    BTCMarket(3, "btc market"),
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
