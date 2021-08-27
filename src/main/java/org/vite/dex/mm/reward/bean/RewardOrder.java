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
            // coefficient = 0.6^((1+9*d)/a)
            double coefficient = Math.pow(0.6, (1 + 9 * dist.doubleValue()) / effectiveDist);
            // factor = amount * timeDuration * coefficient * miningRewardMultiple
            factor = orderModel.getAmount().multiply(BigDecimal.valueOf(endTime - startTime)).multiply(
                    BigDecimal.valueOf(coefficient).multiply(BigDecimal.valueOf(cfg.getMiningRewardMultiple())));
        }

        totalFactor = totalFactor.add(factor);
        // update timestamp
        this.calculateStartTime = event.getTimestamp();
    }

    void applyReward(double sellSharedVxPerAmount, double buyShardVxPerAmount) {
        if (orderModel.isSide()) {
            this.totalRewardVX = new BigDecimal(getAmount() * sellSharedVxPerAmount);
        } else {
            this.totalRewardVX = new BigDecimal(getAmount() * buyShardVxPerAmount);
        }
        /* if (this.totalRewardVX.compareTo(BigDecimal.ZERO) < 0) {
            System.out.println("the XXXXXXXX is :"+ getAmount());
        } */
    }
}
