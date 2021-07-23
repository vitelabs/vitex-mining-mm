package org.vite.dex.mm.entity;

import lombok.Data;
import org.vite.dex.mm.constant.enums.OrderSide;
import org.vite.dex.mm.constant.enums.OrderStatus;
import org.vite.dex.mm.constant.enums.OrderType;

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
