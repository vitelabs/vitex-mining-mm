package org.vite.dex.mm.constant.enums;

public enum QuoteMarketType {
    USDTMarket(1, "usdt market"),
    BTCMarket(2, "btc market"),
    ETHMarket(3, "eth market"),
    VITEMarket(4, "vite market");

    QuoteMarketType(int value, String desc) {
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
