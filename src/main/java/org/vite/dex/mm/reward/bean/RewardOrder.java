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

    private long timestamp;
    private int market;
    private BigDecimal totalFactor = BigDecimal.ZERO;
    private boolean firstGap5min;

    public double getTotalFactorDouble() {
        return totalFactor.doubleValue();
    }

    public String getOrderAddress() {
        return orderModel.getAddress();
    }

    public void deal(MiningRewardCfg cfg, OrderEvent event, BigDecimal topPrice) {
        long startTime = timestamp;
        long endTime = event.getTimestamp();
        // ignored when the first time the gap timestamp is less than 300
        if (firstGap5min && endTime - startTime < 300) {
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
                    .multiply(BigDecimal.valueOf(coefficient));

        }

        this.firstGap5min = false;
        this.timestamp = event.getTimestamp(); // update timestamp
        totalFactor = totalFactor.add(factor);
    }
}
