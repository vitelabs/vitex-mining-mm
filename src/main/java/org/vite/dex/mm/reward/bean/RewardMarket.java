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
    private final Map<String, RewardTradePair> pairs = new HashMap<>();//<TradePair,RewardTradePair>
    private final List<RewardOrder> orders;
    private Map<String, MiningRewardCfg> tradePair2Cfg;
    private double factorSum;
    private double releasedVx;

    public RewardMarket(int market, List<RewardOrder> rewardOrders, Map<String, MiningRewardCfg> tradePair2Cfg) {
        this.market = market;
        Map<String, List<RewardOrder>> tradePairOrderMap =
                rewardOrders.stream().collect(Collectors.groupingBy(RewardOrder::getTradePair));
        tradePairOrderMap.forEach((tradePair, rewardOrderList) -> {
            this.pairs.put(tradePair, new RewardTradePair(tradePair, rewardOrderList, tradePair2Cfg.get(tradePair)));
        });
        this.orders = rewardOrders;
        this.tradePair2Cfg = tradePair2Cfg;
    }

    public void apply(double releasedVx, double f) {
        double sharedVX = releasedVx * f;
        this.factorSum = this.orders.stream().mapToDouble(RewardOrder::getTotalFactorDouble).sum();
        this.pairs.values().forEach(tradePair -> {
            tradePair.applyRule(this.factorSum, sharedVX, tradePair2Cfg.get(tradePair));
        });
    }
}

