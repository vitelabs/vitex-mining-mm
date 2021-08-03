package org.vite.dex.mm.reward;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.vite.dex.mm.constant.enums.OrderEventType;
import org.vite.dex.mm.constant.enums.OrderUpdateInfoStatus;
import org.vite.dex.mm.entity.OrderEvent;
import org.vite.dex.mm.entity.OrderModel;
import org.vite.dex.mm.orderbook.EventStream;
import org.vite.dex.mm.orderbook.OrderBook;
import org.vite.dex.mm.orderbook.TradeRecover;
import org.vite.dex.mm.reward.bean.MiningRewardCfg;
import org.vite.dex.mm.reward.bean.RewardOrder;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.vite.dex.mm.constant.constants.MMConst.OrderIdBytesLength;

@Service
public class RewardKeeper {
    private static final Logger logger = LoggerFactory.getLogger(RewardKeeper.class);
    private Map<String, List<RewardOrder>> orderRewards = new HashMap<>(); //<OrderId,RewardOrder>

    @Autowired
    private TradeRecover tradeRecover;

    /**
     * calculate the mm-mining of pending order which located in the specified orderBook
     *
     * @param eventStream
     * @param originOrderBook
     * @param cfg
     * @return
     */
    public Map<String, List<RewardOrder>> mmMining(EventStream eventStream, OrderBook originOrderBook, MiningRewardCfg cfg,
                                                   long endTime) {
        List<OrderEvent> events = eventStream.getEvents();
        Collections.reverse(events);

        for (OrderEvent e : events) {
            List<OrderModel> buys = originOrderBook.getBuys();
            List<OrderModel> sells = originOrderBook.getSells();
            BigDecimal sell1Price = originOrderBook.getSell1Price();
            BigDecimal buy1Price = originOrderBook.getBuy1Price();
            // when an event coming, update the orderBook and top1 price
            try {
                OrderModel orderModel = e.getOrderModel();
                OrderEventType type = e.getType();
                switch (type) {
                    case OrderNew:
                        if (e.getTimestamp() > endTime) {
                            return orderRewards;
                        }

                        for (OrderModel order : buys) {
                            BigDecimal deltaReward = order.deal(false, sell1Price, cfg.getEffectiveDistance(),
                                    order.getTimestamp(), e.getTimestamp());
                            if (deltaReward != BigDecimal.ZERO) {
                                List<RewardOrder> rewardOrders = orderRewards.get(order.getId());
                                rewardOrders.add(new RewardOrder(order.getMarketId(), deltaReward));
                                orderRewards.put(order.getId(), rewardOrders);
                            }
                        }

                        for (OrderModel order : sells) {
                            BigDecimal deltaReward = order.deal(true, buy1Price, cfg.getEffectiveDistance(),
                                    order.getTimestamp(), e.getTimestamp());
                            if (deltaReward != BigDecimal.ZERO) {
                                List<RewardOrder> rewardOrders = orderRewards.get(order.getId());
                                rewardOrders.add(new RewardOrder(order.getMarketId(), deltaReward));
                                orderRewards.put(order.getId(), rewardOrders);
                            }
                        }
                        //sort after add so as to update the topPrice
                        sells.add(orderModel);
                        break;
                    case OrderUpdate:
                        int status = orderModel.getStatus();
                        boolean side = getOrderSideByParseOrderId(orderModel.getId().getBytes());
                        if (status == OrderUpdateInfoStatus.Cancelled.getValue() ||
                                status == OrderUpdateInfoStatus.FullyExecuted.getValue()) {
                            for (OrderModel order : buys) {
                                BigDecimal deltaReward = order.deal(false, sell1Price, cfg.getEffectiveDistance(),
                                        order.getTimestamp(), e.getTimestamp());
                                if (deltaReward != BigDecimal.ZERO) {
                                    List<RewardOrder> rewardOrders = orderRewards.get(order.getId());
                                    rewardOrders.add(new RewardOrder(order.getMarketId(), deltaReward));
                                    orderRewards.put(order.getId(), rewardOrders);
                                }
                            }

                            for (OrderModel order : sells) {
                                BigDecimal deltaReward = order.deal(true, buy1Price, cfg.getEffectiveDistance(),
                                        order.getTimestamp(), e.getTimestamp());
                                if (deltaReward != BigDecimal.ZERO) {
                                    List<RewardOrder> rewardOrders = orderRewards.get(order.getId());
                                    rewardOrders.add(new RewardOrder(order.getMarketId(), deltaReward));
                                    orderRewards.put(order.getId(), rewardOrders);
                                }
                            }

                            if (side) {
                                sells.remove(orderModel);
                            } else {
                                buys.remove(orderModel);
                            }

                        } else if (status == OrderUpdateInfoStatus.PartialExecuted.getValue()) {
                            // just update order quantity,have no effect on top1 price
                            for (OrderModel o : buys) {
                                if (o.getId().equals(o.getId())) {
                                    o.setExecutedAmount(orderModel.getExecutedQuantity());
                                }
                            }
                            for (OrderModel o : sells) {
                                if (o.getId().equals(o.getId())) {
                                    o.setExecutedAmount(orderModel.getExecutedQuantity());
                                }
                            }
                        }
                        break;
                }
            } catch (Exception ex) {
                logger.error("mmMining occurs error,the err info: ", ex.getMessage());
            }
        }
        return orderRewards;
    }

    // parsing orderId and get order side
    private boolean getOrderSideByParseOrderId(byte[] idBytes) {
        if (idBytes.length != OrderIdBytesLength) {
            throw new RuntimeException("the orderId is illegal");
        }
        return (int) idBytes[3] == 1;
    }

    public void start(long start, long end) {

    }
}
