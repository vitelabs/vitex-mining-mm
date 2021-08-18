package org.vite.dex.mm.reward.bean;

import lombok.Data;
import lombok.experimental.Accessors;
import org.vite.dex.mm.reward.cfg.MiningRewardCfg;

import java.util.List;


@Data
@Accessors(chain = true)
public class RewardTradePair {
    private final String tp;
    private final List<RewardOrder> orders;
    private double factorSum;
    private double pairSharedVX; //the trade-pair shared VX
    private double sellAmountSum;
    private double buyAmountSum;
    private double sellSharedVx;
    private double buySharedVx;
    private MiningRewardCfg cfg;

    public RewardTradePair(String tp, List<RewardOrder> orders, MiningRewardCfg cfg) {
        this.tp = tp;
        this.orders = orders;
        this.cfg = cfg;
    }

    public void applyRule(double marketFactorSum, double marketSharedVX, MiningRewardCfg cfg) {
        this.cfg = cfg;
        this.factorSum = this.orders.stream().mapToDouble(RewardOrder::getTotalFactorDouble).sum();
        this.pairSharedVX = this.factorSum / marketFactorSum * marketSharedVX;
        this.sellAmountSum = orders.stream().filter(reward -> reward.getOrderSide())
                .mapToDouble(RewardOrder::getAmount).sum();
        this.buyAmountSum = orders.stream().filter(reward -> !reward.getOrderSide())
                .mapToDouble(RewardOrder::getAmount).sum();
        calcSharedVxOfEachSide();
        calcOrderVx();
    }

    /**
     * calc the buySide and sellSide of SharedVx for each trade pair
     * please see https://vite.atlassian.net/wiki/spaces/OP/pages/304513109/ViteX+ONE+BTC+10
     */
    private void calcSharedVxOfEachSide() {
        double sellMore = cfg.getMaxSellFactorThanBuy();
        double buyMore = cfg.getMaxBuyFactorThanSell();
        double ratio = 0;
        if (buyAmountSum < sellAmountSum) {
            if (sellMore < 1 || sellAmountSum / buyAmountSum >= sellMore) {
                ratio = 1 / sellMore + 1;
            } else {
                ratio = buyAmountSum / sellAmountSum + buyAmountSum;
            }
        } else {
            if (buyAmountSum / sellAmountSum >= buyMore) {
                ratio = buyMore / (buyMore + 1);
            } else {
                ratio = buyAmountSum / sellAmountSum + buyAmountSum;
            }
        }
        this.buySharedVx = pairSharedVX * ratio;
        this.sellSharedVx = pairSharedVX * (1 - ratio);
    }

    private void calcOrderVx() {
        double sellSharedVxPerAmount = this.sellSharedVx / this.sellAmountSum;
        double buyShardVxPerAmount = this.buySharedVx / this.buyAmountSum;
        this.orders.forEach(order -> {
            order.applyReward(sellSharedVxPerAmount, buyShardVxPerAmount);
        });
    }
}
