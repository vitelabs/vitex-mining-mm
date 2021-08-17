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

            MiningRewardCfg miningRewardCfg = getMiningConfigFromTradePair(tp);
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
        return finalRes;
    }

    private double calculateVXReward(double marketMiningFactor, double marketSum, double dailyReleasedVX, double rate) {
        return marketMiningFactor / marketSum * dailyReleasedVX * rate;
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
        Map<String, Double> tradePair2TotalSharedVX = Maps.newHashMap(); //<TradePair,TotalAmount>
        Map<String, Map<Boolean, Double>> tradePair2TotalAmountGroup = Maps.newHashMap();//<TradePair,<Boolean,
        // TotalAmount>>
        Map<String, Map<Boolean, Double>> tradePair2SideVX = Maps.newHashMap();
        Map<String, Map<Boolean, Double>> tradePair2SideFactorMap = Maps.newHashMap();

        // 1. mmMining for each of origin order-book
        for (TradePair tp : TradeRecover.getMMOpenedTradePairs()) {
            String tradePairSymbol = tp.getTradePairSymbol();
            OrderBook orderBook = tradeRecover.getOrderBooks().get(tradePairSymbol);
            EventStream eventStream = tradeRecover.getEventStreams().get(tradePairSymbol);
            if (orderBook == null || eventStream == null) {
                continue;
            }

            MiningRewardCfg miningRewardCfg = getMiningConfigFromTradePair(tp);
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

        // 3. prepare total factor and total VX of each market
        double btcMarketSum = btcMarketRewards.stream().mapToDouble(RewardOrder::getTotalFactorDouble).sum();
        double ethMarketSum = ethMarketRewards.stream().mapToDouble(RewardOrder::getTotalFactorDouble).sum();
        double viteMarketSum = viteMarketRewards.stream().mapToDouble(RewardOrder::getTotalFactorDouble).sum();
        double usdtMarketSum = usdtMarketRewards.stream().mapToDouble(RewardOrder::getTotalFactorDouble).sum();
        double btcSharedVX = dailyReleasedVX * 0.05;
        double ethSharedVX = dailyReleasedVX * 0.015;
        double viteSharedVX = dailyReleasedVX * 0.015;
        double usdtSharedVX = dailyReleasedVX * 0.02;

        //4. 计算每个交易对总共挖矿分得的VX数量,以及该交易对买单和卖单总金额
        Map<String, List<RewardOrder>> tradePair2RewardList =
                totalMarketRewards.stream().collect(groupingBy(RewardOrder::getTradePair));//<TradePair,
        // List<RewardOrder>>

        tradePair2RewardList.forEach((tradePair, rewardList) -> {
            Map<Boolean, Double> buySellAmountMap = Maps.newHashMap();
            double sellSum = rewardList.stream().filter(reward -> reward.getOrderSide()).
                    mapToDouble(RewardOrder::getAmount).sum();
            double buySum = rewardList.stream().filter(reward -> !reward.getOrderSide()).
                    mapToDouble(RewardOrder::getAmount).sum();
            buySellAmountMap.put(false, buySum);
            buySellAmountMap.put(true, sellSum);
            double sum = rewardList.stream().mapToDouble(RewardOrder::getTotalFactorDouble).sum();
            int market = getMarketFromTradePair(tradePair);
            double tradePairMarketSharedVX = 0;
            switch (market) {
                case 1:
                    tradePairMarketSharedVX = sum / btcMarketSum * btcSharedVX;
                    break;
                case 2:
                    tradePairMarketSharedVX = sum / ethMarketSum * ethSharedVX;
                    break;
                case 3:
                    tradePairMarketSharedVX = sum / viteMarketSum * viteSharedVX;
                    break;
                case 4:
                    tradePairMarketSharedVX = sum / usdtMarketSum * usdtSharedVX;
            }

            tradePair2TotalSharedVX.put(tradePair, tradePairMarketSharedVX);
            tradePair2TotalAmountGroup.put(tradePair, buySellAmountMap);
        });

        // 5. 计算出每个交易对各个方向挖得的总VX数量
        for (TradePair tp : TradeRecover.getMMOpenedTradePairs()) {
            Map<Boolean, Double> map = Maps.newHashMap();
            String tradePairSymbol = tp.getTradePairSymbol();
            MiningRewardCfg miningRewardCfg = getMiningConfigFromTradePair(tp);
            Double tradePairMarketSharedVX = tradePair2TotalSharedVX.get(tradePairSymbol);
            Map<Boolean, Double> buySellAmountMap = tradePair2TotalAmountGroup.get(tradePairSymbol);
            double ratio = getRatioFromBuySellMap(buySellAmountMap, miningRewardCfg);
            Double buySharedVX = tradePairMarketSharedVX * ratio;
            Double sellSharedVX = tradePairMarketSharedVX * (1 - ratio);
            map.put(true, sellSharedVX);
            map.put(false, buySharedVX);
            tradePair2SideVX.put(tradePairSymbol, map);
        }

        // 6. 获得每个交易对在每个方向上的总挖矿factor
        Map<String, Map<Boolean, List<RewardOrder>>> tradePair2SideRewardsMap = totalMarketRewards.stream()
                .collect(groupingBy(RewardOrder::getTradePair, groupingBy(RewardOrder::getOrderSide)));

        tradePair2SideRewardsMap.forEach((tp, sideRewardOrderMap) -> {
            Map<Boolean, Double> side2FactorMap = Maps.newHashMap();
            sideRewardOrderMap.forEach((side, rewardOrderList) -> {
                double sideTotal = rewardOrderList.stream().mapToDouble(RewardOrder::getTotalFactorDouble).sum();
                side2FactorMap.put(side, sideTotal);
            });
            tradePair2SideFactorMap.put(tp, side2FactorMap);
        });

        // 7.计算Address分别在每个trade-pair市场挂单挖矿获得的VX数量
        Map<String, Map<String, List<RewardOrder>>> address2TradePairRewardsMap = totalMarketRewards.stream()
                .collect(groupingBy(RewardOrder::getOrderAddress, groupingBy(RewardOrder::getTradePair)));

        address2TradePairRewardsMap.forEach((addr, tradePair2ListMap) -> {
            tradePair2ListMap.forEach((tradePair, rewardList) -> {
                Map<Boolean, Double> sideVXMap = tradePair2SideVX.get(tradePair);
                Map<Boolean, Double> sideFactorMap = tradePair2SideFactorMap.get(tradePair);
                double sellVX = sideVXMap.get(true);
                double buyVX = sideVXMap.get(false);
                for (RewardOrder rw : rewardList) {
                    BigDecimal reward = BigDecimal.ZERO;
                    if (rw.getOrderSide()) {
                        reward = rw.getTotalFactor().divide(new BigDecimal(
                                String.valueOf(sideFactorMap.get(true)))).multiply(new BigDecimal(sellVX));
                    } else {
                        reward = rw.getTotalFactor().divide(new BigDecimal(
                                String.valueOf(sideFactorMap.get(false)))).multiply(new BigDecimal(buyVX));
                    }
                    rw.setTotalRewardVX(reward);
                }
            });
        });

        //8. 汇总计算Address在各个大市场的总挖矿VX数量
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

    //todo get ratio from formula
    private double getRatioFromBuySellMap(Map<Boolean, Double> buySellMap, MiningRewardCfg cfg) {
        return 0;
    }

    //todo get market type from tradePair
    private int getMarketFromTradePair(String tradePair) {
        return 1;
    }
}
