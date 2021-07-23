package org.vite.dex.mm.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class MainMarket {

    List<TradePair> tradePairs;

    String marketSymbol;

    Double percentageOfDailyVX;

    BigDecimal totalVXReleased;

    //the total market mining amount of this market
    BigDecimal totalMiningFactor;
}
