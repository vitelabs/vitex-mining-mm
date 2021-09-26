package org.vite.dex.mm.reward;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.vite.dex.mm.entity.TradePair;
import org.vite.dex.mm.orderbook.BlockEventStream;
import org.vite.dex.mm.orderbook.OrderBooks;
import org.vite.dex.mm.orderbook.Tokens;
import org.vite.dex.mm.orderbook.TradeRecover;
import org.vite.dex.mm.orderbook.Traveller;
import org.vite.dex.mm.reward.RewardKeeper.FinalResult;
import org.vite.dex.mm.utils.CommonUtils;
import org.vite.dex.mm.utils.client.ViteCli;

import java.math.BigDecimal;
import java.util.List;

/**
 * the core engine to calc marketMining reward
 */
@Slf4j
@Component
public class RewardEngine {
        @Autowired
        private ViteCli viteCli;

        @Autowired
        SettleService settleService;

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
                FinalResult finalRes = rewardKeeper.calcAddressMarketReward(recoveredOrderBooks, stream,
                                totalReleasedViteAmount, prevTime, endTime);
                log.info("succeed to calc each address`s market mining rewards, the result {}", finalRes);

                // 4.write reward results to DB and FundContract chain
                settleService.saveAndSettleRewards(finalRes, totalReleasedViteAmount, cycleKey);
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
                FinalResult finalRes = rewardKeeper.calcAddressMarketReward(recoveredOrderBooks, stream,
                                totalReleasedViteAmount, startTime, estimateTime);
                log.info("succeed to calc each address`s market mining rewards, the result {}", finalRes);

                // 4.save estimated result to DB
                settleService.saveOrderMiningEstimateRes(finalRes.getOrderMiningFinalRes(), totalReleasedViteAmount,
                                cycleKey);
        }

}
