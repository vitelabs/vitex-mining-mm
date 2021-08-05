package org.vite.dex.mm.entity;

import lombok.Data;

import static org.vite.dex.mm.constant.constants.MMConst.UnderscoreStr;

@Data
public class TradePair {
    //trade-pair symbol
    String symbol;

    String tradeTokenSymbol;

    String quoteTokenSymbol;

    String tradeTokenId;

    String quoteTokenId;

    // the effective interval of distance when market mining
    Double effectiveInterval;

    public String getTradePairSymbol() {
        return getTradeTokenId() + UnderscoreStr + getQuoteTokenId();
    }

    public int getMarket() {
        int market = 0;
        switch (quoteTokenSymbol) {
            case "BTC-000":
                market = 1;
                break;
            case "ETH-000":
                market = 2;
                break;
            case "VITE":
                market = 3;
                break;
            case "USDT—000":
                market = 4;
                break;
        }
        return market;
    }
}
