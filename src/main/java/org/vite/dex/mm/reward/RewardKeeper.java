package org.vite.dex.mm.reward;

import com.google.common.collect.Maps;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.vite.dex.mm.constant.constants.MarketMiningConst;
import org.vite.dex.mm.constant.enums.EventType;
import org.vite.dex.mm.constant.enums.OrderStatus;
import org.vite.dex.mm.entity.OrderEvent;
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

@Slf4j
public class RewardKeeper {

    private final TradeRecover tradeRecover;

    public RewardKeeper(TradeRecover tradeRecover) {
        this.tradeRecover = tradeRecover;
    }

    /**
     * calculate the market-mining factor of orders which located in the specified orderBook
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
        Map<String, RewardOrder> orderRewards = Maps.newHashMap();// <orderId,RewardOrder>

        for (OrderEvent e : eventStream.getEvents()) {
            List<OrderModel> buys = originOrderBook.getBuys();
            List<OrderModel> sells = originOrderBook.getSells();
            BigDecimal sell1Price = originOrderBook.getSell1Price();
            BigDecimal buy1Price = originOrderBook.getBuy1Price();

            // 1. compute the reward of each order
            try {
                if (e.getTimestamp() > endTime) {
                    log.debug("the event`s emit time is greater than cycle endTime, stop mining in this cycle");
                    return orderRewards;
                }
                EventType type = e.getType();
                OrderStatus status = e.getOrderLog().getStatus();
                boolean newOrder = (type == EventType.NewOrder);
                boolean canceledOrder = (type == EventType.UpdateOrder && status == OrderStatus.Cancelled);
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

                // 2. make orderBook go forward
                originOrderBook.onward(e);
            } catch (Exception ex) {
                log.error("mmMining occurs error,the err info: ", ex);
            }
        }
        log.info("the order book [{}] is onwarded to the end time of last cycle");
        return orderRewards;
    }

    private RewardOrder getOrInitRewardOrder(Map<String, RewardOrder> rewardOrderMap, OrderModel orderModel,
            MiningRewardCfg cfg, long startTime) {
        RewardOrder rewardOrder = rewardOrderMap.get(orderModel.getOrderId());
        if (rewardOrder == null) {
            rewardOrder = new RewardOrder();
            if (StringUtils.isEmpty(orderModel.getTradePair())) {
                orderModel.setTradePair(cfg.getTradePairSymbol());
            }

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
        miningRewardCfg.setTradePairSymbol(tp.getTradePair());
        miningRewardCfg.setEffectiveDist(tp.getMmEffectiveInterval());
        miningRewardCfg.setMiningRewardMultiple(tp.getMmRewardMultiple());
        miningRewardCfg.setMaxBuyFactorThanSell(tp.getBuyAmountThanSellRatio());
        miningRewardCfg.setMaxSellFactorThanBuy(tp.getSellAmountThanBuyRatio());
        return miningRewardCfg;
    }

    /**
     * calculate each user`s market-mining rewards
     * 
     * @param dailyReleasedVX
     * @param startTime
     * @param endTime
     * @return
     */
    public Map<String, Map<Integer, BigDecimal>> calcAddressMarketReward(double dailyReleasedVX, long startTime,
            long endTime) {
        Map<String, RewardOrder> totalRewardOrders = Maps.newHashMap(); // <Address,RewardOrder>
        Map<String, MiningRewardCfg> tradePairCfgMap = Maps.newHashMap(); // <Address,MiningRewardCfg>
        Map<Integer, RewardMarket> marketRewards = new HashMap<>(); // <MarketId, RewardMarket>
        Map<String, Map<Integer, BigDecimal>> finalRes = Maps.newHashMap(); // <Address, Map<MarketId,RewardMarket>>

        log.debug("start onwarding for each order book and calc the market mining factor of orders");
        TradeRecover.getMarketMiningOpenedTp().stream().forEach(tp -> {
            String tradePairSymbol = tp.getTradePair();
            MiningRewardCfg miningRewardCfg = getMiningConfigFromTradePair(tp);
            tradePairCfgMap.put(tradePairSymbol, miningRewardCfg);

            OrderBook orderBook = tradeRecover.getOrderBooks().get(tradePairSymbol);
            EventStream eventStream = tradeRecover.getEventStreams().get(tradePairSymbol);
            if (orderBook != null && eventStream != null) {
                // 1. get mining factor on the way of onwarding order book
                Map<String, RewardOrder> rewardOrders = mmMining(eventStream, orderBook, miningRewardCfg, startTime,
                        endTime);
                totalRewardOrders.putAll(rewardOrders);
            }
        });

        // 2. calc reward VX for each order
        log.debug("calculating reward VX for each Order");
        Map<Integer, List<RewardOrder>> marketOrderRewards = totalRewardOrders.values().stream()
                .collect(Collectors.groupingBy(RewardOrder::getMarket));

        marketOrderRewards.forEach((market, rewardOrderList) -> marketRewards.put(market,
                new RewardMarket(market, rewardOrderList, tradePairCfgMap)));

        marketRewards.values().forEach(rewardMarket -> {
            double marketSharedRatio = MarketMiningConst.getMarketSharedRatio().get(rewardMarket.getMarket());
            rewardMarket.apply(dailyReleasedVX, marketSharedRatio);
        });

        // 3. calculate total VX mined by each Address in each market
        log.debug("calculating the total number of VX mined by Address in each major market");
        Map<String, Map<Integer, List<RewardOrder>>> address2MarketRewardsMap = totalRewardOrders.values().stream()
                .collect(Collectors.groupingBy(RewardOrder::getOrderAddress,
                        Collectors.groupingBy(RewardOrder::getMarket)));

        address2MarketRewardsMap.forEach((address, market2RewardOrders) -> {
            Map<Integer, BigDecimal> marketVXMap = Maps.newHashMap();
            market2RewardOrders.forEach((market, rewardOrders) -> {
                BigDecimal sum = rewardOrders.stream().map(RewardOrder::getTotalRewardVX).reduce(BigDecimal.ZERO, BigDecimal::add);
                marketVXMap.put(market, sum);
            });
            finalRes.put(address, marketVXMap);
        });
        log.info("successfully calc the VX reward for each Address during the last cycle");
        return finalRes;
    }
}
