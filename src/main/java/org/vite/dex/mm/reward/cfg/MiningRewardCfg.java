package org.vite.dex.mm.reward.cfg;

import lombok.Data;

/**
 * the config is taken from Contract on ViteX
 */
@Data
public class MiningRewardCfg {
    // the trade-pair symbol
    String tradePairSymbol;

    // the marketId which the trade-pair located in
    int marketId;

    //the effective distance for a trade-pair
    double effectiveDist = 0.2;

    //the multiplier factor for a trade-pair
    double miningRewardMultiple = 5.0;

    //the max factor for a trade-pair
    double maxBuyFactorThanSell = 100;

    //the multiplier factor for a trade-pair
    double maxSellFactorThanBuy = 0.01;
}
