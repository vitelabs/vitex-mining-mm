package org.vite.dex.mm.reward.bean;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.vite.dex.mm.entity.OrderEvent;
import org.vite.dex.mm.entity.OrderModel;
import org.vite.dex.mm.reward.cfg.MiningRewardCfg;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
public class RewardOrder {
    private OrderModel orderModel;
    private long calculateStartTime; // the start time of each calculate interval
    private int market;
    private BigDecimal totalFactor = BigDecimal.ZERO; //mining factor,not really VX
    private BigDecimal totalRewardVX = BigDecimal.ZERO; //mining reward VX

    public double getTotalFactorDouble() {
        return totalFactor.doubleValue();
    }

    public double getTotalVXDouble() {
        return totalRewardVX.doubleValue();
    }

    public String getOrderAddress() {
        return orderModel.getAddress();
    }

    public String getTradePair() {
        return orderModel.getTradePair();
    }

    public boolean getOrderSide() {
        return orderModel.isSide();
    }

    public double getAmount() {
        return orderModel.getAmount().doubleValue();
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
            dist = (orderModel.getPrice().subtract(topPrice)).divide(topPrice, 4, BigDecimal.ROUND_HALF_UP);
        } else {
            dist = (topPrice.subtract(orderModel.getPrice())).divide(topPrice, 4, BigDecimal.ROUND_HALF_UP);
        }

        double effectiveDistance = cfg.getEffectiveDistance();
        if (dist.compareTo(BigDecimal.valueOf(effectiveDistance)) < 0) {
            double coefficient = Math.pow(0.6, (1 + 9 * dist.doubleValue()) / effectiveDistance);
            factor = orderModel.getAmount().multiply(BigDecimal.valueOf(endTime - startTime))
                    .multiply(BigDecimal.valueOf(coefficient)
                            .multiply(BigDecimal.valueOf(cfg.getMiningRewardMultiple())));

        }

        this.calculateStartTime = event.getTimestamp(); // update timestamp
        totalFactor = totalFactor.add(factor);
    }

    void applyReward(double sellSharedVxPerAmount, double buyShardVxPerAmount) {
        // TODO
        if (orderModel.isSide()) {
            // TODO
            this.totalRewardVX = BigDecimal.ZERO;
        } else {
            // TODO    
        }
    }
}
