package org.vite.dex.mm.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

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

}
