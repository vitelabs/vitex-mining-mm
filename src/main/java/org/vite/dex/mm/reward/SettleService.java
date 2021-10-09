package org.vite.dex.mm.reward;

import com.google.protobuf.ByteString;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.vite.dex.mm.constant.constants.MarketMiningConst;
import org.vite.dex.mm.constant.enums.QuoteMarketType;
import org.vite.dex.mm.entity.AddressEstimateReward;
import org.vite.dex.mm.entity.AddressMarketRewardDetail;
import org.vite.dex.mm.entity.AddressSettleReward;
import org.vite.dex.mm.entity.InviteOrderMiningStat;
import org.vite.dex.mm.entity.MiningAddressReward;
import org.vite.dex.mm.mapper.AddressEstimateRewardRepository;
import org.vite.dex.mm.mapper.AddressMarketRewardRepository;
import org.vite.dex.mm.mapper.AddressTotalRewardRepository;
import org.vite.dex.mm.mapper.InviteMiningRewardRepository;
import org.vite.dex.mm.mapper.SettleRewardRepository;
import org.vite.dex.mm.model.proto.DexOrderMiningRequest;
import org.vite.dex.mm.reward.RewardKeeper.FinalResult;
import org.vite.dex.mm.utils.client.ViteCli;
import org.vitej.core.wallet.KeyPair;
import vite.ViteWalletHelper;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Component
public class SettleService {
    @Autowired
    private ViteCli viteCli;

    @Autowired
    AddressMarketRewardRepository addrMarketRewardRepo;

    @Autowired
    AddressTotalRewardRepository addrTotalRewardRepo;

    @Autowired
    AddressEstimateRewardRepository addrEstimateRewardRepo;

    @Autowired
    InviteMiningRewardRepository inviteMiningRewardRepo;

    @Autowired
    SettleRewardRepository settleRewardRepo;

    public SettleService(ViteCli viteCli) {
        this.viteCli = viteCli;
    }

    /**
     * store data in db and contract chain note: should in the same Transaction and
     * must test!
     * 
     * @param totalReleasedViteAmount
     * @param finalRes
     * @throws IOException
     */
    @Transactional
    public void saveAndSettleRewards(FinalResult finalRes, BigDecimal totalReleasedViteAmount, int cycleKey)
            throws Exception {
        Map<String, Map<Integer, BigDecimal>> orderMiningFinalRes = finalRes.getOrderMiningFinalRes();
        Map<String, InviteOrderMiningStat> inviteMiningFinalRes = finalRes.getInviteMiningFinalRes();
        List<MiningAddressReward> miningAddrRewards = new ArrayList<>();
        List<InviteOrderMiningStat> inviteOrderMiningStats = new ArrayList<>();

        try {
            saveMiningRewardToDB(orderMiningFinalRes, miningAddrRewards, totalReleasedViteAmount, cycleKey);
            saveInviteRewardToDB(inviteMiningFinalRes, inviteOrderMiningStats, cycleKey);
            settleReward(miningAddrRewards, inviteOrderMiningStats, totalReleasedViteAmount, cycleKey);
        } catch (Exception e) {
            log.error("save vx reward failed ", e);
            throw e;
        }
    }

    public void saveMiningRewardToDB(Map<String, Map<Integer, BigDecimal>> orderMiningFinalRes,
            List<MiningAddressReward> miningAddrRewards, BigDecimal totalReleasedViteAmount, int cycleKey) {
        List<AddressMarketRewardDetail> addrRewards = new ArrayList<>();
        int i = 0;
        // sub-market details
        for (Map.Entry<String, Map<Integer, BigDecimal>> addr2RewardMap : orderMiningFinalRes.entrySet()) {
            for (Map.Entry<Integer, BigDecimal> market2Reward : addr2RewardMap.getValue().entrySet()) {
                BigDecimal vxAmount = market2Reward.getValue();
                if (vxAmount.compareTo(BigDecimal.ZERO) > 0) {
                    AddressMarketRewardDetail addrMarketDetail = new AddressMarketRewardDetail();
                    addrMarketDetail.setAddress(addr2RewardMap.getKey());
                    int market = market2Reward.getKey();
                    addrMarketDetail.setQuoteTokenType(market);
                    addrMarketDetail.setAmount(vxAmount);
                    addrMarketDetail.setCycleKey(cycleKey);
                    addrMarketDetail.setDataPage(i / 30 + 1);
                    BigDecimal marketShared = totalReleasedViteAmount
                            .multiply(BigDecimal.valueOf(MarketMiningConst.getMarketSharedRatio().get(market)));
                    addrMarketDetail.setFactorRatio(vxAmount.divide(marketShared, 18, RoundingMode.DOWN));
                    addrMarketDetail.setSettleStatus(3);
                    addrMarketDetail.setCtime(new Date());
                    addrMarketDetail.setUtime(new Date());
                    if (addrMarketDetail.getAmount().compareTo(BigDecimal.ZERO) > 0) {
                        addrRewards.add(addrMarketDetail);
                    }
                }
            }
        }

        // mining stat
        orderMiningFinalRes.forEach((addr, rewardMap) -> {
            MiningAddressReward miningAddrReward = new MiningAddressReward();
            miningAddrReward.setAddress(addr);
            miningAddrReward.setTotalAmount(rewardMap.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add));
            miningAddrReward.setAmountPercent(miningAddrReward.getTotalAmount().divide(
                    totalReleasedViteAmount.multiply(MarketMiningConst.MARKET_MINING_RATIO), 18, RoundingMode.DOWN));
            miningAddrReward.setCycleKey(cycleKey);
            miningAddrReward.setDataPage(orderMiningFinalRes.size() / 30 + 1);
            miningAddrReward.setSettleStatus(3);
            miningAddrReward.setCtime(new Date());
            miningAddrReward.setUtime(new Date());
            if (miningAddrReward.getTotalAmount().compareTo(BigDecimal.ZERO) > 0) {
                miningAddrRewards.add(miningAddrReward);
            }
        });

        // save db
        addrTotalRewardRepo.saveAll(miningAddrRewards);
        addrMarketRewardRepo.saveAll(addrRewards);
    }

    private void saveInviteRewardToDB(Map<String, InviteOrderMiningStat> inviteMiningFinalRes,
            List<InviteOrderMiningStat> inviteOrderMiningStats, int cycleKey) {

        inviteMiningFinalRes.forEach((addr, inviteOrder) -> {
            InviteOrderMiningStat inviteOrderMiningStat = new InviteOrderMiningStat();
            inviteOrderMiningStat.setAddress(addr);
            inviteOrderMiningStat.setAmount(inviteOrder.getAmount());
            inviteOrderMiningStat.setRatio(inviteOrder.getRatio());
            inviteOrderMiningStat.setCycleKey(cycleKey);
            inviteOrderMiningStat.setCtime(new Date());
            inviteOrderMiningStat.setUtime(new Date());
            if (inviteOrderMiningStat.getAmount().compareTo(BigDecimal.ZERO) > 0) {
                inviteOrderMiningStats.add(inviteOrderMiningStat);
            }
        });

        inviteMiningRewardRepo.saveAll(inviteOrderMiningStats);
    }

    private List<AddressSettleReward> mergeAndSaveSettleRewards(List<MiningAddressReward> miningAddrRewards,
            List<InviteOrderMiningStat> inviteOrderMiningStats, BigDecimal totalReleasedViteAmount, int cycleKey)
            throws IOException {

        List<AddressSettleReward> settleRewards = new ArrayList<>();
        Map<String, MiningAddressReward> miningRewardMap = miningAddrRewards.stream()
                .collect(Collectors.toMap(MiningAddressReward::getAddress, Function.identity()));
        Map<String, InviteOrderMiningStat> inviteRewardMap = inviteOrderMiningStats.stream()
                .collect(Collectors.toMap(InviteOrderMiningStat::getAddress, Function.identity()));

        miningAddrRewards.forEach(orderMiningReward -> {
            AddressSettleReward settleReward = new AddressSettleReward();
            String addr = orderMiningReward.getAddress();
            settleReward.setAddress(addr);
            settleReward.setTotalAmount(orderMiningReward.getTotalAmount());
            if (inviteRewardMap.containsKey(addr)) {
                settleReward.setTotalAmount(settleReward.getTotalAmount().add(inviteRewardMap.get(addr).getAmount()));
            }
            settleReward.setCycleKey(cycleKey);
            settleReward.setDataPage(miningAddrRewards.size() / 30 + 1);
            settleReward.setSettleStatus(3);
            settleReward.setCtime(new Date());
            settleReward.setUtime(new Date());
            if (settleReward.getTotalAmount().compareTo(BigDecimal.ZERO) > 0) {
                settleRewards.add(settleReward);
            }
        });

        List<InviteOrderMiningStat> pureRewardList = inviteOrderMiningStats.stream()
                .filter(t -> !miningRewardMap.containsKey(t.getAddress())).collect(Collectors.toList());

        pureRewardList.forEach(inviteReward -> {
            AddressSettleReward settleReward = new AddressSettleReward();
            settleReward.setAddress(inviteReward.getAddress());
            settleReward.setTotalAmount(inviteReward.getAmount());
            settleReward.setCycleKey(cycleKey);
            settleReward.setDataPage((miningAddrRewards.size() + inviteOrderMiningStats.size()) / 30 + 1);
            settleReward.setSettleStatus(3);
            settleReward.setCtime(new Date());
            settleReward.setUtime(new Date());
            if (settleReward.getTotalAmount().compareTo(BigDecimal.ZERO) > 0) {
                settleRewards.add(settleReward);
            }
        });

        settleRewardRepo.saveAll(settleRewards);
        return settleRewards;
    }

    /**
     * save reward of each address to fund contract chain. Must merge order mining
     * and invite mining result
     * 
     * @param addrRewards
     * @throws Exception
     */
    public void settleReward(List<MiningAddressReward> miningAddrRewards,
            List<InviteOrderMiningStat> inviteOrderMiningStats, BigDecimal totalReleasedViteAmount, int cycleKey)
            throws Exception {
        List<AddressSettleReward> addrRewards = mergeAndSaveSettleRewards(miningAddrRewards, inviteOrderMiningStats,
                totalReleasedViteAmount, cycleKey);
        if (CollectionUtils.isEmpty(addrRewards)) {
            return;
        }

        KeyPair keyPair = viteCli.getKeyPair(MarketMiningConst.WALLET_MNEMONICS, 0);
        if (keyPair == null) {
            throw new Exception("the mnemonic is wrong");
        }

        DexOrderMiningRequest.VxSettleActions.Builder vxSettleActions = DexOrderMiningRequest.VxSettleActions
                .newBuilder();
        vxSettleActions.setPage(addrRewards.get(0).getDataPage()); // todo whether right or not
        vxSettleActions.setPeriod(Long.valueOf(addrRewards.get(0).getCycleKey().longValue()));

        addrRewards.forEach(t -> {
            BigInteger amount = new BigInteger(t.getTotalAmount().toPlainString().replace(".", ""));
            vxSettleActions.addActions(DexOrderMiningRequest.VxSettleAction.newBuilder()
                    .setAddress(ByteString.copyFrom(ViteWalletHelper.generateRealByteAddress(t.getAddress())))
                    .setAmount(ByteString.copyFrom(amount.toByteArray())).build());
        });

        List<Object> methodParams = new ArrayList<>();
        methodParams.add(vxSettleActions.build().toByteArray());

        boolean success = viteCli.callContract(keyPair, keyPair.getAddress().toString(),
                MarketMiningConst.FUND_CONTRACT_ADDRESS, MarketMiningConst.ORDER_MINING_TOKENID, "0",
                MarketMiningConst.ABI_SETTLE, "DexFundSettleMakerMinedVx", methodParams);

        assert success;
    }

    public void saveOrderMiningEstimateRes(Map<String, Map<Integer, BigDecimal>> orderMiningFinalRes,
            BigDecimal totalReleasedViteAmount, int cycleKey) throws IOException {
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
