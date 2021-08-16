package org.vite.dex.mm.reward;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import lombok.extern.slf4j.Slf4j;
import org.vite.dex.mm.constant.enums.EventType;
import org.vite.dex.mm.constant.enums.OrderUpdateInfoStatus;
import org.vite.dex.mm.entity.OrderEvent;
import org.vite.dex.mm.entity.OrderLog;
import org.vite.dex.mm.entity.OrderModel;
import org.vite.dex.mm.entity.TradePair;
import org.vite.dex.mm.orderbook.EventStream;
import org.vite.dex.mm.orderbook.OrderBook;
import org.vite.dex.mm.orderbook.TradeRecover;
import org.vite.dex.mm.reward.bean.RewardOrder;
import org.vite.dex.mm.reward.cfg.MiningRewardCfg;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.groupingBy;
import static org.vite.dex.mm.constant.enums.EventType.NewOrder;
import static org.vite.dex.mm.constant.enums.EventType.UpdateOrder;

@Slf4j
public class RewardKeeper {

    private final TradeRecover tradeRecover;

    public RewardKeeper(TradeRecover tradeRecover) {
        this.tradeRecover = tradeRecover;
    }

    /**
     * calculate the mm-reward of orders which located in the specified orderBook
     * 1. compute the reward of each order
     * 2. update orderBook so as to make it go forward
     *
     * @param eventStream
     * @param originOrderBook
     * @param cfg
     * @param startTime
     * @param endTime
     * @return
     */
    public Map<String, RewardOrder> mmMining(EventStream eventStream, OrderBook originOrderBook, MiningRewardCfg cfg,
            long startTime, long endTime) {
        List<OrderEvent> events = eventStream.getEvents();
        Map<String, RewardOrder> orderRewards = Maps.newHashMap();// <orderId,RewardOrder>

        for (OrderEvent e : events) {
            List<OrderModel> buys = originOrderBook.getBuys();
            List<OrderModel> sells = originOrderBook.getSells();
            BigDecimal sell1Price = originOrderBook.getSell1Price();
            BigDecimal buy1Price = originOrderBook.getBuy1Price();

            // 1. compute the reward of each order
            try {
                if (e.getTimestamp() > endTime) {
                    return orderRewards;
                }
                OrderLog orderLog = e.getOrderLog();
                EventType type = e.getType();
                int status = orderLog.getStatus();
                boolean newOrder = (type == NewOrder);
                boolean canceledOrder = (type == UpdateOrder && status == OrderUpdateInfoStatus.Cancelled.getValue());
                if (newOrder || canceledOrder) {
                    for (OrderModel order : buys) {
                        RewardOrder rewardOrder = getOrInitRewardOrder(orderRewards, order, cfg, startTime);
                        rewardOrder.deal(cfg, e, sell1Price);
                    }
                    for (OrderModel order : sells) {
                        RewardOrder rewardOrder = getOrInitRewardOrder(orderRewards, order, cfg, startTime);
                        rewardOrder.deal(cfg, e, buy1Price);
                    }
                }

                // 2. update orderBook
                originOrderBook.onward(e);
            } catch (Exception ex) {
                log.error("mmMining occurs error,the err info: ", ex);
            }
        }
        return orderRewards;
    }

    private RewardOrder getOrInitRewardOrder(Map<String, RewardOrder> rewardOrderMap, OrderModel orderModel,
            MiningRewardCfg cfg, long startTime) {
        RewardOrder rewardOrder = rewardOrderMap.get(orderModel.getOrderId());
        if (rewardOrder == null) {
            rewardOrder = new RewardOrder();
            rewardOrder.setOrderModel(orderModel);
            rewardOrder.setCalculateStartTime(startTime);
            rewardOrder.setMarket(cfg.getMarketId());
            rewardOrderMap.put(orderModel.getOrderId(), rewardOrder);
        }
        return rewardOrder;
    }

    public void start(long start, long end) {

    }

    /**
     * calculate vx reward of each address in the market dimension
     *
     * @param dailyReleasedVX
     * @param startTime
     * @param endTime
     * @return
     */
    public Map<String, Map<Integer, Double>> calculateAddressReward(double dailyReleasedVX, long startTime,
            long endTime) {
        Map<String, RewardOrder> totalRewardOrders = Maps.newHashMap();
        // 1. mmMining for each of origin order-book
        for (TradePair tp : TradeRecover.getMMOpenedTradePairs()) {
            String tradePairSymbol = tp.getTradePairSymbol();
            OrderBook orderBook = tradeRecover.getOrderBooks().get(tradePairSymbol);
            EventStream eventStream = tradeRecover.getEventStreams().get(tradePairSymbol);
            if (orderBook == null || eventStream == null) {
                continue;
            }

            MiningRewardCfg miningRewardCfg = new MiningRewardCfg();
            miningRewardCfg.setMarketId(tp.getMarket());
            miningRewardCfg.setEffectiveDistance(tp.getMmEffectiveInterval());
            Map<String, RewardOrder> rewardOrders = mmMining(eventStream, orderBook,
                    miningRewardCfg, startTime, endTime);
            totalRewardOrders.putAll(rewardOrders);
        }

        // 2. group RewardOrder by market
        List<RewardOrder> totalMarketRewards = Lists.newArrayList();
        List<RewardOrder> btcMarketRewards = Lists.newArrayList();
        List<RewardOrder> ethMarketRewards = Lists.newArrayList();
        List<RewardOrder> viteMarketRewards = Lists.newArrayList();
        List<RewardOrder> usdtMarketRewards = Lists.newArrayList();
        totalRewardOrders.forEach((orderId, rewardOrder) -> {
            totalMarketRewards.add(rewardOrder);
            int market = rewardOrder.getMarket();
            switch (market) {
                case 1:
                    btcMarketRewards.add(rewardOrder);
                    break;
                case 2:
                    ethMarketRewards.add(rewardOrder);
                    break;
                case 3:
                    viteMarketRewards.add(rewardOrder);
                    break;
                case 4:
                    usdtMarketRewards.add(rewardOrder);
            }
        });

        // 3. total reward of each market
        double btcMarketSum = btcMarketRewards.stream().mapToDouble(RewardOrder::getTotalFactorDouble).sum();
        double ethMarketSum = ethMarketRewards.stream().mapToDouble(RewardOrder::getTotalFactorDouble).sum();
        double viteMarketSum = viteMarketRewards.stream().mapToDouble(RewardOrder::getTotalFactorDouble).sum();
        double usdtMarketSum = usdtMarketRewards.stream().mapToDouble(RewardOrder::getTotalFactorDouble).sum();

        // 4. <Address, <Market,List<RewardOrder>>>
        Map<String, Map<Integer, List<RewardOrder>>> addressRewardOrderMap = totalMarketRewards.stream()
                .collect(groupingBy(RewardOrder::getOrderAddress, groupingBy(RewardOrder::getMarket)));

        // 5. <Address, <Market,FinalReward>>
        Map<String, Map<Integer, Double>> finalRes = Maps.newHashMap();
        addressRewardOrderMap.forEach((address, marketRewardOrderMap) -> {
            Map<Integer, Double> market2RewardMap = Maps.newHashMap();
            marketRewardOrderMap.forEach((market, rewardOrders) -> {
                double vxReward = 0.0;
                double marketTotalFactor = rewardOrders.stream().mapToDouble(RewardOrder::getTotalFactorDouble).sum();
                switch (market) {
                    case 1:
                        vxReward = calculateVXReward(marketTotalFactor, btcMarketSum, dailyReleasedVX, 0.05);
                        break;
                    case 2:
                        vxReward = calculateVXReward(marketTotalFactor, ethMarketSum, dailyReleasedVX, 0.015);
                        break;
                    case 3:
                        vxReward = calculateVXReward(marketTotalFactor, viteMarketSum, dailyReleasedVX, 0.015);
                        break;
                    case 4:
                        vxReward = calculateVXReward(marketTotalFactor, usdtMarketSum, dailyReleasedVX, 0.02);
                }
                market2RewardMap.put(market, vxReward);
            });
            finalRes.put(address, market2RewardMap);
        });
        //TODO系数调整

        return finalRes;
    }

    private double calculateVXReward(double marketMiningFactor, double marketSum, double dailyReleasedVX, double rate) {
        return marketMiningFactor / marketSum * dailyReleasedVX * rate;
    }
}
