package org.vite.dex.mm.reward;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.vite.dex.mm.constant.constants.MarketMiningConst;
import org.vite.dex.mm.constant.enums.QuoteMarketType;
import org.vite.dex.mm.constant.enums.SettleStatus;
import org.vite.dex.mm.entity.AddressEstimateReward;
import org.vite.dex.mm.entity.InviteOrderMiningStat;
import org.vite.dex.mm.entity.MiningAddressReward;
import org.vite.dex.mm.entity.OrderMiningMarketReward;
import org.vite.dex.mm.entity.SettlePage;
import org.vite.dex.mm.mapper.AddressEstimateRewardRepository;
import org.vite.dex.mm.mapper.MiningAddressRewardRepository;
import org.vite.dex.mm.mapper.OrderMiningMarketRewardRepository;
import org.vite.dex.mm.mapper.SettlePageRepository;
import org.vite.dex.mm.reward.RewardKeeper.FinalResult;
import org.vite.dex.mm.utils.client.ViteCli;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
public class SettleService {
    @Autowired
    private ViteCli viteCli;

    @Autowired
    OrderMiningMarketRewardRepository orderMiningMarketRewardRepo;

    @Autowired
    MiningAddressRewardRepository miningAddressRewardRepo;

    @Autowired
    AddressEstimateRewardRepository addrEstimateRewardRepo;

    @Autowired
    SettlePageRepository settlePageRepo;

    public SettleService(ViteCli viteCli) {
        this.viteCli = viteCli;
    }

    /**
     * store order mining reward in db 
     * 
     * @param totalReleasedViteAmount
     * @param finalRes
     * @throws IOException
     */
    @Transactional
    public void saveMiningRewards(FinalResult finalRes, BigDecimal totalReleasedViteAmount, int cycleKey)
            throws Exception {
        List<MiningAddressReward> miningAddressRewards = new ArrayList<>();
        try {
            saveMiningRewardToDB(finalRes, totalReleasedViteAmount, cycleKey, miningAddressRewards);
        } catch (Exception e) {
            log.error("save and settle vx reward failed ", e);
            throw e;
        }
    }

    public void saveMiningRewardToDB(FinalResult finalRes, BigDecimal totalReleasedViteAmount, int cycleKey,
            List<MiningAddressReward> miningAddressRewards) {
        Map<String, Map<Integer, BigDecimal>> orderMiningFinalRes = finalRes.getOrderMiningFinalRes();
        Map<String, InviteOrderMiningStat> inviteMiningFinalRes = finalRes.getInviteMiningFinalRes();

        List<OrderMiningMarketReward> addrMarketRewards = assembleAddrMarketReward(orderMiningFinalRes,
                totalReleasedViteAmount, cycleKey);

        int pageMax = mergeOrderMiningAndInviteReward(orderMiningFinalRes, inviteMiningFinalRes,
                totalReleasedViteAmount, cycleKey, miningAddressRewards);

        List<SettlePage> settlePageList = assembleSettlePage(pageMax, cycleKey, miningAddressRewards);

        // save db
        orderMiningMarketRewardRepo.saveAll(addrMarketRewards);
        miningAddressRewardRepo.saveAll(miningAddressRewards);
        settlePageRepo.saveAll(settlePageList);
    }

    private List<SettlePage> assembleSettlePage(int pageMax, int cycleKey,
            List<MiningAddressReward> miningAddressRewards) {
        List<SettlePage> settlePageList = new ArrayList<>();
        for (int m = 1; m <= pageMax; m++) {
            SettlePage settlePage = new SettlePage();
            settlePage.setCycleKey(cycleKey);
            settlePage.setDataPage(m);
            final int datePage = m;
            List<MiningAddressReward> miningAddressSubList = miningAddressRewards.stream()
                    .filter(t -> t.getDataPage() == datePage).collect(Collectors.toList());
            BigDecimal pageTotalAmount = miningAddressSubList.stream().map(MiningAddressReward::getTotalReward)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            settlePage.setAmount(pageTotalAmount);
            settlePage.setSettleStatus(SettleStatus.UnSettle);
            settlePage.setCtime(new Date());
            settlePage.setUtime(new Date());
            settlePageList.add(settlePage);
        }

        return settlePageList;
    }

    private int mergeOrderMiningAndInviteReward(Map<String, Map<Integer, BigDecimal>> orderMiningFinalRes,
            Map<String, InviteOrderMiningStat> inviteMiningFinalRes, BigDecimal totalReleasedViteAmount, int cycleKey,
            List<MiningAddressReward> miningAddressRewards) {
        // mining_address
        int i = 0;
        for (Map.Entry<String, Map<Integer, BigDecimal>> entry : orderMiningFinalRes.entrySet()) {
            String addr = entry.getKey();
            Map<Integer, BigDecimal> rewardMap = entry.getValue();

            MiningAddressReward miningAddrReward = new MiningAddressReward();
            miningAddrReward.setCycleKey(cycleKey);
            miningAddrReward.setAddress(addr);
            miningAddrReward.setOrderMiningAmount(rewardMap.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add));
            miningAddrReward.setOrderMiningPercent(miningAddrReward.getOrderMiningAmount().divide(
                    totalReleasedViteAmount.multiply(MarketMiningConst.MARKET_MINING_RATIO), 18, RoundingMode.DOWN));
            miningAddrReward.setInviteMiningAmount(BigDecimal.ZERO);
            miningAddrReward.setInviteMiningPercent(BigDecimal.ZERO);

            BigDecimal totalReward = miningAddrReward.getOrderMiningAmount();
            if (inviteMiningFinalRes.containsKey(addr)) {
                InviteOrderMiningStat inviteStat = inviteMiningFinalRes.get(addr);
                miningAddrReward.setInviteMiningAmount(inviteStat.getAmount());
                miningAddrReward.setInviteMiningPercent(inviteStat.getRatio());
                totalReward = totalReward.add(inviteStat.getAmount());
            }
            miningAddrReward.setTotalReward(totalReward);
            miningAddrReward.setDataPage(i / 30 + 1);
            miningAddrReward.setSettleStatus(SettleStatus.UnSettle);
            miningAddrReward.setCtime(new Date());
            miningAddrReward.setUtime(new Date());

            if (miningAddrReward.getTotalReward().compareTo(BigDecimal.ZERO) > 0) {
                miningAddressRewards.add(miningAddrReward);
                i++;
            }
        }

        for (Map.Entry<String, InviteOrderMiningStat> entry : inviteMiningFinalRes.entrySet()) {
            String addr = entry.getKey();
            InviteOrderMiningStat inviteStat = entry.getValue();

            if (!orderMiningFinalRes.containsKey(addr)) {
                MiningAddressReward miningAddrReward = new MiningAddressReward();
                miningAddrReward.setCycleKey(cycleKey);
                miningAddrReward.setAddress(addr);
                miningAddrReward.setOrderMiningAmount(BigDecimal.ZERO);
                miningAddrReward.setOrderMiningPercent(BigDecimal.ZERO);
                miningAddrReward.setInviteMiningAmount(inviteStat.getAmount());
                miningAddrReward.setInviteMiningPercent(inviteStat.getRatio());
                miningAddrReward.setTotalReward(inviteStat.getAmount());
                miningAddrReward.setDataPage(i / 30 + 1);
                miningAddrReward.setSettleStatus(SettleStatus.UnSettle);
                miningAddrReward.setCtime(new Date());
                miningAddrReward.setUtime(new Date());

                if (miningAddrReward.getTotalReward().compareTo(BigDecimal.ZERO) > 0) {
                    miningAddressRewards.add(miningAddrReward);
                    i++;
                }
            }
        }
        return i / 30 + 1;
    }

    private List<OrderMiningMarketReward> assembleAddrMarketReward(
            Map<String, Map<Integer, BigDecimal>> orderMiningFinalRes, BigDecimal totalReleasedViteAmount,
            int cycleKey) {
        List<OrderMiningMarketReward> addrMarketRewards = new ArrayList<>();
        // sub-market details
        orderMiningFinalRes.forEach((addr, marketMap) -> {
            marketMap.forEach((market, vxAmount) -> {
                if (vxAmount.compareTo(BigDecimal.ZERO) > 0) {
                    OrderMiningMarketReward addrMarketReward = new OrderMiningMarketReward();
                    addrMarketReward.setAddress(addr);
                    addrMarketReward.setQuoteTokenType(market);
                    addrMarketReward.setAmount(vxAmount);
                    addrMarketReward.setCycleKey(cycleKey);
                    BigDecimal marketShared = totalReleasedViteAmount
                            .multiply(BigDecimal.valueOf(MarketMiningConst.getMarketSharedRatio().get(market)));
                    addrMarketReward.setFactorRatio(vxAmount.divide(marketShared, 18, RoundingMode.DOWN));
                    addrMarketReward.setCtime(new Date());
                    addrMarketReward.setUtime(new Date());

                    addrMarketRewards.add(addrMarketReward);
                }
            });
        });
        return addrMarketRewards;
    }

    public void saveOrderMiningEstimateRes(Map<String, Map<Integer, BigDecimal>> orderMiningFinalRes, int cycleKey)
            throws IOException {
        List<AddressEstimateReward> estimateRewards = new ArrayList<>();

        orderMiningFinalRes.forEach((addr, rewardMap) -> {
            AddressEstimateReward estimateReward = new AddressEstimateReward();
            estimateReward.setCycleKey(cycleKey);
            estimateReward.setAddress(addr);
            estimateReward.setOrderMiningTotal(rewardMap.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add));
            estimateReward.setViteMarketReward(
                    rewardMap.getOrDefault(QuoteMarketType.VITEMarket.getValue(), BigDecimal.ZERO));
            estimateReward
                    .setEthMarketReward(rewardMap.getOrDefault(QuoteMarketType.ETHMarket.getValue(), BigDecimal.ZERO));
            estimateReward
                    .setBtcMarketReward(rewardMap.getOrDefault(QuoteMarketType.BTCMarket.getValue(), BigDecimal.ZERO));
            estimateReward.setUsdtMarketReward(
                    rewardMap.getOrDefault(QuoteMarketType.USDTMarket.getValue(), BigDecimal.ZERO));
            estimateReward.setCtime(new Date());
            estimateReward.setUtime(new Date());

            estimateRewards.add(estimateReward);
        });

        // save db
        addrEstimateRewardRepo.deleteAll();
        addrEstimateRewardRepo.saveAll(estimateRewards);
    }
}
