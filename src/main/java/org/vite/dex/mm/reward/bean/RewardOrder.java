package org.vite.dex.mm.reward.bean;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.vite.dex.mm.entity.OrderEvent;
import org.vite.dex.mm.entity.OrderModel;
import org.vite.dex.mm.reward.cfg.MiningRewardCfg;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Data
@NoArgsConstructor
public class RewardOrder {
    private OrderModel orderModel;
    private long calculateStartTime; // the start time of each calculate interval
    private int market;
    private BigDecimal totalFactor = BigDecimal.ZERO; // mining factor,not really VX
    private BigDecimal totalRewardVX = BigDecimal.ZERO; // mining reward VX

    public String getOrderAddress() {
        return orderModel.getAddress();
    }

    public String getTradePair() {
        return orderModel.getTradePair();
    }

    public boolean getOrderSide() {
        return orderModel.isSide();
    }

    public BigDecimal getAmount() {
        return orderModel.getAmount();
    }

    public void deal(MiningRewardCfg cfg, OrderEvent event, BigDecimal topPrice) {
        long startTime = calculateStartTime;
        long endTime = event.getTimestamp();
        if (endTime - this.orderModel.getTimestamp() < 300) {
            return;
        }

        BigDecimal dist = null;
        BigDecimal factor = BigDecimal.ZERO;
        if (orderModel.isSide()) {
            dist = (orderModel.getPrice().subtract(topPrice).setScale(12, RoundingMode.DOWN)).divide(topPrice, 12,
                    RoundingMode.DOWN);
        } else {
            dist = (topPrice.subtract(orderModel.getPrice().setScale(12, RoundingMode.DOWN))).divide(topPrice, 12,
                    RoundingMode.DOWN);
        }
        if (dist.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        double effectiveDist = cfg.getEffectiveDist();
        if (dist.compareTo(BigDecimal.valueOf(effectiveDist)) < 0) {
            // coefficient = 0.6^(1+9*d/a)
            BigDecimal exp = new BigDecimal(1).add(
                    new BigDecimal(9).multiply(dist).divide(BigDecimal.valueOf(effectiveDist), 12, RoundingMode.DOWN));
            BigDecimal coefficient = new BigDecimal(Math.pow(0.6, exp.doubleValue())).setScale(12, RoundingMode.DOWN);

            // factor = amount * timeDuration * coefficient * miningRewardMultiple
            factor = orderModel.getAmount().multiply(BigDecimal.valueOf(endTime - startTime)).multiply(coefficient)
                    .multiply(BigDecimal.valueOf(cfg.getMiningRewardMultiple())).setScale(12, RoundingMode.DOWN);

            if (factor.compareTo(BigDecimal.ZERO) == -1) {
                System.out.println(
                        String.format("amount is %s,time slap is %s,the factor is %s,orderID - %s,orderside - %s",
                                orderModel.getAmount(), endTime - startTime, factor, orderModel.getOrderId(),
                                orderModel.isSide()));
            }
            totalFactor = totalFactor.add(factor);
        }

        // update timestamp
        this.calculateStartTime = event.getTimestamp();
    }

    /**
     * calculate the amount of VX obtained for the mining-order
     * 
     * @param sellSharedVxPerFactor
     * @param buyShardVxPerFactor
     */

    void applyReward(BigDecimal sellSharedVxPerFactor, BigDecimal buyShardVxPerFactor) {
        if (orderModel.isSide()) {
            this.totalRewardVX = this.totalFactor.multiply(sellSharedVxPerFactor).setScale(16, RoundingMode.DOWN);
        } else {
            this.totalRewardVX = this.totalFactor.multiply(buyShardVxPerFactor).setScale(16, RoundingMode.DOWN);
        }
    }
}
