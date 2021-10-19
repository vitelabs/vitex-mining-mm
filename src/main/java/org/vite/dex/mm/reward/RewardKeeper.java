package org.vite.dex.mm.reward;

import com.google.common.collect.Maps;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.vite.dex.mm.config.MiningConfiguration;
import org.vite.dex.mm.constant.constants.MarketMiningConst;
import org.vite.dex.mm.entity.InviteOrderMiningReward;
import org.vite.dex.mm.entity.InviteOrderMiningStat;
import org.vite.dex.mm.orderbook.BlockEventStream;
import org.vite.dex.mm.orderbook.IOrderEventHandleAware;
import org.vite.dex.mm.orderbook.OrderBooks;
import org.vite.dex.mm.reward.bean.RewardMarket;
import org.vite.dex.mm.reward.bean.RewardOrder;
import org.vite.dex.mm.reward.cfg.MiningRewardCfg;
import org.vite.dex.mm.utils.CommonUtils;
import org.vite.dex.mm.utils.client.ViteCli;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
public class RewardKeeper implements IOrderEventHandleAware {

    private ViteCli viteCli;

    private MiningConfiguration miningConfig;

    public RewardKeeper(ViteCli viteCli, MiningConfiguration miningConfig) {
        this.viteCli = viteCli;
        this.miningConfig = miningConfig;
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

        log.info("start onwarding for orderbooks and calc the market mining factor of orders");
        Long startHeight = viteCli.getContractChainHeight(startTime) + 1;
        Long endHeight = viteCli.getContractChainHeight(endTime);
        stream = stream.subStream(startHeight, endHeight);

        MminingAware aware = new MminingAware(startTime, endTime, tradePairCfgMap);
        books.setOrderAware(aware);
        stream.travel(books, false, false);
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
    public FinalResult calcAddressMarketReward(OrderBooks books, BlockEventStream stream, BigDecimal dailyReleasedVX,
            long startTime, long endTime) throws IOException {

        Map<String, Map<Integer, BigDecimal>> orderMiningFinalRes = Maps.newHashMap();
        Map<String, InviteOrderMiningStat> inviteMiningFinalRes = new HashMap<>();
        Map<Integer, RewardMarket> marketRewards = new HashMap<>(); // <MarketId, RewardMarket>
        Map<String, MiningRewardCfg> tradePairCfgMap = CommonUtils
                .miningRewardCfgMap(miningConfig.getTradePairSettingUrl());
        List<InviteOrderMiningReward> inviteRewardList = new ArrayList<>();

        // 1.go onward OrderBooks and calc factor of each Order
        Map<String, RewardOrder> totalRewardOrders = mmMining(books, stream, startTime, endTime, tradePairCfgMap);

        adjustFactorByInviteRelation(totalRewardOrders, inviteRewardList);

        // 2. calc rewardVX for per Order
        log.debug("calculating reward VX for each Order");
        Map<Integer, List<RewardOrder>> marketOrderRewards = totalRewardOrders.values().stream()
                .collect(Collectors.groupingBy(RewardOrder::getMarket));

        Map<Integer, List<InviteOrderMiningReward>> marketInviteRewards = inviteRewardList.stream()
                .collect(Collectors.groupingBy(InviteOrderMiningReward::getMarket));

        marketOrderRewards.forEach((market, rewardOrderList) -> marketRewards.put(market,
                new RewardMarket(market, rewardOrderList, marketInviteRewards.get(market), tradePairCfgMap)));

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
            orderMiningFinalRes.put(address, marketVXMap);
        });

        Map<String, List<InviteOrderMiningReward>> address2InviteRewardsMap = inviteRewardList.stream()
                .collect(Collectors.groupingBy(InviteOrderMiningReward::getAddress));

        address2InviteRewardsMap.forEach((address, inviteList) -> {
            BigDecimal inviteAmount = inviteList.stream().map(InviteOrderMiningReward::getInviteRewardVX)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            InviteOrderMiningStat inviteOrderMiningStat = new InviteOrderMiningStat();
            inviteOrderMiningStat.setAddress(address);
            inviteOrderMiningStat.setAmount(inviteAmount);
            inviteOrderMiningStat.setRatio(inviteAmount
                    .divide(dailyReleasedVX.multiply(MarketMiningConst.MARKET_MINING_RATIO), 18, RoundingMode.DOWN));
            inviteMiningFinalRes.put(address, inviteOrderMiningStat);
        });

        FinalResult res = new FinalResult();
        res.setOrderMiningFinalRes(orderMiningFinalRes);
        res.setInviteMiningFinalRes(inviteMiningFinalRes);
        log.info("successfully calc the VX reward for each Address during the last cycle");
        return res;
    }

    @Data
    public static class FinalResult {
        Map<String, Map<Integer, BigDecimal>> orderMiningFinalRes;
        Map<String, InviteOrderMiningStat> inviteMiningFinalRes;
    }

    private void adjustFactorByInviteRelation(Map<String, RewardOrder> totalRewardOrders,
            List<InviteOrderMiningReward> inviteRewardList) throws IOException {

        List<String> addrs = totalRewardOrders.values().stream().map(e -> e.getOrderAddress()).distinct()
                .collect(Collectors.toList());
        Map<String, String> invitee2InviterMap = viteCli.getInvitee2InviterMap(addrs);
        totalRewardOrders.forEach((orderId, rewardOrder) -> {
            String addr = rewardOrder.getOrderAddress();
            // be invited by others
            if (addr != null && invitee2InviterMap.containsKey(addr)) {
                InviteOrderMiningReward inviteeReward = new InviteOrderMiningReward();
                inviteeReward.setOrderId(orderId);
                inviteeReward.setFactor(rewardOrder.getTotalFactor().multiply(MarketMiningConst.PERCENT_00125));
                inviteeReward.setAddress(addr);
                inviteeReward.setMarket(rewardOrder.getMarket());
                inviteeReward.setTradePair(rewardOrder.getTradePair());
                inviteeReward.setSide(rewardOrder.getOrderSide());
                inviteRewardList.add(inviteeReward);

                // contribute 2.5% factor to inviter
                String inviterAddr = invitee2InviterMap.get(addr);
                InviteOrderMiningReward inviterReward = new InviteOrderMiningReward();
                inviterReward.setOrderId(orderId);
                inviterReward.setFactor(rewardOrder.getTotalFactor().multiply(MarketMiningConst.PERCENT_25));
                inviterReward.setAddress(inviterAddr);
                inviterReward.setMarket(rewardOrder.getMarket());
                inviterReward.setTradePair(rewardOrder.getTradePair());
                inviterReward.setSide(rewardOrder.getOrderSide());
                inviteRewardList.add(inviterReward);
            }
        });
    }
}
