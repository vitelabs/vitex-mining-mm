package org.vite.dex.mm.reward.bean;

import lombok.Data;
import lombok.experimental.Accessors;
import org.vite.dex.mm.reward.cfg.MiningRewardCfg;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Data
@Accessors(chain = true)
public class RewardTradePair {
    private final String tp;
    private final List<RewardOrder> rewardOrders;
    private BigDecimal pairFactorSum;
    private BigDecimal pairSharedVX; // the trade-pair shared VX

    private BigDecimal pairSellFactorSum;
    private BigDecimal pairBuyFactorSum;

    private BigDecimal sellAmountSum;
    private BigDecimal buyAmountSum;

    private BigDecimal sellSharedVx;
    private BigDecimal buySharedVx;
    private MiningRewardCfg cfg;

    public RewardTradePair(String tp, List<RewardOrder> rewardOrders, MiningRewardCfg cfg) {
        this.tp = tp;
        this.rewardOrders = rewardOrders;
        this.cfg = cfg;
    }

    /**
     * 1.calculate the amount of VX that each trading pair should get 
     * 2.According to the order-mining configuration for each trading pair, calculate the total VX
     * that buying and selling orders should get in the trading pair
     * 
     * @param marketFactorSum
     * @param marketSharedVX
     * @param cfg
     */
    public void applyRule(BigDecimal marketFactorSum, BigDecimal marketSharedVX, MiningRewardCfg cfg) {
        this.cfg = cfg;
        this.pairFactorSum = this.rewardOrders.stream().map(RewardOrder::getTotalFactor).reduce(BigDecimal.ZERO,
                BigDecimal::add);
        this.pairSharedVX = this.pairFactorSum.divide(marketFactorSum, 12, RoundingMode.DOWN)
                .multiply(marketSharedVX).setScale(12, RoundingMode.DOWN);

        this.pairSellFactorSum = this.rewardOrders.stream().filter(reward -> reward.getOrderSide())
                .map(RewardOrder::getTotalFactor).reduce(BigDecimal.ZERO, BigDecimal::add);
        this.pairBuyFactorSum = this.rewardOrders.stream().filter(reward -> !reward.getOrderSide())
                .map(RewardOrder::getTotalFactor).reduce(BigDecimal.ZERO, BigDecimal::add);

        this.sellAmountSum = rewardOrders.stream().filter(reward -> reward.getOrderSide()).map(RewardOrder::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        this.buyAmountSum = rewardOrders.stream().filter(reward -> !reward.getOrderSide()).map(RewardOrder::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        calcSharedVxOfEachSide();
        calcOrderVx();
    }

    /**
     * calc the buySide and sellSide of SharedVx for each trade pair
     * 
     * https://vite.atlassian.net/wiki/spaces/OP/pages/304513109/ViteX+ONE+BTC+10
     */
    private void calcSharedVxOfEachSide() {
        double sellMore = cfg.getMaxSellFactorThanBuy();
        double buyMore = cfg.getMaxBuyFactorThanSell();
        double ratio = 0;
        if (buyAmountSum.compareTo(sellAmountSum) == -1) {
            if (sellMore < 1 || sellAmountSum.divide(buyAmountSum).doubleValue() >= sellMore) {
                ratio = 1 / sellMore + 1;
            } else {
                ratio = buyAmountSum.divide(sellAmountSum.add(buyAmountSum), 12, RoundingMode.DOWN).doubleValue();
            }
        } else {
            if (buyAmountSum.divide(sellAmountSum, 12, RoundingMode.DOWN).doubleValue() >= buyMore) {
                ratio = buyMore / (buyMore + 1);
            } else {
                ratio = buyAmountSum.divide(sellAmountSum.add(buyAmountSum), 12, RoundingMode.DOWN).doubleValue();
            }
        }
        this.buySharedVx = pairSharedVX.multiply(BigDecimal.valueOf(ratio)).setScale(12, RoundingMode.DOWN);
        this.sellSharedVx = pairSharedVX.multiply(new BigDecimal(1).subtract(BigDecimal.valueOf(ratio))).setScale(12,
                RoundingMode.DOWN);
    }

    /**
     * calculate the amount of VX obtained for each order placed on the trading pair
     */
    private void calcOrderVx() {
        BigDecimal sellSharedVxPerFactor = this.sellSharedVx.divide(pairSellFactorSum, 20, RoundingMode.DOWN);
        BigDecimal buyShardVxPerFactor = this.buySharedVx.divide(pairBuyFactorSum, 20, RoundingMode.DOWN);

        this.rewardOrders.forEach(rewardOrder -> {
            rewardOrder.applyReward(sellSharedVxPerFactor, buyShardVxPerFactor);
        });
    }
}
