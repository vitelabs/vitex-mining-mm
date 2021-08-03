package org.vite.dex.mm.reward;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.vite.dex.mm.constant.enums.OrderEventType;
import org.vite.dex.mm.constant.enums.OrderUpdateInfoStatus;
import org.vite.dex.mm.entity.OrderEvent;
import org.vite.dex.mm.entity.OrderLog;
import org.vite.dex.mm.entity.OrderModel;
import org.vite.dex.mm.orderbook.EventStream;
import org.vite.dex.mm.orderbook.OrderBook;
import org.vite.dex.mm.reward.bean.MiningRewardCfg;
import org.vite.dex.mm.reward.bean.RewardOrder;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class RewardKeeper {

    /**
     * calculate the mm-mining of pending order which located in the specified orderBook
     *
     * @param eventStream
     * @param originOrderBook
     * @param cfg
     * @return
     */
    public Map<String, RewardOrder> mmMining(EventStream eventStream, OrderBook originOrderBook, MiningRewardCfg cfg,
                                             long endTime) {
        List<OrderEvent> events = eventStream.getEvents();
        Map<String, RewardOrder> orderRewards = new HashMap<>();

        for (OrderEvent e : events) {
            List<OrderModel> buys = originOrderBook.getBuys();
            List<OrderModel> sells = originOrderBook.getSells();
            BigDecimal sell1Price = originOrderBook.getSell1Price();
            BigDecimal buy1Price = originOrderBook.getBuy1Price();

            // when an event coming, compute the reward and update orderBooks
            try {
                if (e.getTimestamp() > endTime) {
                    return orderRewards;
                }
                OrderLog orderLog = e.getOrderLog();
                OrderEventType type = e.getType();
                int status = orderLog.getStatus();
                boolean newOrder = (type == OrderEventType.OrderNew);
                boolean canceledOrder = (type == OrderEventType.OrderUpdate && status == OrderUpdateInfoStatus.Cancelled.getValue());
                if (newOrder || canceledOrder) {
                    for (OrderModel order : buys) {
                        RewardOrder rewardOrder = getOrInitRewardOrder(orderRewards, order, e);
                        rewardOrder.deal(cfg, e, sell1Price);
                    }
                    for (OrderModel order : sells) {
                        RewardOrder rewardOrder = getOrInitRewardOrder(orderRewards, order, e);
                        rewardOrder.deal(cfg, e, buy1Price);
                    }
                }
                originOrderBook.onward(e);
            } catch (Exception ex) {
                log.error("mmMining occurs error,the err info: ", ex);
            }
        }
        return orderRewards;
    }

    private RewardOrder getOrInitRewardOrder(Map<String, RewardOrder> rewardOrderMap, OrderModel orderModel, OrderEvent event) {
        RewardOrder result = rewardOrderMap.get(orderModel.getOrderId());
        if (result == null) {
            result = new RewardOrder();
            result.setOrderModel(orderModel);
            result.setTimestamp(event.getTimestamp());
            rewardOrderMap.put(orderModel.getOrderId(), result);
        }
        return result;
    }

    public void start(long start, long end) {

    }
}
