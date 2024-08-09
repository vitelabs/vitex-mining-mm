package org.vite.dex.mm.reward.bean;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.vite.dex.mm.entity.OrderEvent;
import org.vite.dex.mm.entity.OrderModel;
import org.vite.dex.mm.reward.cfg.MiningRewardCfg;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Data
@NoArgsConstructor
@Slf4j
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

    public void deal(OrderEvent event, MiningRewardCfg cfg, BigDecimal topPrice) {
        long startTime = Math.max(calculateStartTime, this.orderModel.getTimestamp());
        long endTime = event.getTimestamp();
        if (startTime > endTime) {
            this.calculateStartTime = event.getTimestamp();
            log.info("time diff.....,{}, {}, {}", event.getBlockHash(), event.getOrderId(), startTime - endTime);
            return;
        }

        if (endTime - this.orderModel.getTimestamp() < 300 || orderModel.getAmount().compareTo(BigDecimal.ZERO) < 0) {
            return;
        }

        BigDecimal dist = null;
        BigDecimal factor = BigDecimal.ZERO;
        double effectiveDist = 0.0;

        if (orderModel.isSide()) {
            dist = (orderModel.getPrice().subtract(topPrice).setScale(18, RoundingMode.DOWN)).divide(topPrice, 18,
                    RoundingMode.DOWN);
            effectiveDist = cfg.getSellEffectiveDist();
        } else {
            dist = (topPrice.subtract(orderModel.getPrice().setScale(18, RoundingMode.DOWN))).divide(topPrice, 18,
                    RoundingMode.DOWN);
            effectiveDist = cfg.getBuyEffectiveDist();
        }

        if (dist.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        if (dist.compareTo(BigDecimal.valueOf(effectiveDist)) < 0) {
            // coefficient = 0.6^(1+9*d/a)
            BigDecimal exp = new BigDecimal(1).add(
                    new BigDecimal(9).multiply(dist).divide(BigDecimal.valueOf(effectiveDist), 18, RoundingMode.DOWN));
            BigDecimal coefficient = new BigDecimal(Math.pow(0.6, exp.doubleValue())).setScale(18, RoundingMode.DOWN);

            // factor = amount * timeDuration * coefficient * miningRewardMultiple
            factor = orderModel.getAmount().multiply(BigDecimal.valueOf(endTime - startTime)).multiply(coefficient)
                    .multiply(BigDecimal.valueOf(cfg.getMiningRewardMultiple())).setScale(18, RoundingMode.DOWN);

            if (factor.compareTo(BigDecimal.ZERO) == -1) {
                log.warn("factor is negative, the amount {}, time slap {}, factor {}, orderID {}, orderside {}",
                        orderModel.getAmount(), endTime - startTime, factor, orderModel.getOrderId(),
                        orderModel.isSide());
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
            this.totalRewardVX = this.totalFactor.multiply(sellSharedVxPerFactor).setScale(18, RoundingMode.DOWN);
        } else {
            this.totalRewardVX = this.totalFactor.multiply(buyShardVxPerFactor).setScale(18, RoundingMode.DOWN);
        }
    }

    @Override
    public String toString() {
        return "RewardOrder{" +
                "orderModel=" + orderModel +
                ", calculateStartTime=" + calculateStartTime +
                ", market=" + market +
                ", totalFactor=" + totalFactor +
                ", totalRewardVX=" + totalRewardVX +
                '}';
    }
}
