package org.vite.dex.mm.reward;

import com.google.common.collect.Maps;
import lombok.extern.slf4j.Slf4j;
import org.vite.dex.mm.constant.constants.MMConst;
import org.vite.dex.mm.constant.enums.EventType;
import org.vite.dex.mm.constant.enums.OrderUpdateInfoStatus;
import org.vite.dex.mm.entity.OrderEvent;
import org.vite.dex.mm.entity.OrderLog;
import org.vite.dex.mm.entity.OrderModel;
import org.vite.dex.mm.entity.TradePair;
import org.vite.dex.mm.orderbook.EventStream;
import org.vite.dex.mm.orderbook.OrderBook;
import org.vite.dex.mm.orderbook.TradeRecover;
import org.vite.dex.mm.reward.bean.RewardMarket;
import org.vite.dex.mm.reward.bean.RewardOrder;
import org.vite.dex.mm.reward.cfg.MiningRewardCfg;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

    private MiningRewardCfg getMiningConfigFromTradePair(TradePair tp) {
        MiningRewardCfg miningRewardCfg = new MiningRewardCfg();
        miningRewardCfg.setMarketId(tp.getMarket());
        miningRewardCfg.setEffectiveDistance(tp.getMmEffectiveInterval());
        miningRewardCfg.setMiningRewardMultiple(tp.getMmRewardMultiple());
        miningRewardCfg.setMaxBuyFactorThanSell(tp.getBuyAmountThanSellRatio());
        miningRewardCfg.setMaxSellFactorThanBuy(tp.getSellAmountThanBuyRatio());
        return miningRewardCfg;
    }

    public Map<String, Map<Integer, Double>> calcAddressMarketReward(double dailyReleasedVX, long startTime,
            long endTime) {
        Map<String, RewardOrder> totalRewardOrders = Maps.newHashMap(); //<Address,RewardOrder>
        Map<String, MiningRewardCfg> tradePairCfgMap = Maps.newHashMap(); //<Address,MiningRewardCfg>
        // 1. mmMining for origin order books
        TradeRecover.getMMOpenedTradePairs().stream().forEach(tp -> {
            String tradePairSymbol = tp.getTradePairSymbol();
            MiningRewardCfg miningRewardCfg = getMiningConfigFromTradePair(tp);
            tradePairCfgMap.put(tp.getTradePairSymbol(), miningRewardCfg);

            OrderBook orderBook = tradeRecover.getOrderBooks().get(tradePairSymbol);
            EventStream eventStream = tradeRecover.getEventStreams().get(tradePairSymbol);
            if (orderBook != null && eventStream != null) {
                Map<String, RewardOrder> rewardOrders = mmMining(eventStream, orderBook,
                        miningRewardCfg, startTime, endTime);
                totalRewardOrders.putAll(rewardOrders);
            }
        });

        // 2. calc reward VX for each order
        List<RewardOrder> totalMarketRewards = totalRewardOrders.values().stream().collect(Collectors.toList());
        Map<Integer, RewardMarket> markets = new HashMap<>();
        Map<Integer, List<RewardOrder>> marketOrderRewards =
                totalRewardOrders.values().stream().collect(Collectors.groupingBy(RewardOrder::getMarket));

        marketOrderRewards.forEach((market, rewardOrderList) -> markets.put(market, new RewardMarket(market,
                rewardOrderList, tradePairCfgMap)));
        markets.values().forEach(market -> {
            double marketSharedRatio = MMConst.getAllSharedRatio().get(market);
            market.apply(dailyReleasedVX, marketSharedRatio);
        });

        //3. 汇总计算Address在各个大市场的总挖矿VX数量
        Map<String, Map<Integer, List<RewardOrder>>> address2MarketRewardsMap = totalMarketRewards.stream()
                .collect(groupingBy(RewardOrder::getOrderAddress, groupingBy(RewardOrder::getMarket)));

        Map<String, Map<Integer, Double>> finalRes = Maps.newHashMap();
        address2MarketRewardsMap.forEach((address, marketRewardOrderMap) -> {
            Map<Integer, Double> marketVXMap = Maps.newHashMap();
            marketRewardOrderMap.forEach((market, rList) -> {
                double sum = rList.stream().mapToDouble(RewardOrder::getTotalVXDouble).sum();
                marketVXMap.put(market, sum);
            });
            finalRes.put(address, marketVXMap);
        });
        return finalRes;
    }
}
