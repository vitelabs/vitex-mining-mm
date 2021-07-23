package org.vite.dex.mm.reward.bean;

import lombok.Data;
import org.vite.dex.mm.entity.Order;
import org.vite.dex.mm.entity.OrderEvent;
import org.vite.dex.mm.reward.RewardOrderBook;
import org.vitej.core.protocol.methods.response.VmLogInfo;

import java.math.BigDecimal;
import java.util.List;

@Data
public class RewardOrder {
    private Order order;
    private BigDecimal rewardTotal;
    private long startCalculatedTime;
    private long lastCalculatedTime;
    int market;

    public RewardOrder(Order order) {
        this.order = order;
    }

    public void revert(OrderEvent event) {

    }

    // 一个交易对的orderbook，从后往前开始计算，依次计算出每个订单的挂单挖矿所获金额
    public void deal(List<VmLogInfo> events, RewardOrderBook orderbook, MiningRewardCfg cfg) {
        for (VmLogInfo e : events) {

        }
    }
}
