package org.vite.dex.mm.reward.cfg;

import lombok.Data;
import org.vite.dex.mm.entity.TradePair;

/**
 * the config is taken from Contract on ViteX
 */
@Data
public class MiningRewardCfg {
    // the trade-pair symbol
    String tradePairSymbol;

    // the marketId which the trade-pair located in
    int marketId;

    // sell side effective distance for a trade-pair
    double sellEffectiveDist = 0.15;

    // buy side effective distance for a trade-pair
    double buyEffectiveDist = 0.05;

    // the multiplier factor for a trade-pair
    double miningRewardMultiple = 5.0;

    // the max factor for a trade-pair
    double maxBuyFactorThanSell = 100;

    // the multiplier factor for a trade-pair
    double maxSellFactorThanBuy = 0.01;

    public static MiningRewardCfg fromTradePair(TradePair tp) {
        MiningRewardCfg miningRewardCfg = new MiningRewardCfg();
        miningRewardCfg.setMarketId(tp.getMarket());
        miningRewardCfg.setTradePairSymbol(tp.getTradePair());
        miningRewardCfg.setSellEffectiveDist(tp.getSellEffectiveInterval());
        miningRewardCfg.setBuyEffectiveDist(tp.getBuyEffectiveInterval());
        miningRewardCfg.setMiningRewardMultiple(tp.getMmRewardMultiple());
        miningRewardCfg.setMaxBuyFactorThanSell(tp.getBuyAmountThanSellRatio());
        miningRewardCfg.setMaxSellFactorThanBuy(tp.getSellAmountThanBuyRatio());
        return miningRewardCfg;
    }
}
