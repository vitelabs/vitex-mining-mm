package org.vite.dex.mm.constant.enums;

import lombok.Getter;

// TODO
public enum QuoteMarketType {
    USDTMarket(1, "usdt market"),
    BTCMarket(2, "btc market"),
    ETHMarket(3, "eth market"),
    VITEMarket(4, "vite market");

    QuoteMarketType(int value, String desc) {
        this.value = value;
        this.desc = desc;
    }

    @Getter
    private final int value;
    @Getter
    private final String desc;

}
