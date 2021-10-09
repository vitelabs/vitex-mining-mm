package org.vite.dex.mm.reward;

import com.google.common.collect.Maps;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.vite.dex.mm.constant.enums.EventType;
import org.vite.dex.mm.constant.enums.OrderStatus;
import org.vite.dex.mm.entity.OrderEvent;
import org.vite.dex.mm.entity.OrderModel;
import org.vite.dex.mm.orderbook.IOrderEventHandleAware;
import org.vite.dex.mm.orderbook.OrderBook;
import org.vite.dex.mm.reward.bean.RewardOrder;
import org.vite.dex.mm.reward.cfg.MiningRewardCfg;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Slf4j
public class MminingAware implements IOrderEventHandleAware {
    private Long startTime;
    private Long endTime;
    public Map<String, RewardOrder> orderRewards = Maps.newHashMap();
    private Map<String, MiningRewardCfg> tradePairCfgMap;

    public MminingAware(Long startTime, Long endTime, Map<String, MiningRewardCfg> tradePairCfgMap) {
        this.startTime = startTime;
        this.endTime = endTime;
        this.tradePairCfgMap = tradePairCfgMap;
    }

    @Override
    public void beforeOnward(OrderBook originOrderBook, OrderEvent e) {
        List<OrderModel> buys = originOrderBook.getBuys();
        List<OrderModel> sells = originOrderBook.getSells();
        BigDecimal sell1Price = originOrderBook.getSell1Price();
        BigDecimal buy1Price = originOrderBook.getBuy1Price();

        if (sell1Price == null || buy1Price == null) {
            return;
        }

        if (e.getTimestamp() > endTime) {
            log.debug("the event`s emit time is greater than cycle endTime");
            throw new RuntimeException("the event`s emit time is greater than cycle endTime");
        }

        EventType type = e.getType();
        OrderStatus status = e.getOrderLog().getStatus();
        boolean newOrder = (type == EventType.NewOrder);
        boolean canceledOrder = (type == EventType.UpdateOrder && status == OrderStatus.Cancelled);
        MiningRewardCfg cfg = tradePairCfgMap.get(e.getOrderLog().getTradePair());
        if (newOrder || canceledOrder) {
            for (OrderModel order : buys) {
                RewardOrder rewardOrder = getOrInitRewardOrder(orderRewards, order, cfg, startTime);
                rewardOrder.deal(e, cfg, sell1Price);
            }
            for (OrderModel order : sells) {
                RewardOrder rewardOrder = getOrInitRewardOrder(orderRewards, order, cfg, startTime);
                rewardOrder.deal(e, cfg, buy1Price);
            }
        }
    }

    private RewardOrder getOrInitRewardOrder(Map<String, RewardOrder> rewardOrderMap, OrderModel orderModel,
            MiningRewardCfg cfg, long startTime) {
        RewardOrder rewardOrder = rewardOrderMap.get(orderModel.getOrderId());
        if (rewardOrder == null) {
            rewardOrder = new RewardOrder();
            rewardOrder.setOrderModel(orderModel);
            rewardOrder.setCalculateStartTime(startTime);
            rewardOrder.setMarket(cfg.getMarketId());

            if (StringUtils.isEmpty(orderModel.getTradePair())) {
                orderModel.setTradePair(cfg.getTradePairSymbol());
            }
            rewardOrderMap.put(orderModel.getOrderId(), rewardOrder);
        }
        return rewardOrder;
    }
}
