package org.vite.dex.mm.reward.bean;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.vite.dex.mm.entity.OrderEvent;
import org.vite.dex.mm.entity.OrderModel;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
public class RewardOrder {
    private OrderModel orderModel;

    private long timestamp;
    private int market;
    private BigDecimal totalFactor;

    public void deal(MiningRewardCfg cfg, OrderEvent event, BigDecimal topPrice) {
        long startTime = timestamp;
        long endTime = event.getTimestamp();

        BigDecimal dist = null;
        BigDecimal factor = BigDecimal.ZERO;
        if (orderModel.isSide()) {
            dist = (orderModel.getPrice().subtract(topPrice)).divide(topPrice);
        } else {
            dist = (topPrice.subtract(orderModel.getPrice())).divide(topPrice);
        }

        double effectiveDistance = cfg.getEffectiveDistance();
        if (dist.compareTo(new BigDecimal(String.valueOf(effectiveDistance))) < 0) {
            double coefficient = Math.pow(0.6, (1 + 9 * dist.doubleValue()) / effectiveDistance);
            factor = orderModel.getAmount().multiply(BigDecimal.valueOf(endTime - startTime)).
                    multiply(BigDecimal.valueOf(coefficient));

            this.timestamp = event.getTimestamp(); // update timestamp
        }

        totalFactor = totalFactor.add(factor);
    }
}
