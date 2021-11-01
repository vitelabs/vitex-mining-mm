package org.vite.dex.mm.constant.constants;

import org.vite.dex.mm.constant.enums.QuoteMarketType;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

public class MiningConst {
    public static final String TRADE_CONTRACT_ADDR = "vite_00000000000000000000000000000000000000079710f19dc7";

    public static final String ORDER_NEW_EVENT_TOPIC =
            "6e65774f726465724576656e7400000000000000000000000000000000000000";

    public static final String ORDER_UPDATE_EVENT_TOPIC =
            "6f726465725570646174654576656e7400000000000000000000000000000000";

    public static final String TX_EVENT_TOPIC = "74784576656e7400000000000000000000000000000000000000000000000000";

    public static final int MAX_RETRY_NUM = 3;
    
    public static final String UnderscoreStr = "_";

    public static final int OrderIdBytesLength = 22;

    public static final double viteSharedRatio = 0.015;

    public static final double ethSharedRatio = 0.015;

    public static final double btcSharedRatio = 0.05;

    public static final double usdtSharedRatio = 0.02;

    public static final long genesisTimestamp = 1558411200l;

    public final static BigDecimal MARKET_MINING_RATIO = new BigDecimal("0.1");

    public final static BigDecimal PERCENT_25 = new BigDecimal("0.025");
    public final static BigDecimal PERCENT_00125 = new BigDecimal("0.0125");

    public static final Map<Integer, Double> getMarketSharedRatio() {
        HashMap<Integer, Double> marketSharedVXRatio = new HashMap<>();
        marketSharedVXRatio.put(QuoteMarketType.VITEMarket.getValue(), viteSharedRatio);
        marketSharedVXRatio.put(QuoteMarketType.ETHMarket.getValue(), ethSharedRatio);
        marketSharedVXRatio.put(QuoteMarketType.BTCMarket.getValue(), btcSharedRatio);
        marketSharedVXRatio.put(QuoteMarketType.USDTMarket.getValue(), usdtSharedRatio);
        return marketSharedVXRatio;
    }
}
