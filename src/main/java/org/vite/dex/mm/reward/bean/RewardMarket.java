package org.vite.dex.mm.reward.bean;

import lombok.Data;
import lombok.experimental.Accessors;
import org.vite.dex.mm.reward.cfg.MiningRewardCfg;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Data
@Accessors(chain = true)
public class RewardMarket {

    private final int market;
    private final Map<String, RewardTradePair> tradePairRewards = new HashMap<>();// <TradePair,RewardTradePair>
    private final List<RewardOrder> rewardOrders;
    private Map<String, MiningRewardCfg> tradePair2Cfg;
    private BigDecimal marketFactorSum;

    public RewardMarket(int market, List<RewardOrder> rewardOrders, Map<String, MiningRewardCfg> tradePair2Cfg) {
        this.market = market;
        this.tradePair2Cfg = tradePair2Cfg;
        this.rewardOrders = rewardOrders;
        Map<String, List<RewardOrder>> tradePairRewardOrders = rewardOrders.stream()
                .collect(Collectors.groupingBy(RewardOrder::getTradePair));

        tradePairRewardOrders.forEach((tradePair, rewardOrderList) -> {
            this.tradePairRewards.put(tradePair,
                    new RewardTradePair(tradePair, rewardOrderList, tradePair2Cfg.get(tradePair)));
        });
    }

    /**
     * 1. calculate the total number of VX allocated to each market 
     * 2. calculate the number of VX that each trading pair should get in the market
     */
    public void apply(BigDecimal releasedVx, double f) {
        BigDecimal sharedVX = releasedVx.multiply(BigDecimal.valueOf(f));
        this.marketFactorSum = this.rewardOrders.stream().map(RewardOrder::getTotalFactor).reduce(BigDecimal.ZERO,
                BigDecimal::add);

        this.tradePairRewards.values().forEach(rewardTradePair -> {
            rewardTradePair.applyRule(this.marketFactorSum, sharedVX, tradePair2Cfg.get(rewardTradePair.getTp()));
        });
    }
}
