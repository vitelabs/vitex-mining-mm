package org.vite.dex.mm.reward;

import com.google.protobuf.ByteString;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.vite.dex.mm.constant.constants.MarketMiningConst;
import org.vite.dex.mm.constant.enums.QuoteMarketType;
import org.vite.dex.mm.entity.AddressEstimateReward;
import org.vite.dex.mm.entity.AddressMarketReward;
import org.vite.dex.mm.entity.AddressSettleReward;
import org.vite.dex.mm.entity.TradePair;
import org.vite.dex.mm.mapper.AddressEstimateRewardRepository;
import org.vite.dex.mm.mapper.AddressMarketRewardRepository;
import org.vite.dex.mm.mapper.AddressTotalRewardRepository;
import org.vite.dex.mm.model.proto.DexOrderMiningRequest;
import org.vite.dex.mm.orderbook.BlockEventStream;
import org.vite.dex.mm.orderbook.OrderBooks;
import org.vite.dex.mm.orderbook.Tokens;
import org.vite.dex.mm.orderbook.TradeRecover;
import org.vite.dex.mm.orderbook.Traveller;
import org.vite.dex.mm.utils.CommonUtils;
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

/**
 * the core engine to calc marketMining reward
 */
@Slf4j
@Component
public class RewardEngine {
        @Autowired
        private ViteCli viteCli;

        @Autowired
        AddressMarketRewardRepository addrMarketRewardRepo;

        @Autowired
        AddressTotalRewardRepository addrTotalRewardRepo;

        @Autowired
        AddressEstimateRewardRepository addrEstimateRewardRepo;

        /**
         * run at 13:00 O`clock everyday and calc mining reward of each address during
         * last cycle
         * 
         * @param prevTime yesterday 12:00 p.m.
         * @param endTime  today 12:00 p.m.
         * @throws Exception
         */
        public void runDaily(long prevTime, long endTime) throws Exception {
                Traveller traveller = new Traveller();
                TradeRecover tradeRecover = new TradeRecover();
                long snapshotTime = CommonUtils.getFixedTime();
                Tokens tokens = viteCli.getAllTokenInfos();
                List<TradePair> tradePairs = CommonUtils.getMarketMiningTradePairs();

                // 1.travel to snapshot time
                OrderBooks snapshotOrderBooks = traveller.travelInTime(snapshotTime, tokens, viteCli, tradePairs);
                log.info("travel to checkpoint successfully, the snapshotOrderBooks`s currentHeight is {}",
                                snapshotOrderBooks.getCurrentHeight());

                // 2.recover orderbooks
                TradeRecover.RecoverResult recoverResult = tradeRecover.recoverInTime(snapshotOrderBooks, prevTime,
                                tokens, viteCli);
                OrderBooks recoveredOrderBooks = recoverResult.getOrderBooks();
                BlockEventStream stream = recoverResult.getStream();
                stream.patchTimestampToOrderEvent(viteCli);
                tradeRecover.fillAddressForOrdersGroupByTimeUnit(recoveredOrderBooks.getBooks(), viteCli);
                log.info("recover to cycle`s startTime successfully, the recoveredOrderBooks`s currentHeight is {},the eventStream startHeight {} and endHeight {}",
                                recoveredOrderBooks.getCurrentHeight(), stream.getStartHeight(), stream.getEndHeight());

                // 3.market-mining
                RewardKeeper rewardKeeper = new RewardKeeper(viteCli);
                int cycleKey = viteCli.getCurrentCycleKey() - 1;
                BigDecimal totalReleasedViteAmount = CommonUtils.getVxAmountByCycleKey(cycleKey);
                Map<String, Map<Integer, BigDecimal>> finalRes = rewardKeeper.calcAddressMarketReward(
                                recoveredOrderBooks, stream, totalReleasedViteAmount, prevTime, endTime);
                log.info("succeed to calc each address`s market mining rewards, the result {}", finalRes);

                // 4.write reward results to DB and FundContract chain
                saveRewards(finalRes, totalReleasedViteAmount, cycleKey);
        }

        /**
         * run every half hour and estimate mining reward of each address during this
         * cycle
         * 
         * @param estimateNodeTime
         * @throws Exception
         */
        public void runHalfHour(long startTime, long estimateTime) throws Exception {
                Traveller traveller = new Traveller();
                TradeRecover tradeRecover = new TradeRecover();
                long snapshotTime = estimateTime - 10 * 60;
                Tokens tokens = viteCli.getAllTokenInfos();
                List<TradePair> tradePairs = CommonUtils.getMarketMiningTradePairs();

                // 1.travel to 10 minutes age
                OrderBooks snapshotOrderBooks = traveller.travelInTime(snapshotTime, tokens, viteCli, tradePairs);
                log.info("travel to checkpoint successfully, the snapshotOrderBooks`s currentHeight is {}",
                                snapshotOrderBooks.getCurrentHeight());

                // 2.recover orderbooks
                TradeRecover.RecoverResult recoverResult = tradeRecover.recoverInTime(snapshotOrderBooks, startTime,
                                tokens, viteCli);
                OrderBooks recoveredOrderBooks = recoverResult.getOrderBooks();
                BlockEventStream stream = recoverResult.getStream();
                stream.patchTimestampToOrderEvent(viteCli);
                tradeRecover.fillAddressForOrdersGroupByTimeUnit(recoveredOrderBooks.getBooks(), viteCli);
                log.info("recover to cycle`s startTime successfully, the recoveredOrderBooks`s currentHeight is {},the eventStream startHeight {} and endHeight {}",
                                recoveredOrderBooks.getCurrentHeight(), stream.getStartHeight(), stream.getEndHeight());

                // 3.market-mining
                RewardKeeper rewardKeeper = new RewardKeeper(viteCli);
                int cycleKey = viteCli.getCurrentCycleKey() - 1;
                BigDecimal totalReleasedViteAmount = CommonUtils.getVxAmountByCycleKey(cycleKey);
                Map<String, Map<Integer, BigDecimal>> finalRes = rewardKeeper.calcAddressMarketReward(
                                recoveredOrderBooks, stream, totalReleasedViteAmount, startTime, estimateTime);
                log.info("succeed to calc each address`s market mining rewards, the result {}", finalRes);

                // 4.save estimated result to DB
                saveEstimateRes(finalRes, totalReleasedViteAmount, cycleKey);
        }

        /**
         * store data in db and contract chain note: should in the same Transaction and
         * must test!
         * 
         * @param totalReleasedViteAmount
         * @param finalRes
         * @throws IOException
         */
        public void saveRewards(Map<String, Map<Integer, BigDecimal>> finalRes, BigDecimal totalReleasedViteAmount,
                        int cycleKey) throws Exception {
                try {
                        saveMiningRewardToDB(finalRes, totalReleasedViteAmount, cycleKey);
                        // saveMiningRewardToFundChain(finalRes, totalReleasedViteAmount, cycleKey);
                } catch (Exception e) {
                        log.error("save vx reward failed ", e);
                        throw e;
                }
        }

        public void saveMiningRewardToDB(Map<String, Map<Integer, BigDecimal>> finalRes,
                        BigDecimal totalReleasedViteAmount, int cycleKey) {
                List<AddressMarketReward> addrRewards = new ArrayList<>();

                int i = 0;
                for (Map.Entry<String, Map<Integer, BigDecimal>> addr2RewardMap : finalRes.entrySet()) {
                        for (Map.Entry<Integer, BigDecimal> market2Reward : addr2RewardMap.getValue().entrySet()) {
                                BigDecimal vxAmount = market2Reward.getValue();
                                if (vxAmount.compareTo(BigDecimal.ZERO) > 0) {
                                        AddressMarketReward addrMarketReward = new AddressMarketReward();
                                        addrMarketReward.setAddress(addr2RewardMap.getKey());
                                        int market = market2Reward.getKey();
                                        addrMarketReward.setQuoteTokenType(market);
                                        addrMarketReward.setAmount(market2Reward.getValue());
                                        addrMarketReward.setCycleKey(cycleKey);
                                        addrMarketReward.setDataPage(i / 30 + 1);
                                        BigDecimal marketShared = totalReleasedViteAmount.multiply(BigDecimal
                                                        .valueOf(MarketMiningConst.getMarketSharedRatio().get(market)));
                                        addrMarketReward.setFactorRatio(
                                                        vxAmount.divide(marketShared, 18, RoundingMode.DOWN));
                                        addrMarketReward.setSettleStatus(3);
                                        addrMarketReward.setCtime(new Date());
                                        addrMarketReward.setUtime(new Date());

                                        addrRewards.add(addrMarketReward);
                                }
                        }
                }

                // save db
                addrMarketRewardRepo.saveAll(addrRewards);
        }

        private List<AddressSettleReward> getAddressSettleRewards(Map<String, Map<Integer, BigDecimal>> finalRes,
                        BigDecimal totalReleasedViteAmount, int cycleKey) throws IOException {
                List<AddressSettleReward> settleRewards = new ArrayList<>();

                finalRes.forEach((addr, rewardMap) -> {
                        AddressSettleReward settleReward = new AddressSettleReward();
                        settleReward.setAddress(addr);
                        settleReward.setTotalAmount(
                                        rewardMap.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add));
                        settleReward.setAmountPercent(settleReward.getTotalAmount().divide(totalReleasedViteAmount, 18,
                                        RoundingMode.DOWN));
                        settleReward.setCycleKey(cycleKey);
                        settleReward.setDataPage(finalRes.size() / 30 + 1);
                        settleReward.setSettleStatus(3);
                        settleReward.setCtime(new Date());
                        settleReward.setUtime(new Date());

                        if (settleReward.getTotalAmount().compareTo(BigDecimal.ZERO) > 0) {
                                settleRewards.add(settleReward);
                        }
                });

                // save db
                addrTotalRewardRepo.saveAll(settleRewards);
                return settleRewards;
        }

        /**
         * save MiningReward of each address to fund contract chain
         * 
         * @param addrRewards
         * @throws Exception
         */
        public void saveMiningRewardToFundChain(Map<String, Map<Integer, BigDecimal>> finalRes,
                        BigDecimal totalReleasedViteAmount, int cycleKey) throws Exception {
                List<AddressSettleReward> addrRewards = getAddressSettleRewards(finalRes, totalReleasedViteAmount,
                                cycleKey);
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
                                        .setAddress(ByteString.copyFrom(
                                                        ViteWalletHelper.generateRealByteAddress(t.getAddress())))
                                        .setAmount(ByteString.copyFrom(amount.toByteArray())).build());
                });

                List<Object> methodParams = new ArrayList<>();
                methodParams.add(vxSettleActions.build().toByteArray());

                boolean success = viteCli.callContract(keyPair, keyPair.getAddress().toString(),
                                MarketMiningConst.FUND_CONTRACT_ADDRESS, MarketMiningConst.ORDER_MINING_TOKENID, "0",
                                MarketMiningConst.ABI_SETTLE, "DexFundSettleMakerMinedVx", methodParams);

                assert success;
        }

        public void saveEstimateRes(Map<String, Map<Integer, BigDecimal>> finalRes, BigDecimal totalReleasedViteAmount,
                        int cycleKey) throws IOException {
                List<AddressEstimateReward> estimateRewards = new ArrayList<>();

                finalRes.forEach((addr, rewardMap) -> {
                        AddressEstimateReward estimateReward = new AddressEstimateReward();
                        estimateReward.setCycleKey(cycleKey);
                        estimateReward.setAddress(addr);
                        estimateReward.setOrderMiningTotal(
                                        rewardMap.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add));
                        estimateReward.setViteMarketReward(
                                        rewardMap.getOrDefault(QuoteMarketType.VITEMarket.getValue(), BigDecimal.ZERO));
                        estimateReward.setEthMarketReward(
                                        rewardMap.getOrDefault(QuoteMarketType.ETHMarket.getValue(), BigDecimal.ZERO));
                        estimateReward.setBtcMarketReward(
                                        rewardMap.getOrDefault(QuoteMarketType.BTCMarket.getValue(), BigDecimal.ZERO));
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
