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

    public String getTp() {
        return getTradeTokenId() + UnderscoreStr + getQuoteTokenId();
    }
}
