package org.vite.dex.mm.reward;

import com.google.protobuf.ByteString;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.vite.dex.mm.constant.constants.MarketMiningConst;
import org.vite.dex.mm.constant.enums.QuoteMarketType;
import org.vite.dex.mm.constant.enums.SettleStatus;
import org.vite.dex.mm.entity.AddressEstimateReward;
import org.vite.dex.mm.entity.InviteOrderMiningStat;
import org.vite.dex.mm.entity.MiningAddress;
import org.vite.dex.mm.entity.MiningAddressQuoteToken;
import org.vite.dex.mm.entity.SettlePage;
import org.vite.dex.mm.mapper.AddressEstimateRewardRepository;
import org.vite.dex.mm.mapper.MiningAddressQuoteTokenRepository;
import org.vite.dex.mm.mapper.MiningAddressRepository;
import org.vite.dex.mm.mapper.SettlePageRepository;
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
import java.util.stream.Collectors;

@Slf4j
@Component
public class SettleService {
    @Autowired
    private ViteCli viteCli;

    @Autowired
    MiningAddressQuoteTokenRepository miningAddressQuoteTokenRepo;

    @Autowired
    MiningAddressRepository miningAddressRepo;

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
        List<MiningAddress> miningAddressList = new ArrayList<>();
        try {
            saveMiningRewardToDB(finalRes, totalReleasedViteAmount, cycleKey, miningAddressList);
        } catch (Exception e) {
            log.error("save and settle vx reward failed ", e);
            throw e;
        }
    }

    public void saveMiningRewardToDB(FinalResult finalRes, BigDecimal totalReleasedViteAmount, int cycleKey,
            List<MiningAddress> miningAddressList) {
        Map<String, Map<Integer, BigDecimal>> orderMiningFinalRes = finalRes.getOrderMiningFinalRes();
        Map<String, InviteOrderMiningStat> inviteMiningFinalRes = finalRes.getInviteMiningFinalRes();

        List<MiningAddressQuoteToken> addrMarketRewards = assembleAddrMarketReward(orderMiningFinalRes,
                totalReleasedViteAmount, cycleKey);

        int pageMax = mergeOrderMiningAndInviteReward(orderMiningFinalRes, inviteMiningFinalRes,
                totalReleasedViteAmount, cycleKey, miningAddressList);

        List<SettlePage> settlePageList = assembleSettlePage(pageMax, cycleKey, miningAddressList);

        // save db
        miningAddressQuoteTokenRepo.saveAll(addrMarketRewards);
        miningAddressRepo.saveAll(miningAddressList);
        settlePageRepo.saveAll(settlePageList);
    }

    private List<SettlePage> assembleSettlePage(int pageMax, int cycleKey,
            List<MiningAddress> miningAddressList) {
        List<SettlePage> settlePageList = new ArrayList<>();
        for (int m = 1; m <= pageMax; m++) {
            // miningAddressList
            SettlePage settlePage = new SettlePage();
            settlePage.setCycleKey(cycleKey);
            settlePage.setDataPage(m);
            final int datePage = m;
            List<MiningAddress> miningAddressSubList = miningAddressList.stream()
                    .filter(t -> t.getDataPage() == datePage).collect(Collectors.toList());
            BigDecimal pageTotalAmount = miningAddressSubList.stream().map(MiningAddress::getTotalReward)
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
            List<MiningAddress> miningAddressList) {
        // mining_address
        int i = 0;
        for (Map.Entry<String, Map<Integer, BigDecimal>> entry : orderMiningFinalRes.entrySet()) {
            String addr = entry.getKey();
            Map<Integer, BigDecimal> rewardMap = entry.getValue();

            MiningAddress miningAddrReward = new MiningAddress();
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
                miningAddressList.add(miningAddrReward);
                i++;
            }
        }

        for (Map.Entry<String, InviteOrderMiningStat> entry : inviteMiningFinalRes.entrySet()) {
            String addr = entry.getKey();
            InviteOrderMiningStat inviteStat = entry.getValue();

            if (!orderMiningFinalRes.containsKey(addr)) {
                MiningAddress miningAddrReward = new MiningAddress();
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
                    miningAddressList.add(miningAddrReward);
                    i++;
                }
            }
        }
        return i / 30 + 1;
    }

    private List<MiningAddressQuoteToken> assembleAddrMarketReward(
            Map<String, Map<Integer, BigDecimal>> orderMiningFinalRes, BigDecimal totalReleasedViteAmount,
            int cycleKey) {
        List<MiningAddressQuoteToken> addrMarketRewards = new ArrayList<>();
        // sub-market details
        orderMiningFinalRes.forEach((addr, marketMap) -> {
            marketMap.forEach((market, vxAmount) -> {
                if (vxAmount.compareTo(BigDecimal.ZERO) > 0) {
                    MiningAddressQuoteToken addrMarketReward = new MiningAddressQuoteToken();
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

    /**
     * settle by page: save reward of each address to fund contract chain.
     * 
     * @param addrRewards
     * @throws Exception
     */
    public void settleReward(List<MiningAddress> miningAddrRewards, BigDecimal totalReleasedViteAmount, int cycleKey)
            throws Exception {
        KeyPair keyPair = viteCli.getKeyPair(MarketMiningConst.WALLET_MNEMONICS, 0);
        if (keyPair == null) {
            throw new Exception("the mnemonic is wrong");
        }

        DexOrderMiningRequest.VxSettleActions.Builder vxSettleActions = DexOrderMiningRequest.VxSettleActions
                .newBuilder();
        vxSettleActions.setPage(miningAddrRewards.get(0).getDataPage()); // todo whether right or not
        vxSettleActions.setPeriod(Long.valueOf(miningAddrRewards.get(0).getCycleKey().longValue()));

        miningAddrRewards.forEach(t -> {
            BigInteger amount = new BigInteger(t.getTotalReward().toPlainString().replace(".", ""));
            vxSettleActions.addActions(DexOrderMiningRequest.VxSettleAction.newBuilder()
                    .setAddress(ByteString.copyFrom(ViteWalletHelper.generateRealByteAddress(t.getAddress())))
                    .setAmount(ByteString.copyFrom(amount.toByteArray())).build());
        });

        List<Object> methodParams = new ArrayList<>();
        methodParams.add(vxSettleActions.build().toByteArray());

        String blockHash = viteCli.callContract(keyPair, keyPair.getAddress().toString(),
                MarketMiningConst.FUND_CONTRACT_ADDRESS, MarketMiningConst.ORDER_MINING_TOKENID, "0",
                MarketMiningConst.ABI_SETTLE, "DexFundSettleMakerMinedVx", methodParams);

        log.info("succeed to settle reward, cycleKey: {}, dataPage: {}, blockHash: {}",
                miningAddrRewards.get(0).getCycleKey(), miningAddrRewards.get(0).getDataPage(), blockHash);
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
