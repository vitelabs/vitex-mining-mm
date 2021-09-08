package org.vite.dex.mm.reward;

import lombok.extern.slf4j.Slf4j;
import org.vite.dex.mm.entity.TradePair;
import org.vite.dex.mm.orderbook.BlockEventStream;
import org.vite.dex.mm.orderbook.OrderBooks;
import org.vite.dex.mm.orderbook.Tokens;
import org.vite.dex.mm.orderbook.TradeRecover;
import org.vite.dex.mm.orderbook.Traveller;
import org.vite.dex.mm.utils.CommonUtils;
import org.vite.dex.mm.utils.client.ViteCli;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * the core engine to calc marketMining reward
 */
@Slf4j
public class RewardEngine {
    private ViteCli viteCli;

    public RewardEngine(ViteCli viteCli) {
        this.viteCli = viteCli;
    }

    public Map<String, Map<Integer, BigDecimal>> run(long prevTime, long endTime) throws Exception {
        Traveller traveller = new Traveller();
        TradeRecover tradeRecover = new TradeRecover();

        long snapshotTime = CommonUtils.getFixedTime();
        Tokens tokens = viteCli.getAllTokenInfos();
        List<TradePair> tradePairs = viteCli.getMarketMiningTradePairs();

        // 1.travel to snapshot time
        OrderBooks snapshotOrderBooks = traveller.travelInTime(snapshotTime, tokens, viteCli, tradePairs);
        log.info("travel to checkpoint successfully, the snapshotOrderBooks`s currentHeight is {}",
                snapshotOrderBooks.getCurrentHeight());

        // 2.recover orderbooks
        TradeRecover.RecoverResult recoverResult = tradeRecover.recoverInTime(snapshotOrderBooks, prevTime, tokens,
                viteCli);
        OrderBooks recoveredOrderBooks = recoverResult.getOrderBooks();
        BlockEventStream stream = recoverResult.getStream();
        stream.patchTimestampToOrderEvent(viteCli);
        tradeRecover.fillAddressForOrdersGroupByTimeUnit(recoveredOrderBooks.getBooks(), viteCli);
        log.info(
                "recover to cycle`s startTime successfully, the recoveredOrderBooks`s currentHeight is {},the eventStream startHeight {} and endHeight {}",
                recoveredOrderBooks.getCurrentHeight(), stream.getStartHeight(), stream.getEndHeight());

        // 3.market-mining
        RewardKeeper rewardKeeper = new RewardKeeper(viteCli);
        double totalReleasedViteAmount = 1000000.0;
        Map<String, Map<Integer, BigDecimal>> finalRes = rewardKeeper.calcAddressMarketReward(recoveredOrderBooks,
                stream, totalReleasedViteAmount, prevTime, endTime, tradeRecover.miningRewardCfgMap(viteCli));
        log.info("succeed to calc each address`s market mining rewards, the result {}", finalRes);
        return finalRes;
    }
}
