package org.vite.dex.mm.reward.bean;

import lombok.Data;
import lombok.experimental.Accessors;
import org.vite.dex.mm.entity.InviteOrderMiningReward;
import org.vite.dex.mm.reward.cfg.MiningRewardCfg;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Data
@Accessors(chain = true)
public class RewardTradePair {
    private final String tp;
    private final List<RewardOrder> rewardOrders;
    private final List<InviteOrderMiningReward> inviteRewards;
    private BigDecimal pairFactorSum;
    private BigDecimal pairSharedVX; // the trade-pair shared VX

    private BigDecimal pairSellFactorSum;
    private BigDecimal pairBuyFactorSum;

    private BigDecimal sellAmountSum;
    private BigDecimal buyAmountSum;

    private BigDecimal sellSharedVx;
    private BigDecimal buySharedVx;
    private MiningRewardCfg cfg;

    public RewardTradePair(String tp, List<RewardOrder> rewardOrders, List<InviteOrderMiningReward> inviteRewards,
            MiningRewardCfg cfg) {
        this.tp = tp;
        this.rewardOrders = rewardOrders;
        this.inviteRewards = inviteRewards;
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
        BigDecimal tpInviteSellOrderFactor = BigDecimal.ZERO;
        BigDecimal tpInviteBuyOrderFactor = BigDecimal.ZERO;
        BigDecimal tpInviteFactor = BigDecimal.ZERO;

        if (this.inviteRewards != null) {
            tpInviteFactor = this.inviteRewards.stream().map(InviteOrderMiningReward::getFactor).reduce(BigDecimal.ZERO,
                    BigDecimal::add);
            tpInviteSellOrderFactor = this.inviteRewards.stream().filter(invite -> invite.isSide())
                    .map(InviteOrderMiningReward::getFactor).reduce(BigDecimal.ZERO, BigDecimal::add);
            tpInviteBuyOrderFactor = this.inviteRewards.stream().filter(invite -> !invite.isSide())
                    .map(InviteOrderMiningReward::getFactor).reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        BigDecimal tpMiningOrderFactor = this.rewardOrders.stream().map(RewardOrder::getTotalFactor)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal tpMiningSellOrderFactor = this.rewardOrders.stream().filter(reward -> reward.getOrderSide())
                .map(RewardOrder::getTotalFactor).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal tpMiningBuyOrderFactor = this.rewardOrders.stream().filter(reward -> !reward.getOrderSide())
                .map(RewardOrder::getTotalFactor).reduce(BigDecimal.ZERO, BigDecimal::add);

        this.pairFactorSum = tpMiningOrderFactor.add(tpInviteFactor);
        this.pairSellFactorSum = tpMiningSellOrderFactor.add(tpInviteSellOrderFactor);
        this.pairBuyFactorSum = tpMiningBuyOrderFactor.add(tpInviteBuyOrderFactor);
        this.pairSharedVX = marketFactorSum.compareTo(BigDecimal.ZERO) > 0 ? this.pairFactorSum.divide(marketFactorSum, 18, RoundingMode.DOWN)
                .multiply(marketSharedVX)
                .setScale(18, RoundingMode.DOWN) : BigDecimal.ZERO;

        this.sellAmountSum = rewardOrders.stream()
                .filter(reward -> reward.getOrderSide() && reward.getTotalFactor().compareTo(BigDecimal.ZERO) > 0)
                .map(RewardOrder::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        this.buyAmountSum = rewardOrders.stream()
                .filter(reward -> !reward.getOrderSide() && reward.getTotalFactor().compareTo(BigDecimal.ZERO) > 0)
                .map(RewardOrder::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

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
        if (this.sellAmountSum.compareTo(BigDecimal.ZERO) == 0 && this.buyAmountSum.compareTo(BigDecimal.ZERO) == 0) {
            ratio = 0.5;
        } else if (this.sellAmountSum.compareTo(BigDecimal.ZERO) == 0) {
            ratio = 1.0;
        } else if (this.buyAmountSum.compareTo(BigDecimal.ZERO) == 0) {
            ratio = 0.0;
        } else {
            if (buyAmountSum.compareTo(sellAmountSum) == -1) {
                if (sellMore < 1
                        || sellAmountSum.divide(buyAmountSum, 18, RoundingMode.DOWN).doubleValue() >= sellMore) {
                    ratio = 1 / (sellMore + 1);
                } else {
                    ratio = buyAmountSum.divide(sellAmountSum.add(buyAmountSum), 18, RoundingMode.DOWN).doubleValue();
                }
            } else {
                if (buyAmountSum.divide(sellAmountSum, 18, RoundingMode.DOWN).doubleValue() >= buyMore) {
                    ratio = buyMore / (buyMore + 1);
                } else {
                    ratio = buyAmountSum.divide(sellAmountSum.add(buyAmountSum), 18, RoundingMode.DOWN).doubleValue();
                }
            }
        }

        this.buySharedVx = pairSharedVX.multiply(BigDecimal.valueOf(ratio)).setScale(18, RoundingMode.DOWN);
        this.sellSharedVx = pairSharedVX.multiply(new BigDecimal(1).subtract(BigDecimal.valueOf(ratio))).setScale(18,
                RoundingMode.DOWN);
    }

    /**
     * calculate the amount of VX obtained for each order placed on the trading pair
     */
    private void calcOrderVx() {
        BigDecimal sellSharedVxPerFactor = pairSellFactorSum.compareTo(BigDecimal.ZERO) > 0
                ? this.sellSharedVx.divide(pairSellFactorSum, 50, RoundingMode.DOWN)
                : BigDecimal.ZERO;
        BigDecimal buySharedVxPerFactor = pairBuyFactorSum.compareTo(BigDecimal.ZERO) > 0
                ? this.buySharedVx.divide(pairBuyFactorSum, 50, RoundingMode.DOWN)
                : BigDecimal.ZERO;

        this.rewardOrders.forEach(rewardOrder -> {
            rewardOrder.applyReward(sellSharedVxPerFactor, buySharedVxPerFactor);
        });

        if (this.inviteRewards != null) {
            this.inviteRewards.forEach(invite -> {
                invite.applyInviteReward(sellSharedVxPerFactor, buySharedVxPerFactor);
            });
        }
    }
}
