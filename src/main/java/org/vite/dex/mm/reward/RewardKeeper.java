package org.vite.dex.mm.reward;

import com.google.common.collect.Maps;
import lombok.extern.slf4j.Slf4j;
import org.vite.dex.mm.constant.constants.MarketMiningConst;
import org.vite.dex.mm.orderbook.BlockEventStream;
import org.vite.dex.mm.orderbook.IOrderEventHandleAware;
import org.vite.dex.mm.orderbook.OrderBooks;
import org.vite.dex.mm.reward.bean.RewardMarket;
import org.vite.dex.mm.reward.bean.RewardOrder;
import org.vite.dex.mm.reward.cfg.MiningRewardCfg;
import org.vite.dex.mm.utils.client.ViteCli;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
public class RewardKeeper implements IOrderEventHandleAware {
    private ViteCli viteCli;

    public RewardKeeper(ViteCli viteCli) {
        this.viteCli = viteCli;
    }

    /**
     * calculate market-mining factor of orders that located in the orderBooks
     * 
     * @param books  all of OrderBooks
     * @param stream BlockEventStream
     * @return the mapping of <OrderId, RewardOrder>
     * @throws IOException
     */
    public Map<String, RewardOrder> mmMining(OrderBooks books, BlockEventStream stream, Long startTime, Long endTime,
            Map<String, MiningRewardCfg> tradePairCfgMap) throws IOException {

        log.debug("start onwarding for orderbooks and calc the market mining factor of orders");
        Long startHeight = viteCli.getContractChainHeight(startTime) + 1;
        Long endHeight = viteCli.getContractChainHeight(endTime);
        stream = stream.subStream(startHeight, endHeight);

        MminingAware aware = new MminingAware(startTime, endTime, tradePairCfgMap);
        books.setOrderAware(aware);
        stream.action(books, false, false);
        books.setOrderAware(null);

        return aware.orderRewards;
    }

    /**
     * calculate each user`s market-mining rewards
     * 
     * @param dailyReleasedVX
     * @param startTime
     * @param endTime
     * @return
     * @throws IOException
     */
    public Map<String, Map<Integer, BigDecimal>> calcAddressMarketReward(OrderBooks books, BlockEventStream stream,
            double dailyReleasedVX, long startTime, long endTime, Map<String, MiningRewardCfg> tradePairCfgMap)
            throws IOException {

        Map<String, Map<Integer, BigDecimal>> finalRes = Maps.newHashMap(); // <Address, Map<MarketId,RewardMarket>>
        Map<Integer, RewardMarket> marketRewards = new HashMap<>(); // <MarketId, RewardMarket>

        // 1.go onward OrderBooks and calc factor of each Order
        Map<String, RewardOrder> totalRewardOrders = mmMining(books, stream, startTime, endTime, tradePairCfgMap);

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
                BigDecimal sum = rewardOrders.stream().map(RewardOrder::getTotalRewardVX).reduce(BigDecimal.ZERO,
                        BigDecimal::add);
                marketVXMap.put(market, sum);
            });
            finalRes.put(address, marketVXMap);
        });
        log.info("successfully calc the VX reward for each Address during the last cycle");
        return finalRes;
    }
}
