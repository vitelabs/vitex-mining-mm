package org.vite.dex.mm.reward.bean;

import lombok.Data;
import lombok.experimental.Accessors;
import org.vite.dex.mm.reward.cfg.MiningRewardCfg;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Data
@Accessors(chain = true)
public class RewardMarket {

    private final int market;
    private final Map<String, RewardTradePair> pairs = new HashMap<>();// <TradePair,RewardTradePair>
    private final List<RewardOrder> rewardOrders;
    private Map<String, MiningRewardCfg> tradePair2Cfg;
    private double marketFactorSum;
    private double releasedVx;

    public RewardMarket(int market, List<RewardOrder> rewardOrders, Map<String, MiningRewardCfg> tradePair2Cfg) {
        this.market = market;
        this.tradePair2Cfg = tradePair2Cfg;
        this.rewardOrders = rewardOrders;
        Map<String, List<RewardOrder>> tradePairRewardOrders = rewardOrders.stream()
                .collect(Collectors.groupingBy(RewardOrder::getTradePair));

        tradePairRewardOrders.forEach((tradePair, rewardOrderList) -> {
            this.pairs.put(tradePair, new RewardTradePair(tradePair, rewardOrderList, tradePair2Cfg.get(tradePair)));
        });
    }

    public void apply(double releasedVx, double f) {
        double sharedVX = releasedVx * f;
        this.marketFactorSum = this.rewardOrders.stream().mapToDouble(RewardOrder::getTotalFactorDouble).sum();

        this.pairs.values().forEach(rewardTradePair -> {
            rewardTradePair.applyRule(this.marketFactorSum, sharedVX, tradePair2Cfg.get(rewardTradePair.getTp()));
        });
    }
}
